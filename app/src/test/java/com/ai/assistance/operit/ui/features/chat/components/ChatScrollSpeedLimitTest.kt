package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollSpeedLimitTest {

    @Test
    fun returnsMinDurationWhenDeltaIsZeroOrNegative() {
        assertEquals(100, calculateScrollDurationMs(0, 2.0f, 600f))
        assertEquals(100, calculateScrollDurationMs(-50, 2.0f, 600f))
    }

    @Test
    fun scalesDurationProportionallyWithDelta() {
        // density = 2.0f, 600 dp/s => 1200 px/s
        // delta = 1200 px => 600 dp => 600 / 600 * 1000 = 1000 ms
        assertEquals(1000, calculateScrollDurationMs(1200, 2.0f, 600f))
        // delta = 600 px => 300 dp => 300 / 600 * 1000 = 500 ms
        assertEquals(500, calculateScrollDurationMs(600, 2.0f, 600f))
    }

    @Test
    fun lowerSpeedCapProducesLongerDuration() {
        val slow = calculateScrollDurationMs(400, 2.0f, 50f)
        val fast = calculateScrollDurationMs(400, 2.0f, 1000f)
        assertTrue("更低的速度上限应产生更长的动画时长", slow > fast)
    }

    @Test
    fun clampsDurationBetweenMinAndMax() {
        assertEquals(100, calculateScrollDurationMs(10, 2.0f, 600f))
        assertEquals(1000, calculateScrollDurationMs(10000, 2.0f, 600f))
    }

    @Test
    fun handlesInvalidDensityOrSpeedGracefully() {
        assertEquals(100, calculateScrollDurationMs(500, 0f, 600f))
        assertEquals(100, calculateScrollDurationMs(500, 2.0f, 0f))
    }

    @Test
    fun speedOptionsHave20LimitedStepsPlusUnlimited() {
        val options = DisplayPreferencesManager.STREAM_SCROLL_SPEED_OPTIONS
        // 50..1000 步进 50 共 20 档，加上最后一档“不设上限”共 21 档
        assertEquals(21, options.size)
        assertEquals(DisplayPreferencesManager.STREAM_SCROLL_SPEED_MIN_DP, options.first())
        assertEquals(
            DisplayPreferencesManager.STREAM_SCROLL_SPEED_MAX_LIMITED_DP,
            options[options.lastIndex - 1]
        )
        assertEquals(DisplayPreferencesManager.STREAM_SCROLL_SPEED_UNLIMITED, options.last())
    }

    @Test
    fun defaultSpeedIsSelectableOption() {
        assertTrue(
            DisplayPreferencesManager.STREAM_SCROLL_SPEED_DEFAULT_DP
                in DisplayPreferencesManager.STREAM_SCROLL_SPEED_OPTIONS
        )
    }
}