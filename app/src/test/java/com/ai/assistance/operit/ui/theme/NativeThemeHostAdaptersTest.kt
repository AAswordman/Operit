package com.ai.assistance.operit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.theme.packages.LinkedThemeRuntimeV2
import com.ai.assistance.operit.data.theme.packages.ResolvedThemeParametersV2
import com.ai.assistance.operit.data.theme.packages.ThemeArchiveSha256V2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentFrameSpecV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentStateSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialColorSchemeV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialProjectionV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageCoordinateV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageIdV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageVersionV2
import com.ai.assistance.operit.data.theme.packages.ThemeShapesV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationV2
import com.ai.assistance.operit.data.theme.packages.ThemeTypographyV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2：系统栏颜色来自主题包 app_bar 皮肤与 Material 投影，
 * 与 primary 角色解耦——这正是旧“整条青色状态栏”缺陷的回归测试。
 */
class NativeThemeHostAdaptersTest {
    @Test
    fun statusBarUsesAppBarSkinAndNavigationBarUsesThemeBackground() {
        val barContainer = Color(0xFFE8F4FF)
        val themeBackground = Color(0xFFF0F0F0)
        val runtime = runtime(barContainer = barContainer, background = themeBackground)

        val state = resolveNativeThemeMainWindowChromeState(runtime)

        assertEquals(barContainer.toArgb(), state.statusBarColor)
        assertTrue(state.lightStatusBarIcons)
        assertEquals(themeBackground.toArgb(), state.navigationBarColor)
        assertTrue(state.navigationBarContrastEnforced)
        assertFalse(state.lightNavigationBarIcons)
    }

    @Test
    fun darkSurfacesUseLightIcons() {
        val runtime = runtime(
            barContainer = Color(0xFF345678),
            background = Color(0xFF101010),
            darkTheme = true,
        )

        val state = resolveNativeThemeMainWindowChromeState(runtime)

        assertFalse(state.lightStatusBarIcons)
        assertTrue(state.lightNavigationBarIcons)
    }

    private fun runtime(
        barContainer: Color,
        background: Color,
        darkTheme: Boolean = false,
    ): ThemePackageUiRuntimeV2 {
        val tokens =
            ThemeSceneTokenSetV1(
                tokens =
                    mapOf(
                        "color.bar" to ThemeSceneTokenValueV1.ColorToken(
                            lightArgb = barContainer.toArgb().toLong() and 0xFFFFFFFFL,
                            darkArgb = barContainer.toArgb().toLong() and 0xFFFFFFFFL,
                        ),
                        "color.background" to ThemeSceneTokenValueV1.ColorToken(
                            lightArgb = background.toArgb().toLong() and 0xFFFFFFFFL,
                            darkArgb = background.toArgb().toLong() and 0xFFFFFFFFL,
                        ),
                    ),
            )
        val coordinate =
            ThemePackageCoordinateV2(
                packageId = ThemePackageIdV2("test.chrome"),
                version = ThemePackageVersionV2("1.0.0"),
                archiveSha256 = ThemeArchiveSha256V2("ab".repeat(32)),
            )
        val linked =
            LinkedThemeRuntimeV2(
                coordinate = coordinate,
                packageChain = listOf(coordinate),
                material =
                    ThemeMaterialProjectionV2(
                        colors = ThemeMaterialColorSchemeV2.uniform("color.background"),
                        typography = ThemeTypographyV2(),
                        shapes = ThemeShapesV2(2f, 4f, 8f, 16f, 28f),
                    ),
                componentSkins =
                    mapOf(
                        ThemeComponentCatalogV2.APP_BAR to
                            ThemeComponentSkinV2(
                                normal =
                                    ThemeComponentStateSkinV2(
                                        containerToken = "color.bar",
                                        contentToken = "color.background",
                                        frame = ThemeComponentFrameSpecV2.RoundRect(cornerRadiusDp = 0f),
                                    ),
                            ),
                    ),
                surfaces =
                    mapOf(
                        ThemeSurfaceCatalogV2.CHAT_MAIN to
                            ThemeSurfaceImplementationV2(
                                surfaceId = ThemeSurfaceCatalogV2.CHAT_MAIN.value,
                                kind = com.ai.assistance.operit.data.theme.packages
                                    .ThemeSurfaceImplementationKindV2.TEMPLATE,
                            ),
                    ),
                tokens = tokens,
                scenes = emptyMap(),
                assets = emptyMap(),
                parameterDefinitions = emptyMap(),
            )
        return createThemePackageUiRuntimeV2(
            linked = linked,
            parameters = ResolvedThemeParametersV2(emptyMap()),
            darkTheme = darkTheme,
            userFontScale = 1f,
        )
    }
}
