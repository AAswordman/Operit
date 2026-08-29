package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeGlanceHostTest {
    @Test
    fun systemAppearanceProjectsDistinctDayAndNightGlanceColors() {
        val dayPrimary = Color(0xFF205080)
        val nightPrimary = Color(0xFF80B0E0)
        val palette =
            resolveNativeThemeGlancePalette(
                snapshot = testSnapshot(),
                lightColorScheme =
                    lightColorScheme(
                        primary = dayPrimary,
                        onPrimary = Color.Black,
                        surface = Color(0xFFF4F1ED),
                        onSurface = Color(0xFF1D252C),
                    ),
                darkColorScheme =
                    darkColorScheme(
                        primary = nightPrimary,
                        onPrimary = Color.White,
                        surface = Color(0xFF1B242D),
                        onSurface = Color(0xFFF1F4F7),
                    ),
            )

        assertEquals(NativeThemeHostSurface.GLANCE, palette.dayTheme.environment.hostSurface)
        assertEquals(NativeThemeHostSurface.GLANCE, palette.nightTheme.environment.hostSurface)
        assertFalse(palette.dayTheme.darkTheme)
        assertTrue(palette.nightTheme.darkTheme)
        assertEquals(dayPrimary, palette.primary.day)
        assertEquals(nightPrimary, palette.primary.night)
        assertEquals(Color.Black, palette.onPrimary.day)
        assertEquals(Color.White, palette.onPrimary.night)
    }

    @Test
    fun explicitDarkAppearanceKeepsTheDarkProjectionForBothGlanceModes() {
        val darkPrimary = Color(0xFF7090C0)
        val palette =
            resolveNativeThemeGlancePalette(
                snapshot =
                    testSnapshot(
                        ThemePreferenceValues.defaultVisual()
                            .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                            .withString(
                                NativeThemePreferenceSchemaV1.themeMode,
                                UserPreferencesManager.THEME_MODE_DARK,
                            ),
                    ),
                lightColorScheme = lightColorScheme(primary = Color(0xFF204060)),
                darkColorScheme = darkColorScheme(primary = darkPrimary),
            )

        assertTrue(palette.dayTheme.darkTheme)
        assertTrue(palette.nightTheme.darkTheme)
        assertEquals(darkPrimary, palette.primary.day)
        assertEquals(darkPrimary, palette.primary.night)
    }

    @Test
    fun customColorsUseTheNativeThemeDerivationForGlanceProjection() {
        val primary = Color(0xFF204060)
        val palette =
            resolveNativeThemeGlancePalette(
                snapshot =
                    testSnapshot(
                        ThemePreferenceValues.defaultVisual()
                            .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, true)
                            .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                            .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb()),
                    ),
                lightColorScheme = lightColorScheme(),
                darkColorScheme = darkColorScheme(),
            )

        assertEquals(primary, palette.primary.day)
        assertEquals(lighten(primary, 0.2f), palette.primary.night)
        assertEquals(Color.Black, palette.onSurface.day)
        assertEquals(Color.White, palette.onSurface.night)
    }

    @Test
    fun alphaProjectionRetainsTheDayAndNightThemeColors() {
        val color = NativeThemeGlanceColor(day = Color.Red, night = Color.Blue)

        val translucent = color.withAlpha(0.8f)

        assertEquals(Color.Red.copy(alpha = 0.8f), translucent.day)
        assertEquals(Color.Blue.copy(alpha = 0.8f), translucent.night)
    }

    private fun testSnapshot(
        values: ThemePreferenceValues = ThemePreferenceValues.defaultVisual(),
    ): ThemePreferenceSnapshot =
        ThemePreferenceSnapshot(
            source = "character_card",
            sourceId = "glance-host-test",
            values = values,
        )

    private fun lighten(color: Color, factor: Float): Color =
        Color(
            red = color.red + (1f - color.red) * factor,
            green = color.green + (1f - color.green) * factor,
            blue = color.blue + (1f - color.blue) * factor,
            alpha = color.alpha,
        )
}
