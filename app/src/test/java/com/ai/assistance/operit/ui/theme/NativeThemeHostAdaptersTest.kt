package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeHostAdaptersTest {
    @Test
    fun primaryStatusBarColorAndThemeBackgroundAreUsedByDefault() {
        val primary = Color(0xFF345678)
        val background = Color(0xFFF0F0F0)
        val state =
            resolveWindowState(lightScheme = lightColorScheme(primary = primary, background = background))

        assertEquals(primary.toArgb(), state.statusBarColor)
        assertFalse(state.lightStatusBarIcons)
        assertEquals(background.toArgb(), state.navigationBarColor)
        assertTrue(state.navigationBarContrastEnforced)
        assertFalse(state.lightNavigationBarIcons)
    }

    @Test
    fun darkNavigationBackgroundUsesLightIcons() {
        val background = Color(0xFF101010)
        val state =
            resolveWindowState(lightScheme = lightColorScheme(background = background))

        assertTrue(state.lightNavigationBarIcons)
    }

    private fun resolveWindowState(
        lightScheme: ColorScheme = lightColorScheme(),
    ): NativeThemeMainWindowChromeState =
        resolveNativeThemeMainWindowChromeState(
            resolveGlobalThemeV1(
                presentation = GlobalPresentationSnapshot.default(),
                environment =
                    NativeThemeEnvironment(
                        hostSurface = NativeThemeHostSurface.MAIN,
                        systemDarkTheme = false,
                    ),
                baseColorScheme = { lightScheme },
            ),
        )
}
