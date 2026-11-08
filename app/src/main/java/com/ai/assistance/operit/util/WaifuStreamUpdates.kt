package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface WaifuSegmentUpdate {
    data class Append(val content: String) : WaifuSegmentUpdate
    data class Retain(val count: Int) : WaifuSegmentUpdate
    data class Preview(val content: String) : WaifuSegmentUpdate
}

private data class WaifuPendingSnapshot(val segments: List<String>, val preview: String?)

/** 逐句展示以最新修订快照为准，撤回失效气泡并清理待发送的旧句子。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun streamWaifuUpdates(
    sourceStream: Stream<String>,
    splitSnapshot: (content: String, final: Boolean) -> List<String>,
    typingDelayMs: (segmentLength: Int, first: Boolean) -> Long,
    nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    firstSegmentPreview: ((String) -> String?)? = null,
): Stream<WaifuSegmentUpdate> = stream {
    coroutineScope {
        val snapshots = Channel<WaifuPendingSnapshot>(Channel.CONFLATED)
        val tracker = TextStreamRevisionTracker()
        val trackerMutex = Mutex()
        val carrier = sourceStream as? TextStreamEventCarrier
        var sourceCompleted = false
        fun currentSnapshot(final: Boolean): WaifuPendingSnapshot {
            val content = tracker.currentContent().toString()
            val segments = splitSnapshot(content, final).toList()
            val preview = if (!final && segments.isEmpty()) firstSegmentPreview?.invoke(content) else null
            return WaifuPendingSnapshot(segments, preview?.takeIf { it.isNotBlank() })
        }
        val producer = launch {
            var failure: Exception? = null
            var lastSnapshot: WaifuPendingSnapshot? = null

            suspend fun publish(final: Boolean) {
                tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                val snapshot = currentSnapshot(final)
                if (snapshot != lastSnapshot) {
                    snapshots.send(snapshot)
                    lastSnapshot = snapshot
                }
            }

            try {
                coroutineScope {
                    val revisions = carrier?.let {
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            it.eventChannel.collect {
                                trackerMutex.withLock { publish(final = false) }
                            }
                        }
                    }
                    try {
                        sourceStream.collect { chunk ->
                            trackerMutex.withLock {
                                tracker.append(chunk)
                                publish(final = false)
                            }
                        }
                        sourceCompleted = true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failure = error
                    } finally {
                        withContext(NonCancellable) { revisions?.cancelAndJoin() }
                    }
                    trackerMutex.withLock {
                        tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                        publish(final = failure == null)
                    }
                }
            } catch (error: CancellationException) {
                failure = error
                throw error
            } catch (error: Exception) {
                failure = error
            } finally {
                snapshots.close(failure)
            }
        }

        val displayed = mutableListOf<String>()
        var target = emptyList<String>()
        var sourceClosed = false
        var pending: String? = null
        var dueAt = 0L
        var previewContent: String? = null

        fun scheduleNext() {
            val next = target.getOrNull(displayed.size)
            if (next != pending) {
                pending = next
                dueAt = if (next == null) 0L else
                    nowMillis() + typingDelayMs(next.length, displayed.isEmpty())
            }
        }

        suspend fun accept(snapshot: WaifuPendingSnapshot, allowPreview: Boolean = true) {
            val segments = snapshot.segments
            var common = 0
            while (common < displayed.size && common < segments.size &&
                displayed[common] == segments[common]
            ) {
                common++
            }
            if (common < displayed.size) {
                emit(WaifuSegmentUpdate.Retain(common))
                displayed.subList(common, displayed.size).clear()
                pending = null
                if (common == 0) previewContent = null
            }
            if (displayed.isEmpty() && segments.isEmpty()) {
                val preview = snapshot.preview
                if (allowPreview && preview != null && preview != previewContent) {
                    emit(WaifuSegmentUpdate.Preview(preview))
                    previewContent = preview
                } else if (previewContent != null && previewContent != preview) {
                    emit(WaifuSegmentUpdate.Retain(0))
                    previewContent = null
                }
            }
            target = segments
            scheduleNext()
        }

        suspend fun appendNext() {
            val revisedSnapshot = trackerMutex.withLock {
                tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                val latest = currentSnapshot(sourceCompleted)
                if (latest.segments != target ||
                    (displayed.isEmpty() && latest.segments.isEmpty() && latest.preview != previewContent)
                ) {
                    snapshots.tryReceive()
                    latest
                } else {
                    null
                }
            }
            if (revisedSnapshot != null) {
                accept(revisedSnapshot)
                return
            }
            val segment = target[displayed.size]
            displayed += segment
            emit(WaifuSegmentUpdate.Append(segment))
            previewContent = null
            pending = null
            scheduleNext()
        }

        var completed = false
        try {
            while (true) {
                val incoming = snapshots.tryReceive()
                if (incoming.isSuccess) {
                    accept(incoming.getOrThrow())
                    continue
                }
                if (incoming.isClosed) {
                    incoming.exceptionOrNull()?.let { throw it }
                    sourceClosed = true
                }
                if (displayed.size == target.size) {
                    if (sourceClosed) break
                    val next = snapshots.receiveCatching()
                    if (next.isSuccess) {
                        accept(next.getOrThrow())
                    } else {
                        next.exceptionOrNull()?.let { throw it }
                        sourceClosed = true
                    }
                    continue
                }
                val remainingDelay = (dueAt - nowMillis()).coerceAtLeast(0L)
                if (remainingDelay == 0L) {
                    appendNext()
                } else if (sourceClosed) {
                    delay(remainingDelay)
                    appendNext()
                } else {
                    select<Unit> {
                        snapshots.onReceiveCatching { next ->
                            if (next.isSuccess) {
                                accept(next.getOrThrow())
                            } else {
                                next.exceptionOrNull()?.let { throw it }
                                sourceClosed = true
                            }
                        }
                        onTimeout(remainingDelay) { appendNext() }
                    }
                }
            }
            producer.join()
            completed = true
        } finally {
            withContext(NonCancellable) {
                producer.cancelAndJoin()
                if (!completed) {
                    val finalSnapshot = trackerMutex.withLock {
                        tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                        currentSnapshot(sourceCompleted)
                    }
                    accept(finalSnapshot, allowPreview = false)
                }
            }
        }
    }
}

internal class WaifuSegmentMessageStore<T>(
    private val messages: MutableList<T>,
    private val createMessage: (String) -> T,
    private val appendMessage: suspend (message: T, interruptSpeech: Boolean) -> Unit,
    private val removeMessages: suspend (List<T>) -> Unit,
    private val reviseMessage: ((T, String) -> T)? = null,
    private val publishPreview: suspend (T) -> Unit = {},
) {
    private var interruptNextSpeech = false
    private var hasPreview = false

    suspend fun apply(update: WaifuSegmentUpdate) {
        when (update) {
            is WaifuSegmentUpdate.Append -> {
                val interruptSpeech = messages.isEmpty() || hasPreview || interruptNextSpeech
                val message = if (hasPreview) {
                    checkNotNull(reviseMessage)(messages.last(), update.content).also {
                        messages[messages.lastIndex] = it
                    }
                } else {
                    createMessage(update.content).also { messages += it }
                }
                appendMessage(message, interruptSpeech)
                hasPreview = false
                interruptNextSpeech = false
            }
            is WaifuSegmentUpdate.Preview -> {
                val message = if (hasPreview) {
                    checkNotNull(reviseMessage)(messages.last(), update.content).also {
                        messages[messages.lastIndex] = it
                    }
                } else {
                    check(messages.isEmpty())
                    createMessage(update.content).also { messages += it }
                }
                hasPreview = true
                publishPreview(message)
            }
            is WaifuSegmentUpdate.Retain -> {
                require(update.count in 0..messages.size)
                if (update.count < messages.size) {
                    removeMessages(messages.drop(update.count))
                    messages.subList(update.count, messages.size).clear()
                    interruptNextSpeech = true
                    hasPreview = false
                }
            }
        }
    }
}
