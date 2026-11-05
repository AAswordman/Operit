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
        assertEquals("dead_letter", MemoryAutoSaveCandidate.STATUS_DEAD_LETTER)
        assertEquals(3, MemoryAutoSaveCandidate.MAX_ATTEMPTS)
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

    @Test fun `failed candidate is retried until the attempt limit`() {
        val candidate = MemoryAutoSaveCandidate()
        val now = Date(1_000L)

        candidate.beginProcessing(now)
        candidate.markFailed(now, "network error")
        assertEquals(MemoryAutoSaveCandidate.STATUS_FAILED, candidate.status)
        assertEquals(1, candidate.attemptCount)
        assertEquals("network error", candidate.lastError)
        assertTrue(candidate.isRetryable())

        candidate.beginProcessing(now)
        candidate.markFailed(now, "second error")
        assertEquals(MemoryAutoSaveCandidate.STATUS_FAILED, candidate.status)
        assertEquals(2, candidate.attemptCount)
        assertTrue(candidate.isRetryable())

        candidate.beginProcessing(now)
        candidate.markFailed(now, "third error")
        assertEquals(MemoryAutoSaveCandidate.STATUS_DEAD_LETTER, candidate.status)
        assertEquals(3, candidate.attemptCount)
        assertEquals("third error", candidate.lastError)
        assertFalse(candidate.isRetryable())
    }

    @Test fun `cancellation restores pending without consuming an attempt`() {
        val candidate =
            MemoryAutoSaveCandidate(
                status = MemoryAutoSaveCandidate.STATUS_PROCESSING,
                attemptCount = 2,
                lastError = "stale"
            )

        candidate.restorePending(Date(2_000L))

        assertEquals(MemoryAutoSaveCandidate.STATUS_PENDING, candidate.status)
        assertEquals(2, candidate.attemptCount)
        assertEquals("", candidate.lastError)
        assertEquals(2_000L, candidate.updatedAt.time)
    }

    @Test fun `interrupted processing is recovered after process restart`() {
        val candidate =
            MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_PROCESSING)

        candidate.recoverInterrupted(Date(3_000L))

        assertEquals(MemoryAutoSaveCandidate.STATUS_PENDING, candidate.status)
        assertEquals(1, candidate.attemptCount)
        assertEquals(MemoryAutoSaveCandidate.ERROR_INTERRUPTED, candidate.lastError)
        assertTrue(candidate.isRetryable())
    }

    @Test fun `repeated interruptions end in the dead letter state`() {
        val candidate =
            MemoryAutoSaveCandidate(
                status = MemoryAutoSaveCandidate.STATUS_PROCESSING,
                attemptCount = MemoryAutoSaveCandidate.MAX_ATTEMPTS - 1
            )

        candidate.recoverInterrupted()

        assertEquals(MemoryAutoSaveCandidate.STATUS_DEAD_LETTER, candidate.status)
        assertEquals(MemoryAutoSaveCandidate.MAX_ATTEMPTS, candidate.attemptCount)
        assertFalse(candidate.isRetryable())
    }

    @Test fun `dead letter candidate is never retried`() {
        val candidate =
            MemoryAutoSaveCandidate(
                status = MemoryAutoSaveCandidate.STATUS_DEAD_LETTER,
                attemptCount = 0
            )

        assertFalse(candidate.isRetryable())
    }

    @Test fun `only retryable candidates enter the next scan`() {
        val pending = MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_PENDING)
        val firstFailure =
            MemoryAutoSaveCandidate(
                status = MemoryAutoSaveCandidate.STATUS_FAILED,
                attemptCount = 1
            )
        val exhaustedFailure =
            MemoryAutoSaveCandidate(
                status = MemoryAutoSaveCandidate.STATUS_FAILED,
                attemptCount = MemoryAutoSaveCandidate.MAX_ATTEMPTS
            )
        val deadLetter =
            MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_DEAD_LETTER)
        val processing =
            MemoryAutoSaveCandidate(status = MemoryAutoSaveCandidate.STATUS_PROCESSING)

        val retryable =
            listOf(pending, firstFailure, exhaustedFailure, deadLetter, processing)
                .filter { it.isRetryable() }

        assertEquals(listOf(pending, firstFailure), retryable)
    }
}
