package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeThemeDetachedHostAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun detachedHostProvidesPresentationResolvedThemeAndScaledTypography() {
        val fontScale = 1.25f
        val presentation =
            GlobalPresentationSnapshot(
                themeMode = GlobalThemeMode.LIGHT,
                fontScale = fontScale,
            )
        val resolvedTheme =
            resolveGlobalThemeForDetachedComposeHost(
                presentation = presentation,
                hostSurface = NativeThemeHostSurface.FLOATING,
                systemDarkTheme = true,
                lightColorScheme = lightColorScheme(),
                darkColorScheme = darkColorScheme(),
            )
        var providedPresentation: GlobalPresentationSnapshot? = null
        var providedResolvedTheme: ResolvedGlobalTheme? = null
        var providedBodyLargeSize = Typography().bodyLarge.fontSize

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = presentation,
                resolvedTheme = resolvedTheme,
            ) {
                val localPresentation = LocalGlobalPresentation.current
                val localResolvedTheme = LocalResolvedGlobalTheme.current
                val materialBodyLargeSize = MaterialTheme.typography.bodyLarge.fontSize
                SideEffect {
                    providedPresentation = localPresentation
                    providedResolvedTheme = localResolvedTheme
                    providedBodyLargeSize = materialBodyLargeSize
                }
            }
        }

        composeTestRule.runOnIdle {
            assertSame(presentation, providedPresentation)
            assertSame(resolvedTheme, providedResolvedTheme)
            assertEquals(Typography().bodyLarge.fontSize * fontScale, providedBodyLargeSize)
        }
    }
}
