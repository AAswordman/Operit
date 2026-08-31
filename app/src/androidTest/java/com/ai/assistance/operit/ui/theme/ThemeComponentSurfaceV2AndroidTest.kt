package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.theme.packages.LinkedThemeRuntimeV2
import com.ai.assistance.operit.data.theme.packages.ResolvedThemeParametersV2
import com.ai.assistance.operit.data.theme.packages.ThemeArchiveSha256V2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentStateSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialColorSchemeV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialProjectionV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageCoordinateV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageIdV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageVersionV2
import com.ai.assistance.operit.data.theme.packages.ThemeShapesV2
import com.ai.assistance.operit.data.theme.packages.ThemeTypographyV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeComponentSurfaceV2AndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun inputSkinChangesItsPaintedContainerForFocusedState() {
        var focused by mutableStateOf(false)
        val normalContainer = Color(0xFF101820)
        val focusedContainer = Color(0xFF203040)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalThemePackageUiRuntimeV2 provides runtime()) {
                MaterialTheme {
                    ThemeComponentSurfaceV2(
                        component = ThemeComponentCatalogV2.INPUT,
                        state =
                            if (focused) {
                                ThemeComponentStateV2.FOCUSED
                            } else {
                                ThemeComponentStateV2.NORMAL
                            },
                        modifier = Modifier.size(48.dp).testTag("input-skin"),
                        applyContentPadding = false,
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        assertEquals(normalContainer.toArgb(), centerPixelArgb())

        composeTestRule.runOnUiThread { focused = true }
        composeTestRule.waitForIdle()

        assertEquals(focusedContainer.toArgb(), centerPixelArgb())
    }

    private fun centerPixelArgb(): Int {
        val image = composeTestRule.onNodeWithTag("input-skin").captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
    }

    private fun runtime(): ThemePackageUiRuntimeV2 {
        val tokenSet =
            ThemeSceneTokenSetV1(
                tokens =
                    mapOf(
                        "color.normal" to colorToken(0xFF101820),
                        "color.focused" to colorToken(0xFF203040),
                        "color.content" to colorToken(0xFFE5F6FF),
                        "color.outline" to colorToken(0xFF00E5FF),
                    ),
            )
        val coordinate =
            ThemePackageCoordinateV2(
                packageId = ThemePackageIdV2("test.component_skin"),
                version = ThemePackageVersionV2("1.0.0"),
                archiveSha256 = ThemeArchiveSha256V2("ab".repeat(32)),
            )
        val linked =
            LinkedThemeRuntimeV2(
                coordinate = coordinate,
                packageChain = listOf(coordinate),
                material =
                    ThemeMaterialProjectionV2(
                        colors = ThemeMaterialColorSchemeV2.uniform("color.normal"),
                        typography = ThemeTypographyV2(),
                        shapes = ThemeShapesV2(2f, 4f, 8f, 16f, 28f),
                    ),
                componentSkins =
                    mapOf(
                        ThemeComponentCatalogV2.INPUT to
                            ThemeComponentSkinV2(
                                normal =
                                    ThemeComponentStateSkinV2(
                                        containerToken = "color.normal",
                                        contentToken = "color.content",
                                    ),
                                focused =
                                    ThemeComponentStateSkinV2(
                                        containerToken = "color.focused",
                                        contentToken = "color.content",
                                        outlineToken = "color.outline",
                                        outlineWidthDp = 1f,
                                    ),
                            ),
                    ),
                surfaces = emptyMap(),
                tokens = tokenSet,
                scenes = emptyMap(),
                assets = emptyMap(),
                parameterDefinitions = emptyMap(),
            )
        return createThemePackageUiRuntimeV2(
            linked = linked,
            parameters = ResolvedThemeParametersV2(emptyMap()),
            darkTheme = true,
            userFontScale = 1f,
        )
    }

    private fun colorToken(argb: Long): ThemeSceneTokenValueV1.ColorToken =
        ThemeSceneTokenValueV1.ColorToken(lightArgb = argb, darkArgb = argb)
}
