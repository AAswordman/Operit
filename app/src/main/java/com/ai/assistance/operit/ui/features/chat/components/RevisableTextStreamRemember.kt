package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.util.stream.MutableSharedStream
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
    val carrier = sourceStream as? TextStreamEventCarrier ?: return sourceStream

    var displayStream by remember(sourceStream) {
        mutableStateOf<Stream<String>?>(MutableSharedStream(replay = Int.MAX_VALUE))
    }

    LaunchedEffect(sourceStream) {
        val tracker = TextStreamRevisionTracker()
        val stateMutex = Mutex()
        var currentOutputStream = MutableSharedStream<String>(replay = Int.MAX_VALUE)
        displayStream = currentOutputStream

        coroutineScope {
            val eventJob = launch {
                carrier.eventChannel.collect { event ->
                    when (event.eventType) {
                        TextStreamEventType.SAVEPOINT -> {
                            stateMutex.withLock {
                                tracker.savepoint(event.id)
                            }
                        }

                        TextStreamEventType.ROLLBACK -> {
                            val rollbackUpdate =
                                stateMutex.withLock {
                                    val snapshot = tracker.rollback(event.id)?.toString()
                                        ?: return@withLock null
                                    val replacementStream =
                                        MutableSharedStream<String>(replay = Int.MAX_VALUE)
                                    val previousOutputStream = currentOutputStream
                                    currentOutputStream = replacementStream
                                    Triple(previousOutputStream, replacementStream, snapshot)
                                } ?: return@collect
                            val (previousOutputStream, replacementStream, snapshot) = rollbackUpdate
                            // The renderer restores this prefix itself; the replacement stream carries only new tail output.
                            val rollbackStream =
                                RollbackTailStream(
                                    upstream = replacementStream,
                                    rollbackPrefix = snapshot,
                                )
                            displayStream = rollbackStream
                            previousOutputStream.resetReplayCache()
                        }
                    }
                }
            }

            try {
                sourceStream.collect { chunk ->
                    val activeDisplayStream =
                        stateMutex.withLock {
                            tracker.append(chunk)
                            currentOutputStream
                        }
                    activeDisplayStream.emit(chunk)
                }
            } finally {
                eventJob.cancelAndJoin()
                currentOutputStream.resetReplayCache()
                displayStream = null
            }
        }
    }

    return displayStream
}
