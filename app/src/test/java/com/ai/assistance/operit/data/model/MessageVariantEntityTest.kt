package com.ai.assistance.operit.data.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageVariantEntityTest {

    @After
    fun tearDown() {
        ChatMessageTimestampAllocator.next()
    }

    @Test fun `create with required fields`() {
        val variant = MessageVariantEntity(
            chatId = "chat1",
            messageTimestamp = 1000L,
            variantIndex = 0,
            content = "Variant content",
        )
        assertEquals("chat1", variant.chatId)
        assertEquals(1000L, variant.messageTimestamp)
        assertEquals(0, variant.variantIndex)
        assertEquals("Variant content", variant.content)
    }

    @Test fun `variant id defaults to zero`() {
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "c"
        )
        assertEquals(0L, variant.variantId)
    }

    @Test fun `role name defaults to empty`() {
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "c"
        )
        assertEquals("", variant.roleName)
    }

    @Test fun `provider defaults to empty`() {
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "c"
        )
        assertEquals("", variant.provider)
    }

    @Test fun `model name defaults to empty`() {
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "c"
        )
        assertEquals("", variant.modelName)
    }

    @Test fun `tokens default to zero`() {
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "c"
        )
        assertEquals(0, variant.inputTokens)
        assertEquals(0, variant.outputTokens)
        assertEquals(0, variant.cachedInputTokens)
    }

    @Test fun `applyTo updates message content`() {
        val base = ChatMessage(sender = "ai", content = "Original")
        val variant = MessageVariantEntity(
            chatId = "chat1", messageTimestamp = 100L, variantIndex = 1, content = "Updated"
        )
        val result = variant.applyTo(base, variantCount = 3)
        assertEquals("Updated", result.content)
        assertEquals(1, result.selectedVariantIndex)
        assertEquals(3, result.variantCount)
    }

    @Test fun `applyTo preserves base message fields`() {
        val base = ChatMessage(
            sender = "ai", content = "Original", timestamp = 500L,
            roleName = "Assistant", isFavorite = true,
        )
        val variant = MessageVariantEntity(
            chatId = "chat1", messageTimestamp = 100L, variantIndex = 1, content = "Updated"
        )
        val result = variant.applyTo(base, variantCount = 2)
        assertEquals("ai", result.sender)
        assertEquals(500L, result.timestamp)
        assertEquals("Assistant", result.roleName)
        assertEquals(true, result.isFavorite)
    }

    @Test fun `applyTo uses variant role name when not blank`() {
        val base = ChatMessage(sender = "ai", content = "Original", roleName = "Assistant")
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "New",
            roleName = "CustomBot",
        )
        val result = variant.applyTo(base, variantCount = 1)
        assertEquals("CustomBot", result.roleName)
    }

    @Test fun `applyTo uses base role name when variant role is blank`() {
        val base = ChatMessage(sender = "ai", content = "Original", roleName = "Assistant")
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "New"
        )
        val result = variant.applyTo(base, variantCount = 1)
        assertEquals("Assistant", result.roleName)
    }

    @Test fun `applyTo updates provider and model`() {
        val base = ChatMessage(sender = "ai", content = "Original")
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "New",
            provider = "anthropic", modelName = "claude-3",
        )
        val result = variant.applyTo(base, variantCount = 1)
        assertEquals("anthropic", result.provider)
        assertEquals("claude-3", result.modelName)
    }

    @Test fun `applyTo updates token counts`() {
        val base = ChatMessage(sender = "ai", content = "Original")
        val variant = MessageVariantEntity(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, content = "New",
            inputTokens = 50, outputTokens = 30,
        )
        val result = variant.applyTo(base, variantCount = 1)
        assertEquals(50, result.inputTokens)
        assertEquals(30, result.outputTokens)
    }

    @Test fun `fromChatMessage creates variant from message`() {
        val msg = ChatMessage(
            sender = "ai", content = "Response",
            roleName = "Bot", provider = "openai", modelName = "gpt-4",
            inputTokens = 10, outputTokens = 20,
        )
        val variant = MessageVariantEntity.fromChatMessage(
            chatId = "chat1", messageTimestamp = 1000L, variantIndex = 0, message = msg
        )
        assertEquals("chat1", variant.chatId)
        assertEquals(1000L, variant.messageTimestamp)
        assertEquals(0, variant.variantIndex)
        assertEquals("Response", variant.content)
        assertEquals("Bot", variant.roleName)
        assertEquals("openai", variant.provider)
        assertEquals("gpt-4", variant.modelName)
        assertEquals(10, variant.inputTokens)
        assertEquals(20, variant.outputTokens)
    }

    @Test fun `fromChatMessage with explicit variant id`() {
        val msg = ChatMessage(sender = "user", content = "Hi")
        val variant = MessageVariantEntity.fromChatMessage(
            chatId = "c", messageTimestamp = 1L, variantIndex = 0, message = msg, variantId = 55L
        )
        assertEquals(55L, variant.variantId)
    }

    @Test fun `applyTo round trip preserves variant fields`() {
        val original = ChatMessage(sender = "ai", content = "Original", roleName = "Bot")
        val variantEntity = MessageVariantEntity.fromChatMessage(
            chatId = "chat1", messageTimestamp = 100L, variantIndex = 0, message = original
        )
        val applied = variantEntity.applyTo(original, variantCount = 1)
        assertEquals(original.content, applied.content)
        assertEquals(original.roleName, applied.roleName)
        assertEquals(0, applied.selectedVariantIndex)
        assertEquals(1, applied.variantCount)
    }
}
