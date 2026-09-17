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
        /** Stable identity for this call, used to pair results back to the correct call. */
        val callId: String? = null
)

/** Represents the result of a tool execution */
@Serializable
data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val result: ToolResultData,
        val error: String? = null,
        /** Stable identity of the call this result answers, used for history rebuild pairing. */
        val callId: String? = null
)

/** Represents the validation result for tool parameters */
@Serializable data class ToolValidationResult(val valid: Boolean, val errorMessage: String = "")
