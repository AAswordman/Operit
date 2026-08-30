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

class NativeThemeRuntimeTest {
    @Test
    fun systemAppearanceSelectsTheMatchingHostPalette() {
        val lightScheme = lightColorScheme(primary = Color.Red)
        val darkScheme = darkColorScheme(primary = Color.Blue)

        val resolved =
            resolve(
                presentation = GlobalPresentationSnapshot.default(),
                systemDarkTheme = true,
                lightScheme = lightScheme,
                darkScheme = darkScheme,
            )

        assertTrue(resolved.darkTheme)
        assertEquals(darkScheme, resolved.colorScheme)
        assertEquals(NativeThemeHostSurface.MAIN, resolved.environment.hostSurface)
    }

    @Test
    fun systemAppearanceLightSelectsTheLightPalette() {
        val lightScheme = lightColorScheme(primary = Color.Red)

        val resolved =
            resolve(
                presentation = GlobalPresentationSnapshot.default(),
                systemDarkTheme = false,
                lightScheme = lightScheme,
                darkScheme = darkColorScheme(primary = Color.Blue),
            )

        assertFalse(resolved.darkTheme)
        assertEquals(lightScheme, resolved.colorScheme)
    }

    @Test
    fun explicitLightModeIgnoresTheSystemAppearance() {
        val resolved =
            resolve(
                presentation =
                    GlobalPresentationSnapshot(themeMode = GlobalThemeMode.LIGHT),
                systemDarkTheme = true,
            )

        assertFalse(resolved.darkTheme)
    }

    @Test
    fun explicitDarkModeIgnoresTheSystemAppearance() {
        val resolved =
            resolve(
                presentation =
                    GlobalPresentationSnapshot(themeMode = GlobalThemeMode.DARK),
                systemDarkTheme = false,
            )

        assertTrue(resolved.darkTheme)
    }

    @Test
    fun fontScaleFlowsThroughTheResolvedTheme() {
        val resolved =
            resolve(
                presentation = GlobalPresentationSnapshot(fontScale = 1.25f),
            )

        assertEquals(1.25f, resolved.fontScale)
    }

    @Test
    fun detachedHostsResolveWithTheirOwnSurface() {
        listOf(
            NativeThemeHostSurface.FLOATING,
            NativeThemeHostSurface.OVERLAY,
            NativeThemeHostSurface.OFFSCREEN,
            NativeThemeHostSurface.GLANCE,
        ).forEach { surface ->
            val resolved =
                resolveGlobalThemeForDetachedComposeHost(
                    presentation =
                        GlobalPresentationSnapshot(themeMode = GlobalThemeMode.DARK),
                    hostSurface = surface,
                    systemDarkTheme = false,
                    lightColorScheme = lightColorScheme(),
                    darkColorScheme = darkColorScheme(),
                )

            assertEquals(surface, resolved.environment.hostSurface)
            assertTrue(resolved.darkTheme)
        }
    }

    private fun resolve(
        presentation: GlobalPresentationSnapshot,
        systemDarkTheme: Boolean = false,
        lightScheme: androidx.compose.material3.ColorScheme = lightColorScheme(),
        darkScheme: androidx.compose.material3.ColorScheme = darkColorScheme(),
    ): ResolvedGlobalTheme =
        resolveGlobalThemeV1(
            presentation = presentation,
            environment =
                NativeThemeEnvironment(
                    hostSurface = NativeThemeHostSurface.MAIN,
                    systemDarkTheme = systemDarkTheme,
                ),
            baseColorScheme = { darkTheme -> if (darkTheme) darkScheme else lightScheme },
        )
}
