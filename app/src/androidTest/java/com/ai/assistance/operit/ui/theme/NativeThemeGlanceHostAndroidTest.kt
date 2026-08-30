package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
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
        val presentation = GlobalPresentationSnapshot.default()
        val palette =
            resolveNativeThemeGlancePalette(
                context = context,
                presentation = presentation,
            )

        assertDynamicColorProjection(context, presentation, palette)

        assertEquals(NativeThemeHostSurface.GLANCE, palette.dayTheme.environment.hostSurface)
        assertEquals(NativeThemeHostSurface.GLANCE, palette.nightTheme.environment.hostSurface)
        assertNotNull(palette.primary.toColorProvider())
        assertNotNull(palette.onSurface.toColorProvider())
    }

    @Test
    fun activeGlanceContentReprojectsForThemeAndDynamicColorChanges() {
        val initialPresentation = GlobalPresentationSnapshot(themeMode = GlobalThemeMode.LIGHT)
        val changedPresentation = GlobalPresentationSnapshot(themeMode = GlobalThemeMode.DARK)
        val presentations = MutableStateFlow(initialPresentation)
        val dynamicColorRevisions = MutableStateFlow(0)
        var observedPalette: NativeThemeGlancePaletteV1? = null

        composeTestRule.setContent {
            NativeThemeGlanceContentHost(
                context = LocalContext.current,
                initialPresentation = initialPresentation,
                presentations = presentations,
                dynamicColorRevisions = dynamicColorRevisions,
            ) { palette ->
                SideEffect { observedPalette = palette }
            }
        }

        composeTestRule.runOnIdle {
            val palette = requireNotNull(observedPalette)
            org.junit.Assert.assertFalse(palette.dayTheme.darkTheme)
            org.junit.Assert.assertTrue(palette.nightTheme.darkTheme)
        }
        val initialPalette = requireNotNull(observedPalette)

        composeTestRule.runOnIdle {
            presentations.value = changedPresentation
        }
        composeTestRule.runOnIdle {
            val palette = requireNotNull(observedPalette)
            assertTrue(palette.dayTheme.darkTheme)
            assertTrue(palette.nightTheme.darkTheme)
            assertEquals(palette.primary.day, palette.primary.night)
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
        presentation: GlobalPresentationSnapshot,
        palette: NativeThemeGlancePaletteV1,
    ) {
        val expected =
            resolveNativeThemeGlancePalette(
                presentation = presentation,
                lightColorScheme = dynamicLightColorScheme(context),
                darkColorScheme = dynamicDarkColorScheme(context),
            )

        assertEquals(expected.primary.day, palette.primary.day)
        assertEquals(expected.primary.night, palette.primary.night)
        assertEquals(expected.onSurface.day, palette.onSurface.day)
        assertEquals(expected.onSurface.night, palette.onSurface.night)
    }
}
