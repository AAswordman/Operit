package com.ai.assistance.operit.integrations.http.commands

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val webChatCommandJson = Json { encodeDefaults = true }

@Serializable
internal data class WebChatCommandRequest(
    val version: Int,
    val command: String,
    val port: Int? = null,
    val login: Boolean = false,
    val example: String? = null,
) {
    fun validate() {
        require(version == 1) { "Unsupported web-chat protocol version" }
        require(command in setOf("start-server", "stop-server", "status", "token-show", "token-reset", "examples")) {
            "Unknown web-chat command"
        }
        require(port == null || (command == "start-server" && port in 1..65535)) { "Invalid --port" }
        require(!login || command == "start-server") { "--login requires start-server" }
        require(example == null || (command == "examples" && example in setOf("sync", "async", "health", "intent"))) {
            "Invalid example type"
        }
    }
}

@Serializable
internal data class WebChatCommandResponse(val success: Boolean, val output: String, val version: Int = 1)

internal fun trustedWebChatPeer(uid: Int, appUid: Int): Boolean = uid == appUid || uid == 0

internal fun webChatLoginUrl(port: Int, token: String): String =
    "http://127.0.0.1:$port/?token=${URLEncoder.encode(token, Charsets.UTF_8.name())}"

internal fun readWebChatCommand(input: InputStream): String {
    val bytes = ByteArrayOutputStream()
    // 本地协议只接受一行有界请求，截断或缺少换行都作为错误报告。
    repeat(16_384) {
        when (val byte = input.read()) {
            -1 -> error("Incomplete web-chat request")
            10 -> return bytes.toString(Charsets.UTF_8.name())
            else -> bytes.write(byte)
        }
    }
    error("web-chat request is too large")
}
