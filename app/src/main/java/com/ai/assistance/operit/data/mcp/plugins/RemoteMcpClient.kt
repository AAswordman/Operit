package com.ai.assistance.operit.data.mcp.plugins

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Tool

/**
 * Injected logging seam so the remote session's recovery path can be unit tested on the JVM,
 * where `android.util.Log` is not available and its stub methods throw "not mocked".
 *
 * The priority matches the `android.util.Log` constants: 4 info, 5 warn, 6 error.
 */
typealias RemoteMcpLog = (priority: Int, message: String, throwable: Throwable?) -> Unit

/**
 * The narrow surface of an MCP transport client that [RemoteMcpRuntimeSession] drives. Keeping the
 * recovery logic against this seam lets the session be tested with a fake client instead of a live
 * HTTP server, and confines every Kotlin SDK / Ktor detail to [SdkRemoteMcpClientFactory].
 */
internal interface RemoteMcpClient {
    suspend fun listTools(): List<Tool>

    suspend fun callTool(name: String, arguments: Map<String, Any?>): CallToolResult

    suspend fun close()
}

/**
 * Creates remote MCP clients. [create] performs the full initialize handshake and capability
 * negotiation and returns an already-connected client, so each reconnect obtains a fresh session id
 * and a dead one is never re-presented. [dispose] releases factory-owned resources such as the shared
 * HTTP client when the owning session is torn down.
 */
internal interface RemoteMcpClientFactory {
    suspend fun create(): RemoteMcpClient

    fun dispose() {}
}

/**
 * Decides whether a failure thrown while sending a remote MCP request proves the server-side session
 * is gone and the request was NOT executed, which is the only condition under which it is safe to
 * reconnect and retry a possibly side-effecting tool.
 *
 * The signal is deliberately narrow. The MCP Streamable HTTP transport spec (2025-06-18, Session
 * Management) says a server that terminated a session MUST answer requests carrying that
 * `Mcp-Session-Id` with HTTP 404, and that a client receiving such a 404 MUST start a new session with
 * a fresh `InitializeRequest`. That rejection happens at the HTTP layer, before the request reaches any
 * tool handler, so the tool provably did not run. Some servers instead answer with a `Session not
 * found` JSON-RPC error, which is the same statement one layer down.
 *
 * Every other failure - a request timeout, a dropped connection, a 5xx after the request was accepted,
 * an internal error - leaves the execution state unknown, so it is NOT treated as recoverable here and
 * the caller must not retry.
 */
internal object RemoteMcpSessionRecovery {
    private const val HTTP_NOT_FOUND = 404
    private const val SESSION_NOT_FOUND_MARKER = "session not found"

    /**
     * Returns non-null evidence describing the confirmed session loss when [error] proves the request
     * was rejected as an unknown session and therefore never executed, or null when the failure is
     * ambiguous and a retry must be avoided.
     */
    fun sessionInvalidEvidence(error: Throwable): String? {
        var current: Throwable? = error
        while (current != null) {
            evidenceFrom(current)?.let { return it }
            // A self-referencing cause would otherwise spin forever.
            val next = current.cause
            if (next === current) break
            current = next
        }
        return null
    }

    /** True when [error] confirms the remote session is gone and the request was not executed. */
    fun isSessionInvalidated(error: Throwable): Boolean = sessionInvalidEvidence(error) != null

    private fun evidenceFrom(error: Throwable): String? = when {
        error is StreamableHttpError && error.code == HTTP_NOT_FOUND ->
            "server returned HTTP 404 for the active MCP session id"

        // A server that answers with an explicit "Session not found" JSON-RPC error could not have
        // routed the request to a tool, so the call provably did not run.
        error is McpException &&
            error.message?.contains(SESSION_NOT_FOUND_MARKER, ignoreCase = true) == true ->
            "server reported the MCP session was not found"

        else -> null
    }
}

/**
 * Thrown when a confirmed-dead remote MCP session could not be re-established, or when the single
 * safe retry after a successful reconnect still failed. The message carries the diagnostic handed
 * back to the model, and [cause] is the underlying failure.
 */
internal class RemoteMcpSessionRecoveryException(
    message: String,
    cause: Throwable
) : Exception(message, cause)
