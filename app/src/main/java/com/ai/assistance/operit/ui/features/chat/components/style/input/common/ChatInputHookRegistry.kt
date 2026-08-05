package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ChatInputHookRegistry"

object ChatInputEvents {
    const val INPUT_CHANGED = "input_changed"
    const val SUBMIT_REQUESTED = "submit_requested"
    const val SUBMITTED = "submitted"
}

object ChatInputSubmitActions {
    const val ALLOW = "allow"
    const val BLOCK = "block"
    const val REPLACE = "replace"
    const val CONSUME = "consume"
}

data class ChatInputHookContext(
    val context: Context,
    val eventName: String,
    val chatId: String? = null,
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val hasAttachments: Boolean = false,
    val attachmentCount: Int = 0,
    val isProcessing: Boolean = false,
    val inputStyle: String = "",
    val source: String = "",
    val submitSource: String = ""
)

data class ChatInputHookResult(
    val action: String = ChatInputSubmitActions.ALLOW,
    val text: String? = null,
    val message: String? = null,
    val noticeMessage: String? = null,
    val clearInput: Boolean = false,
    val metadata: Map<String, Any?> = emptyMap()
)

interface ChatInputHook {
    val id: String

    suspend fun onEvent(context: ChatInputHookContext): ChatInputHookResult? = null
}

object ChatInputHookRegistry {
    private val hooks = CopyOnWriteArrayList<ChatInputHook>()
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun register(hook: ChatInputHook) {
        unregister(hook.id)
        hooks.add(hook)
    }

    @Synchronized
    fun unregister(hookId: String) {
        hooks.removeAll { it.id == hookId }
    }

    fun dispatchNotification(context: ChatInputHookContext) {
        val snapshot = hooks.toList()
        if (snapshot.isEmpty()) {
            return
        }
        snapshot.forEach { hook ->
            notificationScope.launch {
                runCatching { hook.onEvent(context) }
                    .onFailure { error ->
                        AppLogger.e(
                            TAG,
                            "Chat input hook notification failed: hook=${hook.id}, event=${context.eventName}",
                            error
                        )
                    }
            }
        }
    }

    suspend fun dispatchSubmitRequested(
        context: ChatInputHookContext
    ): ChatInputHookResult {
        var current = context.copy(eventName = ChatInputEvents.SUBMIT_REQUESTED)
        var noticeMessage: String? = null
        for (hook in hooks) {
            val resultOrNull =
                runCatching { hook.onEvent(current) }
                    .getOrElse { error ->
                        AppLogger.e(TAG, "Chat input submit hook failed: hook=${hook.id}", error)
                        null
                    }
            if (resultOrNull == null) {
                continue
            }
            val result = resultOrNull
            if (!result.noticeMessage.isNullOrBlank()) {
                noticeMessage = result.noticeMessage
            }

            when (result.action.trim().lowercase()) {
                ChatInputSubmitActions.BLOCK -> return result.copy(action = ChatInputSubmitActions.BLOCK)
                ChatInputSubmitActions.CONSUME -> return result.copy(action = ChatInputSubmitActions.CONSUME)
                ChatInputSubmitActions.REPLACE -> {
                    val replacement = result.text ?: current.text
                    current =
                        current.copy(
                            text = replacement,
                            selectionStart = replacement.length,
                            selectionEnd = replacement.length
                        )
                }
            }
        }
        return ChatInputHookResult(
            action = ChatInputSubmitActions.ALLOW,
            text = current.text,
            noticeMessage = noticeMessage
        )
    }
}
