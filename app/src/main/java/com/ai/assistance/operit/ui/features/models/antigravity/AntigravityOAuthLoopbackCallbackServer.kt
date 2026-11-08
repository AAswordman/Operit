package com.ai.assistance.operit.ui.features.models.antigravity

import com.ai.assistance.operit.data.api.AntigravityOAuthProtocol
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class AntigravityOAuthLoopbackCallbackServer internal constructor(
    private val serverSocket: ServerSocket,
    private val requestReadTimeoutMillis: Int = 30_000,
    private val log: (String) -> Unit = { AppLogger.d(TAG, it) },
) {
    val redirectUri: String = "http://localhost:${serverSocket.localPort}${AntigravityOAuthProtocol.CALLBACK_PATH}"
    private val socketLock = Any()
    private var activeSocket: Socket? = null

    suspend fun awaitCallback(expectedState: String): String = withContext(Dispatchers.IO) {
        try {
            suspendCancellableCoroutine { continuation ->
                // accept/read 不会随协程取消自动退出，必须主动关闭监听和正在读取的连接。
                continuation.invokeOnCancellation { close() }
                try {
                    continuation.resume(awaitCallbackBlocking(expectedState))
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            }
        } finally {
            close()
        }
    }

    private fun awaitCallbackBlocking(expectedState: String): String {
        require(expectedState.isNotBlank())
        while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            synchronized(socketLock) {
                if (serverSocket.isClosed) {
                    closeSocket(socket)
                    throw IOException("OAuth callback listener closed")
                }
                activeSocket = socket
            }
            try {
                val callback = handleRequest(socket, expectedState)
                if (callback != null) return callback
            } catch (_: SocketTimeoutException) {
                log("Callback request read timed out; still waiting for authorization")
            } catch (_: IOException) {
                // 浏览器预连接、刷新或断开只影响本次连接，不能结束整个登录会话。
                log("Callback connection closed before a complete request; still waiting")
            } finally {
                synchronized(socketLock) { activeSocket = null }
                closeSocket(socket)
            }
        }
        throw IOException("OAuth callback listener closed")
    }

    fun close() {
        synchronized(socketLock) {
            try {
                serverSocket.close()
            } catch (_: IOException) {
                // 重复关闭不影响取消流程。
            }
            activeSocket?.let(::closeSocket)
        }
    }

    internal fun handleRequest(socket: Socket, expectedState: String): String? {
        val requestLine = readRequestLineAndHeaders(socket) ?: return null
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size != 3 || parts[0] != "GET" || parts[2] !in listOf("HTTP/1.0", "HTTP/1.1")) {
            writeResponseSafely(socket, "400 Bad Request", "Invalid callback request.")
            return null
        }
        val requestUri = try {
            URI(parts[1])
        } catch (_: java.net.URISyntaxException) {
            writeResponseSafely(socket, "400 Bad Request", "Invalid callback request.")
            return null
        }
        if (requestUri.isAbsolute || requestUri.rawAuthority != null || requestUri.rawFragment != null) {
            writeResponseSafely(socket, "400 Bad Request", "Invalid callback request.")
            return null
        }
        if (requestUri.rawPath != AntigravityOAuthProtocol.CALLBACK_PATH) {
            log("Ignoring request to a non-callback path")
            writeResponseSafely(socket, "404 Not Found", "Not found.")
            return null
        }
        val fields = try {
            requestUri.rawQuery.orEmpty().split('&').map { field ->
                val pair = field.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }.groupBy({ it.first }, { it.second })
        } catch (_: IllegalArgumentException) {
            writeResponseSafely(socket, "400 Bad Request", "Invalid callback parameters.")
            return null
        }
        val state = fields["state"]?.singleOrNull()
        val hasCode = !fields["code"]?.singleOrNull().isNullOrBlank()
        val hasError = !fields["error"]?.singleOrNull().isNullOrBlank()
        if (state != expectedState || !(hasCode xor hasError) ||
            (hasCode && fields.containsKey("error")) || (hasError && fields.containsKey("code"))) {
            log("Ignoring callback with invalid state or authorization parameters")
            writeResponseSafely(socket, "400 Bad Request", "Invalid or expired callback. Return to Operit.")
            return null
        }
        // 回调通过校验后，浏览器接收提示页失败也不能丢弃已经收到的授权码。
        log("Validated OAuth callback received")
        writeResponseSafely(socket, "200 OK", "Authorization response received. Return to Operit to finish signing in.")
        return "$redirectUri?${requestUri.rawQuery}"
    }

    private fun readRequestLineAndHeaders(socket: Socket): String? {
        val input = socket.getInputStream().buffered()
        val header = StringBuilder()
        val deadline = System.nanoTime() + requestReadTimeoutMillis * 1_000_000L
        // 完整读完请求头再回复，避免未读数据导致连接复位；限制大小和总读取时间。
        while (header.length < MAX_HEADER_BYTES) {
            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMillis <= 0) throw SocketTimeoutException("Callback request read timed out")
            socket.soTimeout = remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
            val value = input.read()
            if (value < 0) return null
            header.append(value.toChar())
            if (header.endsWith("\r\n\r\n")) {
                return header.toString().substringBefore("\r\n")
            }
        }
        writeResponseSafely(socket, "431 Request Header Fields Too Large", "Callback request headers too large.")
        return null
    }

    private fun writeResponseSafely(socket: Socket, status: String, message: String) {
        val body = "<!doctype html><html><body>$message</body></html>".toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        try {
            socket.getOutputStream().apply {
                write(header)
                write(body)
                flush()
            }
        } catch (_: IOException) {
            log("Browser disconnected while receiving callback response")
        }
    }

    private fun closeSocket(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
            // 清理连接失败不能覆盖已经接收的回调结果。
        }
    }

    companion object {
        private const val TAG = "AntigravityOAuthCallback"
        private const val MAX_HEADER_BYTES = 16 * 1024

        fun open(): AntigravityOAuthLoopbackCallbackServer {
            return AntigravityOAuthLoopbackCallbackServer(
                ServerSocket(AntigravityOAuthProtocol.CALLBACK_PORT, 8, InetAddress.getByName("127.0.0.1")),
            )
        }
    }
}
