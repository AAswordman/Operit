package com.ai.assistance.operit.ui.features.conversation.chat.foundation.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.data.model.ChatMessage

@Stable
class ChatMessageHeightMemory {
    private val measuredHeightsPx = linkedMapOf<Long, Int>()

    fun updateMeasured(messageId: Long, heightPx: Int) {
        if (heightPx <= 0) {
            return
        }
        measuredHeightsPx[messageId] = heightPx
    }

    fun prune(validMessageIds: Set<Long>) {
        val iterator = measuredHeightsPx.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key !in validMessageIds) {
                iterator.remove()
            }
        }
    }
}

@Composable
fun rememberChatMessageHeightMemory(messages: List<ChatMessage>): ChatMessageHeightMemory {
    val heightMemory = remember { ChatMessageHeightMemory() }
    val validMessageIds =
        messages
            .asSequence()
            .filter { it.sender == "ai" }
            .map { it.timestamp }
            .toSet()

    heightMemory.prune(validMessageIds)
    return heightMemory
}
