package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.markdown.SmartString

/** 使用原始输出坐标删除失败尝试的区间，保证文本和事件先后到达时结果一致。 */
class TextStreamRevisionTracker(initialContent: String = "") {
    private val rawBuffer = SmartString(initialContent)
    private val visibleBuffer = SmartString(initialContent)
    private val savepoints = linkedMapOf<String, Int>()
    private val discardedRanges = mutableListOf<DiscardedRange>()

    private data class DiscardedRange(val start: Int, val end: Int)

    internal val rawContentLength: Int get() = rawBuffer.length

    internal fun contentRange(start: Int, end: Int): String = visibleBuffer.substring(start, end)

    /** Converts producer offsets to retained text offsets, merging overlapping discarded ranges. */
    internal fun visibleOffsetAt(rawOffset: Int): Int {
        val limit = rawOffset.coerceIn(0, rawBuffer.length)
        var removed = 0
        var consumed = 0
        for (range in discardedRanges) {
            val start = maxOf(consumed, range.start)
            val end = minOf(limit, range.end)
            if (end > start) removed += end - start
            consumed = maxOf(consumed, end)
            if (consumed >= limit) break
        }
        return limit - removed
    }

    fun currentContent(): SmartString = visibleBuffer

    fun append(chunk: String): SmartString {
        val start = rawBuffer.length
        rawBuffer.append(chunk)
        val end = start + chunk.length
        var cursor = start
        for (range in discardedRanges) {
            if (range.end <= cursor) continue
            if (range.start >= end) break
            val retainedEnd = minOf(range.start, end)
            if (retainedEnd > cursor) visibleBuffer.append(chunk.substring(cursor - start, retainedEnd - start))
            cursor = maxOf(cursor, minOf(end, range.end))
        }
        if (cursor < end) visibleBuffer.append(chunk.substring(cursor - start))
        return visibleBuffer
    }
    fun savepoint(id: String, contentLength: Int) {
        savepoints[id] = contentLength
    }

    fun rollback(id: String, contentLength: Int, discardedLength: Int): SmartString? {
        if (savepoints[id] == null) return null
        if (discardedLength > 0) {
            // Map against the existing ranges before adding this interval; overlapping retries
            // must only remove characters still present in the retained buffer.
            val start = visibleOffsetAt(contentLength)
            val end = visibleOffsetAt(contentLength + discardedLength)
            visibleBuffer.removeRange(start, end)
            discardedRanges += DiscardedRange(contentLength, contentLength + discardedLength)
            discardedRanges.sortBy { it.start }
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

    fun applyEventHistory(events: List<TextStreamEvent>): SmartString {
        discardedRanges.clear()
        savepoints.clear()
        events.forEach { event ->
            when (event.eventType) {
                TextStreamEventType.SAVEPOINT -> savepoint(event.id, event.contentLength)
                TextStreamEventType.ROLLBACK -> if (event.id in savepoints) {
                    if (event.discardedLength > 0) {
                        discardedRanges += DiscardedRange(event.contentLength, event.contentLength + event.discardedLength)
                    }
                    savepoints.entries.removeAll { it.value > event.contentLength }
                }
            }
        }
        discardedRanges.sortBy { it.start }
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
