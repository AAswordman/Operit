package com.ai.nonoassistance.memory

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContextBudgetManagerTest {

    private lateinit var manager: ContextBudgetManager

    @Before
    fun setup() {
        manager = ContextBudgetManager(BudgetConfig(maxTokens = 1000))
    }

    @Test
    fun `remaining tokens initially equals max budget`() {
        assertEquals(1000, manager.remainingTokens())
    }

    @Test
    fun `record usage reduces remaining tokens`() {
        manager.recordUsage(300)
        assertEquals(700, manager.remainingTokens())
    }

    @Test
    fun `wouldExceedBudget returns false when under budget`() {
        assertFalse(manager.wouldExceedBudget(500))
    }

    @Test
    fun `wouldExceedBudget returns true when over budget`() {
        manager.recordUsage(800)
        assertTrue(manager.wouldExceedBudget(300))
    }

    @Test
    fun `usage percentage calculated correctly`() {
        manager.recordUsage(250)
        assertEquals(0.25, manager.tokenUsagePercentage(), 0.001)
    }

    @Test
    fun `reset clears usage`() {
        manager.recordUsage(500)
        manager.reset()
        assertEquals(1000, manager.remainingTokens())
    }

    @Test
    fun `remaining tokens never goes below zero`() {
        manager.recordUsage(2000)
        assertEquals(0, manager.remainingTokens())
    }
}
