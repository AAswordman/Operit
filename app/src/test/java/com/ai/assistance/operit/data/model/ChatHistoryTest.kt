package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ChatHistoryTest {

    private fun createHistory(
        id: String = "hist1",
        title: String = "Test History",
        messages: List<ChatMessage> = emptyList(),
        displayOrder: Long = 0L,
    ) = ChatHistory(
        id = id, title = title, messages = messages,
        createdAt = LocalDateTime.of(2024, 1, 15, 10, 30),
        updatedAt = LocalDateTime.of(2024, 1, 15, 11, 0),
        displayOrder = displayOrder,
    )

    @Test fun `create with required fields`() {
        val hist = createHistory()
        assertEquals("hist1", hist.id)
        assertEquals("Test History", hist.title)
        assertTrue(hist.messages.isEmpty())
    }

    @Test fun `messages list is preserved`() {
        val msgs = listOf(
            ChatMessage(sender = "user", content = "Hi"),
            ChatMessage(sender = "ai", content = "Hello"),
        )
        val hist = createHistory(messages = msgs)
        assertEquals(2, hist.messages.size)
        assertEquals("Hi", hist.messages[0].content)
    }

    @Test fun `created at is preserved`() {
        val hist = createHistory()
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), hist.createdAt)
    }

    @Test fun `updated at is preserved`() {
        val hist = createHistory()
        assertEquals(LocalDateTime.of(2024, 1, 15, 11, 0), hist.updatedAt)
    }

    @Test fun `display order defaults to zero`() {
        val hist = createHistory()
        assertEquals(0L, hist.displayOrder)
    }

    @Test fun `display order can be set`() {
        val hist = createHistory(displayOrder = 42L)
        assertEquals(42L, hist.displayOrder)
    }

    @Test fun `input tokens defaults to zero`() {
        val hist = createHistory()
        assertEquals(0, hist.inputTokens)
    }

    @Test fun `output tokens defaults to zero`() {
        val hist = createHistory()
        assertEquals(0, hist.outputTokens)
    }

    @Test fun `current window size defaults to zero`() {
        val hist = createHistory()
        assertEquals(0, hist.currentWindowSize)
    }

    @Test fun `group defaults to null`() {
        val hist = createHistory()
        assertNull(hist.group)
    }

    @Test fun `workspace defaults to null`() {
        val hist = createHistory()
        assertNull(hist.workspace)
    }

    @Test fun `parent chat id defaults to null`() {
        val hist = createHistory()
        assertNull(hist.parentChatId)
    }

    @Test fun `character card name defaults to null`() {
        val hist = createHistory()
        assertNull(hist.characterCardName)
    }

    @Test fun `character group id defaults to null`() {
        val hist = createHistory()
        assertNull(hist.characterGroupId)
    }

    @Test fun `locked defaults to false`() {
        val hist = createHistory()
        assertFalse(hist.locked)
    }

    @Test fun `pinned defaults to false`() {
        val hist = createHistory()
        assertFalse(hist.pinned)
    }

    @Test fun `copy with new title`() {
        val hist = createHistory()
        val copy = hist.copy(title = "Updated Title")
        assertEquals("Updated Title", copy.title)
        assertEquals(hist.id, copy.id)
    }

    @Test fun `copy with new messages`() {
        val hist = createHistory()
        val newMsgs = listOf(ChatMessage(sender = "user", content = "New"))
        val copy = hist.copy(messages = newMsgs)
        assertEquals(1, copy.messages.size)
    }

    @Test fun `copy with tokens`() {
        val hist = createHistory()
        val copy = hist.copy(inputTokens = 100, outputTokens = 50, currentWindowSize = 10)
        assertEquals(100, copy.inputTokens)
        assertEquals(50, copy.outputTokens)
        assertEquals(10, copy.currentWindowSize)
    }

    @Test fun `copy with group and workspace`() {
        val hist = createHistory()
        val copy = hist.copy(group = "group1", workspace = "ws1")
        assertEquals("group1", copy.group)
        assertEquals("ws1", copy.workspace)
    }

    @Test fun `copy with parent chat and character`() {
        val hist = createHistory()
        val copy = hist.copy(
            parentChatId = "parent1",
            characterCardName = "char1",
            characterGroupId = "group1",
        )
        assertEquals("parent1", copy.parentChatId)
        assertEquals("char1", copy.characterCardName)
        assertEquals("group1", copy.characterGroupId)
    }

    @Test fun `copy with pinned and locked`() {
        val hist = createHistory()
        val copy = hist.copy(locked = true, pinned = true)
        assertTrue(copy.locked)
        assertTrue(copy.pinned)
    }
}
