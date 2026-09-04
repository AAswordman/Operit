package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAreaTokenSpeedTest {
    @Test
    fun calculatesOutputTokensPerSecond() {
        assertEquals(25.0, calculateMessageTokenSpeed(100L, 4_000L)!!, 0.001)
    }

    @Test
    fun returnsNullWhenOutputTokensAreNotPositive() {
        assertNull(calculateMessageTokenSpeed(0L, 4_000L))
        assertNull(calculateMessageTokenSpeed(-1L, 4_000L))
    }

    @Test
    fun returnsNullWhenOutputDurationIsNotPositive() {
        assertNull(calculateMessageTokenSpeed(100L, 0L))
        assertNull(calculateMessageTokenSpeed(100L, -1L))
    }
}
