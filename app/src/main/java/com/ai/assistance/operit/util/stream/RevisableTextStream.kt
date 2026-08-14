package com.ai.assistance.operit.util.stream

import kotlinx.coroutines.CoroutineScope

data class TextStreamEvent(
    val eventType: TextStreamEventType,
    val id: String
)

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
    onComplete: suspend () -> Unit = {}
): SharedStream<String> {
    val sharedTextStream = share(scope = scope, replay = replay, started = started, onComplete = onComplete)
    val carrier = this as? TextStreamEventCarrier ?: return sharedTextStream
    val sharedEventStream =
        carrier.eventChannel.share(scope = scope, replay = Int.MAX_VALUE, started = started)
    return sharedTextStream.withEventChannel(sharedEventStream)
}
