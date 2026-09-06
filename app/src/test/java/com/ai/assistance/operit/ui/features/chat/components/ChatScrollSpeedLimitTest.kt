package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatScrollSpeedLimitTest {

    @Test
    fun returnsMinDurationWhenDeltaIsZeroOrNegative() {
        assertEquals(100, calculateScrollDurationMs(0, 2.0f, 800f))
        assertEquals(100, calculateScrollDurationMs(-50, 2.0f, 800f))
    }

    @Test
    fun scalesDurationProportionallyWithDelta() {
        // density = 2.0f, 800 dp/s => 1600 px/s => 1.6 px/ms
        // delta = 800 px => 400 dp => 400 / 800 * 1000 = 500 ms
        assertEquals(500, calculateScrollDurationMs(800, 2.0f, 800f))
        // delta = 400 px => 200 dp => 200 / 800 * 1000 = 250 ms
        assertEquals(250, calculateScrollDurationMs(400, 2.0f, 800f))
    }

    @Test
    fun clampsDurationBetweenMinAndMax() {
        // Very small delta -> clamped to min (100ms)
        assertEquals(100, calculateScrollDurationMs(10, 2.0f, 800f))
        // Very large delta -> clamped to max (1000ms)
        assertEquals(1000, calculateScrollDurationMs(10000, 2.0f, 800f))
    }

    @Test
    fun handlesInvalidDensityOrSpeedGracefully() {
        assertEquals(100, calculateScrollDurationMs(500, 0f, 800f))
        assertEquals(100, calculateScrollDurationMs(500, 2.0f, 0f))
    }
}