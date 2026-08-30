package com.ai.assistance.operit.ui.theme.style.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.ThemeStyleInstanceRecordV1
import com.ai.assistance.operit.data.preferences.themeStyleInstanceKey
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.ui.theme.NativeThemeEnvironment
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.NativeThemeV1LightColorScheme
import com.ai.assistance.operit.ui.theme.resolveNativeThemeV1
import com.ai.assistance.operit.ui.theme.renderer.contract.NativeThemeComponentStylePartIdsV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatContractV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeComponentStyleV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderAlignmentV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderLayerV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderSideV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBorderStackSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleBrushV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleColorSourceV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleColorSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePartPatchV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePatchV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePropertyIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStylePropertyV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleRuleIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleScopeV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleValueV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleIssueCodeV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLinkResultV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeNativeV1StyleCompilerV1Test {
    @Test
    fun nativeV1BaselineLinksForMainAndEditorPreview() {
        val linked =
            requireLinked(
                NativeThemeNativeV1StyleCompilerV1.linkStat(
                    resolvedTheme = resolvedTheme(),
                    instanceLayer = ThemeStyleInstanceRecordV1.empty().instanceLayer,
                ),
            )

        val main =
            NativeThemeNativeV1StyleCompilerV1.resolveStat(
                linked = linked,
                surface = NativeThemeHostSurface.MAIN,
                darkTheme = false,
            )
        val preview =
            NativeThemeNativeV1StyleCompilerV1.resolveStatForEditorPreview(
                linked = linked,
                darkTheme = false,
            )

        assertEquals(
            NativeThemeV1LightColorScheme.surfaceVariant.copy(alpha = 0.6f).toArgb(),
            main.surfaceColor.toArgb(),
        )
        assertEquals(main.surfaceColor.toArgb(), preview.surfaceColor.toArgb())
        assertEquals(16f, main.contentPaddingDp)
        assertEquals(1f, main.opacity)
        assertEquals(null, main.border)
        assertEquals(null, main.leadingIconContainer)
    }

    @Test
    fun statEditorProducesAStylePlanUsedByMainAndPreview() {
        val instance =
            ThemeStyleInstanceRecordV1.empty()
                .instanceLayer
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setSurfaceColor(layer, Color(0xff2d4b73.toInt())) }
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setValueColor(layer, Color(0xfff0f4ff.toInt())) }
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setLabelColor(layer, Color(0xffbfd4ff.toInt())) }
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setRoundedCorners(layer, 28f) }
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setBorder(layer, Color(0xff9cc8ff.toInt()), 2f) }
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setOpacity(layer, 0.72f) }
                .let { layer -> NativeThemeStatStyleInstanceEditorV1.setContentPadding(layer, 22f) }
                .let {
                    layer ->
                    NativeThemeStatStyleInstanceEditorV1.setIconContainer(
                        layer = layer,
                        containerColor = Color(0xffd4e3ff.toInt()),
                        iconColor = Color(0xff183a64.toInt()),
                    )
                }
        val linked =
            requireLinked(
                NativeThemeNativeV1StyleCompilerV1.linkStat(resolvedTheme(), instance),
            )

        val main =
            NativeThemeNativeV1StyleCompilerV1.resolveStat(
                linked = linked,
                surface = NativeThemeHostSurface.MAIN,
                darkTheme = false,
            )
        val preview = NativeThemeNativeV1StyleCompilerV1.resolveStatForEditorPreview(linked, darkTheme = false)

        assertEquals(0xff2d4b73.toInt(), main.surfaceColor.toArgb())
        assertEquals(0xfff0f4ff.toInt(), main.value.color.toArgb())
        assertEquals(0xffbfd4ff.toInt(), main.label.color.toArgb())
        assertEquals(28f, main.cornerRadiusDp())
        assertEquals(2f, main.borderWidth())
        assertEquals(0.72f, main.opacity)
        assertEquals(22f, main.contentPaddingDp)
        assertEquals(40f, main.leadingIconContainer?.containerSizeDp)
        assertEquals(main.surfaceColor.toArgb(), preview.surfaceColor.toArgb())
    }

    @Test
    fun nativeV1TypographyScaleChangesTheStatTextPlan() {
        val values =
            ThemePreferenceValues.defaultVisual().withFloat(
                NativeThemePreferenceSchemaV1.fontScale,
                1.25f,
            )
        val linked =
            requireLinked(
                NativeThemeNativeV1StyleCompilerV1.linkStat(
                    resolvedTheme(values),
                    ThemeStyleInstanceRecordV1.empty().instanceLayer,
                ),
            )

        val plan =
            NativeThemeNativeV1StyleCompilerV1.resolveStat(
                linked = linked,
                surface = NativeThemeHostSurface.MAIN,
                darkTheme = false,
            )

        assertEquals(20f, plan.value.spec.fontSizeSp)
        assertEquals(30f, plan.value.spec.lineHeightSp)
        assertEquals(15f, plan.label.spec.fontSizeSp)
        assertEquals(20f, plan.label.spec.lineHeightSp)
    }

    @Test
    fun statCompilerRejectsABorderThatItsRendererCannotDraw() {
        val unsupportedBorder =
            NativeThemeStyleLayerV1(
                id = NativeThemeStyleLayerIdV1("operit.style.instance"),
                componentStyles =
                    listOf(
                        NativeThemeComponentStyleV1(
                            componentId = NativeThemeStatContractV1.contract.id,
                            scope =
                                NativeThemeStyleScopeV1(
                                    common =
                                        NativeThemeStylePatchV1(
                                            parts =
                                                listOf(
                                                    NativeThemeStylePartPatchV1(
                                                        part = NativeThemeComponentStylePartIdsV1.surface,
                                                        properties =
                                                            listOf(
                                                                NativeThemeStylePropertyV1(
                                                                    id =
                                                                        NativeThemeStylePropertyIdV1.BORDER_STACK,
                                                                    value =
                                                                        NativeThemeStyleValueV1.Border(
                                                                            NativeThemeStyleBorderStackSpecV1(
                                                                                layers =
                                                                                    listOf(
                                                                                        NativeThemeStyleBorderLayerV1(
                                                                                            id =
                                                                                                NativeThemeStyleRuleIdV1(
                                                                                                    "center",
                                                                                                ),
                                                                                            sides =
                                                                                                NativeThemeStyleBorderSideV1.entries.toSet(),
                                                                                            alignment =
                                                                                                NativeThemeStyleBorderAlignmentV1.CENTER,
                                                                                            widthDp = 1f,
                                                                                            brush =
                                                                                                NativeThemeStyleBrushV1.Solid(
                                                                                                    color(
                                                                                                        "#112233ff",
                                                                                                    ),
                                                                                                ),
                                                                                        ),
                                                                                    ),
                                                                            ),
                                                                        ),
                                                                ),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
            )

        val result = NativeThemeNativeV1StyleCompilerV1.linkStat(resolvedTheme(), unsupportedBorder)

        assertTrue(result is NativeThemeStyleLinkResultV1.Rejected)
        val issues = (result as NativeThemeStyleLinkResultV1.Rejected).issues
        assertTrue(
            issues.any { issue -> issue.code == NativeThemeStyleIssueCodeV1.UNSUPPORTED_COMPOSE_RENDER_PLAN },
        )
    }

    @Test
    fun styleInstancesUseDistinctCardAndGroupKeys() {
        assertEquals("character_card:card-1", ActivePrompt.CharacterCard("card-1").themeStyleInstanceKey())
        assertEquals("character_group:group-1", ActivePrompt.CharacterGroup("group-1").themeStyleInstanceKey())
    }

    private fun resolvedTheme(
        values: ThemePreferenceValues = ThemePreferenceValues.defaultVisual(),
    ) =
        resolveNativeThemeV1(
            snapshot =
                ThemePreferenceSnapshot(
                    source = "character_card",
                    sourceId = "default_character",
                    values = values,
                ),
            environment = NativeThemeEnvironment(NativeThemeHostSurface.MAIN, systemDarkTheme = false),
            baseColorScheme = { NativeThemeV1LightColorScheme },
        )

    private fun requireLinked(result: NativeThemeStyleLinkResultV1): NativeThemeStyleLinkResultV1.Linked {
        return when (result) {
            is NativeThemeStyleLinkResultV1.Linked -> result
            is NativeThemeStyleLinkResultV1.Rejected ->
                throw AssertionError(result.issues.joinToString { issue -> issue.code.name })
        }
    }

    private fun color(rgba: String): NativeThemeStyleColorSpecV1 =
        NativeThemeStyleColorSpecV1(
            light = NativeThemeStyleColorSourceV1.Literal(rgba),
            dark = NativeThemeStyleColorSourceV1.Literal(rgba),
        )
}
