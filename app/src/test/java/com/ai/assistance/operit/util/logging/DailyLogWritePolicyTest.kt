package com.ai.assistance.operit.util.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLogWritePolicyTest {
    @Test
    fun loggingIsEnabledByDefault() {
        assertTrue(DailyLogWritePolicy { "2026-10-20" }.isEnabled())
    }

    @Test
    fun disablingStaysEffectiveOnTheSameDay() {
        val policy = DailyLogWritePolicy { "2026-10-20" }
        policy.setEnabled(false)
        assertFalse(policy.isEnabled())
        assertFalse(policy.isEnabled())
    }

    @Test
    fun restartingOnTheSameDayPreservesDisabledState() {
        val policy = DailyLogWritePolicy { "2026-10-20" }
        policy.restore("2026-10-20")
        assertFalse(policy.isEnabled())
    }

    @Test
    fun runningAcrossMidnightRestoresLogging() {
        var date = "2026-10-20"
        val policy = DailyLogWritePolicy { date }
        policy.setEnabled(false)
        date = "2026-10-21"
        assertTrue(policy.isEnabled())
        assertNull(policy.disabledDate)
    }

    @Test
    fun restartingOnAnotherDayRestoresLogging() {
        val policy = DailyLogWritePolicy { "2026-10-21" }
        policy.restore("2026-10-20")
        assertTrue(policy.isEnabled())
    }

    @Test
    fun manuallyEnablingClearsTheDisabledDate() {
        val policy = DailyLogWritePolicy { "2026-10-20" }
        policy.setEnabled(false)
        policy.setEnabled(true)
        assertTrue(policy.isEnabled())
        assertNull(policy.disabledDate)
    }

    @Test
    fun disablingAgainAfterMidnightAppliesToTheNewDay() {
        var date = "2026-10-20"
        val policy = DailyLogWritePolicy { date }
        policy.setEnabled(false)
        date = "2026-10-21"
        assertTrue(policy.isEnabled())
        policy.setEnabled(false)
        assertFalse(policy.isEnabled())
        date = "2026-10-22"
        assertTrue(policy.isEnabled())
    }
}
