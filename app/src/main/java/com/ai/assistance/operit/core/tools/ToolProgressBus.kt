package com.ai.assistance.operit.core.tools

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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

    private data class ScopedProgress(
        val event: ToolProgressEvent,
        val updateOrder: Long,
    )

    private val scopeThreadLocal = ThreadLocal<Long?>()
    private val scopeSequence = AtomicLong(0L)
    private val updateSequence = AtomicLong(0L)
    private val lock = Any()
    private val progressByScope = linkedMapOf<Long?, ScopedProgress>()

    internal fun newScopeId(): Long = scopeSequence.incrementAndGet()

    internal suspend fun <T> withScope(scopeId: Long, block: suspend () -> T): T {
        return withContext(scopeThreadLocal.asContextElement(scopeId)) {
            block()
        }
    }

    private fun currentScopeId(): Long? = scopeThreadLocal.get()

    private fun priorityForTool(toolName: String): Int {
        return when (toolName) {
            SUMMARY_PROGRESS_TOOL_NAME -> 1000
            "grep_context" -> 100
            "grep_code" -> 10
            "find_files" -> 5
            else -> 0
        }
    }

    fun update(
        toolName: String,
        progress: Float,
        message: String = "",
        scopeId: Long? = currentScopeId(),
    ) {
        update(
            toolName = toolName,
            progress = progress,
            message = message,
            priority = priorityForTool(toolName),
            scopeId = scopeId,
        )
    }

    fun update(
        toolName: String,
        progress: Float,
        message: String = "",
        priority: Int,
        level: Int = 0,
        scopeId: Long? = currentScopeId(),
    ) {
        val next = ToolProgressEvent(
            toolName = toolName,
            progress = progress,
            message = message,
            priority = priority,
            level = level
        )
        synchronized(lock) {
            val current = progressByScope[scopeId]?.event
            val shouldReplace =
                current == null ||
                    current.toolName == next.toolName ||
                    current.progress >= 1f ||
                    next.priority > current.priority ||
                    (next.priority == current.priority && next.level >= current.level)
            if (shouldReplace) {
                progressByScope[scopeId] =
                    ScopedProgress(
                        event = next,
                        updateOrder = updateSequence.incrementAndGet(),
                    )
                publishVisibleProgressLocked()
            }
        }
    }

    /** Clears only the caller's progress scope and keeps other active scopes visible. */
    fun clear(scopeId: Long? = currentScopeId()) {
        synchronized(lock) {
            if (progressByScope.remove(scopeId) != null) {
                publishVisibleProgressLocked()
            }
        }
    }

    private fun publishVisibleProgressLocked() {
        var selected: ScopedProgress? = null
        progressByScope.values.forEach { candidate ->
            val current = selected
            if (
                current == null ||
                    candidate.event.priority > current.event.priority ||
                    (candidate.event.priority == current.event.priority &&
                        candidate.event.level > current.event.level) ||
                    (candidate.event.priority == current.event.priority &&
                        candidate.event.level == current.event.level &&
                        candidate.updateOrder > current.updateOrder)
            ) {
                selected = candidate
            }
        }
        _progress.value = selected?.event
    }

    internal fun resetForTest() {
        synchronized(lock) {
            progressByScope.clear()
            _progress.value = null
        }
    }
}
