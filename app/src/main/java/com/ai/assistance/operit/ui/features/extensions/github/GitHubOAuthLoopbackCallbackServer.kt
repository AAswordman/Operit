package com.ai.assistance.operit.ui.features.extensions.github

import android.net.Uri
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Receives the one browser callback that completes an external GitHub login. */
internal class GitHubOAuthLoopbackCallbackServer private constructor(
    private val serverSocket: ServerSocket
) {
    val completionRedirectUri: String = Uri.Builder()
        .scheme(HTTP_SCHEME)
        .encodedAuthority("$LOOPBACK_HOST:${serverSocket.localPort}")
        .appendPath("oauth")
        .appendPath("github")
        .appendPath("complete")
        .build()
        .toString()

    suspend fun awaitCompletion(): Uri = withContext(Dispatchers.IO) {
        var completedUri: Uri? = null
        while (completedUri == null) {
            val socket = serverSocket.accept()
            socket.use {
                socket.soTimeout = REQUEST_READ_TIMEOUT_MILLIS
                val receivedUri = readCompletionUri(socket)
                if (receivedUri == null) {
                    writeResponse(socket, NOT_FOUND_RESPONSE)
                } else {
                    writeResponse(socket, SUCCESS_RESPONSE)
                    completedUri = receivedUri
                }
            }
        }
        checkNotNull(completedUri)
    }

    fun close() {
        if (!serverSocket.isClosed) {
            serverSocket.close()
        }
    }

    private fun readCompletionUri(socket: Socket): Uri? {
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

        val callbackUri = Uri.parse(completionRedirectUri)
        if (requestUri.path != callbackUri.path) {
            return null
        }

        return callbackUri.buildUpon()
            .encodedQuery(requestUri.encodedQuery)
            .build()
    }

    private fun writeResponse(socket: Socket, response: HttpResponse) {
        val body = response.body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ")
            append(response.status)
            append("\r\nContent-Type: text/html; charset=utf-8")
            append("\r\nContent-Length: ")
            append(body.size)
            append("\r\nConnection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        socket.getOutputStream().use { output ->
            output.write(header)
            output.write(body)
            output.flush()
        }
    }

    private data class HttpResponse(
        val status: String,
        val body: String
    )

    companion object {
        private const val HTTP_SCHEME = "http"
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val MIN_LOOPBACK_PORT = 1024
        private const val REQUEST_READ_TIMEOUT_MILLIS = 5_000
        private val SUCCESS_RESPONSE = HttpResponse(
            status = "200 OK",
            body = "<html><body>GitHub login complete. Return to Operit.</body></html>"
        )
        private val NOT_FOUND_RESPONSE = HttpResponse(
            status = "404 Not Found",
            body = "<html><body>Not found.</body></html>"
        )

        fun open(): GitHubOAuthLoopbackCallbackServer {
            val serverSocket = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST))
            try {
                require(serverSocket.localPort >= MIN_LOOPBACK_PORT) {
                    "GitHub OAuth loopback port is outside the permitted range"
                }
                return GitHubOAuthLoopbackCallbackServer(serverSocket)
            } catch (error: Exception) {
                serverSocket.close()
                throw error
            }
        }
    }
}
