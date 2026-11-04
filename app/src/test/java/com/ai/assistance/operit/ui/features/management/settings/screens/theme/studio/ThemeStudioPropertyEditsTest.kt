package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.theme.script.ThemeAppearanceMode
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceState
import com.ai.assistance.operit.data.theme.script.ThemeAppearanceText
import com.ai.assistance.operit.data.theme.script.ThemeScriptAppearance
import com.ai.assistance.operit.data.theme.script.ThemeScriptInsets
import com.ai.assistance.operit.data.theme.script.ThemeScriptSurface
import com.ai.assistance.operit.data.theme.script.forMode
import com.ai.assistance.operit.data.theme.script.forState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.ai.assistance.operit.data.theme.script.ThemeTextStroke

/** 直接检查保存后的主题声明，不依赖 Android 颜色解析或 Compose 渲染。 */
class ThemeStudioPropertyEditsTest {
    @Test fun choosingBorderColorMakesAnUnconfiguredBorderVisible() {
        val changed = ThemeScriptSurface().withStudioColor("strokeColor", "#FF123456")
        assertEquals("#FF123456", changed.strokeColor)
        assertEquals(1f, changed.strokeWidth, 0f)
    }

    @Test fun existingWidthAndExplicitTransparencyRemainUnchanged() {
        val original = ThemeScriptSurface(strokeWidth = 2.5f, opacity = 0.4f)
        val changed = original.withStudioColor("strokeColor", "#00123456")
        assertEquals(2.5f, changed.strokeWidth, 0f)
        assertEquals("#00123456", changed.strokeColor)
        assertEquals(0.4f, changed.opacity, 0f)
        assertNull(changed.withStudioColor("strokeColor", null).strokeColor)
    }

    @Test fun stateBorderIsEnabledWithoutChangingOtherStatesOrDefaultColors() {
        val disabled = ThemeAppearanceState(contentColor = "#FF222222", opacity = 0.3f)
        val original = ThemeScriptSurface(
            fillColor = "#FF000000",
            strokeColor = "#FF555555",
            states = mapOf("disabled" to disabled),
        )
        val changed = original.withStudioColor("strokeColor", "#FF123456", "pressed")
        assertEquals("#FF555555", changed.strokeColor)
        assertEquals(0f, changed.strokeWidth, 0f)
        assertEquals(disabled, changed.states.getValue("disabled"))
        assertEquals("#FF123456", changed.forState("pressed").strokeColor)
        assertEquals(1f, changed.forState("pressed").strokeWidth, 0f)
    }

    @Test fun positiveInheritedStateWidthIsNotReplacedWithOneDp() {
        val original = ThemeScriptSurface(strokeWidth = 3f)
        val changed = original.withStudioColor("strokeColor", "#FF123456", "focused")
        assertNull(changed.states.getValue("focused").strokeWidth)
        assertEquals(3f, changed.forState("focused").strokeWidth, 0f)
    }

    @Test fun oneTextColorEntryPreservesFontsAndClearsTheDuplicateOverride() {
        val font = ThemeAppearanceText(family = "serif", scale = 1.2f, lineHeight = 1.3f, color = "#FF654321")
        val original = ThemeScriptSurface(contentColor = "#FF111111", textStyle = font)
        val changed = original.withStudioColor("contentColor", "#FF123456")
        assertEquals("#FF123456", changed.contentColor)
        assertEquals(font.copy(color = null), changed.textStyle)
        val cleared = changed.withStudioColor("contentColor", null)
        assertNull(cleared.contentColor)
        assertNull(cleared.textStyle.color)
        assertEquals("serif", cleared.textStyle.family)
    }

    @Test fun stateTextColorDoesNotEraseDefaultTypography() {
        val original = ThemeScriptSurface(textStyle = ThemeAppearanceText(color = "#FF654321"))
        val changed = original.withStudioColor("contentColor", "#FF123456", "pressed")
        assertEquals(original.textStyle, changed.textStyle)
        assertEquals("#FF123456", changed.forState("pressed").contentColor)
    }

    @Test fun exportedEditsKeepTheOtherModeAndUnrelatedSurfaceFields() {
        val light = ThemeScriptSurface(padding = ThemeScriptInsets(2f, 3f, 4f, 5f), messageWidth = 0.7f)
        val dark = ThemeScriptSurface(fillColor = "#FF222222", strokeWidth = 4f)
        val project = ThemeScriptAppearance(
            apiVersion = 1,
            modes = mapOf(
                "light" to ThemeAppearanceMode(surfaces = mapOf("assistantMessage" to light)),
                "dark" to ThemeAppearanceMode(surfaces = mapOf("assistantMessage" to dark)),
            ),
        )
        val edited = project.copy(modes = project.modes + (
            "light" to project.modes.getValue("light").copy(surfaces = mapOf(
                "assistantMessage" to light.withStudioColor("strokeColor", "#FF123456"),
            ))
        ))
        val restored = Json.decodeFromString<ThemeScriptAppearance>(Json.encodeToString(edited))
        assertEquals(dark, restored.forMode("dark").surfaces.getValue("assistantMessage"))
        val restoredLight = restored.forMode("light").surfaces.getValue("assistantMessage")
        assertEquals(light.padding, restoredLight.padding)
        assertEquals(light.messageWidth, restoredLight.messageWidth, 0f)
        assertEquals(1f, restoredLight.strokeWidth, 0f)
    }
    @Test fun editingAndClearingMessageTextColorKeepsTheTextOutline() {
        val outline = ThemeTextStroke("#FF225588", 2f)
        val font = ThemeAppearanceText(family = "serif", color = "#FF111111", stroke = outline)
        val original = ThemeScriptSurface(contentColor = "#FF222222", textStyle = font)
        val edited = original.withStudioColor("contentColor", "#FF333333")
        assertEquals("#FF333333", edited.contentColor)
        assertNull(edited.textStyle.color)
        assertEquals(outline, edited.textStyle.stroke)
        val cleared = edited.withStudioColor("contentColor", null)
        assertNull(cleared.contentColor)
        assertNull(cleared.textStyle.color)
        assertEquals(outline, cleared.textStyle.stroke)
        assertEquals("serif", cleared.textStyle.family)
    }
}