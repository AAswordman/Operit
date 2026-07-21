package com.ai.assistance.operit.data.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTest {

    @After
    fun tearDown() {
        ChatMessageTimestampAllocator.next()
    }

    @Test fun `default sender is user`() {
        val msg = ChatMessage(sender = "user")
        assertEquals("user", msg.sender)
    }

    @Test fun `default content is empty`() {
        val msg = ChatMessage(sender = "user")
        assertEquals("", msg.content)
    }

    @Test fun `custom content is stored`() {
        val msg = ChatMessage(sender = "user", content = "Hello")
        assertEquals("Hello", msg.content)
    }

    @Test fun `timestamp is allocated`() {
        val msg = ChatMessage(sender = "user")
        assertTrue(msg.timestamp > 0)
    }

    @Test fun `consecutive messages have increasing timestamps`() {
        val msg1 = ChatMessage(sender = "user")
        val msg2 = ChatMessage(sender = "user")
        assertTrue(msg2.timestamp > msg1.timestamp)
    }

    @Test fun `role name can be set`() {
        val msg = ChatMessage(sender = "user", roleName = "assistant")
        assertEquals("assistant", msg.roleName)
    }

    @Test fun `selected variant index defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0, msg.selectedVariantIndex)
    }

    @Test fun `variant count defaults to one`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(1, msg.variantCount)
    }

    @Test fun `provider defaults to empty`() {
        val msg = ChatMessage(sender = "user")
        assertEquals("", msg.provider)
    }

    @Test fun `model name defaults to empty`() {
        val msg = ChatMessage(sender = "user")
        assertEquals("", msg.modelName)
    }

    @Test fun `input tokens defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0, msg.inputTokens)
    }

    @Test fun `output tokens defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0, msg.outputTokens)
    }

    @Test fun `display mode defaults to normal`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(ChatMessageDisplayMode.NORMAL, msg.displayMode)
    }

    @Test fun `is favorite defaults to false`() {
        val msg = ChatMessage(sender = "user")
        assertFalse(msg.isFavorite)
    }

    @Test fun `is favorite can be set to true`() {
        val msg = ChatMessage(sender = "user", isFavorite = true)
        assertTrue(msg.isFavorite)
    }

    @Test fun `copy with new content`() {
        val msg = ChatMessage(sender = "user", content = "original")
        val copy = msg.copy(content = "modified")
        assertEquals("modified", copy.content)
        assertEquals(msg.sender, copy.sender)
        assertEquals(msg.timestamp, copy.timestamp)
    }

    @Test fun `copy with new provider`() {
        val msg = ChatMessage(sender = "ai", provider = "openai")
        val copy = msg.copy(provider = "anthropic")
        assertEquals("anthropic", copy.provider)
    }

    @Test fun `copy with tokens updated`() {
        val msg = ChatMessage(sender = "ai")
        val copy = msg.copy(inputTokens = 100, outputTokens = 50)
        assertEquals(100, copy.inputTokens)
        assertEquals(50, copy.outputTokens)
    }

    @Test fun `copy with display mode`() {
        val msg = ChatMessage(sender = "ai")
        val copy = msg.copy(displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER)
        assertEquals(ChatMessageDisplayMode.HIDDEN_PLACEHOLDER, copy.displayMode)
    }

    @Test fun `is variant preview defaults to false`() {
        val msg = ChatMessage(sender = "user")
        assertFalse(msg.isVariantPreview)
    }

    @Test fun `content stream defaults to null`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(null, msg.contentStream)
    }

    @Test fun `cached input tokens defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0, msg.cachedInputTokens)
    }

    @Test fun `sent at defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0L, msg.sentAt)
    }

    @Test fun `output duration defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0L, msg.outputDurationMs)
    }

    @Test fun `wait duration defaults to zero`() {
        val msg = ChatMessage(sender = "user")
        assertEquals(0L, msg.waitDurationMs)
    }

    @Test fun `ai message can have model info`() {
        val msg = ChatMessage(sender = "ai", modelName = "gpt-4", provider = "openai")
        assertEquals("gpt-4", msg.modelName)
        assertEquals("openai", msg.provider)
    }

    @Test fun `multiple messages have distinct timestamps`() {
        val msgs = (1..10).map { ChatMessage(sender = "user") }
        for (i in 1 until msgs.size) {
            assertTrue(msgs[i].timestamp > msgs[i - 1].timestamp)
        }
    }
}
