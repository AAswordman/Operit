package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker

/** Owned by the background stream consumer; all calls are serialized with its mutex. */
internal class RevisableTextDisplayState {
    private val tracker = TextStreamRevisionTracker()
    private val filter = ProtocolMetaDisplayFilter()
    private val visible = StringBuilder()
    private var parsedLength = 0
    private val savepoints = linkedMapOf<String, Int>()
    private val checkpoints = linkedMapOf<String, Checkpoint>()
    private val pending = linkedMapOf<String, Int>()
    private val processedEvents = mutableMapOf<TextStreamEvent, Int>()
    private val origin = Checkpoint(0, 0, 0, filter.checkpoint())

    private data class Checkpoint(
        val rawOffset: Int,
        val sourceOffset: Int,
        val visibleLength: Int,
        val parser: ProtocolMetaDisplayFilter.Snapshot,
    )

    fun append(chunk: String): String {
        tracker.append(chunk)
        return buildString {
            val ready = pending.entries.filter { it.value <= tracker.rawContentLength }.sortedBy { it.value }
            for ((id, rawOffset) in ready) {
                val offset = tracker.visibleOffsetAt(rawOffset)
                if (offset >= parsedLength) append(parseUntil(offset))
                checkpoints[id] = capture(rawOffset)
                pending.remove(id)
            }
            append(parseUntil(tracker.currentContent().length))
        }
    }

    /** Returns a replacement visible prefix only when a received rollback changes existing text. */
    fun apply(event: TextStreamEvent): String? {
        processedEvents[event] = (processedEvents[event] ?: 0) + 1
        when (event.eventType) {
            TextStreamEventType.SAVEPOINT -> {
                tracker.savepoint(event.id, event.contentLength)
                savepoints[event.id] = event.contentLength
                pending.remove(event.id)
                checkpoints.remove(event.id)
                if (event.contentLength <= tracker.rawContentLength) checkpoints[event.id] = capture(event.contentLength)
                else pending[event.id] = event.contentLength
                return null
            }
            TextStreamEventType.ROLLBACK -> {
                if (event.id !in savepoints) return null
                // Capture before altering the retained source, including a savepoint received late.
                val checkpoint = if (event.contentLength <= tracker.rawContentLength) {
                    checkpoints[event.id]?.takeIf { it.rawOffset == event.contentLength } ?: capture(event.contentLength)
                } else null
                tracker.rollback(event.id, event.contentLength, event.discardedLength)
                savepoints.entries.removeAll { it.value > event.contentLength }
                checkpoints.entries.removeAll { it.value.rawOffset > event.contentLength }
                pending.entries.removeAll { it.value > event.contentLength }
                if (checkpoint == null || event.discardedLength == 0) return null
                filter.restore(checkpoint.parser)
                visible.setLength(checkpoint.visibleLength)
                parsedLength = checkpoint.sourceOffset
                parseUntil(tracker.currentContent().length)
                return visible.toString()
            }
        }
    }

    /** Event delivery can lag behind text completion; apply only occurrences not yet consumed. */
    fun reconcile(history: List<TextStreamEvent>): String? {
        val occurrences = mutableMapOf<TextStreamEvent, Int>()
        var replacement: String? = null
        for (event in history) {
            val count = (occurrences[event] ?: 0) + 1
            occurrences[event] = count
            if (count > (processedEvents[event] ?: 0)) apply(event)?.let { replacement = it }
        }
        return replacement
    }

    fun finish(): String = filter.finish().also { visible.append(it) }

    internal fun visibleContent(): String = visible.toString()

    private fun parseUntil(offset: Int): String {
        if (offset == parsedLength) return ""
        val text = tracker.contentRange(parsedLength, offset)
        parsedLength = offset
        return filter.append(text).also { visible.append(it) }
    }

    private fun capture(rawOffset: Int): Checkpoint {
        val offset = tracker.visibleOffsetAt(rawOffset)
        if (offset == parsedLength) return Checkpoint(rawOffset, offset, visible.length, filter.checkpoint())
        // A producer can publish the savepoint after its text was already consumed. Derive that
        // one checkpoint from the nearest retained parser state, without resetting the live filter.
        val earlier = checkpoints.values.fold(origin) { best, candidate ->
            if (candidate.rawOffset <= rawOffset && candidate.sourceOffset <= offset &&
                candidate.sourceOffset >= best.sourceOffset) candidate else best
        }
        val projection = ProtocolMetaDisplayFilter()
        projection.restore(earlier.parser)
        val delta = projection.append(tracker.contentRange(earlier.sourceOffset, offset))
        return Checkpoint(rawOffset, offset, earlier.visibleLength + delta.length, projection.checkpoint())
    }
}
