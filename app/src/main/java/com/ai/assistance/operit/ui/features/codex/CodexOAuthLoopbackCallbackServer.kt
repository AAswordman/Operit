package com.ai.assistance.operit.ui.features.codex

import android.net.Uri
import com.ai.assistance.operit.data.api.CodexOAuthProtocol
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CodexOAuthLoopbackCallbackServer private constructor(
    private val serverSocket: ServerSocket,
) {
    val port: Int = serverSocket.localPort
    val redirectUri: String = "http://localhost:$port${CodexOAuthProtocol.CALLBACK_PATH}"

    suspend fun awaitCallback(): Uri = withContext(Dispatchers.IO) {
        while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            socket.use {
                socket.soTimeout = REQUEST_READ_TIMEOUT_MILLIS
                val callback = readCallbackUri(socket)
                if (callback == null) {
                    writeResponse(socket, NOT_FOUND_RESPONSE)
                } else {
                    writeResponse(socket, SUCCESS_RESPONSE)
                    return@withContext callback
                }
            }
        }
        throw IllegalStateException("Codex OAuth callback server stopped before receiving a callback")
    }

    fun close() {
        if (!serverSocket.isClosed) {
            serverSocket.close()
        }
    }

    private fun readCallbackUri(socket: Socket): Uri? {
        val requestLine = socket.getInputStream()
            .bufferedReader(StandardCharsets.US_ASCII)
            .readLine()
            ?: return null
        val requestParts = requestLine.split(' ', limit = 3)
        if (requestParts.size != 3 || requestParts[0] != "GET") {
            return null
        }

        val requestUri = Uri.parse(requestParts[1])
        if (requestUri.scheme != null || requestUri.authority != null) {
            return null
        }
        if (requestUri.path != CodexOAuthProtocol.CALLBACK_PATH) {
            return null
        }

        return Uri.parse(redirectUri).buildUpon()
            .encodedQuery(requestUri.encodedQuery)
            .build()
    }

    private fun writeResponse(socket: Socket, response: HttpResponse) {
        val body = response.body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(response.status).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        socket.getOutputStream().use { output ->
            output.write(header)
            output.write(body)
            output.flush()
        }
    }

    private data class HttpResponse(val status: String, val body: String)

    companion object {
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val REQUEST_READ_TIMEOUT_MILLIS = 5_000
        private val SUCCESS_RESPONSE = HttpResponse(
            status = "200 OK",
            body = "<html><body>Codex login complete. Return to Operit.</body></html>",
        )
        private val NOT_FOUND_RESPONSE = HttpResponse(
            status = "404 Not Found",
            body = "<html><body>Not found.</body></html>",
        )

        fun open(): CodexOAuthLoopbackCallbackServer {
            val socket = ServerSocket(
                CodexOAuthProtocol.PRIMARY_CALLBACK_PORT,
                1,
                InetAddress.getByName(LOOPBACK_HOST),
            )
            return CodexOAuthLoopbackCallbackServer(socket)
        }
    }
}
