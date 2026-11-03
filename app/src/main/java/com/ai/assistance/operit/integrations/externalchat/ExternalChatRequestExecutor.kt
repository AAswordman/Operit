package com.ai.assistance.operit.integrations.externalchat

import android.content.Context
import com.ai.assistance.operit.core.tools.ChatListResultData
import com.ai.assistance.operit.core.tools.MessageSendResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.MessageSendStreamSession
import com.ai.assistance.operit.core.tools.defaultTool.standard.MessageSendStreamStartResult
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardChatManagerTool
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatTrace
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class ExternalChatStreamingSession(
    val requestId: String,
    val message: String,
    val chatId: String,
    val responseStreamSession: MessageSendStreamSession,
    private val cleanupAction: () -> Unit
) {
    private val cleanedUp = AtomicBoolean(false)

    fun cleanup() {
        if (cleanedUp.compareAndSet(false, true)) {
            cleanupAction()
        }
    }
}

sealed class ExternalChatStreamingStartResult {
    data class Started(val session: ExternalChatStreamingSession) : ExternalChatStreamingStartResult()

    data class Failed(val result: ExternalChatResult) : ExternalChatStreamingStartResult()
}

class ExternalChatRequestExecutor(context: Context) {

    private val appContext = context.applicationContext

    suspend fun execute(request: ExternalChatRequest): ExternalChatResult {
        return try {
            when (val preparation = prepareRequest(request)) {
                is PreparationResult.Failed -> preparation.result
                is PreparationResult.Ready -> {
                    try {
                        val sendResult = preparation.chatTool.sendMessageToAI(preparation.sendTool)
                        toExternalChatResult(
                            requestId = preparation.requestId,
                            sendResult = sendResult,
                            returnToolStatus = preparation.returnToolStatus
                        )
                    } finally {
                        preparation.cleanup()
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to execute external chat request", e)
            ExternalChatResult(
                requestId = request.requestId?.trim()?.takeIf { it.isNotBlank() },
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    suspend fun startStreaming(request: ExternalChatRequest): ExternalChatStreamingStartResult {
        return try {
            when (val preparation = prepareRequest(request)) {
                is PreparationResult.Failed -> ExternalChatStreamingStartResult.Failed(preparation.result)
                is PreparationResult.Ready -> {
                    when (val startResult = preparation.chatTool.startMessageToAIStream(preparation.sendTool)) {
                        is MessageSendStreamStartResult.Failed -> {
                            preparation.cleanup()
                            ExternalChatStreamingStartResult.Failed(
                                toExternalChatResult(
                                    requestId = preparation.requestId,
                                    sendResult = startResult.result,
                                    returnToolStatus = preparation.returnToolStatus
                                )
                            )
                        }

                        is MessageSendStreamStartResult.Started -> {
                            ExternalChatStreamingStartResult.Started(
                                ExternalChatStreamingSession(
                                    requestId = preparation.resolvedRequestId,
                                    message = preparation.message,
                                    chatId = startResult.session.chatId,
                                    responseStreamSession = startResult.session,
                                    cleanupAction = {
                                        preparation.cleanup()
                                    }
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start external chat streaming request", e)
            ExternalChatStreamingStartResult.Failed(
                ExternalChatResult(
                    requestId = request.requestId?.trim()?.takeIf { it.isNotBlank() },
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            )
        }
    }

    private suspend fun prepareRequest(request: ExternalChatRequest): PreparationResult {
        val requestId = request.requestId?.trim()?.takeIf { it.isNotBlank() }
        val resolvedRequestId = requestId ?: UUID.randomUUID().toString()
        val message = request.message?.trim()
        if (message.isNullOrBlank()) {
            return PreparationResult.Failed(
                ExternalChatResult(
                    requestId = requestId,
                    success = false,
                    error = "Missing extra: message"
                )
            )
        }

        val chatTool = StandardChatManagerTool(appContext)

        AppLogger.d(
            TAG,
            "prepareRequest resolved=${requestId != null} ${ChatTrace.describe(message)}"
        )

        if (request.showFloating) {
            val params = mutableListOf<ToolParameter>()
            request.initialMode?.trim()?.takeIf { it.isNotBlank() }?.let {
                params += ToolParameter(name = "initial_mode", value = it)
            }
            if (request.autoExitAfterMs > 0) {
                params += ToolParameter(name = "timeout_ms", value = request.autoExitAfterMs.toString())
            }
            val startResult = chatTool.startChatService(
                AITool(
                    name = "start_chat_service",
                    parameters = params
                )
            )
            if (!startResult.success) {
                return PreparationResult.Failed(
                    ExternalChatResult(
                        requestId = requestId,
                        success = false,
                        error = startResult.error?.takeIf { it.isNotBlank() }
                            ?: "Failed to start chat service"
                    )
                )
            }
        }

        if (!request.createNewChat && request.chatId.isNullOrBlank() && !request.createIfNone) {
            val listResult = chatTool.listChats(AITool(name = "list_chats"))
            val currentChatId = (listResult.result as? ChatListResultData)?.currentChatId
            if (currentChatId.isNullOrBlank()) {
                return PreparationResult.Failed(
                    ExternalChatResult(
                        requestId = requestId,
                        success = false,
                        error = "No current chat and create_if_none=false"
                    )
                )
            }
        }

        if (request.createNewChat) {
            val params = mutableListOf<ToolParameter>()
            request.group?.trim()?.takeIf { it.isNotBlank() }?.let {
                params += ToolParameter(name = "group", value = it)
            }
            chatTool.createNewChat(
                AITool(
                    name = "create_new_chat",
                    parameters = params
                )
            )
        }

        val sendParams = mutableListOf(
            ToolParameter(name = "message", value = message)
        )
        if (!request.createNewChat) {
            request.chatId?.trim()?.takeIf { it.isNotBlank() }?.let {
                sendParams += ToolParameter(name = "chat_id", value = it)
            }
        }
        if (request.timeoutMs > 0) {
            sendParams += ToolParameter(name = "timeout_ms", value = request.timeoutMs.toString())
        }

        return PreparationResult.Ready(
            requestId = requestId,
            resolvedRequestId = resolvedRequestId,
            message = message,
            returnToolStatus = request.returnToolStatus,
            chatTool = chatTool,
            sendTool = AITool(
                name = "send_message_to_ai",
                parameters = sendParams
            ),
            cleanupAction = {
                if (request.stopAfter) {
                    runCatching {
                        runBlocking {
                            chatTool.stopChatService(AITool(name = "stop_chat_service"))
                        }
                    }
                }
            }
        )
    }

    private suspend fun toExternalChatResult(
        requestId: String?,
        sendResult: ToolResult,
        returnToolStatus: Boolean
    ): ExternalChatResult {
        val resultData = sendResult.result as? MessageSendResultData
        return ExternalChatResult(
            requestId = requestId,
            success = sendResult.success,
            chatId = resultData?.chatId?.takeIf { it.isNotBlank() },
            aiResponse = ExternalChatResponseSanitizer.sanitize(resultData?.aiResponse, returnToolStatus),
            error = sendResult.error?.takeIf { it.isNotBlank() }
        )
    }

    private sealed class PreparationResult {
        data class Ready(
            val requestId: String?,
            val resolvedRequestId: String,
            val message: String,
            val returnToolStatus: Boolean,
            val chatTool: StandardChatManagerTool,
            val sendTool: AITool,
            val cleanupAction: () -> Unit
        ) : PreparationResult() {
            fun cleanup() {
                cleanupAction()
            }
        }

        data class Failed(val result: ExternalChatResult) : PreparationResult()
    }

    companion object {
        private const val TAG = "ExternalChatExecutor"
    }
}
