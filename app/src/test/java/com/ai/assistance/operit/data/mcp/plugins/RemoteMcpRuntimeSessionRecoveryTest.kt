package com.ai.assistance.operit.data.mcp.plugins

import com.ai.assistance.operit.core.tools.mcp.McpRuntimeDescriptor
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the session lifecycle that issue #1312 asks for: recognize a confirmed session expiry,
 * re-handshake, and replay the original request once - while refusing to replay any failure whose
 * execution state is unknown.
 *
 * A [FakeRemoteMcpClient] stands in for the Kotlin SDK transport so the recovery state machine is
 * tested without a network. [ScriptedFactory] hands out a queue of pre-programmed clients, which lets
 * each test assert both the result the caller sees and how many handshakes and tool executions ran.
 */
class RemoteMcpRuntimeSessionRecoveryTest {

    private companion object {
        const val CONCURRENCY = 8
        const val SPIN_LIMIT = 10_000
    }

    private val descriptor = McpRuntimeDescriptor.Remote(
        endpoint = "https://example.invalid/mcp",
        connectionType = "httpStream",
        bearerToken = "token",
        headers = emptyMap()
    )

    private val quietLog: RemoteMcpLog = { _, _, _ -> }

    @Test
    fun `a healthy session calls the tool once and never reconnects`() = runTest {
        val healthy = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("ok"))) }
        )
        val factory = ScriptedFactory(listOf(healthy))
        val session = newSession(factory)

        assertTrue(session.connect())
        val result = session.callTool("echo", mapOf("v" to 1))

        assertTrue(result.success)
        assertEquals(1, healthy.callCount.get())
        assertEquals(1, factory.createdCount.get())
        assertEquals(0, factory.disposedCount.get())
    }

    @Test
    fun `a confirmed session expiry reconnects and replays the tool exactly once`() = runTest {
        val stale = FakeRemoteMcpClient(
            toolResult = { _, _ -> throw streamableNotFound() }
        )
        val fresh = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("recovered"))) }
        )
        val factory = ScriptedFactory(listOf(stale, fresh))
        val session = newSession(factory)

        assertTrue(session.connect())
        val result = session.callTool("echo", mapOf("v" to 1))

        assertTrue(result.success)
        // The stale client attempted once and the fresh client replayed once: no extra executions.
        assertEquals(1, stale.callCount.get())
        assertEquals(1, fresh.callCount.get())
        assertEquals(2, factory.createdCount.get())
        assertTrue("the dead session must be closed", stale.closed)
    }

    @Test
    fun `a wrapped session-not-found error also triggers recovery`() = runTest {
        val stale = FakeRemoteMcpClient(
            toolResult = { _, _ ->
                throw McpException(
                    code = RPCError.ErrorCode.INTERNAL_ERROR,
                    message = "Error while sending message",
                    cause = StreamableHttpError(404, "Not Found")
                )
            }
        )
        val fresh = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("recovered"))) }
        )
        val factory = ScriptedFactory(listOf(stale, fresh))
        val session = newSession(factory)

        assertTrue(session.connect())
        val result = session.callTool("echo", emptyMap())

        assertTrue(result.success)
        assertEquals(1, fresh.callCount.get())
    }

    @Test
    fun `an ambiguous timeout is rethrown without reconnect or replay`() = runTest {
        val stale = FakeRemoteMcpClient(
            toolResult = { _, _ ->
                throw McpException(code = RPCError.ErrorCode.REQUEST_TIMEOUT, message = "Request timed out")
            }
        )
        val factory = ScriptedFactory(listOf(stale))
        val session = newSession(factory)

        assertTrue(session.connect())
        val error = runCatching { session.callTool("side-effecting", emptyMap()) }.exceptionOrNull()

        assertTrue("a timeout must propagate as the original McpException", error is McpException)
        assertEquals(RPCError.ErrorCode.REQUEST_TIMEOUT, (error as McpException).code)
        // No second handshake, no replay: the tool may have run on the server already.
        assertEquals(1, stale.callCount.get())
        assertEquals(1, factory.createdCount.get())
        assertFalse("an ambiguous failure must leave the session flagged disconnected", session.isConnected())
    }

    @Test
    fun `listTools recovers from a confirmed expiry the same way`() = runTest {
        val stale = FakeRemoteMcpClient(
            listToolsResult = { throw streamableNotFound() }
        )
        val fresh = FakeRemoteMcpClient(
            listToolsResult = {
                listOf(Tool(name = "echo", inputSchema = ToolSchema(), description = "echoes"))
            }
        )
        val factory = ScriptedFactory(listOf(stale, fresh))
        val session = newSession(factory)

        assertTrue(session.connect())
        val tools = session.listTools()

        assertEquals(listOf("echo"), tools.map { it.name })
        assertEquals(1, stale.listCount.get())
        assertEquals(1, fresh.listCount.get())
    }

    @Test
    fun `recovery surfaces a clear diagnostic when the re-handshake itself fails`() = runTest {
        val stale = FakeRemoteMcpClient(toolResult = { _, _ -> throw streamableNotFound() })
        // The second create() simulates the server still being down: reconnect throws.
        val factory = ScriptedFactory(listOf(stale), createFailure = IllegalStateException("server down"))
        val session = newSession(factory)

        assertTrue(session.connect())
        val error = runCatching { session.callTool("echo", emptyMap()) }.exceptionOrNull()

        assertTrue(
            "a failed re-handshake must surface as RemoteMcpSessionRecoveryException",
            error is RemoteMcpSessionRecoveryException
        )
        error as RemoteMcpSessionRecoveryException
        assertNotNull(error.cause)
        assertTrue(error.message!!.contains("re-initialization failed"))
        assertTrue(error.message!!.contains("server down"))
        assertFalse("a failed recovery must not leave a broken session marked connected", session.isConnected())
    }

    @Test
    fun `recovery surfaces a clear diagnostic when the replay still fails`() = runTest {
        val stale = FakeRemoteMcpClient(toolResult = { _, _ -> throw streamableNotFound() })
        // Reconnect succeeds, but the replayed call fails for an unrelated reason.
        val fresh = FakeRemoteMcpClient(toolResult = { _, _ -> throw IllegalStateException("tool blew up") })
        val factory = ScriptedFactory(listOf(stale, fresh))
        val session = newSession(factory)

        assertTrue(session.connect())
        val error = runCatching { session.callTool("echo", emptyMap()) }.exceptionOrNull()

        assertTrue(
            "a failed replay must surface as RemoteMcpSessionRecoveryException",
            error is RemoteMcpSessionRecoveryException
        )
        error as RemoteMcpSessionRecoveryException
        assertTrue(error.message!!.contains("still failed"))
        assertTrue(error.message!!.contains("tool blew up"))
        // The retry boundary: exactly one replay attempt, never two.
        assertEquals(1, fresh.callCount.get())
    }

    @Test
    fun `concurrent callers hitting one dead session share a single re-handshake`() = runTest {
        // Every caller has to fail on the dead session before any of them may start recovering,
        // otherwise the first caller finishes the whole recovery synchronously on the single-threaded
        // test dispatcher and the shared-recovery logic is never reached. The gate holds them all in the
        // failing call until all eight have arrived.
        val allArrived = CompletableDeferred<Unit>()
        val arrived = AtomicInteger(0)
        val stale = FakeRemoteMcpClient(
            toolResult = { _, _ ->
                if (arrived.incrementAndGet() == CONCURRENCY) allArrived.complete(Unit)
                allArrived.await()
                throw streamableNotFound()
            }
        )
        val fresh = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("ok"))) }
        )
        val factory = ScriptedFactory(listOf(stale, fresh))
        val session = newSession(factory)

        assertTrue(session.connect())

        val results = (1..CONCURRENCY).map {
            async { session.callTool("echo", mapOf("v" to it)) }
        }.awaitAll()

        assertTrue(results.all { it.success })
        // All eight failed on the dead session, but it is rebuilt exactly once: the first waiter
        // re-handshakes and the other seven see a different current client and reuse that one.
        assertEquals(CONCURRENCY, stale.callCount.get())
        assertEquals(2, factory.createdCount.get())
        assertEquals(CONCURRENCY, fresh.callCount.get())
        assertEquals(1, stale.closeCount())
    }

    @Test
    fun `close disposes the factory-owned http client`() = runTest {
        val healthy = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("ok"))) }
        )
        val factory = ScriptedFactory(listOf(healthy))
        val session = newSession(factory)

        assertTrue(session.connect())
        session.close()

        assertFalse(session.isConnected())
        assertEquals(1, factory.disposedCount.get())
        assertTrue(healthy.closed)
    }

    @Test
    fun `a request in flight while the session is closed does not resurrect it`() = runTest {
        // The in-flight call fails with a confirmed 404 only after close() has already torn the session
        // down and disposed the factory. Recovering here would rebuild a session whose HTTP client is
        // gone, so the guard must report the closure instead of handing out a fresh client.
        val closedSignal = CompletableDeferred<Unit>()
        val stale = FakeRemoteMcpClient(
            toolResult = { _, _ ->
                closedSignal.await()
                throw streamableNotFound()
            }
        )
        val factory = ScriptedFactory(listOf(stale))
        val session = newSession(factory)

        assertTrue(session.connect())

        val inFlight = async { runCatching { session.callTool("echo", emptyMap()) } }
        // Let the call reach the transport before closing underneath it. The bound turns a scheduler
        // surprise into a readable failure instead of running out the runTest timeout.
        var spins = 0
        while (stale.callCount.get() == 0) {
            assertTrue("the in-flight call never reached the transport", ++spins < SPIN_LIMIT)
            yield()
        }
        session.close()
        closedSignal.complete(Unit)

        val error = inFlight.await().exceptionOrNull()
        assertTrue(
            "a torn-down session must not be rebuilt by an in-flight request",
            error is RemoteMcpSessionRecoveryException
        )
        error as RemoteMcpSessionRecoveryException
        assertTrue(error.message!!.contains("was closed before it could be re-initialized"))
        // Only the initial connect ever created a client; recovery refused to create another.
        assertEquals(1, factory.createdCount.get())
    }

    @Test
    fun `a session can be reconnected after being closed`() = runTest {
        val first = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("first"))) }
        )
        val second = FakeRemoteMcpClient(
            toolResult = { _, _ -> CallToolResult(content = listOf(TextContent("second"))) }
        )
        val factory = ScriptedFactory(listOf(first, second))
        val session = newSession(factory)

        assertTrue(session.connect())
        session.close()

        // MCPManager re-activates cached sessions through connect(), so the teardown flag must not
        // permanently block a legitimate new lifecycle.
        assertTrue(session.connect())
        val result = session.callTool("echo", emptyMap())

        assertTrue(result.success)
        assertEquals(1, second.callCount.get())
        assertTrue(session.isConnected())
    }

    private fun newSession(factory: RemoteMcpClientFactory) = RemoteMcpRuntimeSession(
        pluginId = "remote-test",
        descriptor = descriptor,
        log = quietLog,
        factory = factory
    )

    private fun streamableNotFound() = StreamableHttpError(404, "Not Found")
}

