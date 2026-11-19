package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.core.tools.ToolResultData
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/** Represents a tool parameter in an AI tool */
@Serializable data class ToolParameter(val name: String, val value: String)

/** Represents a tool that can be used by the AI */
@Serializable
data class AITool(
        val name: String,
        val parameters: List<ToolParameter> = emptyList(),
        val description: String = ""
)

/** Represents an invocation of a tool in the AI's response */
@Serializable
data class ToolInvocation(
        val tool: AITool,
        val rawText: String,
        @Contextual
        val responseLocation: IntRange, // Where in the response this tool invocation was found
        val callId: String? = null,
        val invocationIndex: Int = -1
)

/** Represents the result of a tool execution */
@Serializable
data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val result: ToolResultData,
        val error: String? = null,
        val callId: String? = null,
        val invocationIndex: Int? = null,
        val executionDurationMs: Long? = null,
        val executionState: ToolExecutionState? = null,
        val isFinal: Boolean = false
)

@Serializable
enum class ToolExecutionState {
    WAITING_AUTHORIZATION,
    WAITING_EXECUTION,
    RUNNING,
    COMPLETED,
    NOT_EXECUTED
}

/** Represents the validation result for tool parameters */
@Serializable data class ToolValidationResult(val valid: Boolean, val errorMessage: String = "")
