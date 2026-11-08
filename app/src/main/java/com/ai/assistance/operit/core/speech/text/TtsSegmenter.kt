package com.ai.assistance.operit.core.speech.text

object TtsSegmenter {
    const val MAX_SEGMENT_LENGTH = 50
    const val END_CHARS = "!?;:。！？；：\n"

    fun findFirstEndCharIndex(text: CharSequence): Int {
        for (index in 0 until text.length) {
            if (isSegmentEndingChar(text, index)) return index
        }
        return -1
    }

    fun nextSegmentEnd(buffer: CharSequence): Int {
        val endIndex = findFirstEndCharIndex(buffer)
        if (endIndex >= 0) {
            var boundary = endIndex + 1
            while (boundary < buffer.length && isTrailingEndingChar(buffer, boundary)) {
                boundary++
            }
            return boundary
        }
        if (buffer.length >= MAX_SEGMENT_LENGTH) return buffer.length
        return -1
    }

    fun split(text: String): List<String> {
        val buffer = StringBuilder(text)
        val segments = mutableListOf<String>()

        while (buffer.isNotEmpty()) {
            val endIndex = nextSegmentEnd(buffer)
            if (endIndex < 0) break

            val segment = buffer.substring(0, endIndex).trim()
            if (segment.isNotEmpty()) segments += segment
            buffer.delete(0, endIndex)
        }

        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) segments += remaining
        return segments
    }

    private fun isSegmentEndingChar(text: CharSequence, index: Int): Boolean {
        val current = text[index]
        if (END_CHARS.indexOf(current) >= 0) {
            return true
        }

        if (current != '.') {
            return false
        }

        val next = text.getOrNull(index + 1)
        return next == null || (!next.isDigit() && next != '.')
    }

    private fun isTrailingEndingChar(text: CharSequence, index: Int): Boolean {
        val current = text[index]
        if (END_CHARS.indexOf(current) >= 0) {
            return true
        }

        if (current != '.') {
            return false
        }

        val previous = text.getOrNull(index - 1)
        return previous == '.'
    }

    private fun CharSequence.getOrNull(index: Int): Char? {
        if (index < 0 || index >= length) {
            return null
        }
        return this[index]
    }
}
