package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeThemeGlanceHostAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun android12ContextProjectsDynamicColorsForBothLauncherModes() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot =
            ThemePreferenceSnapshot(
                source = "character_card",
                sourceId = "glance-android-test",
                values = ThemePreferenceValues.defaultVisual(),
            )
        val palette =
            resolveNativeThemeGlancePalette(
                context = context,
                snapshot = snapshot,
            )

        assertDynamicColorProjection(context, snapshot, palette)

        assertEquals(NativeThemeHostSurface.GLANCE, palette.dayTheme.environment.hostSurface)
        assertEquals(NativeThemeHostSurface.GLANCE, palette.nightTheme.environment.hostSurface)
        assertNotNull(palette.primary.toColorProvider())
        assertNotNull(palette.onSurface.toColorProvider())
    }

    @Test
    fun activeGlanceContentReprojectsForThemeAndDynamicColorChanges() {
        val initialPrimary = Color(0xFF205080)
        val changedPrimary = Color(0xFF805020)
        val initialSnapshot = customColorSnapshot("glance-active-initial", initialPrimary)
        val changedSnapshot = customColorSnapshot("glance-active-changed", changedPrimary)
        val themeSnapshots = MutableStateFlow(initialSnapshot)
        val dynamicColorRevisions = MutableStateFlow(0)
        var observedPalette: NativeThemeGlancePaletteV1? = null

        composeTestRule.setContent {
            NativeThemeGlanceContentHost(
                context = LocalContext.current,
                initialSnapshot = initialSnapshot,
                themeSnapshots = themeSnapshots,
                dynamicColorRevisions = dynamicColorRevisions,
            ) { palette ->
                SideEffect { observedPalette = palette }
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(initialPrimary, requireNotNull(observedPalette).primary.day)
        }
        val initialPalette = requireNotNull(observedPalette)

        composeTestRule.runOnIdle {
            themeSnapshots.value = changedSnapshot
        }
        composeTestRule.runOnIdle {
            assertEquals(changedPrimary, requireNotNull(observedPalette).primary.day)
        }
        val changedPalette = requireNotNull(observedPalette)

        composeTestRule.runOnIdle {
            dynamicColorRevisions.value += 1
        }
        composeTestRule.runOnIdle {
            assertNotSame(initialPalette, changedPalette)
            assertNotSame(changedPalette, requireNotNull(observedPalette))
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun assertDynamicColorProjection(
        context: Context,
        snapshot: ThemePreferenceSnapshot,
        palette: NativeThemeGlancePaletteV1,
    ) {
        val expected =
            resolveNativeThemeGlancePalette(
                snapshot = snapshot,
                lightColorScheme = dynamicLightColorScheme(context),
                darkColorScheme = dynamicDarkColorScheme(context),
            )

        assertEquals(expected.primary.day, palette.primary.day)
        assertEquals(expected.primary.night, palette.primary.night)
        assertEquals(expected.onSurface.day, palette.onSurface.day)
        assertEquals(expected.onSurface.night, palette.onSurface.night)
    }

    private fun customColorSnapshot(
        sourceId: String,
        primary: Color,
    ): ThemePreferenceSnapshot =
        ThemePreferenceSnapshot(
            source = "character_card",
            sourceId = sourceId,
            values =
                ThemePreferenceValues.defaultVisual()
                    .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                    .withString(
                        NativeThemePreferenceSchemaV1.themeMode,
                        UserPreferencesManager.THEME_MODE_LIGHT,
                    )
                    .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                    .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb()),
        )
}
