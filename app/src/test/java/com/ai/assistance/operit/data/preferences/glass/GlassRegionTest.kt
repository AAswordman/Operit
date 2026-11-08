package com.ai.assistance.operit.data.preferences.glass

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRegionTest {
    @Test
    fun existingThemesKeepFollowingGlobalParameters() {
        val values =
            ThemePreferenceValues.defaultVisual()
                .withFloat("glass_blur_radius", 26f)
                .withFloat("glass_tint_opacity", 0.7f)
        GlassThemePreferences.regions.forEach { region ->
            assertFalse(region.isCustom(values))
            assertEquals(GlassRegion.GLOBAL.parameters(values), region.parameters(values))
        }
    }

    @Test
    fun independentMessagesDoNotChangeOtherRegions() {
        val original = ThemePreferenceValues.defaultVisual().withFloat("glass_blur_radius", 22f)
        val values =
            GlassRegion.USER_MESSAGE.setCustom(original, true)
                .withFloat(GlassRegion.USER_MESSAGE.parameterKey("glass_blur_radius"), 8f)
        assertEquals(
            8f,
            GlassRegion.USER_MESSAGE.parameters(values).getValue("glass_blur_radius"),
            0f,
        )
        assertEquals(
            22f,
            GlassRegion.AI_MESSAGE.parameters(values).getValue("glass_blur_radius"),
            0f,
        )
        assertEquals(22f, GlassRegion.INPUT.parameters(values).getValue("glass_blur_radius"), 0f)
        assertEquals(22f, original.requiredFloat("glass_blur_radius"), 0f)
    }

    @Test
    fun enablingIndependentParametersStartsFromCurrentGlobalAppearance() {
        val values =
            ThemePreferenceValues.defaultVisual()
                .withFloat("glass_edge_width", 1.4f)
                .withFloat("glass_highlight_alpha", 0.6f)
        val edited = GlassRegion.AI_MESSAGE.setCustom(values, true)
        assertTrue(GlassRegion.AI_MESSAGE.isCustom(edited))
        assertEquals(
            GlassRegion.GLOBAL.parameters(values),
            GlassRegion.AI_MESSAGE.parameters(edited),
        )
    }

    @Test
    fun globalChangesOnlyReachRegionsThatFollowGlobal() {
        val values = GlassRegion.AI_MESSAGE.setCustom(ThemePreferenceValues.defaultVisual(), true)
        val before = GlassRegion.AI_MESSAGE.parameters(values)
        val edited = values.withFloat("glass_shadow_radius", 16f)
        assertEquals(before, GlassRegion.AI_MESSAGE.parameters(edited))
        assertEquals(
            16f,
            GlassRegion.USER_MESSAGE.parameters(edited).getValue("glass_shadow_radius"),
            0f,
        )
        assertEquals(
            16f,
            GlassRegion.SIDEBAR.parameters(edited).getValue("glass_shadow_radius"),
            0f,
        )
    }

    @Test
    fun disablingIndependentParametersImmediatelyFollowsGlobalAgain() {
        val values =
            GlassRegion.USER_MESSAGE.setCustom(ThemePreferenceValues.defaultVisual(), true)
                .withFloat(GlassRegion.USER_MESSAGE.parameterKey("glass_blur_radius"), 31f)
                .withFloat("glass_blur_radius", 6f)
        val edited = GlassRegion.USER_MESSAGE.setCustom(values, false)
        assertFalse(GlassRegion.USER_MESSAGE.isCustom(edited))
        assertEquals(
            6f,
            GlassRegion.USER_MESSAGE.parameters(edited).getValue("glass_blur_radius"),
            0f,
        )
        assertTrue(GlassRegion.USER_MESSAGE.isCustom(values))
    }

    @Test
    fun everyRegionFieldIsRegisteredForThemePersistenceAndReset() {
        val defaults = ThemePreferenceValues.defaultVisual()
        val booleanNames = GlassThemePreferences.booleanKeys.map { it.name }.toSet()
        val floatNames = GlassThemePreferences.floatKeys.map { it.name }.toSet()
        GlassThemePreferences.regions.forEach { region ->
            assertTrue(region.overrideKey in booleanNames)
            assertFalse(defaults.requiredBoolean(region.overrideKey))
            GlassThemePreferences.globalFloatDefaults.forEach { (key, value) ->
                assertTrue(region.parameterKey(key) in floatNames)
                assertEquals(value, defaults.requiredFloat(region.parameterKey(key)), 0f)
            }
        }
        assertEquals(GlassThemePreferences.floatKeys.size, floatNames.size)
        assertEquals(GlassThemePreferences.booleanKeys.size, booleanNames.size)
    }
}
