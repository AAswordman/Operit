package com.ai.assistance.operit.data.mcp.plugins

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the only signal that makes replaying a possibly side-effecting MCP tool safe (issue #1312).
 *
 * A request may be replayed exactly when the server proved it rejected the request as an unknown
 * session, because that rejection happens at the transport layer before any tool handler runs. Every
 * ambiguous failure - a timeout, a dropped connection, an internal error - leaves the execution state
 * unknown and must be classified as non-recoverable so the caller rethrows instead of replaying.
 */
class RemoteMcpSessionRecoveryTest {

    @Test
    fun `http 404 confirms the session is gone and the request never ran`() {
        val error = StreamableHttpError(404, "Not Found")

        assertTrue(RemoteMcpSessionRecovery.isSessionInvalidated(error))
        assertNotNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(error))
    }

    @Test
    fun `http 404 wrapped by the transport still confirms session loss`() {
        // AbstractClientTransport.send wraps a non-McpException from performSend into
        // McpException(INTERNAL_ERROR, cause = StreamableHttpError(404)), so the 404 sits one level
        // below the throwable the session actually catches.
        val error = McpException(
            code = RPCError.ErrorCode.INTERNAL_ERROR,
            message = "Error while sending message",
            cause = StreamableHttpError(404, "Not Found")
        )

        assertTrue(RemoteMcpSessionRecovery.isSessionInvalidated(error))
    }

    @Test
    fun `an explicit session-not-found error confirms session loss`() {
        val error = McpException(code = -32603, message = "Session not found")

        assertTrue(RemoteMcpSessionRecovery.isSessionInvalidated(error))
        assertEquals(
            "server reported the MCP session was not found",
            RemoteMcpSessionRecovery.sessionInvalidEvidence(error)
        )
    }

    @Test
    fun `the session-not-found marker is matched case-insensitively`() {
        assertTrue(RemoteMcpSessionRecovery.isSessionInvalidated(McpException(-32603, "SESSION NOT FOUND")))
        assertTrue(RemoteMcpSessionRecovery.isSessionInvalidated(McpException(-32603, "session not found: abc")))
    }

    @Test
    fun `a request timeout is ambiguous and must not be replayed`() {
        val error = McpException(code = RPCError.ErrorCode.REQUEST_TIMEOUT, message = "Request timed out")

        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(error))
        assertFalse(RemoteMcpSessionRecovery.isSessionInvalidated(error))
    }

    @Test
    fun `a socket timeout is ambiguous and must not be replayed`() {
        val error = McpException(
            code = RPCError.ErrorCode.INTERNAL_ERROR,
            message = "Error while sending message: timeout",
            cause = SocketTimeoutException("timeout")
        )

        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(error))
    }

    @Test
    fun `a dropped connection is ambiguous and must not be replayed`() {
        val error = McpException(code = RPCError.ErrorCode.CONNECTION_CLOSED, message = "Connection closed")

        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(error))
    }

    @Test
    fun `an http 500 is a server fault, not session loss`() {
        // A 500 means the server accepted the session and then failed while handling the request, so
        // the tool may already have run.
        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(StreamableHttpError(500, "boom")))
    }

    @Test
    fun `an http 401 is an auth problem, not session loss`() {
        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(StreamableHttpError(401, "Unauthorized")))
    }

    @Test
    fun `a streamable http error without a status code is ambiguous`() {
        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(StreamableHttpError(null, "stream error")))
    }

    @Test
    fun `a bare io error is ambiguous and must not be replayed`() {
        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(IOException("unexpected end of stream")))
    }

    @Test
    fun `a tool-level error result is not a session failure`() {
        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(IllegalStateException("tool rejected input")))
    }

    @Test
    fun `the cause chain is walked without hanging on a self-referencing cause`() {
        val selfReferencing = object : Exception("loop") {
            override val cause: Throwable
                get() = this
        }

        assertNull(RemoteMcpSessionRecovery.sessionInvalidEvidence(selfReferencing))
    }

    @Test
    fun `session loss buried deep in the cause chain is still detected`() {
        val error = IllegalStateException(
            "outer",
            RuntimeException("middle", StreamableHttpError(404, "Not Found"))
        )

        assertTrue(RemoteMcpSessionRecovery.isSessionInvalidated(error))
    }
}
