package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeCallResult
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeSession
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeTool
import org.json.JSONArray
import org.json.JSONObject

/** Local stdio runtime backed by the Node bridge. */
class BridgeMcpRuntimeSession(
    context: Context,
    private val serviceName: String
) : McpRuntimeSession {
    private val bridgeClient = MCPBridgeClient(context, serviceName)

    override suspend fun connect(): Boolean = bridgeClient.connect()

    override fun isConnected(): Boolean = bridgeClient.isConnected()

    override suspend fun isActive(): Boolean = bridgeClient.isActive()

    override suspend fun listTools(): List<McpRuntimeTool> = bridgeClient.getTools().mapNotNull { tool ->
        val name = tool.optString("name").trim()
        if (name.isEmpty()) return@mapNotNull null

        McpRuntimeTool(
            name = name,
            description = tool.optString("description"),
            inputSchema = tool.optJSONObject("inputSchema")?.toString() ?: JSONObject().toString()
        )
    }

    override suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>
    ): McpRuntimeCallResult {
        val response = bridgeClient.callTool(name, arguments.toJsonObject())
        if (response == null) {
            return McpRuntimeCallResult(false, null, "MCP bridge returned no response")
        }

        val success = response.optBoolean("success", false)
        return McpRuntimeCallResult(
            success = success,
            result = response.optJSONObject("result"),
            errorMessage = response.optJSONObject("error")?.optString("message")
        )
    }

    override fun getLastFailureDetail(): String? = bridgeClient.getLastConnectionFailureDetail()

    override suspend fun close() {
        bridgeClient.disconnect()
    }
}

private fun Map<String, Any?>.toJsonObject(): JSONObject = JSONObject().also { target ->
    forEach { (key, value) -> target.put(key, value.toJsonValue()) }
}

private fun Any?.toJsonValue(): Any = when (this) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, is String, is Number, is Boolean -> this
    is Map<*, *> -> JSONObject().also { target ->
        forEach { (key, value) ->
            require(key is String) { "MCP argument object keys must be strings" }
            target.put(key, value.toJsonValue())
        }
    }
    is Iterable<*> -> JSONArray().also { target -> forEach { value -> target.put(value.toJsonValue()) } }
    else -> this
}
