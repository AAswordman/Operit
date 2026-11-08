package com.ai.assistance.operit.ui.features.management.settings.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayBehaviorInputTest {
    @Test
    fun thresholdAcceptsInclusiveLimitsAndNormalValues() {
        assertEquals(1000, parseDisplayThreshold("1000"))
        assertEquals(3000, parseDisplayThreshold("3000"))
        assertEquals(100000, parseDisplayThreshold("100000"))
    }

    @Test
    fun thresholdRejectsIncompleteOutOfRangeAndOverflowingInput() {
        listOf("", "-", "999", "100001", "-1000", "3.5", "3000x", "999999999999999999999")
            .forEach { text -> assertNull(text, parseDisplayThreshold(text)) }
    }
}
