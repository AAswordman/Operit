package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
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
                presentation = GlobalPresentationSnapshot.default(),
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
                presentation =
                    GlobalPresentationSnapshot(themeMode = GlobalThemeMode.DARK),
                lightColorScheme = lightColorScheme(primary = Color(0xFF204060)),
                darkColorScheme = darkColorScheme(primary = darkPrimary),
            )

        assertTrue(palette.dayTheme.darkTheme)
        assertTrue(palette.nightTheme.darkTheme)
        assertEquals(darkPrimary, palette.primary.day)
        assertEquals(darkPrimary, palette.primary.night)
    }

    @Test
    fun alphaProjectionRetainsTheDayAndNightThemeColors() {
        val color = NativeThemeGlanceColor(day = Color.Red, night = Color.Blue)

        val translucent = color.withAlpha(0.8f)

        assertEquals(Color.Red.copy(alpha = 0.8f), translucent.day)
        assertEquals(Color.Blue.copy(alpha = 0.8f), translucent.night)
    }
}
