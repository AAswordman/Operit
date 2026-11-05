package com.ai.assistance.operit.util.stream

/**
 * Collects this stream into a single string and applies the revision events carried by
 * [TextStreamEventCarrier].
 *
 * Plain `collect { append(it) }` keeps the prefix of an aborted attempt: a retry republishes the
 * request from its start, so the raw chunk sequence still contains everything the discarded
 * attempt emitted. [TextStreamEvent.contentLength] and [TextStreamEvent.discardedLength] describe
 * exactly which slice of the raw sequence belongs to the discarded attempt.
 *
 * The events are replayed after the chunks have been collected. Text and events travel through two
 * separate replayable channels, so a consumer that applies an event while collecting can truncate
 * before the lagging chunks of the discarded attempt arrive, or append retried chunks before their
 * ROLLBACK arrives. Replaying the complete event history against the complete raw sequence does not
 * depend on that scheduling.
 *
 * [onChunk] receives every chunk as it arrives so callers can keep forwarding a live stream. The
 * returned string is the revision-applied text, not the concatenation of the forwarded chunks.
 */
suspend fun Stream<String>.collectRevisionAppliedText(
    onChunk: suspend (String) -> Unit = {},
): String {
    val content = StringBuilder()
    collect { chunk ->
        content.append(chunk)
        onChunk(chunk)
    }

    val carrier = this as? TextStreamEventCarrier ?: return content.toString()
    return applyRevisionEvents(content.toString(), carrier.eventChannel.replayCache)
}

/** Removes every discarded attempt from [rawContent]. */
fun applyRevisionEvents(rawContent: String, events: List<TextStreamEvent>): String {
    val tracker = TextStreamRevisionTracker(rawContent)
    tracker.applyEventHistory(events)
    return tracker.currentContent().toString()
}
