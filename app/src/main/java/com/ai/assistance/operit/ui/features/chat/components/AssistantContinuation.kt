package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode

internal fun isAssistantContinuation(messages: List<ChatMessage>, index: Int): Boolean {
    val message = messages.getOrNull(index) ?: return false
    if (message.sender != "ai" || message.sentAt <= 0L) return false
    val previousIndex = (index - 1 downTo 0).firstOrNull { messages[it].sender == "ai" }
        ?: return false
    val previous = messages[previousIndex]
    return previous.displayMode == ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE &&
        previous.sentAt == message.sentAt
}
