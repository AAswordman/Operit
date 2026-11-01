package com.ai.assistance.operit.core.tools.runtime

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

/** Decision returned by a tool hook before execution begins. */
sealed class AIToolHookDecision {
    object Allow : AIToolHookDecision()
    data class Block(val reason: String) : AIToolHookDecision()
}

/**
 * Hook for observing tool call lifecycle events.
 * Hooks can block a tool call by returning [AIToolHookDecision.Block] from [onToolCallIntercept].
 */
interface AIToolHook {
    /** Called when a tool call request is received. */
    fun onToolCallRequested(tool: AITool) {}

    /** Called before permission checks and executor activation. */
    fun onToolCallIntercept(tool: AITool): AIToolHookDecision = AIToolHookDecision.Allow

    /** Called after permission check is completed (if applicable). */
    fun onToolPermissionChecked(tool: AITool, granted: Boolean, reason: String? = null) {}

    /** Called when actual tool execution is about to start. */
    fun onToolExecutionStarted(tool: AITool) {}

    /** Called when a tool execution result is produced. */
    fun onToolExecutionResult(tool: AITool, result: ToolResult) {}

    /** Called when execution throws an exception. */
    fun onToolExecutionError(tool: AITool, throwable: Throwable) {}

    /** Called when the tool request lifecycle is finished. */
    fun onToolExecutionFinished(tool: AITool) {}
}
