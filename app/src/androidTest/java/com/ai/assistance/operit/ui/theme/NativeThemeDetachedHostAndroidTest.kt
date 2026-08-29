package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
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
    fun detachedHostProvidesSnapshotResolvedThemeAndScaledTypography() {
        val primary = Color(0xFF204060)
        val fontScale = 1.25f
        val snapshot =
            ThemePreferenceSnapshot(
                source = "character_card",
                sourceId = "detached-host-test",
                values =
                    ThemePreferenceValues.defaultVisual()
                        .withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
                        .withString(
                            NativeThemePreferenceSchemaV1.themeMode,
                            UserPreferencesManager.THEME_MODE_LIGHT,
                        )
                        .withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
                        .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, primary.toArgb())
                        .withFloat(NativeThemePreferenceSchemaV1.fontScale, fontScale),
            )
        val resolvedTheme =
            resolveNativeThemeForDetachedComposeHost(
                snapshot = snapshot,
                hostSurface = NativeThemeHostSurface.FLOATING,
                systemDarkTheme = true,
                lightColorScheme = lightColorScheme(),
                darkColorScheme = darkColorScheme(),
            )
        var providedSnapshot: ThemePreferenceSnapshot? = null
        var providedResolvedTheme: ResolvedNativeThemeV1? = null
        var providedPrimary: Color? = null
        var providedBodyLargeSize = Typography().bodyLarge.fontSize

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                snapshot = snapshot,
                resolvedTheme = resolvedTheme,
            ) {
                val localSnapshot = LocalThemePreferenceSnapshot.current
                val localResolvedTheme = LocalResolvedNativeThemeV1.current
                val materialPrimary = MaterialTheme.colorScheme.primary
                val materialBodyLargeSize = MaterialTheme.typography.bodyLarge.fontSize
                SideEffect {
                    providedSnapshot = localSnapshot
                    providedResolvedTheme = localResolvedTheme
                    providedPrimary = materialPrimary
                    providedBodyLargeSize = materialBodyLargeSize
                }
            }
        }

        composeTestRule.runOnIdle {
            assertSame(snapshot, providedSnapshot)
            assertSame(resolvedTheme, providedResolvedTheme)
            assertEquals(primary, providedPrimary)
            assertEquals(Typography().bodyLarge.fontSize * fontScale, providedBodyLargeSize)
        }
    }
}
