package com.ai.assistance.operit.util.stream

import kotlinx.coroutines.CoroutineScope

/**
 * A revision event for a streamed text response.
 *
 * [contentLength] is the offset in the raw chunk sequence where the attempt starts, recorded by the
 * producer when the event was emitted. [discardedLength] is how many characters the aborted attempt
 * produced; a ROLLBACK removes `[contentLength, contentLength + discardedLength)` from the raw
 * chunk sequence.
 *
 * Both values describe the raw sequence, not the visible text: a producer truncates its own buffer
 * when it rolls back, so a length measured from that buffer skips over text an earlier attempt
 * already discarded. Consumers must not re-derive either value from their own accumulator either:
 * content chunks and revision events travel through separate coroutines and replayable side
 * channels, so an event can be processed before or after the chunks the producer emitted alongside
 * it.
 */
data class TextStreamEvent(
    val eventType: TextStreamEventType,
    val id: String,
    val contentLength: Int,
    val discardedLength: Int = 0
)

/**
 * Moves an event recorded in a single request's coordinate space into the coordinate space of a
 * stream that concatenates several requests.
 *
 * Providers emit revision events for one request. A chat turn that executes tools performs several
 * requests and publishes all of them into one response stream, so an event of a later request must
 * be offset by the number of characters published before that request started.
 */
fun TextStreamEvent.shiftedBy(offset: Int): TextStreamEvent {
    if (offset == 0) return this
    return copy(contentLength = contentLength + offset)
}

enum class TextStreamEventType {
    SAVEPOINT,
    ROLLBACK
}

interface TextStreamEventCarrier {
    val eventChannel: SharedStream<TextStreamEvent>
}

/** Marks a replacement stream with content already rendered before a rollback. */
internal interface StreamRollbackPrefix {
    val rollbackPrefix: String
}

interface RevisableTextStream : Stream<String>, TextStreamEventCarrier

interface RevisableSharedTextStream : SharedStream<String>, RevisableTextStream

interface RevisableCharStream : Stream<Char>, TextStreamEventCarrier

private class DelegatingRevisableTextStream(
    private val upstream: Stream<String>,
    override val eventChannel: SharedStream<TextStreamEvent>
) : RevisableTextStream {
    override val isLocked: Boolean
        get() = upstream.isLocked

    override val bufferedCount: Int
        get() = upstream.bufferedCount

    override suspend fun lock() {
        upstream.lock()
    }

    override suspend fun unlock() {
        upstream.unlock()
    }

    override fun clearBuffer() {
        upstream.clearBuffer()
    }

    override suspend fun collect(collector: StreamCollector<String>) {
        upstream.collect(collector)
    }
}

private class DelegatingRevisableSharedTextStream(
    private val upstream: SharedStream<String>,
    override val eventChannel: SharedStream<TextStreamEvent>
) : RevisableSharedTextStream {
    override val isLocked: Boolean
        get() = upstream.isLocked

    override val bufferedCount: Int
        get() = upstream.bufferedCount

    override val subscriptionCount: Int
        get() = upstream.subscriptionCount

    override val replayCache: List<String>
        get() = upstream.replayCache

    override val completionCause: Throwable?
        get() = upstream.completionCause

    override suspend fun lock() {
        upstream.lock()
    }

    override suspend fun unlock() {
        upstream.unlock()
    }

    override fun clearBuffer() {
        upstream.clearBuffer()
    }

    override suspend fun collect(collector: StreamCollector<String>) {
        upstream.collect(collector)
    }
}

private class DelegatingRevisableCharStream(
    private val upstream: Stream<Char>,
    override val eventChannel: SharedStream<TextStreamEvent>
) : RevisableCharStream {
    override val isLocked: Boolean
        get() = upstream.isLocked

    override val bufferedCount: Int
        get() = upstream.bufferedCount

    override suspend fun lock() {
        upstream.lock()
    }

    override suspend fun unlock() {
        upstream.unlock()
    }

    override fun clearBuffer() {
        upstream.clearBuffer()
    }

    override suspend fun collect(collector: StreamCollector<Char>) {
        upstream.collect(collector)
    }
}

private class DelegatingRollbackPrefixCharStream(
    private val upstream: Stream<Char>,
    override val rollbackPrefix: String,
) : Stream<Char> by upstream, StreamRollbackPrefix

fun Stream<String>.withEventChannel(eventChannel: SharedStream<TextStreamEvent>): Stream<String> {
    if (this is RevisableTextStream && this.eventChannel === eventChannel) {
        return this
    }
    return DelegatingRevisableTextStream(this, eventChannel)
}

fun SharedStream<String>.withEventChannel(
    eventChannel: SharedStream<TextStreamEvent>
): SharedStream<String> {
    if (this is RevisableSharedTextStream && this.eventChannel === eventChannel) {
        return this
    }
    return DelegatingRevisableSharedTextStream(this, eventChannel)
}

fun Stream<Char>.withTextEventChannel(eventChannel: SharedStream<TextStreamEvent>): Stream<Char> {
    if (this is RevisableCharStream && this.eventChannel === eventChannel) {
        return this
    }
    return DelegatingRevisableCharStream(this, eventChannel)
}

internal fun Stream<Char>.withRollbackPrefix(rollbackPrefix: String): Stream<Char> {
    if (this is StreamRollbackPrefix && this.rollbackPrefix == rollbackPrefix) {
        return this
    }
    return DelegatingRollbackPrefixCharStream(this, rollbackPrefix)
}

fun Stream<String>.shareRevisable(
    scope: CoroutineScope,
    replay: Int = 0,
    started: StreamStart = StreamStart.EAGERLY,
    onComplete: suspend () -> Unit = {},
    propagateCompletionCause: Boolean = true,
): SharedStream<String> {
    val sharedTextStream = share(
        scope = scope,
        replay = replay,
        started = started,
        onComplete = onComplete,
        propagateCompletionCause = propagateCompletionCause,
    )
    val carrier = this as? TextStreamEventCarrier ?: return sharedTextStream
    // Attach the producer's event channel directly. Wrapping it in another share() would deliver
    // revision events from a separate coroutine, so a ROLLBACK could arrive after the last text
    // chunk and a consumer that replays the shared stream would never see it.
    return sharedTextStream.withEventChannel(carrier.eventChannel)
}
