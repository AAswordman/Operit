package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ChatEntityTest {

    @Test fun `create with required fields`() {
        val entity = ChatEntity(title = "Test Chat")
        assertEquals("Test Chat", entity.title)
        assertNotNull(entity.id)
        assertTrue(entity.id.isNotEmpty())
    }

    @Test fun `id is randomly generated`() {
        val e1 = ChatEntity(title = "A")
        val e2 = ChatEntity(title = "B")
        assertFalse(e1.id == e2.id)
    }

    @Test fun `created at defaults to current time`() {
        val before = System.currentTimeMillis()
        val entity = ChatEntity(title = "Test")
        val after = System.currentTimeMillis()
        assertTrue(entity.createdAt >= before)
        assertTrue(entity.createdAt <= after + 1000)
    }

    @Test fun `updated at defaults to current time`() {
        val before = System.currentTimeMillis()
        val entity = ChatEntity(title = "Test")
        val after = System.currentTimeMillis()
        assertTrue(entity.updatedAt >= before)
        assertTrue(entity.updatedAt <= after + 1000)
    }

    @Test fun `input tokens defaults to zero`() {
        val entity = ChatEntity(title = "Test")
        assertEquals(0, entity.inputTokens)
    }

    @Test fun `output tokens defaults to zero`() {
        val entity = ChatEntity(title = "Test")
        assertEquals(0, entity.outputTokens)
    }

    @Test fun `current window size defaults to zero`() {
        val entity = ChatEntity(title = "Test")
        assertEquals(0, entity.currentWindowSize)
    }

    @Test fun `group defaults to null`() {
        val entity = ChatEntity(title = "Test")
        assertNull(entity.group)
    }

    @Test fun `display order defaults to negative createdAt`() {
        val entity = ChatEntity(title = "Test")
        assertEquals(-entity.createdAt, entity.displayOrder)
    }

    @Test fun `workspace defaults to null`() {
        val entity = ChatEntity(title = "Test")
        assertNull(entity.workspace)
    }

    @Test fun `parent chat id defaults to null`() {
        val entity = ChatEntity(title = "Test")
        assertNull(entity.parentChatId)
    }

    @Test fun `character card name defaults to null`() {
        val entity = ChatEntity(title = "Test")
        assertNull(entity.characterCardName)
    }

    @Test fun `locked defaults to false`() {
        val entity = ChatEntity(title = "Test")
        assertFalse(entity.locked)
    }

    @Test fun `pinned defaults to false`() {
        val entity = ChatEntity(title = "Test")
        assertFalse(entity.pinned)
    }

    @Test fun `toChatHistory creates correct object`() {
        val entity = ChatEntity(
            id = "test-id",
            title = "My Chat",
            inputTokens = 100,
            outputTokens = 50,
            currentWindowSize = 10,
            group = "group1",
            workspace = "workspace1",
            parentChatId = "parent1",
            characterCardName = "char1",
            locked = true,
            pinned = true,
        )
        val history = entity.toChatHistory(emptyList())
        assertEquals("test-id", history.id)
        assertEquals("My Chat", history.title)
        assertEquals(0, history.messages.size)
        assertEquals(100, history.inputTokens)
        assertEquals(50, history.outputTokens)
        assertEquals(10, history.currentWindowSize)
        assertEquals("group1", history.group)
        assertEquals("workspace1", history.workspace)
        assertEquals("parent1", history.parentChatId)
        assertEquals("char1", history.characterCardName)
        assertEquals(true, history.locked)
        assertEquals(true, history.pinned)
        assertNotNull(history.createdAt)
        assertNotNull(history.updatedAt)
    }

    @Test fun `toChatHistory preserves messages`() {
        val entity = ChatEntity(title = "Chat")
        val messages = listOf(
            ChatMessage(sender = "user", content = "Hi"),
            ChatMessage(sender = "ai", content = "Hello"),
        )
        val history = entity.toChatHistory(messages)
        assertEquals(2, history.messages.size)
        assertEquals("Hi", history.messages[0].content)
        assertEquals("Hello", history.messages[1].content)
    }

    @Test fun `fromChatHistory round trip`() {
        val original = ChatEntity(
            id = "round-trip-id",
            title = "Round Trip",
            inputTokens = 50,
            outputTokens = 25,
            group = "test-group",
        )
        val history = original.toChatHistory(emptyList())
        val roundTripped = ChatEntity.fromChatHistory(history)
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.title, roundTripped.title)
        assertEquals(original.inputTokens, roundTripped.inputTokens)
        assertEquals(original.outputTokens, roundTripped.outputTokens)
        assertEquals(original.group, roundTripped.group)
    }

    @Test fun `fromChatHistory computes displayOrder when zero`() {
        val history = ChatHistory(
            id = "test", title = "Test", messages = emptyList(),
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
            displayOrder = 0L,
        )
        val entity = ChatEntity.fromChatHistory(history)
        assertTrue(entity.displayOrder < 0)
    }

    @Test fun `fromChatHistory preserves non-zero displayOrder`() {
        val history = ChatHistory(
            id = "test", title = "Test", messages = emptyList(),
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
            displayOrder = 42L,
        )
        val entity = ChatEntity.fromChatHistory(history)
        assertEquals(42L, entity.displayOrder)
    }

    @Test fun `explicit fields are stored`() {
        val entity = ChatEntity(
            id = "explicit-id",
            title = "Explicit",
            createdAt = 1000L,
            updatedAt = 2000L,
            inputTokens = 10,
            outputTokens = 20,
            currentWindowSize = 5,
            group = "explicit-group",
            displayOrder = 99L,
            workspace = "explicit-ws",
            characterCardName = "explicit-char",
            locked = true,
            pinned = true,
        )
        assertEquals("explicit-id", entity.id)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
        assertEquals(10, entity.inputTokens)
        assertEquals(20, entity.outputTokens)
        assertEquals(5, entity.currentWindowSize)
        assertEquals("explicit-group", entity.group)
        assertEquals(99L, entity.displayOrder)
        assertEquals("explicit-ws", entity.workspace)
        assertEquals("explicit-char", entity.characterCardName)
        assertTrue(entity.locked)
        assertTrue(entity.pinned)
    }
}
