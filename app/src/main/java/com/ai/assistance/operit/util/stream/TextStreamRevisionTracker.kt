package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.markdown.SmartString

class TextStreamRevisionTracker(initialContent: String = "") {
    private val contentBuffer = SmartString(initialContent)
    private val savepoints = linkedMapOf<String, Int>()

    fun currentContent(): SmartString = contentBuffer

    fun append(chunk: String): SmartString = contentBuffer.append(chunk)

    fun savepoint(id: String) {
        // Rollback discards a suffix, so a character index is the complete revision state.
        // Retaining full snapshots here multiplies live response memory across stream consumers.
        savepoints[id] = contentBuffer.length
    }

    fun rollback(id: String): SmartString? {
        val length = savepoints[id] ?: return null
        contentBuffer.truncate(length)
        savepoints.entries.removeAll { it.value > length }
        return contentBuffer
    }

    fun replace(content: String) {
        contentBuffer.replace(content)
        savepoints.entries.removeAll { it.value > content.length }
    }
}
