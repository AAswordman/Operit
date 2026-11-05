package com.ai.assistance.operit.util.stream

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Collects one provider round, forwarding its revision events into [eventChannel] exactly once and
 * in producer order. Only forwarded events are shifted by [roundStartLength]; [onRollback] and
 * [onCompletion] receive revision-applied text in the provider round's own coordinate space.
 *
 * The provider must retain its complete, append-only event history in its replay cache and finish
 * emitting events before text collection exits. Its event channel may stay open afterwards.
 * [onChunk] receives every raw chunk. Before [onCompletion] runs, the live subscriber is stopped and
 * the unforwarded history is drained, including on text failure or cancellation. This cleanup does
 * not wait for the provider's event channel to close. Callback and provider failures propagate.
 */
internal suspend fun Stream<String>.collectWithRevisionRelay(
    eventChannel: MutableSharedStream<TextStreamEvent>,
    roundStartLength: Int,
    onRollback: (String) -> Unit,
    onCompletion: (String) -> Unit,
    onChunk: suspend (String) -> Unit,
) = coroutineScope {
    val providerEvents = (this@collectWithRevisionRelay as? TextStreamEventCarrier)?.eventChannel
    val tracker = TextStreamRevisionTracker()
    val trackerMutex = Mutex()
    val collectionJob = coroutineContext.job
    val stoppingRelay = AtomicBoolean(false)
    var relayFailure: Throwable? = null
    var forwardedEventCount = 0

    suspend fun forwardEvent(event: TextStreamEvent) {
        eventChannel.emit(event.shiftedBy(roundStartLength))
        // Count delivery before the cancellable tracker lock. Cancelling a local update must not
        // cause an already-published event to be emitted a second time during completion.
        forwardedEventCount++
        val rollbackContent =
            trackerMutex.withLock {
                when (event.eventType) {
                    TextStreamEventType.SAVEPOINT -> {
                        tracker.savepoint(event.id, event.contentLength)
                        null
                    }
                    TextStreamEventType.ROLLBACK ->
                        tracker.rollback(
                            event.id,
                            event.contentLength,
                            event.discardedLength,
                        )?.toString()
                }
            }
        if (rollbackContent != null) {
            onRollback(rollbackContent)
        }
    }

    val relayJob =
        providerEvents?.let { events ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    events.collect { event -> forwardEvent(event) }
                } catch (error: Throwable) {
                    // Parent cancellation and our completion stop are lifecycle cleanup. An
                    // independently cancelled event channel must still cancel this collection.
                    if (error !is CancellationException ||
                        (collectionJob.isActive && !stoppingRelay.get())
                    ) {
                        relayFailure = error
                        if (error is CancellationException) {
                            collectionJob.cancel(error)
                        }
                    }
                    throw error
                }
            }
        }

    try {
        collectionJob.ensureActive()
        this@collectWithRevisionRelay.collect { content ->
            trackerMutex.withLock { tracker.append(content) }
            onChunk(content)
        }
    } finally {
        withContext(NonCancellable) {
            stoppingRelay.set(true)
            relayJob?.cancelAndJoin()
            val history = providerEvents?.replayCache.orEmpty()
            try {
                relayFailure?.let { throw it }
                // HotStream.emit only queues delivery. Text completion can run before the relay
                // dequeues its final events, so completion explicitly publishes the remaining tail.
                for (index in forwardedEventCount until history.size) {
                    forwardEvent(history[index])
                }
            } finally {
                onCompletion(tracker.applyEventHistory(history).toString())
            }
        }
    }
}
