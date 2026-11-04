package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.markdown.SmartString

/** 使用原始输出坐标删除失败尝试的区间，保证文本和事件先后到达时结果一致。 */
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

    fun savepoint(id: String, contentLength: Int) {
        savepoints[id] = contentLength
    }

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
