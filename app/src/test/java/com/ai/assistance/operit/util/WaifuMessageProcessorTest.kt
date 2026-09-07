package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WaifuMessageProcessorTest {
    @Test
    fun calculateTypingDelayMs_firstSegmentIsImmediate() {
        assertEquals(
            0L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 80,
                charDelayMs = 240,
                isFirstSegment = true,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_usesCurrentSegmentLengthForShortTail() {
        assertEquals(
            720L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 3,
                charDelayMs = 240,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_capsLongSegmentDelay() {
        assertEquals(
            3000L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 80,
                charDelayMs = 240,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_nonPositiveDelayIsImmediate() {
        assertEquals(
            0L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 10,
                charDelayMs = 0,
                isFirstSegment = false,
            )
        )
    }
}
