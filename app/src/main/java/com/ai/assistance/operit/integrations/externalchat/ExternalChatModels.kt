package com.ai.assistance.operit.integrations.externalchat

import java.util.Locale
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalChatRequest(
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("group")
    val group: String? = null,
    @SerialName("create_new_chat")
    val createNewChat: Boolean = false,
    @SerialName("chat_id")
    val chatId: String? = null,
    @SerialName("create_if_none")
    val createIfNone: Boolean = true,
    @SerialName("show_floating")
    val showFloating: Boolean = false,
    @SerialName("return_tool_status")
    val returnToolStatus: Boolean = true,
    @SerialName("initial_mode")
    val initialMode: String? = null,
    @SerialName("auto_exit_after_ms")
    val autoExitAfterMs: Long = -1L,
    @SerialName("timeout_ms")
    val timeoutMs: Long = -1L,
    @SerialName("stop_after")
    val stopAfter: Boolean = false
)

@Serializable
data class ExternalChatResult(
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("success")
    val success: Boolean,
    @SerialName("chat_id")
    val chatId: String? = null,
    @SerialName("ai_response")
    val aiResponse: String? = null,
    @SerialName("error")
    val error: String? = null
)

enum class ExternalChatResponseMode {
    SYNC,
    ASYNC_CALLBACK
}

@Serializable
data class ExternalChatHttpRequest(
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("group")
    val group: String? = null,
    @SerialName("create_new_chat")
    val createNewChat: Boolean = false,
    @SerialName("chat_id")
    val chatId: String? = null,
    @SerialName("create_if_none")
    val createIfNone: Boolean = true,
    @SerialName("show_floating")
    val showFloating: Boolean = false,
    @SerialName("return_tool_status")
    val returnToolStatus: Boolean = true,
    @SerialName("initial_mode")
    val initialMode: String? = null,
    @SerialName("auto_exit_after_ms")
    val autoExitAfterMs: Long = -1L,
    @SerialName("timeout_ms")
    val timeoutMs: Long = -1L,
    @SerialName("stop_after")
    val stopAfter: Boolean = false,
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("response_mode")
    val responseMode: String = "sync",
    @SerialName("callback_url")
    val callbackUrl: String? = null
) {
    fun normalizedResponseMode(): ExternalChatResponseMode? {
        return when (responseMode.trim().lowercase(Locale.US)) {
            "sync" -> ExternalChatResponseMode.SYNC
            "async_callback" -> ExternalChatResponseMode.ASYNC_CALLBACK
            else -> null
        }
    }

    fun resolvedRequestId(): String {
        return requestId?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    }

    fun toExecutionRequest(resolvedRequestId: String): ExternalChatRequest {
        return ExternalChatRequest(
            requestId = resolvedRequestId,
            message = message,
            group = group,
            createNewChat = createNewChat,
            chatId = chatId,
            createIfNone = createIfNone,
            showFloating = showFloating,
            returnToolStatus = returnToolStatus,
            initialMode = initialMode,
            autoExitAfterMs = autoExitAfterMs,
            timeoutMs = timeoutMs,
            stopAfter = stopAfter
        )
    }
}

@Serializable
data class ExternalChatAcceptedResponse(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("accepted")
    val accepted: Boolean = true,
    @SerialName("status")
    val status: String = "accepted"
)

@Serializable
data class ExternalChatHealthResponse(
    @SerialName("status")
    val status: String = "ok",
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("service_running")
    val serviceRunning: Boolean,
    @SerialName("port")
    val port: Int,
    @SerialName("version_name")
    val versionName: String
)

@Serializable
data class ExternalChatStreamEnvelope(
    @SerialName("event")
    val event: String,
    @SerialName("request_id")
    val requestId: String,
    @SerialName("chat_id")
    val chatId: String? = null,
    @SerialName("delta")
    val delta: String? = null,
    @SerialName("ai_response")
    val aiResponse: String? = null,
    @SerialName("success")
    val success: Boolean? = null,
    @SerialName("error")
    val error: String? = null
)
