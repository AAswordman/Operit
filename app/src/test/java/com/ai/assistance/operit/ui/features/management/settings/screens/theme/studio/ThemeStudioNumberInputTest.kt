package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeStudioNumberInputTest {
    private val range = 0f..8f

    @Test
    fun replacingFractionalDigitKeepsCurrentStrokeUntilFinished() {
        var applied = 0.5f
        for (draft in listOf("0.", "0", "", "0.", "0.9")) {
            studioNumberInputValue(draft, range, commitOnFinish = true, finished = false)?.let { applied = it }
            assertEquals(0.5f, applied, 0f)
        }
        assertEquals(0.9f, requireNotNull(studioNumberInputValue("0.9", range, true, true)), 0f)
    }

    @Test
    fun zeroOnlyDisablesStrokeAfterFinishing() {
        assertNull(studioNumberInputValue("0", range, true, false))
        assertNull(studioNumberInputValue("0.0", range, true, false))
        assertEquals(0f, requireNotNull(studioNumberInputValue("0", range, true, true)), 0f)
    }

    @Test
    fun incompleteOrInvalidDraftNeverChangesSavedWidth() {
        for (draft in listOf("", " ", "0.", ".", "8.", "-0.1", "8.01", "NaN", "Infinity", "1e", "abc")) {
            assertNull(draft, studioNumberInputValue(draft, range, true, true))
        }
    }

    @Test
    fun completeValuesKeepFloatPrecisionAndOtherControlsKeepLiveUpdates() {
        for (draft in listOf("0.0001", "0.05", "0.9", "1", "8")) {
            val expected = draft.toFloat()
            assertEquals(expected, requireNotNull(studioNumberInputValue(draft, range, true, true)), 0f)
            assertEquals(expected, requireNotNull(studioNumberInputValue(draft, range, false, false)), 0f)
        }
    }
}
