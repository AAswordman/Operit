package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.*
import org.junit.Test

class PhonePlanProgressTest {
    @Test fun threeRejectedOrNonMutatingPlansStopInsteadOfConsumingAllRounds() {
        val progress = PhonePlanProgress()
        assertFalse(progress.record(0, false))
        assertFalse(progress.record(0, false))
        assertTrue(progress.record(0, false))
    }

    @Test fun requestingAnImageDoesNotEraseEarlierStalls() {
        val progress = PhonePlanProgress()
        assertFalse(progress.record(0, false))
        assertFalse(progress.record(0, true))
        assertEquals(1, progress.stalledPlans)
        assertFalse(progress.record(0, false))
        assertTrue(progress.record(0, false))
    }

    @Test fun checkedMutationStartsANewProgressWindow() {
        val progress = PhonePlanProgress()
        progress.record(0, false)
        progress.record(0, false)
        assertFalse(progress.record(1, false))
        assertEquals(0, progress.stalledPlans)
        assertFalse(progress.record(0, false))
    }

    @Test fun resetDoesNotCarryFailuresIntoANewTask() {
        val progress = PhonePlanProgress()
        progress.record(0, false)
        progress.reset()
        assertEquals(0, progress.stalledPlans)
        assertFalse(progress.record(0, false))
    }
}
