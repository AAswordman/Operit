package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.ui.features.chat.components.style.input.common.PendingQueueMessageItem
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
    private val chatGenerations = mutableMapOf<String, Long>()
    private val inactiveChatIds = mutableSetOf<String>()
    private var lastExistingChatIds: Set<String> = emptySet()
    private val _states = MutableStateFlow<Map<String, PendingMessageQueueState>>(emptyMap())

    val states: StateFlow<Map<String, PendingMessageQueueState>> = _states.asStateFlow()

    fun enqueue(chatId: String, text: String, isQueueBlocked: Boolean,
                attachments: List<com.ai.assistance.operit.data.model.AttachmentInfo> = emptyList(),
                replyToMessage: com.ai.assistance.operit.data.model.ChatMessage? = null): Long? {
        synchronized(lock) {
            if (chatId in inactiveChatIds) return null
            val generation = chatGenerations[chatId] ?: 0L
            val id = nextMessageId.getAndIncrement()
            updateState(chatId) { state ->
                state.copy(
                    messages =
                        state.messages +
                            PendingQueueMessageItem(
                                id = id,
                                text = text,
                                chatGeneration = generation,
                                attachments = attachments.toList(),
                                replyToMessage = replyToMessage,
                            ),
                    isExpanded = true,
                    wasBlocked = isQueueBlocked,
                )
            }
            return id
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

    fun restore(chatId: String, message: PendingQueueMessageItem, pauseAutoSend: Boolean = false) {
        synchronized(lock) {
            if (chatId in inactiveChatIds) return
            if (message.chatGeneration != (chatGenerations[chatId] ?: 0L)) return
            updateState(chatId) { state ->
                state.copy(
                    messages = if (state.messages.any { it.id == message.id }) state.messages
                        else (state.messages + message).sortedBy { it.id },
                    suppressNextAutoDequeue = state.suppressNextAutoDequeue || pauseAutoSend,
                )
            }
        }
    }

    fun setExpanded(chatId: String, expanded: Boolean) {
        synchronized(lock) {
            updateState(chatId) { state -> state.copy(isExpanded = expanded) }
        }
    }

    fun returnSteer(chatId: String, message: PendingQueueMessageItem, sendLater: Boolean = false) {
        synchronized(lock) {
            if (!isCurrentGeneration(chatId, message.chatGeneration)) return
            updateState(chatId) { state ->
                state.copy(
                    messages = state.messages.map { if (it.id == message.id) message else it },
                    suppressNextAutoDequeue = !sendLater,
                    wasBlocked = state.wasBlocked || sendLater,
                )
            }
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
            if (isQueueBlocked) {
                updateState(chatId) { it.copy(wasBlocked = true) }
                return@synchronized false
            }
            if (state.suppressNextAutoDequeue) {
                updateState(chatId) {
                    it.copy(wasBlocked = false, suppressNextAutoDequeue = false)
                }
                return@synchronized false
            }
            state.wasBlocked && state.messages.any { !it.isSteering }
        }

    fun takeNextAutoDequeue(chatId: String): PendingQueueMessageItem? =
        synchronized(lock) {
            val state = _states.value[chatId] ?: return@synchronized null
            if (!state.wasBlocked || state.suppressNextAutoDequeue) return@synchronized null
            val message = state.messages.firstOrNull { !it.isSteering } ?: return@synchronized null
            updateState(chatId) {
                it.copy(
                    messages = it.messages.filterNot { queued -> queued.id == message.id },
                    wasBlocked = false,
                )
            }
            message
        }

    fun removeChat(chatId: String) {
        synchronized(lock) {
            chatGenerations[chatId] = (chatGenerations[chatId] ?: 0L) + 1L
            inactiveChatIds += chatId
            if (chatId in _states.value) _states.value = _states.value - chatId
        }
    }

    /** Reactivates a deleted ID only after the chat list observes an absent -> present transition. */
    fun syncExistingChatIds(existingChatIds: Set<String>) {
        synchronized(lock) {
            val reintroducedChatIds = existingChatIds - lastExistingChatIds
            inactiveChatIds.removeAll(reintroducedChatIds)
            lastExistingChatIds = existingChatIds.toSet()
        }
    }

    fun isChatInactive(chatId: String): Boolean = synchronized(lock) { chatId in inactiveChatIds }

    fun isCurrentGeneration(chatId: String, generation: Long): Boolean = synchronized(lock) {
        chatId !in inactiveChatIds && generation == (chatGenerations[chatId] ?: 0L)
    }

    private fun updateState(
        chatId: String,
        transform: (PendingMessageQueueState) -> PendingMessageQueueState,
    ) {
        if (chatId in inactiveChatIds) return
        val updated = _states.value.toMutableMap()
        updated[chatId] = transform(updated[chatId] ?: PendingMessageQueueState())
        _states.value = updated
    }
}
