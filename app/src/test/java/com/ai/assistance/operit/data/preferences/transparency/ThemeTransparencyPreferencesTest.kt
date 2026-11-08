package com.ai.assistance.operit.data.preferences.transparency

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTransparencyPreferencesTest {
    private val tolerance = 0.00001f

    @Test
    fun percentageEndpointsHaveTheExpectedMeaning() {
        assertEquals(1f, ThemeTransparencyPreferences.opacityFromPercent(0), tolerance)
        assertEquals(0f, ThemeTransparencyPreferences.opacityFromPercent(100), tolerance)
        assertEquals(0, ThemeTransparencyPreferences.transparencyPercent(1f))
        assertEquals(100, ThemeTransparencyPreferences.transparencyPercent(0f))
    }

    @Test
    fun everyWholePercentageRoundTrips() {
        for (percent in 0..100) {
            val opacity = ThemeTransparencyPreferences.opacityFromPercent(percent)
            assertEquals(percent, ThemeTransparencyPreferences.transparencyPercent(opacity))
        }
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        assertEquals(1f, ThemeTransparencyPreferences.opacityFromPercent(-1), tolerance)
        assertEquals(0f, ThemeTransparencyPreferences.opacityFromPercent(101), tolerance)
        assertEquals(0, ThemeTransparencyPreferences.transparencyPercent(1.1f))
        assertEquals(100, ThemeTransparencyPreferences.transparencyPercent(-0.1f))
        val values = ThemePreferenceValues(floats = mapOf(ThemeTransparencyPreferences.INPUT to 2f))
        assertEquals(1f, ThemeTransparencyPreferences.inputOpacity(values), tolerance)
    }

    @Test
    fun legacyInputSwitchRetainsBothStates() {
        val opaque = ThemePreferenceValues(booleans = mapOf("chat_input_transparent" to false))
        val transparent = opaque.withBoolean("chat_input_transparent", true)
        assertEquals(1f, ThemeTransparencyPreferences.inputOpacity(opaque), tolerance)
        assertEquals(0f, ThemeTransparencyPreferences.inputOpacity(transparent), tolerance)
        assertEquals(0.85f, ThemeTransparencyPreferences.inputOpacity(opaque, 0.85f), tolerance)
        assertEquals(0.82f, ThemeTransparencyPreferences.inputOpacity(opaque, 0.82f), tolerance)
    }

    @Test
    fun legacyGlassInputRetainsItsTintOpacity() {
        val values = ThemePreferenceValues(booleans = mapOf("chat_input_transparent" to true))
        val opacity = ThemeTransparencyPreferences.inputOpacity(
            values,
            opaqueOpacity = 0.85f,
            transparentOpacity = 0.24f,
        )
        assertEquals(0.24f, opacity, tolerance)
        assertEquals(76, ThemeTransparencyPreferences.transparencyPercent(opacity))
    }

    @Test
    fun explicitInputOpacityOverridesEitherLegacySwitchState() {
        for (legacyTransparent in listOf(false, true)) {
            val values = ThemePreferenceValues(
                booleans = mapOf("chat_input_transparent" to legacyTransparent),
                floats = mapOf(ThemeTransparencyPreferences.INPUT to 0.37f),
            )
            assertEquals(0.37f, ThemeTransparencyPreferences.inputOpacity(values), tolerance)
        }
    }

    @Test
    fun missingBubbleValuesRetainLegacyBackgrounds() {
        val values = ThemePreferenceValues()
        for (name in ThemeTransparencyPreferences.names - ThemeTransparencyPreferences.INPUT) {
            assertEquals(0.4f, ThemeTransparencyPreferences.opacity(values, name, 0.4f), tolerance)
        }
        assertEquals(
            0f,
            ThemeTransparencyPreferences.opacity(values, ThemeTransparencyPreferences.CURSOR_AI, 0f),
            tolerance,
        )
    }

    @Test
    fun stylesAndSendersHaveIndependentOpacityValues() {
        val configured = ThemeTransparencyPreferences.names.withIndex().associate { (index, name) ->
            name to index / 4f
        }
        val values = ThemePreferenceValues(floats = configured)
        for ((name, opacity) in configured) {
            assertEquals(opacity, ThemeTransparencyPreferences.opacity(values, name, 1f), tolerance)
        }
    }

    @Test
    fun registeredPreferenceKeysPreserveAllOpacityValues() {
        val preferences = mutablePreferencesOf()
        val keys = ThemeTransparencyPreferences.floatKeys
        assertEquals(ThemeTransparencyPreferences.names, keys.map { it.name }.toSet())
        assertEquals(5, keys.size)
        for ((index, key) in keys.withIndex()) preferences[key] = index / 4f
        val restored = ThemePreferenceValues(
            floats = keys.associate { key -> key.name to requireNotNull(preferences[key]) },
        )
        for ((index, key) in keys.withIndex()) {
            assertEquals(index / 4f, restored.requiredFloat(key.name), tolerance)
        }
    }

    @Test
    fun editingOpacityDoesNotMutateTheOriginalThemeOrItsColors() {
        val color = 0x80445566.toInt()
        val original = ThemePreferenceValues(
            ints = mapOf("bubble_user_bubble_color" to color),
            floats = mapOf(ThemeTransparencyPreferences.BUBBLE_AI to 0.7f),
        )
        val edited = original.withFloat(ThemeTransparencyPreferences.BUBBLE_USER, 0.25f)
        assertNull(original.float(ThemeTransparencyPreferences.BUBBLE_USER))
        assertEquals(color, requireNotNull(edited.int("bubble_user_bubble_color")))
        assertEquals(0.7f, edited.requiredFloat(ThemeTransparencyPreferences.BUBBLE_AI), tolerance)
    }

    @Test
    fun snapshotUsesExplicitInputOpacityBeforeTheLegacySwitch() {
        fun snapshot(opacity: Float?, legacy: Boolean): ThemePreferenceSnapshot {
            val values = ThemePreferenceValues(
                booleans = mapOf("chat_input_transparent" to legacy),
                floats = if (opacity == null) emptyMap() else mapOf(ThemeTransparencyPreferences.INPUT to opacity),
            )
            return ThemePreferenceSnapshot(source = "test", values = values)
        }
        assertFalse(snapshot(1f, true).chatInputTransparent)
        assertTrue(snapshot(0.5f, false).chatInputTransparent)
        assertTrue(snapshot(0f, false).chatInputTransparent)
        assertFalse(snapshot(null, false).chatInputTransparent)
        assertTrue(snapshot(null, true).chatInputTransparent)
        assertNull(snapshot(null, true).chatInputOpacity)
    }
}
