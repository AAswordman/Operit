package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.stream.emptyStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageProcessingDelegateTest {
    @Test
    fun completeInterruptedMessage_appliesTurnSnapshotAndCompletesPartialContent() {
        val message =
            ChatMessage(
                sender = "ai",
                content = "stale",
                timestamp = 10L,
                sentAt = 20L,
                contentStream = emptyStream(),
            )
        val snapshot =
            MessageProcessingDelegate.TurnCancellationSnapshot(
                inputTokens = 120L,
                outputTokens = 34L,
                cachedInputTokens = 56L,
                sentAt = 30L,
                outputDurationMs = 4_000L,
                waitDurationMs = 500L,
            )

        val result =
            MessageProcessingDelegate.completeInterruptedMessage(
                streamingMessage = message,
                finalContent = "partial response",
                snapshot = snapshot,
                completedAt = 5_000L,
            )

        assertEquals("partial response", result.content)
        assertNull(result.contentStream)
        assertEquals(120L, result.inputTokens)
        assertEquals(34L, result.outputTokens)
        assertEquals(56L, result.cachedInputTokens)
        assertEquals(30L, result.sentAt)
        assertEquals(4_000L, result.outputDurationMs)
        assertEquals(500L, result.waitDurationMs)
        assertEquals(5_000L, result.completedAt)
    }

    @Test
    fun shouldPersistInterruptedMessage_skipsEmptyResponseAndKeepsPartialContent() {
        assertFalse(MessageProcessingDelegate.shouldPersistInterruptedMessage(""))
        assertFalse(MessageProcessingDelegate.shouldPersistInterruptedMessage("   \n"))
        assertTrue(MessageProcessingDelegate.shouldPersistInterruptedMessage("partial response"))
    }
}
