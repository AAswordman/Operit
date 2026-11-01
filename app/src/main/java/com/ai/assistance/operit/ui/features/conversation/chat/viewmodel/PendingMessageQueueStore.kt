package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel

import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.PendingQueueMessageItem
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PendingMessageQueueState(
    val messages: List<PendingQueueMessageItem> = emptyList(),
    val isExpanded: Boolean = true,
    val wasBlocked: Boolean = false,
    val suppressNextAutoDequeue: Boolean = false,
)

internal class PendingMessageQueueStore {
    private val lock = Any()
    private val nextMessageId = AtomicLong(1L)
    private val _states = MutableStateFlow<Map<String, PendingMessageQueueState>>(emptyMap())

    val states: StateFlow<Map<String, PendingMessageQueueState>> = _states.asStateFlow()

    fun enqueue(chatId: String, text: String, isQueueBlocked: Boolean) {
        synchronized(lock) {
            updateState(chatId) { state ->
                state.copy(
                    messages = state.messages + PendingQueueMessageItem(nextMessageId.getAndIncrement(), text),
                    isExpanded = true,
                    wasBlocked = isQueueBlocked,
                )
            }
        }
    }

    fun remove(chatId: String, messageId: Long): PendingQueueMessageItem? = synchronized(lock) {
        val state = _states.value[chatId] ?: return@synchronized null
        val messageIndex = state.messages.indexOfFirst { item -> item.id == messageId }
        if (messageIndex < 0) return@synchronized null

        val message = state.messages[messageIndex]
        updateState(chatId) {
            it.copy(messages = it.messages.filterNot { item -> item.id == messageId })
        }
        message
    }

    fun restore(chatId: String, message: PendingQueueMessageItem) {
        synchronized(lock) {
            updateState(chatId) { state ->
                if (state.messages.any { item -> item.id == message.id }) {
                    state
                } else {
                    state.copy(messages = listOf(message) + state.messages)
                }
            }
        }
    }

    fun setExpanded(chatId: String, expanded: Boolean) {
        synchronized(lock) {
            updateState(chatId) { state -> state.copy(isExpanded = expanded) }
        }
    }

    fun suppressNextAutoDequeue(chatId: String) {
        synchronized(lock) {
            updateState(chatId) { state -> state.copy(suppressNextAutoDequeue = true) }
        }
    }

    fun consumeAutoDequeueSignal(chatId: String, isQueueBlocked: Boolean): Boolean =
        synchronized(lock) {
            val state = _states.value[chatId] ?: return@synchronized false
            val shouldAutoDequeue =
                state.wasBlocked &&
                    !isQueueBlocked &&
                    !state.suppressNextAutoDequeue &&
                    state.messages.isNotEmpty()
            updateState(chatId) {
                it.copy(
                    wasBlocked = isQueueBlocked,
                    suppressNextAutoDequeue =
                        if (isQueueBlocked) it.suppressNextAutoDequeue else false,
                )
            }
            shouldAutoDequeue
        }

    fun removeChat(chatId: String) {
        synchronized(lock) {
            if (chatId in _states.value) {
                _states.value = _states.value - chatId
            }
        }
    }

    private fun updateState(
        chatId: String,
        transform: (PendingMessageQueueState) -> PendingMessageQueueState,
    ) {
        val updatedStates = _states.value.toMutableMap()
        updatedStates[chatId] = transform(updatedStates[chatId] ?: PendingMessageQueueState())
        _states.value = updatedStates
    }
}
