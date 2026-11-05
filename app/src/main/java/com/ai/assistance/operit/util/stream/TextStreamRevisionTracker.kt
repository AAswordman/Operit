package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.markdown.SmartString

/**
 * Applies SAVEPOINT / ROLLBACK events to a locally accumulated copy of a streamed response.
 *
 * [TextStreamEvent.contentLength] is the raw offset in the stream where the attempt starts, and
 * [TextStreamEvent.discardedLength] is how many characters the attempt produced. The visible text is
 * the raw chunk sequence with every `[contentLength, contentLength + discardedLength)` range removed.
 *
 * Both values are producer-recorded. They describe positions in the raw chunk sequence, not in the
 * visible text: a provider truncates its own buffer on rollback, so a length measured from that
 * buffer would skip over the text the attempt already discarded. Consumers must also never derive
 * the offset from their own accumulator: content chunks and revision events travel through separate
 * replayable channels, so a consumer can process a ROLLBACK before the chunks of the discarded
 * attempt arrive (they must be dropped as they arrive), or after the first chunks of the retried
 * attempt arrive (only the recorded range may be removed, the retried tail must stay).
 */
class TextStreamRevisionTracker(initialContent: String = "") {
    private val rawBuffer = SmartString(initialContent)
    private val visibleBuffer = SmartString(initialContent)
    private val savepoints = linkedMapOf<String, Int>()
    private val discardedRanges = mutableListOf<DiscardedRange>()

    private data class DiscardedRange(val start: Int, val end: Int)

    fun currentContent(): SmartString = visibleBuffer

    fun append(chunk: String): SmartString {
        val start = rawBuffer.length
        rawBuffer.append(chunk)
        val end = start + chunk.length
        val intersectsDiscardedRange =
            discardedRanges.any { range -> range.start < end && range.end > start }
        if (intersectsDiscardedRange) {
            rebuildVisibleContent()
        } else {
            visibleBuffer.append(chunk)
        }
        return visibleBuffer
    }

    /**
     * Registers a savepoint at [contentLength], the raw offset the producer recorded when it emitted
     * the event. Consumers must not re-derive this offset from their own buffer.
     */
    fun savepoint(id: String, contentLength: Int) {
        // Rollback discards a range, so the start offset is the complete revision state.
        // Retaining full snapshots here multiplies live response memory across stream consumers.
        savepoints[id] = contentLength
    }

    /**
     * Removes the attempt recorded for [id].
     *
     * [contentLength] is the raw offset where the attempt starts and [discardedLength] is the number
     * of characters it produced. Both are producer-recorded; the savepoint map is only consulted to
     * reject a ROLLBACK whose SAVEPOINT this consumer missed.
     */
    fun rollback(id: String, contentLength: Int, discardedLength: Int): SmartString? {
        if (savepoints[id] == null) return null
        if (discardedLength > 0) {
            discardedRanges += DiscardedRange(contentLength, contentLength + discardedLength)
            discardedRanges.sortBy { it.start }
            rebuildVisibleContent()
        }
        savepoints.entries.removeAll { it.value > contentLength }
        return visibleBuffer
    }

    fun replace(content: String) {
        rawBuffer.replace(content)
        visibleBuffer.replace(content)
        discardedRanges.clear()
        savepoints.entries.removeAll { it.value > content.length }
    }

    /**
     * Recomputes the visible text from every chunk received so far and the complete [events]
     * history.
     *
     * A live collector can be cancelled before it dequeues the final ROLLBACK, so the ranges it
     * maintained incrementally are not authoritative when a response ends. [rawBuffer] holds the
     * whole chunk sequence, which makes replaying the recorded history give the same text no matter
     * how far the live collector got.
     */
    fun applyEventHistory(events: List<TextStreamEvent>): SmartString {
        discardedRanges.clear()
        savepoints.clear()
        events.forEach { event ->
            when (event.eventType) {
                TextStreamEventType.SAVEPOINT -> savepoint(event.id, event.contentLength)
                TextStreamEventType.ROLLBACK ->
                    rollback(event.id, event.contentLength, event.discardedLength)
            }
        }
        rebuildVisibleContent()
        return visibleBuffer
    }

    private fun rebuildVisibleContent() {
        val raw = rawBuffer.toString()
        val rawLength = raw.length
        val visible = StringBuilder(rawLength)
        var consumed = 0
        discardedRanges.forEach { range ->
            if (range.end <= consumed || range.start >= rawLength) return@forEach
            val start = maxOf(range.start, consumed)
            if (start > consumed) {
                visible.append(raw, consumed, start)
            }
            consumed = maxOf(consumed, range.end.coerceAtMost(rawLength))
        }
        if (consumed < rawLength) {
            visible.append(raw, consumed, rawLength)
        }
        visibleBuffer.replace(visible.toString())
    }
}
