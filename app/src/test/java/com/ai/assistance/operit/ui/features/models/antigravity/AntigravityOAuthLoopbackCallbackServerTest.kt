package com.ai.assistance.operit.ui.features.models.antigravity

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityOAuthLoopbackCallbackServerTest {
    @Test
    fun brokenPipeWhileWriting_doesNotDiscardValidatedCode() {
        ObservableServerSocket().use { listener ->
            val logs = mutableListOf<String>()
            val server = AntigravityOAuthLoopbackCallbackServer(listener, log = { logs.add(it) })
            val socket = MemorySocket(request("$CALLBACK?code=private-code&state=$STATE"), brokenPipe = true)
            assertEquals("${server.redirectUri}?code=private-code&state=$STATE", server.handleRequest(socket, STATE))
            assertTrue(socket.responseAttempted)
            assertTrue(logs.none { it.contains("private-code") || it.contains(STATE) })
        }
    }

    @Test
    fun invalidCallbacks_areRejectedWithoutConsumingTheSession() {
        ObservableServerSocket().use { listener ->
            val server = AntigravityOAuthLoopbackCallbackServer(listener, log = {})
            val queries = listOf(
                "code=code&state=wrong",
                "code=code",
                "state=$STATE",
                "code=&state=$STATE",
                "code=code&state=$STATE&state=$STATE",
                "code=first&code=second&state=$STATE",
                "code=code&error=access_denied&state=$STATE",
                "code=code&state=%zz",
            )
            queries.forEach { query ->
                val socket = MemorySocket(request("$CALLBACK?$query"))
                assertNull(server.handleRequest(socket, STATE))
                assertTrue(socket.response().startsWith("HTTP/1.1 400"))
                assertEquals(1, Regex("HTTP/1.1").findAll(socket.response()).count())
                assertFalse(listener.isClosed)
            }
            val error = "error=access_denied&state=$STATE"
            assertEquals("${server.redirectUri}?$error", server.handleRequest(MemorySocket(request("$CALLBACK?$error")), STATE))
        }
    }

    @Test
    fun incompleteRequestHeaders_doNotConsumeTheCodeOrWriteAResponse() {
        ObservableServerSocket().use { listener ->
            val server = AntigravityOAuthLoopbackCallbackServer(listener, log = {})
            val socket = MemorySocket("GET $CALLBACK?code=code&state=$STATE HTTP/1.1\r\nHost: localhost\r\n")
            assertNull(server.handleRequest(socket, STATE))
            assertFalse(socket.responseAttempted)
        }
    }

    @Test
    fun oversizedHeaders_receiveOneRejection() {
        ObservableServerSocket().use { listener ->
            val server = AntigravityOAuthLoopbackCallbackServer(listener, log = {})
            val socket = MemorySocket("GET $CALLBACK?code=code&state=$STATE HTTP/1.1\r\nX-Large: " + "a".repeat(17 * 1024))
            assertNull(server.handleRequest(socket, STATE))
            assertTrue(socket.response().startsWith("HTTP/1.1 431"))
            assertEquals(1, Regex("HTTP/1.1").findAll(socket.response()).count())
        }
    }

    @Test(timeout = 10_000)
    fun preconnectAndFaviconAndInvalidState_doNotPreventLaterCallback() {
        runBlocking {
            ObservableServerSocket().use { listener ->
                val server = AntigravityOAuthLoopbackCallbackServer(listener, log = {})
                val result = async(Dispatchers.IO) { server.awaitCallback(STATE) }
                try {
                    // 浏览器可以先建立空连接，随后才发送真正的回调。
                    Socket("127.0.0.1", listener.localPort).close()
                    assertTrue(send(listener, "/favicon.ico").startsWith("HTTP/1.1 404"))
                    assertTrue(send(listener, "$CALLBACK?code=old&state=expired").startsWith("HTTP/1.1 400"))
                    val response = send(listener, "$CALLBACK?code=4%2Fcode&state=$STATE")
                    assertTrue(response.startsWith("HTTP/1.1 200"))
                    assertEquals("${server.redirectUri}?code=4%2Fcode&state=$STATE", withTimeout(2_000) { result.await() })
                    assertTrue(listener.isClosed)
                } finally {
                    server.close()
                    result.cancelAndJoin()
                }
            }
        }
    }

    @Test(timeout = 10_000)
    fun requestReadTimeout_keepsListenerAvailableForRetry() {
        runBlocking {
            ObservableServerSocket().use { listener ->
                val server = AntigravityOAuthLoopbackCallbackServer(listener, requestReadTimeoutMillis = 100, log = {})
                val result = async(Dispatchers.IO) { server.awaitCallback(STATE) }
                try {
                    Socket("127.0.0.1", listener.localPort).use { idle ->
                        idle.soTimeout = 2_000
                        // 等待服务端关闭空预连接，不靠固定 sleep 猜测读取超时是否发生。
                        assertEquals(-1, idle.getInputStream().read())
                    }
                    assertFalse(listener.isClosed)
                    assertTrue(send(listener, "$CALLBACK?code=code&state=$STATE").startsWith("HTTP/1.1 200"))
                    assertTrue(withTimeout(2_000) { result.await() }.contains("code=code"))
                } finally {
                    server.close()
                    result.cancelAndJoin()
                }
            }
        }
    }

    @Test(timeout = 10_000)
    fun sessionTimeout_closesListenerEvenWhenNoBrowserConnects() {
        runBlocking {
            ObservableServerSocket().use { listener ->
                val server = AntigravityOAuthLoopbackCallbackServer(listener, log = {})
                try {
                    val result = async(Dispatchers.IO) {
                        withTimeoutOrNull(250) { server.awaitCallback(STATE) }
                    }
                    assertNull(withTimeout(2_000) { result.await() })
                    assertTrue(listener.isClosed)
                } finally {
                    server.close()
                }
            }
        }
    }

    @Test(timeout = 10_000)
    fun cancellation_closesAnAcceptedSocketWhileReadingHeaders() {
        runBlocking {
            ObservableServerSocket().use { listener ->
                val server = AntigravityOAuthLoopbackCallbackServer(listener, log = {})
                val result = async(Dispatchers.IO) { server.awaitCallback(STATE) }
                try {
                    Socket("127.0.0.1", listener.localPort).use { browser ->
                        assertTrue(listener.accepted.await(2, TimeUnit.SECONDS))
                        withTimeout(2_000) { result.cancelAndJoin() }
                        assertTrue(listener.isClosed)
                        browser.soTimeout = 2_000
                        assertEquals(-1, browser.getInputStream().read())
                    }
                } finally {
                    server.close()
                    result.cancelAndJoin()
                }
            }
        }
    }

    private fun send(listener: ServerSocket, target: String): String {
        return Socket("127.0.0.1", listener.localPort).use { socket ->
            socket.soTimeout = 2_000
            socket.getOutputStream().write(request(target).toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readText()
        }
    }

    private fun request(target: String): String = "GET $target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"

    private class ObservableServerSocket : ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) {
        val accepted = CountDownLatch(1)
        override fun accept(): Socket = super.accept().also { accepted.countDown() }
    }

    private class MemorySocket(request: String, private val brokenPipe: Boolean = false) : Socket() {
        private val input = ByteArrayInputStream(request.toByteArray(StandardCharsets.US_ASCII))
        private val output = ByteArrayOutputStream()
        var responseAttempted = false
        override fun setSoTimeout(timeout: Int) {}
        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream {
            responseAttempted = true
            if (brokenPipe) throw SocketException("Broken pipe")
            return output
        }
        fun response(): String = output.toString("UTF-8")
    }

    companion object {
        private const val CALLBACK = "/oauth-callback"
        private const val STATE = "test-oauth-state"
    }
}