/**
 * A programmable [RemoteMcpClient]. Each behavior defaults to a benign result; tests override the ones
 * they care about and read the counters to assert how often the transport was actually hit.
 */
private class FakeRemoteMcpClient(
    val toolResult: suspend (String, Map<String, Any?>) -> CallToolResult = { _, _ ->
        CallToolResult(content = listOf(TextContent("ok")))
    },
    val listToolsResult: suspend () -> List<Tool> = { emptyList() }
) : RemoteMcpClient {
    val callCount = AtomicInteger(0)
    val listCount = AtomicInteger(0)
    private val closeCounter = AtomicInteger(0)

    @Volatile
    var closed = false
        private set

    override suspend fun listTools(): List<Tool> {
        listCount.incrementAndGet()
        return listToolsResult()
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any?>): CallToolResult {
        callCount.incrementAndGet()
        return toolResult(name, arguments)
    }

    override suspend fun close() {
        closed = true
        closeCounter.incrementAndGet()
    }

    /** How many times the transport was closed, to assert a dead session is released exactly once. */
    fun closeCount(): Int = closeCounter.get()
}

/**
 * Hands out a fixed queue of clients, one per handshake, then fails with [createFailure] once the queue
 * is exhausted so a test can model "the server is still down on reconnect".
 */
private class ScriptedFactory(
    private val clients: List<RemoteMcpClient>,
    private val createFailure: Throwable? = null
) : RemoteMcpClientFactory {
    val createdCount = AtomicInteger(0)
    val disposedCount = AtomicInteger(0)

    override suspend fun create(): RemoteMcpClient {
        val index = createdCount.getAndIncrement()
        if (index < clients.size) return clients[index]
        throw createFailure ?: IllegalStateException("ScriptedFactory has no client #$index queued")
    }

    override fun dispose() {
        disposedCount.incrementAndGet()
    }
}
