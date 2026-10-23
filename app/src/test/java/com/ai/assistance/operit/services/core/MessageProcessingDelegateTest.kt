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
    fun planTurnRuntimeReset_staleTurnLeavesRuntimeUntouched() {
        val plan =
            MessageProcessingDelegate.planTurnRuntimeReset(
                activeTurnId = 7L,
                turnId = 6L,
                cancellationInProgress = false,
            )

        assertFalse(plan.resetDerivedRuntime)
        assertFalse(plan.resetLoadingState)
    }

    @Test
    fun planTurnRuntimeReset_currentTurnClearsLoading() {
        val plan =
            MessageProcessingDelegate.planTurnRuntimeReset(
                activeTurnId = 7L,
                turnId = 7L,
                cancellationInProgress = false,
            )

        assertTrue(plan.resetDerivedRuntime)
        assertTrue(plan.resetLoadingState)
    }

    @Test
    fun planTurnRuntimeReset_cancellingTurnDefersLoadingResetToCancellationPath() {
        val plan =
            MessageProcessingDelegate.planTurnRuntimeReset(
                activeTurnId = 7L,
                turnId = 7L,
                cancellationInProgress = true,
            )

        assertTrue(plan.resetDerivedRuntime)
        assertFalse(plan.resetLoadingState)
    }
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
}
