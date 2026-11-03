package com.ai.assistance.operit.integrations.http

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for issue #1375.
 *
 * This test drives a real NanoHTTPD server over a real TCP socket, so it exercises the same
 * request parsing the external chat endpoint uses. The body is written in small segments with
 * explicit flushes, which is how MacroDroid and other HTTP clients deliver it. The assertion is on
 * the exact text the handler received, not on what the client believes it sent.
 */
class ExternalChatHttpIngressTest {

    private lateinit var server: BodyCaptureServer

    @Before
    fun startServer() {
        server = BodyCaptureServer()
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
    }

    @After
    fun stopServer() {
        server.stop()
    }

    @Test
    fun bodyLargerThanNanoHttpdBufferArrivesIntact() {
        val payload = buildPayload()
        val response = sendRawBody(payload, segmentSize = 13)
        assertEquals(describe(payload), response)
    }

    @Test
    fun bodyArrivesIntactWhenDeliveredOneByteAtATime() {
        val payload = """{"message":"TESTLINE-完整","request_id":"chunk-1"}"""
        val response = sendRawBody(payload, segmentSize = 1)
        assertEquals(describe(payload), response)
    }

    @Test
    fun shortBodyArrivesIntact() {
        val payload = """{"message":"OK"}"""
        val response = sendRawBody(payload, segmentSize = 2)
        assertEquals(describe(payload), response)
    }

    private fun buildPayload(): String {
        val padding = "【填充填充填充填充】".repeat(600)
        return """{"message":"HEAD-$padding-TAIL-完整","timeout_ms":30000}"""
    }

    private fun describe(payload: String): String {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        return "len=${payload.length}, bytes=${bytes.size}, sha256=${sha256(payload)}"
    }

    private fun sha256(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** Sends a raw HTTP/1.1 POST, splitting the body into [segmentSize] byte writes. */
    private fun sendRawBody(body: String, segmentSize: Int): String {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        Socket("127.0.0.1", server.listeningPort).use { socket ->
            socket.soTimeout = 15_000
            val out = socket.getOutputStream()
            val head = buildString {
                append("POST /api/external-chat HTTP/1.1\r\n")
                append("Host: 127.0.0.1:${server.listeningPort}\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray(StandardCharsets.ISO_8859_1))
            var offset = 0
            while (offset < bodyBytes.size) {
                val count = minOf(segmentSize, bodyBytes.size - offset)
                out.write(bodyBytes, offset, count)
                out.flush()
                offset += count
            }
            socket.shutdownOutput()

            val responseBytes = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val read = socket.getInputStream().read(buffer)
                if (read < 0) break
                responseBytes.write(buffer, 0, read)
            }
            val response = responseBytes.toString(StandardCharsets.UTF_8.name())
            val headerEnd = response.indexOf("\r\n\r\n")
            return if (headerEnd >= 0) response.substring(headerEnd + 4) else response
        }
    }

    /** Minimal NanoHTTPD handler that reads the body exactly like the chat endpoint does. */
    private class BodyCaptureServer : NanoHTTPD(0) {
        override fun serve(session: IHTTPSession): Response {
            val contentLength = session.headers.entries
                .firstOrNull { it.key.equals("content-length", ignoreCase = true) }
                ?.value
                ?.trim()
                ?.toLongOrNull()
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    MIME_PLAINTEXT,
                    "Missing or invalid Content-Length",
                )
            val body = ExternalChatRequestBodyReader.read(
                inputStream = session.inputStream,
                contentLength = contentLength,
                charset = StandardCharsets.UTF_8,
            )
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(body.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            val report = "len=${body.length}, bytes=${body.toByteArray(StandardCharsets.UTF_8).size}, sha256=$digest"
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, report)
        }
    }
}
