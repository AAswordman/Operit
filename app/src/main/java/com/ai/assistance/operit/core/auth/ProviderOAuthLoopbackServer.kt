package com.ai.assistance.operit.core.auth

import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One bounded, cancellable listener for one authorization attempt; never binds external interfaces. */
internal class ProviderOAuthLoopbackServer private constructor(
    private val server: ServerSocket,
    private val callbackPath: String,
    private val callbackHost: String,
) : Closeable {
    val redirectUri = "http://$callbackHost:${server.localPort}$callbackPath"

    suspend fun awaitCallback(accept: (String) -> Boolean): String = withContext(Dispatchers.IO) {
        while (!server.isClosed) {
            currentCoroutineContext().ensureActive()
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            socket.use {
                socket.soTimeout = 250
                val candidate = try {
                    val input = socket.getInputStream()
                    val deadline = System.nanoTime() + 3_000_000_000L
                    val request = readLine(input, 16_384, deadline).split(' ')
                    require(request.size == 3 && request[0] == "GET" && request[2] in setOf("HTTP/1.0", "HTTP/1.1"))
                    require(request[1].startsWith("$callbackPath?") && !request[1].contains('#'))
                    var host: String? = null
                    var headerBytes = 0
                    while (true) {
                        val header = readLine(input, 4096, deadline)
                        headerBytes += header.length
                        require(headerBytes <= 16_384)
                        if (header.isEmpty()) break
                        val parts = header.split(':', limit = 2)
                        require(parts.size == 2)
                        if (parts[0].lowercase(Locale.ROOT) == "host") {
                            require(host == null)
                            host = parts[1].trim()
                        }
                    }
                    require(host == "$callbackHost:${server.localPort}")
                    "http://$callbackHost:${server.localPort}" + request[1]
                } catch (_: java.io.IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
                val accepted = candidate != null && accept(candidate)
                val body = if (accepted) "Authorization received. Return to Operit." else "Invalid authorization callback."
                val bytes = body.toByteArray(UTF_8)
                try {
                    val headers = "HTTP/1.1 ${if (accepted) "200 OK" else "400 Bad Request"}\r\n" +
                        "Content-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                        "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
                        "Content-Security-Policy: default-src 'none'\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(headers.toByteArray(US_ASCII)); write(bytes); flush()
                    }
                } catch (_: java.io.IOException) {
                    // A browser closing its tab does not invalidate a verified callback.
                }
                if (accepted) return@withContext candidate!!
            }
        }
        throw IllegalStateException("OAuth callback listener was closed")
    }

    private suspend fun readLine(input: InputStream, limit: Int, deadline: Long): String {
        val result = StringBuilder()
        while (true) {
            currentCoroutineContext().ensureActive()
            require(System.nanoTime() < deadline && result.length <= limit)
            val value = input.read()
            require(value >= 0)
            if (value == 10) {
                require(result.endsWith("\r"))
                return result.dropLast(1).toString()
            }
            require(value == 13 || value in 0x20..0x7e)
            result.append(value.toChar())
        }
    }

    override fun close() { server.close() }

    companion object {
        fun open(config: ProviderOAuthConfig): ProviderOAuthLoopbackServer {
            val server = ServerSocket(config.redirectPort, 4, InetAddress.getByName("127.0.0.1"))
            server.soTimeout = 250
            return ProviderOAuthLoopbackServer(server, config.redirectPath, config.redirectHost)
        }
    }
}
