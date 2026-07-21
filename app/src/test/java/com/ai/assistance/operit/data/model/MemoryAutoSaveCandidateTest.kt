package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MemoryAutoSaveCandidateTest {

    @Test fun `create with defaults`() {
        val candidate = MemoryAutoSaveCandidate()
        assertEquals(0L, candidate.id)
        assertEquals("", candidate.chatId)
        assertEquals(0L, candidate.triggerMessageTimestamp)
        assertNotNull(candidate.createdAt)
        assertNotNull(candidate.updatedAt)
        assertEquals(MemoryAutoSaveCandidate.STATUS_PENDING, candidate.status)
        assertEquals(0, candidate.attemptCount)
        assertEquals("", candidate.lastError)
        assertEquals(MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO, candidate.sourceType)
    }

    @Test fun `create with chat id`() {
        val candidate = MemoryAutoSaveCandidate(chatId = "chat123")
        assertEquals("chat123", candidate.chatId)
    }

    @Test fun `create with trigger timestamp`() {
        val candidate = MemoryAutoSaveCandidate(triggerMessageTimestamp = 1000L)
        assertEquals(1000L, candidate.triggerMessageTimestamp)
    }

    @Test fun `id can be set`() {
        val candidate = MemoryAutoSaveCandidate(id = 42L)
        assertEquals(42L, candidate.id)
    }

    @Test fun `status can be set to processing`() {
        val candidate = MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_PROCESSING)
        assertEquals(MemoryAutoSaveCandidate.STATUS_PROCESSING, candidate.status)
    }

    @Test fun `status can be set to failed`() {
        val candidate = MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_FAILED)
        assertEquals(MemoryAutoSaveCandidate.STATUS_FAILED, candidate.status)
    }

    @Test fun `attempt count can be incremented via copy`() {
        val candidate = MemoryAutoSaveCandidate(attemptCount = 1)
        val updated = candidate.copy(attemptCount = 2)
        assertEquals(2, updated.attemptCount)
        assertEquals(1, candidate.attemptCount) // original unchanged
    }

    @Test fun `last error is stored`() {
        val candidate = MemoryAutoSaveCandidate(lastError = "Network error")
        assertEquals("Network error", candidate.lastError)
    }

    @Test fun `source type can be selected user message`() {
        val candidate = MemoryAutoSaveCandidate(
            sourceType = MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE
        )
        assertEquals(MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE, candidate.sourceType)
    }

    @Test fun `isSelectedUserMessageSource returns true for user message`() {
        assertTrue(MemoryAutoSaveCandidate.isSelectedUserMessageSource(
            MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE
        ))
    }

    @Test fun `isSelectedUserMessageSource returns false for auto`() {
        assertFalse(MemoryAutoSaveCandidate.isSelectedUserMessageSource(
            MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO
        ))
    }

    @Test fun `isSelectedUserMessageSource returns false for unknown`() {
        assertFalse(MemoryAutoSaveCandidate.isSelectedUserMessageSource("unknown"))
    }

    @Test fun `status constants are defined`() {
        assertEquals("pending", MemoryAutoSaveCandidate.STATUS_PENDING)
        assertEquals("processing", MemoryAutoSaveCandidate.STATUS_PROCESSING)
        assertEquals("failed", MemoryAutoSaveCandidate.STATUS_FAILED)
    }

    @Test fun `source type constants are defined`() {
        assertEquals("reply_finalized_auto", MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO)
        assertEquals("selected_user_message", MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE)
    }

    @Test fun `created at and updated at are set independently`() {
        val candidate = MemoryAutoSaveCandidate(
            createdAt = Date(1000L),
            updatedAt = Date(2000L),
        )
        assertEquals(1000L, candidate.createdAt.time)
        assertEquals(2000L, candidate.updatedAt.time)
    }

    @Test fun `copy with new status`() {
        val candidate = MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_PENDING)
        val updated = candidate.copy(status = MemoryAutoSaveCandidate.STATUS_PROCESSING)
        assertEquals(MemoryAutoSaveCandidate.STATUS_PROCESSING, updated.status)
        assertEquals(MemoryAutoSaveCandidate.STATUS_PENDING, candidate.status)
    }
}
