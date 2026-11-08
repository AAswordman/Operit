package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.*
import org.junit.Test

class TokenUsageBreakdownTest {
    @Test
    fun legacyTotalsRemainUnchangedWithoutInventedInputCategories() {
        val usage = TokenUsageBreakdown.fromStored(null, 1000L, 75L)
        assertEquals(1000L, usage.otherTokens)
        assertEquals(75L, usage.categoryTokens[1])
        assertEquals(1075L, usage.categoryTokens.sum())
    }

    @Test
    fun requestUsesActualInputAndCountsGeneratedOutputAsAssistant() {
        val snapshot = RequestWindowSnapshot(1000L, listOf(
            PromptTurn(PromptTurnKind.SYSTEM, "系统提示词"),
            PromptTurn(PromptTurnKind.USER, "用户发言"),
            PromptTurn(PromptTurnKind.ASSISTANT, "历史回答"),
            PromptTurn(PromptTurnKind.TOOL_RESULT, "工具结果"),
        ), null)
        val usage = TokenUsageBreakdown.fromRequest(snapshot, 50L)
        assertEquals(1000L, usage.inputTokens)
        assertEquals(1050L, usage.categoryTokens.sum())
        assertTrue(usage.userMessageTokens > 0L)
        assertTrue(usage.toolResultTokens > 0L)
        assertEquals(usage.assistantMessageTokens + 50L, usage.categoryTokens[1])
    }

    @Test
    fun multipleRequestsAccumulateAndSurvivePersistence() {
        val usage = TokenUsageBreakdown(userMessageTokens = 10L, outputTokens = 3L) +
            TokenUsageBreakdown(toolResultTokens = 20L, outputTokens = 4L)
        assertEquals(usage, TokenUsageBreakdown.fromStored(usage.encode(), 30L, 7L))
        assertEquals(37L, usage.categoryTokens.sum())
    }

    @Test
    fun repeatedAndLateSnapshotsDoNotCountTheSameTurnAgain() {
        val turn = TokenUsageBreakdown(userMessageTokens = 10L, outputTokens = 3L)
        val once = TokenUsageBreakdown.advance(null, 100L, 5L, 110L, 8L, turn)
        val repeated = TokenUsageBreakdown.advance(once.encode(), 110L, 8L, 110L, 8L, turn)
        val late = TokenUsageBreakdown.advance(once.encode(), 110L, 8L, 100L, 5L, turn)
        assertEquals(once, repeated)
        assertEquals(once, late)
        assertEquals(10L, once.userMessageTokens)
        assertEquals(100L, once.otherTokens)
    }

    @Test
    fun resetTotalsRemoveOldClassificationBeforeNewRequests() {
        val old = TokenUsageBreakdown(userMessageTokens = 100L, outputTokens = 10L)
        val reset = TokenUsageBreakdown.fromStored(old.encode(), 0L, 0L)
        assertEquals(0L, reset.totalTokens)
        val next = TokenUsageBreakdown.advance(old.encode(), 0L, 0L, 7L, 2L, TokenUsageBreakdown(toolResultTokens = 7L, outputTokens = 2L))
        assertEquals(0L, next.userMessageTokens)
        assertEquals(9L, next.totalTokens)
    }

    @Test
    fun calibrationNeverExceedsProviderTotal() {
        val usage = TokenUsageBreakdown(userMessageTokens = 1000L, systemPromptTokens = 1000L).withTotals(7L, 2L)
        assertEquals(9L, usage.categoryTokens.sum())
        assertTrue(usage.categoryTokens.all { it >= 0L })
    }
}
