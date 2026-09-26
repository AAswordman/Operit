package com.ai.assistance.operit.ui.features.vertex

import android.net.Uri
import com.ai.assistance.operit.data.api.VertexOAuthProtocol
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class VertexOAuthLoopbackCallbackServer private constructor(
    private val serverSocket: ServerSocket,
) {
    val redirectUri: String = "http://localhost:${serverSocket.localPort}${VertexOAuthProtocol.CALLBACK_PATH}"

    suspend fun awaitCallback(): Uri = withContext(Dispatchers.IO) {
        while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            socket.use {
                socket.soTimeout = 5_000
                val callback = readCallbackUri(socket)
                val response = if (callback == null) NOT_FOUND else SUCCESS
                writeResponse(socket, response)
                if (callback != null) return@withContext callback
            }
        }
        throw IllegalStateException("Vertex OAuth callback server stopped")
    }

    fun close() {
        if (!serverSocket.isClosed) serverSocket.close()
    }

    private fun readCallbackUri(socket: Socket): Uri? {
        val requestLine = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII).readLine() ?: return null
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size != 3 || parts[0] != "GET") return null
        val requestUri = Uri.parse(parts[1])
        if (requestUri.path != VertexOAuthProtocol.CALLBACK_PATH) return null
        return Uri.parse(redirectUri).buildUpon().encodedQuery(requestUri.encodedQuery).build()
    }

    private fun writeResponse(socket: Socket, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().use {
            it.write(header.toByteArray(StandardCharsets.US_ASCII))
            it.write(bytes)
        }
    }

    companion object {
        private const val SUCCESS = "<html><body>Vertex AI login complete. Return to Operit.</body></html>"
        private const val NOT_FOUND = "<html><body>Not found.</body></html>"

        fun open(): VertexOAuthLoopbackCallbackServer {
            val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            return VertexOAuthLoopbackCallbackServer(socket)
        }
    }
}