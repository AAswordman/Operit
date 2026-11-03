package com.ai.assistance.operit.data.mcp.plugins

import android.util.Log
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeCallResult
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeDescriptor
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeSession
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Remote MCP runtime backed directly by the Kotlin SDK and Ktor.
 *
 * A remote Streamable HTTP server drops its session after an idle window or a restart, and it then
 * answers a request that still carries the old `Mcp-Session-Id` with HTTP 404 or a `Session not found`
 * error. Before this class handled that signal, the first tool call after the drop failed and only the
 * model's second attempt reached a freshly initialized session, so every expiry cost one wasted tool
 * call.
 *
 * The recovery contract is deliberately narrow:
 * - [RemoteMcpSessionRecovery] decides whether a failure *proves* the server rejected the request as an
 *   unknown session, which also proves the tool never ran.
 * - Only then is a new session established, and the original request is replayed exactly once.
 * - Timeouts, dropped connections and any other ambiguous failure are rethrown untouched, because the
 *   execution state is unknown and replaying a tool that may have side effects is not safe.
 * - Concurrent requests that all hit the same dead session share one reconnect, guarded by
 *   [connectionMutex] and by comparing client identity in [recoverSession].
 */
class RemoteMcpRuntimeSession internal constructor(
    private val pluginId: String,
    private val descriptor: McpRuntimeDescriptor.Remote,
    private val log: RemoteMcpLog,
    private val factory: RemoteMcpClientFactory
) : McpRuntimeSession {
    constructor(
        pluginId: String,
        descriptor: McpRuntimeDescriptor.Remote
    ) : this(
        pluginId,
        descriptor,
        androidRemoteMcpLog,
        SdkRemoteMcpClientFactory(pluginId, descriptor, androidRemoteMcpLog)
    )

    /**
     * Serializes connect, close and session recovery so that a burst of requests hitting the same dead
     * session triggers a single re-handshake instead of one per request.
     */
    private val connectionMutex = Mutex()

    @Volatile
    private var client: RemoteMcpClient? = null

    @Volatile
    private var connected = false

    /**
     * Set by [close] so that an in-flight request still waiting on [connectionMutex] cannot resurrect a
     * session the owner deliberately tore down. [connect] clears it, because re-activating a cached
     * session is a legitimate new lifecycle.
     */
    @Volatile
    private var tornDown = false

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val existing = client
            if (connected && existing != null) return@withLock true

            tornDown = false

            try {
                val freshClient = factory.create()
                // Publish the new session before releasing the old one so a concurrent reader never
                // observes a null client on the way through.
                client = freshClient
                connected = true
                if (existing != null && existing !== freshClient) closeClientQuietly(existing)
                true
            } catch (e: Exception) {
                client = null
                connected = false
                existing?.let { closeClientQuietly(it) }
                log(LOG_ERROR, "Failed to connect remote MCP plugin $pluginId: ${e.message}", e)
                false
            }
        }
    }

    override fun isConnected(): Boolean = connected && client != null

    override suspend fun isActive(): Boolean = isConnected()

    override suspend fun listTools(): List<McpRuntimeTool> =
        withSessionRecovery("tools/list") { remoteClient ->
            remoteClient.listTools().map(Tool::toRuntimeTool)
        }

    override suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>
    ): McpRuntimeCallResult {
        val response = withSessionRecovery("tool call $name") { remoteClient ->
            remoteClient.callTool(name, arguments)
        }
        val responseJson = JSONObject(McpJson.encodeToString(CallToolResult.serializer(), response))
        return McpRuntimeCallResult(
            success = response.isError != true,
            result = responseJson,
            errorMessage = responseJson.optString("content").takeIf { response.isError == true }
        )
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            tornDown = true
            connected = false
            val currentClient = client
            client = null
            currentClient?.let { closeClientQuietly(it) }
        }
        factory.dispose()
    }

    /**
     * Runs [operation] against the current session and replays it once on a freshly initialized
     * session, but only when the first failure proved the server rejected the request as an unknown
     * session and therefore never executed it.
     *
     * Every other failure - including a request timeout or a dropped connection, where the server may
     * already have run the tool - is rethrown untouched after marking the session disconnected, exactly
     * as before recovery existed. Replaying those could repeat a tool with side effects.
     *
     * @throws RemoteMcpSessionRecoveryException when the re-handshake or the single replay fails; its
     * message carries the evidence and the underlying cause so the model sees why the call did not run.
     */
    private suspend fun <T> withSessionRecovery(
        requestLabel: String,
        operation: suspend (RemoteMcpClient) -> T
    ): T {
        val activeClient = requireClient()

        return try {
            operation(activeClient)
        } catch (error: Exception) {
            val evidence = RemoteMcpSessionRecovery.sessionInvalidEvidence(error)
            if (evidence == null) {
                connected = false
                log(
                    LOG_ERROR,
                    "Failed to run remote MCP $requestLabel for $pluginId: ${error.message}",
                    error
                )
                throw error
            }

            log(
                LOG_WARN,
                "Remote MCP session for $pluginId is gone ($evidence); " +
                    "re-initializing before replaying $requestLabel",
                null
            )
            // Pass the exact client that failed so recovery replays on a genuinely new session rather
            // than trusting a stamp that could have gone stale across a concurrent recovery.
            val recoveredClient = recoverSession(activeClient)
            try {
                operation(recoveredClient)
            } catch (cancel: CancellationException) {
                // The caller went away mid-replay. Swallowing this inside a recovery exception would
                // break structured cancellation, so let it propagate unchanged.
                throw cancel
            } catch (retryError: Exception) {
                connected = false
                throw RemoteMcpSessionRecoveryException(
                    "Remote MCP session for $pluginId expired ($evidence). A new session was " +
                        "established, but replaying $requestLabel still failed: " +
                        "${retryError.message ?: retryError.javaClass.simpleName}",
                    retryError
                )
            }
        }
    }

    /**
     * Establishes a replacement session after a confirmed expiry.
     *
     * The decision is made on client identity rather than on a generation counter, because the only
     * question that matters is whether the client that just failed is still the one in use. A peer that
     * already recovered has published a different instance, so this request reuses it; otherwise the
     * failed client is still current and has to be rebuilt. Comparing a counter stamped before the
     * failure instead would let a request that captured a newer client keep an older stamp, conclude
     * someone else recovered, and replay on the dead instance it actually failed with.
     *
     * Waiters that all failed on the same dead client therefore share one handshake: the first rebuilds,
     * the rest observe a different current client and reuse it.
     *
     * @throws RemoteMcpSessionRecoveryException when the re-initialization handshake itself fails, or
     * when the owner closed the session while this request waited.
     */
    private suspend fun recoverSession(failedClient: RemoteMcpClient): RemoteMcpClient {
        val recovered = connectionMutex.withLock {
            if (tornDown) {
                // close() ran while we waited for the mutex. Its factory resources are already
                // disposed, so building a client here would resurrect a torn-down session.
                throw RemoteMcpSessionRecoveryException(
                    "Remote MCP session for $pluginId expired and was closed before it could be " +
                        "re-initialized",
                    IllegalStateException("session closed during recovery")
                )
            }

            val current = client
            if (current != null && current !== failedClient) {
                // Another request already recovered this session while we were waiting.
                return@withLock current
            }

            val freshClient = try {
                factory.create()
            } catch (error: Exception) {
                connected = false
                throw RemoteMcpSessionRecoveryException(
                    "Remote MCP session for $pluginId expired and re-initialization failed: " +
                        "${error.message ?: error.javaClass.simpleName}",
                    error
                )
            }
            // Publish the recovered session before closing the dead one, keeping the client reference
            // non-null for any concurrent reader across the swap.
            client = freshClient
            connected = true
            current?.let { closeClientQuietly(it) }
            freshClient
        }

        log(LOG_INFO, "Recovered remote MCP session for $pluginId", null)
        return recovered
    }

    private suspend fun closeClientQuietly(deadClient: RemoteMcpClient) {
        try {
            deadClient.close()
        } catch (e: Exception) {
            log(LOG_ERROR, "Failed to close remote MCP plugin $pluginId: ${e.message}", e)
        }
    }

    private fun requireClient(): RemoteMcpClient = checkNotNull(client) {
        "Remote MCP plugin $pluginId is not connected"
    }

    companion object {
        private const val LOG_INFO = 4
        private const val LOG_WARN = 5
        private const val LOG_ERROR = 6
    }
}

private const val TAG = "RemoteMcpRuntimeSession"

/** Routes the injected logging seam to `android.util.Log` in production builds. */
private val androidRemoteMcpLog: RemoteMcpLog = { priority, message, throwable ->
    val rendered = if (throwable == null) message else "$message\n${Log.getStackTraceString(throwable)}"
    Log.println(priority, TAG, rendered)
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
