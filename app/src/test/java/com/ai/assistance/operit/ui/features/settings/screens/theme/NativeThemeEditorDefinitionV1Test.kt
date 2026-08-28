package com.ai.assistance.operit.ui.features.settings.screens.theme

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSection
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeEditorDefinitionV1Test {
    @Test
    fun inputDefinitionCoversEveryInputField() {
        val definitionFields =
            listOf(NativeThemeEditorDefinitionV1.inputStyle.field.name) +
                NativeThemeEditorDefinitionV1.inputAppearance.controls.map { control ->
                    control.field.name
                }
        val expectedFields =
            listOf(
                "input_style",
                "chat_input_transparent",
                "chat_input_floating",
                "chat_input_liquid_glass",
                "chat_input_water_glass",
            )
        val schemaFields =
            NativeThemePreferenceSchemaV1.fields
                .filter { field -> field.section == NativeThemePreferenceSection.INPUT }
                .map { field -> field.name }
                .toSet()

        assertEquals(expectedFields, definitionFields)
        assertEquals(expectedFields.size, definitionFields.toSet().size)
        assertEquals(schemaFields, definitionFields.toSet())
    }

    @Test
    fun inputStyleOptionsRemainClassicAndAgent() {
        assertEquals(
            listOf(
                UserPreferencesManager.INPUT_STYLE_CLASSIC,
                UserPreferencesManager.INPUT_STYLE_AGENT,
            ),
            NativeThemeEditorDefinitionV1.inputStyle.options.map { option -> option.value },
        )
    }

    @Test
    fun glassControlsAreVisibleOnlyForTransparentInput() {
        val controls = NativeThemeEditorDefinitionV1.inputAppearance.controls
        val defaultValues = ThemePreferenceValues.defaultVisual()
        val transparentValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent, true)

        assertEquals(
            listOf("chat_input_transparent", "chat_input_floating"),
            controls.filter { it.isVisible(defaultValues) }.map { it.field.name },
        )
        assertEquals(
            listOf(
                "chat_input_transparent",
                "chat_input_floating",
                "chat_input_liquid_glass",
                "chat_input_water_glass",
            ),
            controls.filter { it.isVisible(transparentValues) }.map { it.field.name },
        )
        assertFalse(defaultValues.requiredBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent))
        assertTrue(transparentValues.requiredBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent))
    }
}
