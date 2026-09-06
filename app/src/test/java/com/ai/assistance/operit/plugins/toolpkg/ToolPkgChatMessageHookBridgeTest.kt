package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ToolPkgChatMessageHookBridgeTest {

    @Test
    fun `buildChatMessageEventPayload contains all required message fields including variant index`() {
        val message = ChatMessage(
            sender = "ai",
            roleName = "Assistant",
            content = "This is a regenerated variant response",
            timestamp = 1700000000000L,
            completedAt = 1700000005000L,
            provider = "deepseek",
            modelName = "deepseek-chat",
            inputTokens = 120,
            outputTokens = 250,
            cachedInputTokens = 64,
            sentAt = 1699999999000L,
            outputDurationMs = 3500L,
            waitDurationMs = 500L,
            displayMode = MessageDisplayMode.NORMAL,
            selectedVariantIndex = 2,
            isFavorite = false
        )

        val payload = ToolPkgChatMessageHookBridge.buildChatMessageEventPayload(
            chatId = "test-chat-123",
            message = message
        )

        assertEquals("test-chat-123", payload["chatId"])
        assertEquals(1700000000000L, payload["timestamp"])
        assertEquals("ai", payload["sender"])
        assertEquals("Assistant", payload["roleName"])
        assertEquals("This is a regenerated variant response", payload["content"])
        assertEquals(1700000005000L, payload["completedAt"])
        assertEquals("deepseek", payload["provider"])
        assertEquals("deepseek-chat", payload["modelName"])
        assertEquals(120, payload["inputTokens"])
        assertEquals(250, payload["outputTokens"])
        assertEquals(64, payload["cachedInputTokens"])
        assertEquals(1699999999000L, payload["sentAt"])
        assertEquals(3500L, payload["outputDurationMs"])
        assertEquals(500L, payload["waitDurationMs"])
        assertEquals("NORMAL", payload["displayMode"])
        assertEquals(2, payload["selectedVariantIndex"])
        assertEquals(false, payload["isFavorite"])
    }

    @Test
    fun `dispatchMessagePersisted triggers test listener with correct variant information`() {
        var receivedChatId: String? = null
        var receivedMessage: ChatMessage? = null

        ToolPkgChatMessageHookBridge.onMessagePersistedDispatchedForTest = { chatId, message ->
            receivedChatId = chatId
            receivedMessage = message
        }

        try {
            val variantMessage = ChatMessage(
                sender = "ai",
                content = "Regenerated content from roll",
                timestamp = 1700000000000L,
                selectedVariantIndex = 3
            )

            ToolPkgChatMessageHookBridge.dispatchMessagePersisted(
                chatId = "chat-abc",
                message = variantMessage
            )

            assertEquals("chat-abc", receivedChatId)
            assertNotNull(receivedMessage)
            assertEquals("Regenerated content from roll", receivedMessage?.content)
            assertEquals(1700000000000L, receivedMessage?.timestamp)
            assertEquals(3, receivedMessage?.selectedVariantIndex)
        } finally {
            ToolPkgChatMessageHookBridge.onMessagePersistedDispatchedForTest = null
        }
    }
}
