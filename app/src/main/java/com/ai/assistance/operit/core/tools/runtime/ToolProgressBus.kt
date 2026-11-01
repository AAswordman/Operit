package com.ai.assistance.operit.core.tools.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToolProgressEvent(
    val toolName: String,
    val progress: Float,
    val message: String = "",
    val priority: Int = 0,
    val level: Int = 0
)

object ToolProgressBus {
    const val SUMMARY_PROGRESS_TOOL_NAME: String = "__SUMMARY__"

    private val _progress = MutableStateFlow<ToolProgressEvent?>(null)
    val progress: StateFlow<ToolProgressEvent?> = _progress.asStateFlow()

    private fun priorityForTool(toolName: String): Int {
        return when (toolName) {
            SUMMARY_PROGRESS_TOOL_NAME -> 1000
            "grep_context" -> 100
            "grep_code" -> 10
            "find_files" -> 5
            else -> 0
        }
    }

    fun update(toolName: String, progress: Float, message: String = "") {
        update(toolName = toolName, progress = progress, message = message, priority = priorityForTool(toolName))
    }

    fun update(toolName: String, progress: Float, message: String = "", priority: Int, level: Int = 0) {
        val next = ToolProgressEvent(
            toolName = toolName,
            progress = progress,
            message = message,
            priority = priority,
            level = level
        )

        val current = _progress.value
        val shouldReplace =
            current == null ||
                current.toolName == next.toolName ||
                current.progress >= 1f ||
                next.priority > current.priority ||
                (next.priority == current.priority && next.level >= current.level)

        if (shouldReplace) {
            _progress.value = next
        }
    }

    fun clear() {
        _progress.value = null
    }
}
