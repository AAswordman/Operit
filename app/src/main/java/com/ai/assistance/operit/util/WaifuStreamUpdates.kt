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
}

/**
 * Keeps typing work relative to the latest revision-applied sentence snapshot. The splitter is
 * supplied by the renderer, so revision and scheduling tests do not need an Android native library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun streamWaifuUpdates(
    sourceStream: Stream<String>,
    splitSnapshot: (content: String, final: Boolean) -> List<String>,
    typingDelayMs: (segmentLength: Int, first: Boolean) -> Long,
    nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
): Stream<WaifuSegmentUpdate> = stream {
    coroutineScope {
        // A snapshot supersedes older pending work; retaining an append-only queue would allow
        // sentences from an abandoned attempt to be displayed after its ROLLBACK.
        val snapshots = Channel<List<String>>(Channel.CONFLATED)
        val tracker = TextStreamRevisionTracker()
        val trackerMutex = Mutex()
        val carrier = sourceStream as? TextStreamEventCarrier
        var sourceCompleted = false
        val producer = launch {
            var failure: Exception? = null
            var lastSnapshot: List<String>? = null

            suspend fun publish(final: Boolean) {
                tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                val segments = splitSnapshot(tracker.currentContent().toString(), final).toList()
                if (segments != lastSnapshot) {
                    snapshots.send(segments)
                    lastSnapshot = segments
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
                        // Text can finish before the live event subscriber runs. Its complete
                        // history must determine the last snapshot, including on provider failure.
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

        fun scheduleNext() {
            val next = target.getOrNull(displayed.size)
            if (next != pending) {
                pending = next
                dueAt = if (next == null) 0L else
                    nowMillis() + typingDelayMs(next.length, displayed.isEmpty())
            }
        }

        suspend fun accept(snapshot: List<String>) {
            var common = 0
            while (common < displayed.size && common < snapshot.size &&
                displayed[common] == snapshot[common]
            ) {
                common++
            }
            if (common < displayed.size) {
                emit(WaifuSegmentUpdate.Retain(common))
                displayed.subList(common, displayed.size).clear()
                pending = null
            }
            target = snapshot
            scheduleNext()
        }

        suspend fun appendNext() {
            val revisedSnapshot = trackerMutex.withLock {
                // The typing deadline and a queued ROLLBACK can become ready together. Read the
                // producer history before sending, even when its event subscriber has not run.
                tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                val latest = splitSnapshot(tracker.currentContent().toString(), sourceCompleted)
                if (latest != target) {
                    // The mutex also owns publication, so this consumes only a superseded snapshot.
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
            // The consumer owns the new message before its suspendable persistence finishes.
            // Track that delivery now so cancellation cleanup can retract a partially saved bubble.
            displayed += segment
            emit(WaifuSegmentUpdate.Append(segment))
            pending = null
            scheduleNext()
        }

        var completed = false
        try {
            while (true) {
                // Process known revisions before sending even an immediately due sentence.
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
                    // select is biased: a queued revision wins over a simultaneous typing timeout.
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
                    // Cancellation stops typing, but must still retract bubbles invalidated by
                    // events from the source's finalizer. No pending sentence is appended here.
                    val finalSnapshot = trackerMutex.withLock {
                        tracker.applyEventHistory(carrier?.eventChannel?.replayCache.orEmpty())
                        splitSnapshot(tracker.currentContent().toString(), sourceCompleted)
                    }
                    accept(finalSnapshot)
                }
            }
        }
    }
}

/** Applies the same segment changes to this turn's message identities and its persisted bubbles. */
internal class WaifuSegmentMessageStore<T>(
    private val messages: MutableList<T>,
    private val createMessage: (String) -> T,
    private val appendMessage: suspend (message: T, interruptSpeech: Boolean) -> Unit,
    private val removeMessages: suspend (List<T>) -> Unit,
) {
    private var interruptNextSpeech = false

    suspend fun apply(update: WaifuSegmentUpdate) {
        when (update) {
            is WaifuSegmentUpdate.Append -> {
                val interruptSpeech = messages.isEmpty() || interruptNextSpeech
                val message = createMessage(update.content)
                messages += message
                appendMessage(message, interruptSpeech)
                interruptNextSpeech = false
            }
            is WaifuSegmentUpdate.Retain -> {
                require(update.count in 0..messages.size)
                if (update.count < messages.size) {
                    removeMessages(messages.drop(update.count))
                    messages.subList(update.count, messages.size).clear()
                    interruptNextSpeech = true
                }
            }
        }
    }
}
