package com.ai.assistance.operit.data.mcp.plugins

import com.ai.assistance.operit.core.tools.mcp.McpRuntimeDescriptor
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
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Tool

/**
 * The production [RemoteMcpClientFactory]: it owns the shared Ktor [HttpClient] for one plugin and
 * builds a fresh Kotlin SDK [Client] per connection attempt.
 *
 * The HTTP client is created once and reused across reconnects, because a Streamable HTTP session is
 * identified by the `Mcp-Session-Id` the server hands out during the initialize handshake, not by the
 * socket. Each [create] therefore builds a brand new SDK client and transport so a dead session id is
 * never re-presented. [dispose] closes the shared HTTP client only when the owning session is torn
 * down.
 */
internal class SdkRemoteMcpClientFactory(
    private val pluginId: String,
    private val descriptor: McpRuntimeDescriptor.Remote,
    private val log: RemoteMcpLog
) : RemoteMcpClientFactory {
    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val REMOTE_RESULT_TIMEOUT_MILLIS = 60_000L
        private const val ANDROID_LOG_INFO = 4
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
    }

    override suspend fun create(): RemoteMcpClient {
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
        // Client.connect performs the initialize handshake and capability negotiation. If it throws,
        // the SDK has already cleaned up the transport, so nothing extra is needed here.
        mcpClient.connect(transport)
        log(ANDROID_LOG_INFO, "Established remote MCP session for plugin $pluginId", null)
        return SdkRemoteMcpClient(mcpClient)
    }

    override fun dispose() {
        runCatching { httpClient.close() }
    }

    private class SdkRemoteMcpClient(private val client: Client) : RemoteMcpClient {
        override suspend fun listTools(): List<Tool> {
            val tools = mutableListOf<Tool>()
            var result = client.listTools()
            tools += result.tools

            while (result.nextCursor != null) {
                result = client.listTools(
                    ListToolsRequest(PaginatedRequestParams(cursor = result.nextCursor))
                )
                tools += result.tools
            }
            return tools
        }

        override suspend fun callTool(name: String, arguments: Map<String, Any?>): CallToolResult =
            client.callTool(name = name, arguments = arguments)

        override suspend fun close() = client.close()
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
