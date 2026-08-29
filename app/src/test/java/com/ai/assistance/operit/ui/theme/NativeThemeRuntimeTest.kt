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

class NativeThemeRuntimeTest {
    @Test
    fun systemAppearanceSelectsTheMatchingHostPalette() {
        val lightScheme = lightColorScheme(primary = Color.Red)
        val darkScheme = darkColorScheme(primary = Color.Blue)
        val resolved = resolve(systemDarkTheme = true, lightScheme = lightScheme, darkScheme = darkScheme)

        assertTrue(resolved.darkTheme)
        assertEquals(darkScheme, resolved.colorScheme)
        assertEquals(NativeThemeHostSurface.MAIN, resolved.environment.hostSurface)
        assertEquals(NATIVE_THEME_V1_DEFINITION_ID, resolved.definitionId)
    }

    @Test
    fun explicitAppearanceIgnoresTheSystemAppearance() {
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                .withString(
                    NativeThemePreferenceSchemaV1.themeMode,
                    UserPreferencesManager.THEME_MODE_LIGHT,
                )
        val resolved = resolve(values = values, systemDarkTheme = true)

        assertFalse(resolved.darkTheme)
    }

    @Test
    fun customLightPalettePreservesReleasedColorDerivation() {
        val primary = Color(0xFF204060)
        val secondary = Color(0xFF805020)
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb())
                .withInt(NativeThemePreferenceSchemaV1.customSecondaryColor, secondary.toArgb())
        val resolved = resolve(values = values)

