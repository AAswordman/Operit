package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.preferences.LlmRetryMode
import com.ai.assistance.operit.data.preferences.LlmRetrySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmRetryPolicyTest {
    @Test
    fun standardModePreservesExistingRetrySchedule() {
        val policy =
            LlmRetryPolicy.fromSettings(
                LlmRetrySettings(maxAttempts = 5, mode = LlmRetryMode.STANDARD)
            )

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), policy.delayScheduleMs())
    }

    @Test
    fun presetModesCapLongRetrySchedules() {
        val fast =
            LlmRetryPolicy.fromSettings(
                LlmRetrySettings(maxAttempts = 6, mode = LlmRetryMode.FAST)
            )
        val stable =
            LlmRetryPolicy.fromSettings(
                LlmRetrySettings(maxAttempts = 6, mode = LlmRetryMode.STABLE)
            )

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 4_000L, 4_000L, 4_000L), fast.delayScheduleMs())
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), stable.delayScheduleMs())
    }

    @Test
    fun customModeUsesInitialDelayAndMaximumCap() {
        val policy =
            LlmRetryPolicy.fromSettings(
                LlmRetrySettings(
                    maxAttempts = 6,
                    mode = LlmRetryMode.CUSTOM,
                    customInitialDelaySeconds = 3,
                    customMaxDelaySeconds = 10
                )
            )

        assertEquals(listOf(3_000L, 6_000L, 10_000L, 10_000L, 10_000L, 10_000L), policy.delayScheduleMs())
    }

    @Test
    fun retryCountAndCustomDelaysAreBounded() {
        val disabled =
            LlmRetryPolicy.fromSettings(
                LlmRetrySettings(maxAttempts = -1, mode = LlmRetryMode.STANDARD)
            )
        val bounded =
            LlmRetryPolicy.fromSettings(
                LlmRetrySettings(
                    maxAttempts = 99,
                    mode = LlmRetryMode.CUSTOM,
                    customInitialDelaySeconds = 20,
                    customMaxDelaySeconds = 5
                )
            )

        assertEquals(emptyList<Long>(), disabled.delayScheduleMs())
        assertEquals(10, bounded.maxRetryAttempts)
        assertEquals(5_000L, bounded.nextDelayMs(1))
        assertEquals(5_000L, bounded.nextDelayMs(10))
    }
}
