package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CurrentChatWindowLoadResult(
    val messages: List<ChatMessage>,
    val hasOlderPersistedHistory: Boolean,
    val hasNewerPersistedHistory: Boolean,
)

internal class CurrentChatWindowController {
    private var generation = 0L
    fun generation(): Long = generation
    fun invalidateLoads() {
        generation++
        _isLoadingDisplayWindow.value = false
    }
    fun isCurrent(token: Long): Boolean = generation == token
    private var displayStartTimestamp: Long? = null
    private var displayEndTimestamp: Long? = null

    private var hasOlderPersistedHistory = false
    private var hasNewerPersistedHistory = false

    private val _hasOlderDisplayHistory = MutableStateFlow(false)
    val hasOlderDisplayHistory: StateFlow<Boolean> = _hasOlderDisplayHistory.asStateFlow()

    private val _hasNewerDisplayHistory = MutableStateFlow(false)
    val hasNewerDisplayHistory: StateFlow<Boolean> = _hasNewerDisplayHistory.asStateFlow()

    private val _isLoadingDisplayWindow = MutableStateFlow(false)
    val isLoadingDisplayWindow: StateFlow<Boolean> = _isLoadingDisplayWindow.asStateFlow()

    fun reset() {
        invalidateLoads()
        displayStartTimestamp = null
        displayEndTimestamp = null
        hasOlderPersistedHistory = false
        hasNewerPersistedHistory = false
        _hasOlderDisplayHistory.value = false
        _hasNewerDisplayHistory.value = false
        _isLoadingDisplayWindow.value = false
    }

    fun applyLoadResult(
        result: CurrentChatWindowLoadResult,
        chatHistoryFlow: MutableStateFlow<List<ChatMessage>>,
    ) {
        applyMessages(
            messages = result.messages,
            chatHistoryFlow = chatHistoryFlow,
            hasOlderPersistedHistory = result.hasOlderPersistedHistory,
            hasNewerPersistedHistory = result.hasNewerPersistedHistory,
        )
        _isLoadingDisplayWindow.value = false
    }

    fun tryApplyLoadResult(
        result: CurrentChatWindowLoadResult,
        chatHistoryFlow: MutableStateFlow<List<ChatMessage>>,
        token: Long,
        expectedMessages: List<ChatMessage>? = null,
    ): Boolean {
        if (!isCurrent(token) ||
            (expectedMessages != null && chatHistoryFlow.value !== expectedMessages)) return false
        applyLoadResult(result, chatHistoryFlow)
        return true
    }

    fun applyMessages(
        messages: List<ChatMessage>,
        chatHistoryFlow: MutableStateFlow<List<ChatMessage>>,
        hasOlderPersistedHistory: Boolean? = null,
        hasNewerPersistedHistory: Boolean? = null,
    ) {
        chatHistoryFlow.value = messages
        displayStartTimestamp = messages.firstOrNull()?.timestamp
        displayEndTimestamp = messages.lastOrNull()?.timestamp
        if (hasOlderPersistedHistory != null) {
            this.hasOlderPersistedHistory = hasOlderPersistedHistory
        }
        if (hasNewerPersistedHistory != null) {
            this.hasNewerPersistedHistory = hasNewerPersistedHistory
        }
        if (messages.isEmpty()) {
            this.hasOlderPersistedHistory = false
            this.hasNewerPersistedHistory = false
            _hasOlderDisplayHistory.value = false
            _hasNewerDisplayHistory.value = false
            return
        }

        _hasOlderDisplayHistory.value = this.hasOlderPersistedHistory
        _hasNewerDisplayHistory.value = this.hasNewerPersistedHistory
    }

    fun currentDisplayStartTimestamp(): Long? = displayStartTimestamp

    fun currentDisplayEndTimestamp(): Long? = displayEndTimestamp

    fun hasPersistedOlderHistoryNow(): Boolean = hasOlderPersistedHistory

    fun hasPersistedNewerHistoryNow(): Boolean = hasNewerPersistedHistory

    fun hasNewerDisplayHistoryNow(): Boolean = _hasNewerDisplayHistory.value

    fun beginLoadingDisplayWindow(): Boolean {
        if (_isLoadingDisplayWindow.value) {
            return false
        }
        _isLoadingDisplayWindow.value = true
        return true
    }

    fun finishLoadingDisplayWindowFailure(token: Long = generation) {
        if (isCurrent(token)) _isLoadingDisplayWindow.value = false
    }
}
