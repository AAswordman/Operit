package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStudioNumberFormattingTest {
    @Test
    fun fractionalWidthsRemainVisibleInsteadOfIntegerLabels() {
        assertEquals("0.18", formatStudioNumber(0.18f, 2))
        assertEquals("0.05", formatStudioNumber(0.05f, 2))
        assertEquals("0.10", formatStudioNumber(0.1f, 2))
        assertEquals("1.00", formatStudioNumber(1f, 2))
    }

    @Test
    fun veryThinWidthsAreNeverDisplayedAsZero() {
        assertEquals("0.001", formatStudioNumber(0.001f, 2))
        assertEquals("-0.001", formatStudioNumber(-0.001f, 2))
        assertEquals("0.00", formatStudioNumber(0f, 2))
        assertEquals("24", formatStudioNumber(24f, 0))
    }
}
