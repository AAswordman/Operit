package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeHostAdaptersTest {
    @Test
    fun hiddenStatusBarProducesTheHiddenWindowState() {
        val background = Color(0xFFF0F0F0)
        val state =
            resolveWindowState(
                values =
                    ThemePreferenceValues.defaultVisual().withBoolean(
                        NativeThemePreferenceSchemaV1.statusBarHidden,
                        true,
                    ),
                lightScheme = lightColorScheme(background = background),
            )
        val hiddenState =
            when (state) {
                is NativeThemeMainWindowChromeState.Hidden -> state
                is NativeThemeMainWindowChromeState.Visible ->
                    throw AssertionError("Expected a hidden window chrome state")
            }

        assertEquals(background.toArgb(), hiddenState.navigationBarColor)
        assertTrue(hiddenState.navigationBarContrastEnforced)
        assertFalse(hiddenState.lightNavigationBarIcons)
    }

    @Test
    fun backgroundMakesSystemBarsTransparentBeforeCustomStatusBarColor() {
        val customStatusBarColor = 0xFF112233.toInt()
        val state =
            visibleWindowState(
                values =
                    ThemePreferenceValues.defaultVisual()
                        .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
                        .withString(
                            NativeThemePreferenceSchemaV1.backgroundImageUri,
                            "file:///theme/background.png",
                        )
                        .withBoolean(NativeThemePreferenceSchemaV1.useCustomStatusBarColor, true)
                        .withInt(
                            NativeThemePreferenceSchemaV1.customStatusBarColor,
                            customStatusBarColor,
                        ),
            )

        assertEquals(Color.Transparent.toArgb(), state.statusBarColor)
        assertFalse(state.lightStatusBarIcons)
        assertEquals(Color.Transparent.toArgb(), state.navigationBarColor)
        assertFalse(state.navigationBarContrastEnforced)
        assertTrue(state.lightNavigationBarIcons)
    }

    @Test
    fun explicitTransparencyTakesPriorityOverCustomStatusBarColor() {
        val background = Color(0xFFF0F0F0)
        val state =
            visibleWindowState(
                values =
                    ThemePreferenceValues.defaultVisual()
                        .withBoolean(NativeThemePreferenceSchemaV1.statusBarTransparent, true)
                        .withBoolean(NativeThemePreferenceSchemaV1.useCustomStatusBarColor, true)
                        .withInt(
                            NativeThemePreferenceSchemaV1.customStatusBarColor,
                            0xFF112233.toInt(),
                        ),
                lightScheme = lightColorScheme(background = background),
            )

        assertEquals(Color.Transparent.toArgb(), state.statusBarColor)
        assertEquals(background.toArgb(), state.navigationBarColor)
        assertTrue(state.navigationBarContrastEnforced)
        assertFalse(state.lightNavigationBarIcons)
    }

    @Test
    fun customStatusBarColorUsesResolvedThemeBackgroundForNavigation() {
        val background = Color(0xFFF0F0F0)
        val customStatusBarColor = Color(0xFFEEEEEE)
        val state =
            visibleWindowState(
                values =
                    ThemePreferenceValues.defaultVisual()
                        .withBoolean(NativeThemePreferenceSchemaV1.useCustomStatusBarColor, true)
                        .withInt(
                            NativeThemePreferenceSchemaV1.customStatusBarColor,
                            customStatusBarColor.toArgb(),
                        ),
                lightScheme = lightColorScheme(background = background),
            )

        assertEquals(customStatusBarColor.toArgb(), state.statusBarColor)
        assertTrue(state.lightStatusBarIcons)
        assertEquals(background.toArgb(), state.navigationBarColor)
        assertTrue(state.navigationBarContrastEnforced)
        assertFalse(state.lightNavigationBarIcons)
    }

    @Test
    fun primaryStatusBarColorAndThemeBackgroundAreUsedWithoutOverrides() {
        val primary = Color(0xFF345678)
        val background = Color(0xFFF0F0F0)
        val state =
            visibleWindowState(
                lightScheme = lightColorScheme(primary = primary, background = background),
            )

        assertEquals(primary.toArgb(), state.statusBarColor)
        assertFalse(state.lightStatusBarIcons)
        assertEquals(background.toArgb(), state.navigationBarColor)
        assertTrue(state.navigationBarContrastEnforced)
        assertFalse(state.lightNavigationBarIcons)
    }

    @Test
    fun themeSnapshotTargetRemainsBoundToItsOwnSource() {
        val cardSnapshot =
            ThemePreferenceSnapshot(
                source = "character_card",
                sourceId = "card-a",
                values = ThemePreferenceValues.defaultVisual(),
            )
        val groupSnapshot =
            ThemePreferenceSnapshot(
                source = "character_group",
                sourceId = "group-b",
                values = ThemePreferenceValues.defaultVisual(),
            )

        assertEquals(ActivePrompt.CharacterCard("card-a"), cardSnapshot.toThemeTarget())
        assertEquals(ActivePrompt.CharacterGroup("group-b"), groupSnapshot.toThemeTarget())
    }

    @Test
    fun backgroundFailureOnlyDisablesTheMatchingCurrentResource() {
        val failure =
            NativeThemeBackgroundLoadFailure(
                uri = "file:///theme/broken.png",
                mediaType = UserPreferencesManager.MEDIA_TYPE_IMAGE,
                cause = null,
            )
        val matchingValues =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
                .withString(NativeThemePreferenceSchemaV1.backgroundImageUri, failure.uri)
                .withString(NativeThemePreferenceSchemaV1.backgroundMediaType, failure.mediaType)
        val replacedValues =
            matchingValues.withString(
                NativeThemePreferenceSchemaV1.backgroundImageUri,
                "file:///theme/replacement.png",
            )
        val changedMediaTypeValues =
            matchingValues.withString(
                NativeThemePreferenceSchemaV1.backgroundMediaType,
                UserPreferencesManager.MEDIA_TYPE_VIDEO,
            )

        assertTrue(shouldDisableBackgroundForFailure(matchingValues, failure))
        assertFalse(shouldDisableBackgroundForFailure(replacedValues, failure))
        assertFalse(shouldDisableBackgroundForFailure(changedMediaTypeValues, failure))
    }

    @Test
    fun offscreenVideoOverlayAlphaClampsInvalidOpacity() {
        assertEquals(
            0,
            resolveNativeThemeVideoOverlayAlpha(
                1.5f,
                NativeThemeVideoOverlayAlphaPolicy.CLAMP_TO_COLOR_RANGE,
            ),
        )
        assertEquals(
            255,
            resolveNativeThemeVideoOverlayAlpha(
                -0.5f,
                NativeThemeVideoOverlayAlphaPolicy.CLAMP_TO_COLOR_RANGE,
            ),
        )
    }

    @Test
    fun mainVideoOverlayAlphaRetainsTheCurrentCalculation() {
        assertEquals(
            -127,
            resolveNativeThemeVideoOverlayAlpha(
                1.5f,
                NativeThemeVideoOverlayAlphaPolicy.PRESERVE_CALCULATED_VALUE,
            ),
        )
    }

    private fun visibleWindowState(
        values: ThemePreferenceValues = ThemePreferenceValues.defaultVisual(),
        lightScheme: ColorScheme = lightColorScheme(),
    ): NativeThemeMainWindowChromeState.Visible =
        when (val state = resolveWindowState(values, lightScheme)) {
            is NativeThemeMainWindowChromeState.Hidden ->
                throw AssertionError("Expected a visible window chrome state")
            is NativeThemeMainWindowChromeState.Visible -> state
        }

    private fun resolveWindowState(
        values: ThemePreferenceValues = ThemePreferenceValues.defaultVisual(),
        lightScheme: ColorScheme = lightColorScheme(),
    ): NativeThemeMainWindowChromeState =
        resolveNativeThemeMainWindowChromeState(
            resolveNativeThemeV1(
                snapshot =
                    ThemePreferenceSnapshot(
                        source = "character_card",
                        sourceId = "test-card",
                        values = values,
                    ),
                environment =
                    NativeThemeEnvironment(
                        hostSurface = NativeThemeHostSurface.MAIN,
                        systemDarkTheme = false,
                    ),
                baseColorScheme = { lightScheme },
            ),
        )
}
