package com.ai.assistance.operit.integrations.intent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequest
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequestExecutor
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExternalChatReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ExternalChatReceiver"

        const val ACTION_EXTERNAL_CHAT = "com.ai.assistance.operit.EXTERNAL_CHAT"
        const val ACTION_EXTERNAL_CHAT_RESULT = "com.ai.assistance.operit.EXTERNAL_CHAT_RESULT"

        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_GROUP = "group"
        const val EXTRA_CREATE_NEW_CHAT = "create_new_chat"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CREATE_IF_NONE = "create_if_none"

        const val EXTRA_SHOW_FLOATING = "show_floating"
        const val EXTRA_RETURN_TOOL_STATUS = "return_tool_status"
        const val EXTRA_INITIAL_MODE = "initial_mode"
        const val EXTRA_AUTO_EXIT_AFTER_MS = "auto_exit_after_ms"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val EXTRA_STOP_AFTER = "stop_after"

        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"

        const val EXTRA_RESULT_SUCCESS = "success"
        const val EXTRA_RESULT_CHAT_ID = "chat_id"
        const val EXTRA_RESULT_AI_RESPONSE = "ai_response"
        const val EXTRA_RESULT_ERROR = "error"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXTERNAL_CHAT) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.takeIf { it.isNotBlank() }
                    ?: ACTION_EXTERNAL_CHAT_RESULT
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.takeIf { it.isNotBlank() }
                val result = ExternalChatRequestExecutor(context.applicationContext).execute(
                    ExternalChatRequest(
                        requestId = intent.getStringExtra(EXTRA_REQUEST_ID),
                        message = intent.getStringExtra(EXTRA_MESSAGE),
                        group = intent.getStringExtra(EXTRA_GROUP),
                        createNewChat = intent.getBooleanExtra(EXTRA_CREATE_NEW_CHAT, false),
                        chatId = intent.getStringExtra(EXTRA_CHAT_ID),
                        createIfNone = intent.getBooleanExtra(EXTRA_CREATE_IF_NONE, true),
                        showFloating = intent.getBooleanExtra(EXTRA_SHOW_FLOATING, false),
                        returnToolStatus = intent.getBooleanExtra(EXTRA_RETURN_TOOL_STATUS, true),
                        initialMode = intent.getStringExtra(EXTRA_INITIAL_MODE),
                        autoExitAfterMs = intent.getLongExtra(EXTRA_AUTO_EXIT_AFTER_MS, -1L),
                        timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT_MS, -1L),
                        stopAfter = intent.getBooleanExtra(EXTRA_STOP_AFTER, false)
                    )
                )

                sendResultBroadcast(
                    context = context,
                    action = replyAction,
                    packageName = replyPackage,
                    result = result
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to handle external chat", e)
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.takeIf { it.isNotBlank() }
                    ?: ACTION_EXTERNAL_CHAT_RESULT
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.takeIf { it.isNotBlank() }
                sendResultBroadcast(
                    context = context,
                    action = replyAction,
                    packageName = replyPackage,
                    result = ExternalChatResult(
                        requestId = intent.getStringExtra(EXTRA_REQUEST_ID),
                        success = false,
                        error = e.message ?: "Unknown error"
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun sendResultBroadcast(
        context: Context,
        action: String,
        packageName: String?,
        result: ExternalChatResult
    ) {
        val out = Intent(action)
        if (!packageName.isNullOrBlank()) {
            out.`package` = packageName
        }
        if (!result.requestId.isNullOrBlank()) {
            out.putExtra(EXTRA_REQUEST_ID, result.requestId)
        }
        out.putExtra(EXTRA_RESULT_SUCCESS, result.success)
        if (!result.chatId.isNullOrBlank()) {
            out.putExtra(EXTRA_RESULT_CHAT_ID, result.chatId)
        }
        if (!result.aiResponse.isNullOrBlank()) {
            out.putExtra(EXTRA_RESULT_AI_RESPONSE, result.aiResponse)
        }
        if (!result.error.isNullOrBlank()) {
            out.putExtra(EXTRA_RESULT_ERROR, result.error)
        }
        context.sendBroadcast(out)
    }
}
