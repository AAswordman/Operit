package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantContinuationTest {
    @Test fun steeringAndMailboxInputsKeepOnlyTheFirstHeader() {
        val first = ai(10).copy(displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE)
        val messages = listOf(
            first,
            ChatMessage(sender = "user", content = "steer"),
            first.copy(timestamp = 2),
            ChatMessage(sender = "user", content = "mail", displayMode = ChatMessageDisplayMode.COLLABORATION_EVENT),
            ai(10),
        )
        assertFalse(isAssistantContinuation(messages, 0))
        assertFalse(isAssistantContinuation(messages, 1))
        assertTrue(isAssistantContinuation(messages, 2))
        assertTrue(isAssistantContinuation(messages, 4))
    }

    @Test fun newTurnsAndIndependentRepliesKeepTheirHeaders() {
        val intermediate = ai(10).copy(displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE)
        assertFalse(isAssistantContinuation(listOf(intermediate, ai(20)), 1))
        assertFalse(isAssistantContinuation(listOf(ai(10), ai(10)), 1))
    }

    @Test fun missingHistoryOrTurnIdentityDoesNotHideTheFirstVisibleHeader() {
        assertFalse(isAssistantContinuation(listOf(ai(10)), 0))
        val unknown = ai(0).copy(displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE)
        assertFalse(isAssistantContinuation(listOf(unknown, ai(0)), 1))
        assertFalse(isAssistantContinuation(emptyList(), -1))
    }

    private fun ai(sentAt: Long) = ChatMessage(sender = "ai", content = "reply", sentAt = sentAt)
}
