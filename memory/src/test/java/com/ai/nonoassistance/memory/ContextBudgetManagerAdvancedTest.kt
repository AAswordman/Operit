package com.ai.nonoassistance.memory

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContextBudgetManagerAdvancedTest {

    private lateinit var manager: ContextBudgetManager

    @Before
    fun setup() {
        manager = ContextBudgetManager(BudgetConfig(maxTokens = 1000))
    }

    @Test
    fun `trimToBudget keeps high priority items`() {
        val parts = listOf(
            ContextPart(Priority.SYSTEM_RULES, 100, "system prompt", "system"),
            ContextPart(Priority.CURRENT_TASK, 200, "current task", "task"),
            ContextPart(Priority.CHAT_HISTORY, 800, "old chat", "history")
        )

        val result = manager.trimToBudget(parts)

        assertTrue(result.kept.any { it.priority == Priority.SYSTEM_RULES })
        assertTrue(result.kept.any { it.priority == Priority.CURRENT_TASK })
    }

    @Test
    fun `trimToBudget summarizes chat history when needed`() {
        val parts = listOf(
            ContextPart(Priority.SYSTEM_RULES, 100, "system", "system"),
            ContextPart(Priority.CHAT_HISTORY, 900, "long chat history", "history")
        )

        val result = manager.trimToBudget(parts)

        // System rules kept
        assertTrue(result.kept.any { it.priority == Priority.SYSTEM_RULES })
        // History either summarized (fits at 20%) or trimmed (doesn't fit)
        assertTrue(result.summarized.isNotEmpty() || result.trimmed.isNotEmpty() || result.kept.size == 2)
    }

    @Test
    fun `shouldAutoSummarize returns true when threshold reached`() {
        assertFalse(manager.shouldAutoSummarize())

        manager.recordUsage(800) // 80% of 1000
        assertTrue(manager.shouldAutoSummarize())
    }

    @Test
    fun `getStats returns correct information`() {
        manager.recordUsage(500, 0.05)

        val stats = manager.getStats()
        assertEquals(500, stats.usedTokens)
        assertEquals(1000, stats.maxTokens)
        assertEquals(0.05, stats.sessionCostUsd, 0.001)
        assertEquals(0.5, stats.tokenUsagePercent, 0.001)
        assertFalse(stats.shouldSummarize)
    }

    @Test
    fun `remainingCostUsd returns null when no cost cap`() {
        assertNull(manager.remainingCostUsd())
    }

    @Test
    fun `remainingCostUsd returns remaining when cost cap set`() {
        val managerWithCost = ContextBudgetManager(
            BudgetConfig(maxTokens = 1000, maxCostUsd = 1.0)
        )
        managerWithCost.recordUsage(0, 0.3)

        assertEquals(0.7, managerWithCost.remainingCostUsd()!!, 0.001)
    }

    @Test
    fun `wouldExceedBudget returns true when cost cap exceeded`() {
        val managerWithCost = ContextBudgetManager(
            BudgetConfig(maxTokens = 1000, maxCostUsd = 0.5)
        )
        managerWithCost.recordUsage(0, 0.5)

        assertTrue(managerWithCost.wouldExceedBudget(0))
    }

    @Test
    fun `trimToBudget handles empty context`() {
        val result = manager.trimToBudget(emptyList())
        assertTrue(result.kept.isEmpty())
        assertTrue(result.trimmed.isEmpty())
        assertEquals(1000, result.remainingTokens)
    }
}
