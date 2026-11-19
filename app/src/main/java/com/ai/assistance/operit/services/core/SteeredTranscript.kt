package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage

/** Boundaries are canonical display prefixes, never offsets into the raw replay stream. */
internal class SteeredTranscript {
    private data class Boundary(val displayPrefix: String, val nextTimestamp: Long)

    /**
     * Only the newest boundary is ever read, so older prefixes are dropped as newer ones arrive.
     *
     * A boundary prefix is the whole displayed turn up to that point, and one turn can publish many
     * of them: keeping every prefix would hold the entire turn once per boundary, which is most of
     * what a long tool-heavy turn retains in memory.
     */
    private var boundary: Boundary? = null
    private var rootTimestamp: Long? = null
    private var segmentStream: SteeredSegmentStream? = null
    /** The turn ended and its rows are saved; its text is let go, only row times stay. */
    private var released = false
    fun finalTimestamp(original: Long): Long =
        if (rootTimestamp == original) boundary?.nextTimestamp ?: original else original
    fun closeStream() = segmentStream?.close()

    /**
     * Called once the turn has ended and its rows are saved. A long steered turn's text would
     * otherwise stay in memory until the chat's next turn; a late snapshot of the ended turn is
     * then ignored instead of being written over its saved rows.
     */
    fun release() {
        segmentStream?.close()
        segmentStream = null
        boundary = boundary?.let { Boundary("", it.nextTimestamp) }
        released = boundary != null
    }

    fun clear() {
        segmentStream?.close()
        segmentStream = null
        boundary = null
        rootTimestamp = null
        released = false
    }

    fun add(displayPrefix: String, nextTimestamp: Long, assistantTimestamp: Long) {
        if (rootTimestamp == null) rootTimestamp = assistantTimestamp
        require(rootTimestamp == assistantTimestamp)
        require(boundary?.let { displayPrefix.startsWith(it.displayPrefix) } != false)
        boundary = Boundary(displayPrefix, nextTimestamp)
        segmentStream?.close()
        segmentStream = SteeredSegmentStream()
    }

    fun project(input: ChatMessage): List<ChatMessage> {
        val message = if (input.displayMode ==
            com.ai.assistance.operit.data.model.ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE
        ) input.copy(
            inputTokens = 0, outputTokens = 0, cachedInputTokens = 0,
            waitDurationMs = 0, outputDurationMs = 0, completedAt = 0,
        ) else input
        val lastBoundary = boundary ?: return listOf(message)
        if (message.timestamp != rootTimestamp) return listOf(message)
        if (released) return emptyList()
        val result = mutableListOf<ChatMessage>()
        // onTurnInput persists each segment before sealing it. Replaying sealed rows here
        // would push old pages back into the visible window on every streaming snapshot.
        val start = lastBoundary.displayPrefix.length
        val timestamp = lastBoundary.nextTimestamp
        // A collector may have captured a snapshot before sealing; it cannot overwrite the
        // new segment with that stale text.
        if (message.content.startsWith(lastBoundary.displayPrefix)) {
            val suffix = message.content.substring(start)
            segmentStream?.update(suffix)
            if (message.contentStream == null) segmentStream?.close()
            result += message.copy(
                content = suffix, timestamp = timestamp,
                contentStream = if (message.contentStream != null) segmentStream?.stream else null,
            )
        }
        return result
    }
}
