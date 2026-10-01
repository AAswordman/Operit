package com.ai.assistance.operit.data.mcp.plugins

import android.util.Log
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeCallResult
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeDescriptor
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeSession
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Remote MCP runtime backed directly by the Kotlin SDK and Ktor. */
class RemoteMcpRuntimeSession(
    private val pluginId: String,
    private val descriptor: McpRuntimeDescriptor.Remote
) : McpRuntimeSession {
    companion object {
        private const val TAG = "RemoteMcpRuntimeSession"
        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val REMOTE_RESULT_TIMEOUT_MILLIS = 60_000L
    }

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REMOTE_RESULT_TIMEOUT_MILLIS
            // Streamable HTTP delivers a 202 tool result through the existing SSE connection.
            // Without an explicit idle window, OkHttp's short default can close that stream first.
            socketTimeoutMillis = REMOTE_RESULT_TIMEOUT_MILLIS
        }
        install(SSE)
        if (descriptor.connectionType == "httpStream") {
            installMcpHttpResponseGuard(
                pendingRequestIds = { requireClient().responseHandlers.keys },
                logWarning = { message -> Log.w(TAG, "Remote MCP plugin $pluginId (httpStream): $message") }
            )
        }
    }

    private var client: Client? = null
    private var connected = false

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (connected && client != null) return@withContext true

        try {
            val transport = when (descriptor.connectionType) {
                "httpStream" -> StreamableHttpClientTransport(
                    client = httpClient,
                    url = descriptor.endpoint,
                    requestBuilder = { descriptor.applyHeaders(this) }
                )
                "sse" -> SseClientTransport(
                    client = httpClient,
                    urlString = descriptor.endpoint,
                    requestBuilder = { descriptor.applyHeaders(this) }
                )
                else -> error("Unsupported MCP connection type: ${descriptor.connectionType}")
            }

            val mcpClient = Client(
                clientInfo = Implementation(
                    name = "Operit",
                    version = "mcp-runtime"
                )
            )
            client = mcpClient
            mcpClient.connect(transport)
            connected = true
            true
        } catch (e: Exception) {
            connected = false
            client = null
            Log.e(TAG, "Failed to connect remote MCP plugin $pluginId: ${e.message}", e)
            false
        }
    }

    override fun isConnected(): Boolean = connected && client != null

    override suspend fun isActive(): Boolean = isConnected()

    override suspend fun listTools(): List<McpRuntimeTool> {
        val client = requireClient()
        val tools = mutableListOf<Tool>()
        var result = client.listTools()
        tools += result.tools

        while (result.nextCursor != null) {
            result = client.listTools(
                ListToolsRequest(PaginatedRequestParams(cursor = result.nextCursor))
            )
            tools += result.tools
        }

        return tools.map(Tool::toRuntimeTool)
    }

    override suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>
    ): McpRuntimeCallResult {
        return try {
            val response = requireClient().callTool(name = name, arguments = arguments)
            val responseJson = JSONObject(McpJson.encodeToString(CallToolResult.serializer(), response))
            McpRuntimeCallResult(
                success = response.isError != true,
                result = responseJson,
                errorMessage = responseJson.optString("content").takeIf { response.isError == true }
            )
        } catch (e: Exception) {
            connected = false
            Log.e(TAG, "Failed to call MCP tool $pluginId:$name: ${e.message}", e)
            throw e
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        connected = false
        val currentClient = client
        client = null
        if (currentClient != null) {
            try {
                currentClient.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close remote MCP plugin $pluginId: ${e.message}", e)
            }
        }
        httpClient.close()
    }

    private fun requireClient(): Client = checkNotNull(client) {
        "Remote MCP plugin $pluginId is not connected"
    }
}

private fun McpRuntimeDescriptor.Remote.applyHeaders(builder: HttpRequestBuilder) {
    builder.headers {
        bearerToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
        headers.forEach { (name, value) ->
            remove(name)
            append(name, value)
        }
    }
}

private fun Tool.toRuntimeTool(): McpRuntimeTool {
    val schema = JSONObject().apply {
        put("type", inputSchema.type)
        inputSchema.properties?.let { put("properties", JSONObject(it.toString())) }
        inputSchema.required?.let { put("required", it) }
        inputSchema.defs?.let { put("\$defs", JSONObject(it.toString())) }
    }
    return McpRuntimeTool(
        name = name,
        description = description.orEmpty(),
        inputSchema = schema.toString()
    )
}
