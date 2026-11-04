package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 校验主题声明和保存结果，不调用 Android 颜色解析或实际绘制。 */
class ThemeTextStrokeContractTest {
    private val manifest = ThemeScriptManifest(1, "assets/main.js")

    private fun validate(stroke: ThemeTextStroke) {
        ThemeScriptContract.validate(
            ThemeScriptAppearance(1, typography = ThemeAppearanceText(stroke = stroke)), manifest,
        )
    }

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("应拒绝无效的文字描边声明")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun themesWithoutAnOutlineKeepTheirExistingSerializedFields() {
        val text = ThemeAppearanceText(family = "serif", color = "#FFFFFF")
        val encoded = ThemeScriptContract.json.encodeToJsonElement(ThemeAppearanceText.serializer(), text) as JsonObject
        assertFalse(encoded.containsKey("stroke"))
        val restored = ThemeScriptContract.json.decodeFromString(ThemeAppearanceText.serializer(), """{"family":"serif","color":"#FFFFFF"}""")
        assertEquals(text, restored)
        assertNull(resolveThemeTextStroke(null, restored.stroke))
    }

    @Test
    fun globalOutlineIsInheritedAndLocalColorAndWidthCanOverrideIt() {
        val global = ThemeTextStroke("#FF000000", 1.5f)
        val local = ThemeTextStroke("#FFFF0000", 2f)
        assertEquals(global, resolveThemeTextStroke(global, null))
        assertEquals(local, resolveThemeTextStroke(global, local))
        assertEquals("#FF000000", global.color)
        assertEquals(1.5f, global.width, 0f)
    }

    @Test
    fun anExplicitZeroWidthDisablesTheGlobalOutlineForOneArea() {
        val global = ThemeTextStroke("#FF000000", 2f)
        assertNull(resolveThemeTextStroke(global, ThemeTextStroke(width = 0f)))
        assertNull(resolveThemeTextStroke(ThemeTextStroke(width = 0f), null))
        assertEquals(global, resolveThemeTextStroke(global, null))
    }

    @Test
    fun zeroAndMaximumWidthsAndTransparentColorsAreValid() {
        validate(ThemeTextStroke("#00000000", 0f))
        validate(ThemeTextStroke("#00123456", 8f))
        validate(ThemeTextStroke("#123456", 1f))
    }

    @Test
    fun negativeNonFiniteAndOversizedWidthsAreRejected() {
        listOf(-0.01f, 8.01f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { width ->
            rejects { validate(ThemeTextStroke(width = width)) }
        }
    }

    @Test
    fun malformedOutlineColorsAreRejectedInGlobalAndAreaTypography() {
        listOf("red", "#12345", "#GG112233").forEach { color ->
            rejects { validate(ThemeTextStroke(color)) }
            rejects {
                ThemeScriptContract.validate(ThemeScriptAppearance(1, surfaces = mapOf(
                    "userMessage" to ThemeScriptSurface(textStyle = ThemeAppearanceText(stroke = ThemeTextStroke(color))),
                )), manifest)
            }
        }
    }

    @Test
    fun textOutlineAndBubbleBorderRemainSeparateAcrossExportAndModes() {
        val global = ThemeTextStroke("#FF000000", 1f)
        val local = ThemeTextStroke("#FF112233", 2f)
        val message = ThemeScriptSurface(
            contentColor = "#FFFFFFFF", strokeColor = "#FF445566", strokeWidth = 3f,
            textStyle = ThemeAppearanceText(family = "serif", stroke = local),
        )
        val project = ThemeScriptAppearance(
            1, typography = ThemeAppearanceText(stroke = global),
            modes = mapOf(
                "light" to ThemeAppearanceMode(surfaces = mapOf("userMessage" to message)),
                "dark" to ThemeAppearanceMode(surfaces = mapOf("userMessage" to message.copy(
                    textStyle = message.textStyle.copy(stroke = ThemeTextStroke(width = 0f)),
                ))),
            ),
        )
        ThemeScriptContract.validate(project, manifest)
        val encoded = ThemeScriptContract.json.encodeToString(ThemeScriptAppearance.serializer(), project)
        val restored = ThemeScriptContract.json.decodeFromString(ThemeScriptAppearance.serializer(), encoded)
        assertEquals(global, restored.typography.stroke)
        val light = restored.forMode("light").surfaces.getValue("userMessage")
        assertEquals(local, light.textStyle.stroke)
        assertEquals("#FFFFFFFF", light.contentColor)
        assertEquals("#FF445566", light.strokeColor)
        assertEquals(3f, light.strokeWidth, 0f)
        assertEquals("serif", light.textStyle.family)
        assertNull(resolveThemeTextStroke(global, restored.forMode("dark").surfaces.getValue("userMessage").textStyle.stroke))
        assertTrue(encoded.contains("stroke"))
    }
}