        assertEquals(primary, resolved.colorScheme.primary)
        assertEquals(secondary, resolved.colorScheme.secondary)
        assertEquals(lighten(primary, 0.7f), resolved.colorScheme.primaryContainer)
        assertEquals(Color.White, resolved.colorScheme.onPrimary)
        assertEquals(Color.Black, resolved.colorScheme.onSurface)
    }

    @Test
    fun customDarkPalettePreservesReleasedColorDerivation() {
        val primary = Color(0xFF204060)
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                .withString(
                    NativeThemePreferenceSchemaV1.themeMode,
                    UserPreferencesManager.THEME_MODE_DARK,
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb())
        val resolved = resolve(values = values)

        assertTrue(resolved.darkTheme)
        assertEquals(lighten(primary, 0.2f), resolved.colorScheme.primary)
        assertEquals(darken(primary, 0.3f), resolved.colorScheme.primaryContainer)
        assertEquals(Color.White, resolved.colorScheme.onPrimaryContainer)
        assertEquals(Color.White, resolved.colorScheme.onSurface)
    }

    @Test
    fun backgroundTypographyAndChromeResolveFromOneSnapshot() {
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
                .withString(NativeThemePreferenceSchemaV1.backgroundImageUri, "file:///theme/background.png")
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundBlur, true)
                .withFloat(NativeThemePreferenceSchemaV1.backgroundBlurRadius, 18f)
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomFont, true)
                .withString(NativeThemePreferenceSchemaV1.fontType, UserPreferencesManager.FONT_TYPE_FILE)
                .withString(NativeThemePreferenceSchemaV1.customFontPath, "/theme/font.ttf")
                .withFloat(NativeThemePreferenceSchemaV1.fontScale, 1.25f)
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomStatusBarColor, true)
                .withInt(NativeThemePreferenceSchemaV1.customStatusBarColor, 0xFF123456.toInt())
                .withBoolean(NativeThemePreferenceSchemaV1.statusBarTransparent, true)
        val translucentScheme =
            lightColorScheme(surface = Color.Red.copy(alpha = 0.2f))
        val resolved =
            resolve(
                values = values,
                lightScheme = translucentScheme,
                darkScheme = darkColorScheme(),
            )

        assertTrue(resolved.background.enabled)
        assertEquals("file:///theme/background.png", resolved.background.uri)
        assertTrue(resolved.background.blurEnabled)
        assertEquals(18f, resolved.background.blurRadius)
        assertEquals(1f, resolved.contentColorScheme.surface.alpha)
        assertTrue(resolved.typography.useCustomFont)
        assertEquals(UserPreferencesManager.FONT_TYPE_FILE, resolved.typography.fontType)
        assertEquals("/theme/font.ttf", resolved.typography.customFontPath)
        assertEquals(1.25f, resolved.typography.fontScale)
        assertTrue(resolved.systemChrome.useCustomStatusBarColor)
        assertEquals(0xFF123456.toInt(), resolved.systemChrome.customStatusBarColor)
        assertTrue(resolved.systemChrome.statusBarTransparent)
    }

    @Test
    fun backgroundRequiresBothEnablementAndAResource() {
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
        val resolved = resolve(values = values)

        assertFalse(resolved.background.enabled)
        assertEquals(resolved.colorScheme, resolved.contentColorScheme)
    }

    @Test
    fun offscreenResolutionUsesTheTargetSnapshotInsteadOfSystemDefaults() {
        val primary = Color(0xFF204060)
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                .withString(
                    NativeThemePreferenceSchemaV1.themeMode,
                    UserPreferencesManager.THEME_MODE_DARK,
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb())
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
                .withString(
                    NativeThemePreferenceSchemaV1.backgroundImageUri,
                    "file:///theme/export-background.png",
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomFont, true)
                .withString(NativeThemePreferenceSchemaV1.fontType, UserPreferencesManager.FONT_TYPE_FILE)
                .withString(NativeThemePreferenceSchemaV1.customFontPath, "/theme/export-font.ttf")
                .withFloat(NativeThemePreferenceSchemaV1.fontScale, 1.25f)
        val resolved =
            resolveNativeThemeOffscreen(
                snapshot =
                    ThemePreferenceSnapshot(
                        source = "character_card",
                        sourceId = "test-card",
                        values = values,
                    ),
                systemDarkTheme = false,
                lightColorScheme = lightColorScheme(),
                darkColorScheme = darkColorScheme(),
            )

        assertEquals(NativeThemeHostSurface.OFFSCREEN, resolved.environment.hostSurface)
        assertTrue(resolved.darkTheme)
        assertEquals(lighten(primary, 0.2f), resolved.colorScheme.primary)
        assertTrue(resolved.background.enabled)
        assertEquals("file:///theme/export-background.png", resolved.background.uri)
        assertTrue(resolved.typography.useCustomFont)
        assertEquals("/theme/export-font.ttf", resolved.typography.customFontPath)
        assertEquals(1.25f, resolved.typography.fontScale)
    }

    @Test
    fun offscreenResolutionSelectsTheInjectedDarkBasePalette() {
        val lightScheme = lightColorScheme(primary = Color.Red)
        val darkScheme = darkColorScheme(primary = Color.Blue)
        val resolved =
            resolveNativeThemeOffscreen(
                snapshot =
                    ThemePreferenceSnapshot(
                        source = "character_card",
                        sourceId = "test-card",
                        values = ThemePreferenceValues.defaultVisual(),
                    ),
                systemDarkTheme = true,
                lightColorScheme = lightScheme,
                darkColorScheme = darkScheme,
            )

        assertEquals(NativeThemeHostSurface.OFFSCREEN, resolved.environment.hostSurface)
        assertTrue(resolved.darkTheme)
        assertEquals(darkScheme, resolved.colorScheme)
    }

    @Test
    fun floatingResolutionUsesTheActiveSnapshotVisualFields() {
        val primary = Color(0xFF406020)
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                .withString(
                    NativeThemePreferenceSchemaV1.themeMode,
                    UserPreferencesManager.THEME_MODE_DARK,
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb())
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
                .withString(
                    NativeThemePreferenceSchemaV1.backgroundImageUri,
                    "file:///theme/floating-background.png",
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomFont, true)
                .withString(NativeThemePreferenceSchemaV1.fontType, UserPreferencesManager.FONT_TYPE_FILE)
                .withString(NativeThemePreferenceSchemaV1.customFontPath, "/theme/floating-font.ttf")
                .withFloat(NativeThemePreferenceSchemaV1.fontScale, 1.15f)
        val resolved =
            resolveNativeThemeForDetachedComposeHost(
                snapshot = testSnapshot(values),
                hostSurface = NativeThemeHostSurface.FLOATING,
                systemDarkTheme = false,
                lightColorScheme = lightColorScheme(),
                darkColorScheme = darkColorScheme(),
            )

        assertEquals(NativeThemeHostSurface.FLOATING, resolved.environment.hostSurface)
        assertTrue(resolved.darkTheme)
        assertEquals(lighten(primary, 0.2f), resolved.colorScheme.primary)
        assertTrue(resolved.background.enabled)
        assertEquals("file:///theme/floating-background.png", resolved.background.uri)
        assertTrue(resolved.typography.useCustomFont)
        assertEquals("/theme/floating-font.ttf", resolved.typography.customFontPath)
        assertEquals(1.15f, resolved.typography.fontScale)
    }

    @Test
    fun overlayResolutionUsesTheInjectedLightBasePalette() {
        val lightScheme = lightColorScheme(primary = Color.Red)
        val darkScheme = darkColorScheme(primary = Color.Blue)
        val primary = Color(0xFF206040)
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                .withString(
                    NativeThemePreferenceSchemaV1.themeMode,
                    UserPreferencesManager.THEME_MODE_LIGHT,
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb())
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
                .withString(
                    NativeThemePreferenceSchemaV1.backgroundImageUri,
                    "file:///theme/overlay-background.png",
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useCustomFont, true)
                .withString(NativeThemePreferenceSchemaV1.fontType, UserPreferencesManager.FONT_TYPE_FILE)
                .withString(NativeThemePreferenceSchemaV1.customFontPath, "/theme/overlay-font.ttf")
                .withFloat(NativeThemePreferenceSchemaV1.fontScale, 1.1f)
        val resolved =
            resolveNativeThemeForDetachedComposeHost(
                snapshot = testSnapshot(values),
                hostSurface = NativeThemeHostSurface.OVERLAY,
                systemDarkTheme = true,
                lightColorScheme = lightScheme,
                darkColorScheme = darkScheme,
            )

        assertEquals(NativeThemeHostSurface.OVERLAY, resolved.environment.hostSurface)
        assertFalse(resolved.darkTheme)
        assertEquals(primary, resolved.colorScheme.primary)
        assertTrue(resolved.background.enabled)
        assertEquals("file:///theme/overlay-background.png", resolved.background.uri)
        assertTrue(resolved.typography.useCustomFont)
        assertEquals("/theme/overlay-font.ttf", resolved.typography.customFontPath)
        assertEquals(1.1f, resolved.typography.fontScale)
    }

    private fun resolve(
        values: ThemePreferenceValues = ThemePreferenceValues.defaultVisual(),
        systemDarkTheme: Boolean = false,
        lightScheme: androidx.compose.material3.ColorScheme = lightColorScheme(),
        darkScheme: androidx.compose.material3.ColorScheme = darkColorScheme(),
    ): ResolvedNativeThemeV1 =
        resolveNativeThemeV1(
            snapshot =
                testSnapshot(values),
            environment =
                NativeThemeEnvironment(
                    hostSurface = NativeThemeHostSurface.MAIN,
                    systemDarkTheme = systemDarkTheme,
                ),
            baseColorScheme = { darkTheme -> if (darkTheme) darkScheme else lightScheme },
        )

    private fun testSnapshot(values: ThemePreferenceValues): ThemePreferenceSnapshot =
        ThemePreferenceSnapshot(
            source = "character_card",
            sourceId = "test-card",
            values = values,
        )

    private fun lighten(color: Color, factor: Float): Color =
        Color(
            red = color.red + (1f - color.red) * factor,
            green = color.green + (1f - color.green) * factor,
            blue = color.blue + (1f - color.blue) * factor,
            alpha = color.alpha,
        )

    private fun darken(color: Color, factor: Float): Color =
        Color(
            red = color.red * (1f - factor),
            green = color.green * (1f - factor),
            blue = color.blue * (1f - factor),
            alpha = color.alpha,
        )
}
