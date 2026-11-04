package com.ai.assistance.operit.ui.features.conversation.chat.foundation.stream

import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.core.chat.protocol.ProtocolMetaDisplayFilter
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamRollbackPrefix
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private class RollbackTailStream(
    private val upstream: Stream<String>,
    override val rollbackPrefix: String,
) : Stream<String> by upstream, StreamRollbackPrefix

@Composable
fun rememberRevisableTextStream(sourceStream: Stream<String>?): Stream<String>? {
    if (sourceStream == null) return null
    val carrier = sourceStream as? TextStreamEventCarrier

    var displayStream by remember(sourceStream) {
        mutableStateOf<Stream<String>?>(MutableSharedStream(replay = Int.MAX_VALUE))
    }

    LaunchedEffect(sourceStream) {
        val tracker = TextStreamRevisionTracker()
        val displayFilter = ProtocolMetaDisplayFilter()
        val stateMutex = Mutex()
        var currentOutputStream = MutableSharedStream<String>(replay = Int.MAX_VALUE)
        displayStream = currentOutputStream

        coroutineScope {
            val eventJob = carrier?.let { eventCarrier ->
                launch {
                    eventCarrier.eventChannel.collect { event ->
                        when (event.eventType) {
                            TextStreamEventType.SAVEPOINT -> {
                                stateMutex.withLock {
                                    tracker.savepoint(event.id, event.contentLength)
                                }
                            }

                            TextStreamEventType.ROLLBACK -> {
                                val rollbackUpdate =
                                    stateMutex.withLock {
                                        val snapshot = tracker.rollback(event.id, event.contentLength, event.discardedLength)?.toString()
                                            ?: return@withLock null
                                        val replacementStream =
                                            MutableSharedStream<String>(replay = Int.MAX_VALUE)
                                        val previousOutputStream = currentOutputStream
                                        currentOutputStream = replacementStream
                                        Triple(previousOutputStream, replacementStream, displayFilter.reset(snapshot))
                                    } ?: return@collect
                                val (previousOutputStream, replacementStream, visiblePrefix) = rollbackUpdate
                                // 渲染器恢复过滤后的前缀，新流仅承载后续可见增量。
                                val rollbackStream =
                                    RollbackTailStream(
                                        upstream = replacementStream,
                                        rollbackPrefix = visiblePrefix,
                                    )
                                displayStream = rollbackStream
                                previousOutputStream.resetReplayCache()
                            }
                        }
                    }
                }
            }

            try {
                sourceStream.collect { chunk ->
                    val (activeDisplayStream, visibleChunk) =
                        stateMutex.withLock {
                            // 保存点使用完整原文，只有显示增量进入 Markdown 解析。
                            val previousLength = tracker.currentContent().length
                            val visible = tracker.append(chunk).substring(previousLength)
                            currentOutputStream to displayFilter.append(visible)
                        }
                    if (visibleChunk.isNotEmpty()) activeDisplayStream.emit(visibleChunk)
                }
                val (activeDisplayStream, tail) = stateMutex.withLock {
                    currentOutputStream to displayFilter.finish()
                }
                if (tail.isNotEmpty()) activeDisplayStream.emit(tail)
            } finally {
                withContext(NonCancellable) {
                    eventJob?.cancelAndJoin()
                    val replacement = stateMutex.withLock {
                        val previous = tracker.currentContent().toString()
                        val snapshot = tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty()).toString()
                        if (snapshot != previous) {
                            val oldOutput = currentOutputStream
                            currentOutputStream = MutableSharedStream(replay = Int.MAX_VALUE)
                            oldOutput.resetReplayCache()
                            RollbackTailStream(currentOutputStream, displayFilter.reset(snapshot))
                        } else null
                    }
                    if (replacement != null) displayStream = replacement
                }
                // 静态消息接管前继续保留已完成的显示回放，避免最后一帧与清理竞争。
                (currentOutputStream as? MutableSharedStreamImpl<String>)?.close()
            }
        }
    }

    return displayStream
}
