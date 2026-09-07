package com.ai.assistance.operit.core.chat.hooks

import android.content.Context
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ChatRuntimeHookRegistry"

enum class ChatRuntimeHookEvent(val wireName: String) {
    STATE_CHANGED("state_changed")
}

data class ChatRuntimeHookContext(
    val context: Context,
    val chatId: String,
    val slot: ChatRuntimeSlot,
    val state: InputProcessingState,
    val activeChatIds: Set<String>,
    val currentTurnToolInvocationCount: Int,
    val activeConversationCount: Int,
    val currentSessionToolCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

interface ChatRuntimeHook {
    val id: String

    suspend fun onEvent(
        event: ChatRuntimeHookEvent,
        context: ChatRuntimeHookContext
    )
}

object ChatRuntimeHookRegistry {
    private val hooks = CopyOnWriteArrayList<ChatRuntimeHook>()
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Synchronized
    fun registerHook(hook: ChatRuntimeHook) {
        unregisterHook(hook.id)
        hooks.add(hook)
    }

    @Synchronized
    fun unregisterHook(hookId: String) {
        hooks.removeAll { it.id == hookId }
    }

    suspend fun dispatch(
        event: ChatRuntimeHookEvent,
        context: ChatRuntimeHookContext
    ) {
        hooks.forEach { hook ->
            try {
                hook.onEvent(event, context)
            } catch (error: Exception) {
                AppLogger.e(
                    TAG,
                    "Chat runtime hook failed: ${hook.id}, event=${event.wireName}, chatId=${context.chatId}",
                    error
                )
            }
        }
    }

    fun dispatchAsync(
        event: ChatRuntimeHookEvent,
        context: ChatRuntimeHookContext
    ) {
        dispatchScope.launch {
            dispatch(event = event, context = context)
        }
    }
}
