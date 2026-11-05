package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.Serializable
import org.json.JSONObject

/** Runtime transport selected for an installed MCP plugin. */
sealed interface McpRuntimeDescriptor {
    data class Local(val serviceName: String) : McpRuntimeDescriptor

    data class Remote(
        val endpoint: String,
        val connectionType: String,
        val bearerToken: String?,
        val headers: Map<String, String>
    ) : McpRuntimeDescriptor
}

/** A transport-neutral MCP tool definition used by the application runtime. */
@Serializable
data class McpRuntimeTool(
    val name: String,
    val description: String,
    val inputSchema: String
) {
    fun inputSchemaObject(): JSONObject = JSONObject(inputSchema)
}

data class McpRuntimeCallResult(
    val success: Boolean,
    val result: JSONObject?,
    val errorMessage: String? = null
)

/**
 * Application-facing MCP session. Implementations own their transport and lifecycle.
 * The manager is the only component that chooses a concrete implementation.
 */
interface McpRuntimeSession {
    suspend fun connect(): Boolean

    fun isConnected(): Boolean

    suspend fun isActive(): Boolean

    suspend fun listTools(): List<McpRuntimeTool>

    suspend fun getToolDescriptions(): List<String> = listTools().map { tool ->
        tool.description.takeIf { it.isNotBlank() }?.let { "${tool.name}: $it" } ?: tool.name
    }

    suspend fun callTool(name: String, arguments: Map<String, Any?>): McpRuntimeCallResult

    /**
     * Human-readable reason the last [connect] failed, when the transport knows one.
     * Startup diagnostics surface this instead of a generic verification failure.
     */
    fun getLastFailureDetail(): String? = null

    suspend fun close()
}
