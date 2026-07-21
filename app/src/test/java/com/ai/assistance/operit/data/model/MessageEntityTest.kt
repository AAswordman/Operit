package com.ai.assistance.operit.data.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEntityTest {

    @After
    fun tearDown() {
        ChatMessageTimestampAllocator.next()
    }

    @Test fun `create with required fields`() {
        val entity = MessageEntity(chatId = "chat1", sender = "user", content = "Hello", orderIndex = 0)
        assertEquals("chat1", entity.chatId)
        assertEquals("user", entity.sender)
        assertEquals("Hello", entity.content)
        assertEquals(0, entity.orderIndex)
    }

    @Test fun `message id defaults to zero`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals(0L, entity.messageId)
    }

    @Test fun `timestamp defaults to current time`() {
        val before = System.currentTimeMillis()
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        val after = System.currentTimeMillis()
        assertTrue(entity.timestamp >= before)
        assertTrue(entity.timestamp <= after + 1000)
    }

    @Test fun `order index is preserved`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 42)
        assertEquals(42, entity.orderIndex)
    }

    @Test fun `role name defaults to empty`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals("", entity.roleName)
    }

    @Test fun `selected variant index defaults to zero`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals(0, entity.selectedVariantIndex)
    }

    @Test fun `provider defaults to empty`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals("", entity.provider)
    }

    @Test fun `model name defaults to empty`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals("", entity.modelName)
    }

    @Test fun `tokens default to zero`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals(0, entity.inputTokens)
        assertEquals(0, entity.outputTokens)
        assertEquals(0, entity.cachedInputTokens)
    }

    @Test fun `display mode defaults to NORMAL`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertEquals(ChatMessageDisplayMode.NORMAL.name, entity.displayMode)
    }

    @Test fun `is favorite defaults to false`() {
        val entity = MessageEntity(chatId = "c", sender = "s", content = "c", orderIndex = 0)
        assertFalse(entity.isFavorite)
    }

    @Test fun `toChatMessage converts correctly`() {
        val entity = MessageEntity(
            chatId = "chat1",
            sender = "ai",
            content = "Response",
            orderIndex = 1,
            timestamp = 1000L,
            roleName = "Assistant",
            selectedVariantIndex = 0,
            provider = "openai",
            modelName = "gpt-4",
            inputTokens = 10,
            outputTokens = 20,
            displayMode = ChatMessageDisplayMode.NORMAL.name,
            isFavorite = true,
        )
        val msg = entity.toChatMessage()
        assertEquals("ai", msg.sender)
        assertEquals("Response", msg.content)
        assertEquals(1000L, msg.timestamp)
        assertEquals("Assistant", msg.roleName)
        assertEquals(0, msg.selectedVariantIndex)
        assertEquals("openai", msg.provider)
        assertEquals("gpt-4", msg.modelName)
        assertEquals(10, msg.inputTokens)
        assertEquals(20, msg.outputTokens)
        assertEquals(ChatMessageDisplayMode.NORMAL, msg.displayMode)
        assertEquals(true, msg.isFavorite)
    }

    @Test fun `toChatMessage handles unknown display mode`() {
        val entity = MessageEntity(
            chatId = "c", sender = "s", content = "c", orderIndex = 0,
            displayMode = "UNKNOWN_MODE"
        )
        val msg = entity.toChatMessage()
        assertEquals(ChatMessageDisplayMode.NORMAL, msg.displayMode)
    }

    @Test fun `fromChatMessage converts correctly`() {
        val msg = ChatMessage(
            sender = "user",
            content = "Hello",
            timestamp = 500L,
            roleName = "User",
            selectedVariantIndex = 0,
            provider = "openai",
            modelName = "gpt-4",
            inputTokens = 5,
            outputTokens = 10,
            isFavorite = true,
        )
        val entity = MessageEntity.fromChatMessage("chat1", msg, orderIndex = 0)
        assertEquals("chat1", entity.chatId)
        assertEquals("user", entity.sender)
        assertEquals("Hello", entity.content)
        assertEquals(500L, entity.timestamp)
        assertEquals(0, entity.orderIndex)
        assertEquals("User", entity.roleName)
        assertEquals(0, entity.selectedVariantIndex)
        assertEquals("openai", entity.provider)
        assertEquals("gpt-4", entity.modelName)
        assertEquals(5, entity.inputTokens)
        assertEquals(10, entity.outputTokens)
        assertEquals(true, entity.isFavorite)
    }

    @Test fun `fromChatMessage with explicit message id`() {
        val msg = ChatMessage(sender = "user", content = "Hi")
        val entity = MessageEntity.fromChatMessage("chat1", msg, orderIndex = 0, messageId = 99)
        assertEquals(99L, entity.messageId)
    }

    @Test fun `fromChatMessage preserves order index`() {
        val msg = ChatMessage(sender = "user", content = "Hi")
        val entity = MessageEntity.fromChatMessage("chat1", msg, orderIndex = 5)
        assertEquals(5, entity.orderIndex)
    }

    @Test fun `fromChatMessage round trip preserves data`() {
        val original = ChatMessage(
            sender = "ai", content = "Test message", timestamp = 12345L,
            roleName = "Bot", provider = "test", modelName = "test-model",
            inputTokens = 7, outputTokens = 3
        )
        val entity = MessageEntity.fromChatMessage("chat_x", original, orderIndex = 0)
        val roundTripped = entity.toChatMessage()
        assertEquals(original.sender, roundTripped.sender)
        assertEquals(original.content, roundTripped.content)
        assertEquals(original.timestamp, roundTripped.timestamp)
        assertEquals(original.roleName, roundTripped.roleName)
        assertEquals(original.provider, roundTripped.provider)
        assertEquals(original.modelName, roundTripped.modelName)
        assertEquals(original.inputTokens, roundTripped.inputTokens)
        assertEquals(original.outputTokens, roundTripped.outputTokens)
    }
}
