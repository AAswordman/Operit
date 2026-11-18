package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveResponseTimerTest {
    @Test fun elapsedFormattingHandlesBoundaries() {
        assertEquals("00:00", formatResponseElapsed(-1))
        assertEquals("00:59", formatResponseElapsed(59_999))
        assertEquals("01:00", formatResponseElapsed(60_000))
        // A long task keeps counting instead of piling minutes past the hour.
        assertEquals("59:59", formatResponseElapsed(3_599_999))
        assertEquals("1:00:00", formatResponseElapsed(3_600_000))
        assertEquals("2:05:06", formatResponseElapsed(7_506_000))
    }
}
