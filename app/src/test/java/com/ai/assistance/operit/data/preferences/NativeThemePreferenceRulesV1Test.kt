package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemePreferenceRulesV1Test {
    @Test
    fun releasedBooleanActivationRulesRemainFrozen() {
        assertEquals(
            mapOf(
                "chat_input_liquid_glass" to setOf("chat_input_water_glass"),
                "chat_input_water_glass" to setOf("chat_input_liquid_glass"),
                "cursor_user_bubble_liquid_glass" to setOf("cursor_user_bubble_water_glass"),
                "cursor_user_bubble_water_glass" to setOf("cursor_user_bubble_liquid_glass"),
                "bubble_user_bubble_liquid_glass" to
                    setOf("bubble_user_bubble_water_glass", "bubble_user_use_image"),
                "bubble_user_bubble_water_glass" to
                    setOf("bubble_user_bubble_liquid_glass", "bubble_user_use_image"),
                "bubble_ai_bubble_liquid_glass" to
                    setOf("bubble_ai_bubble_water_glass", "bubble_ai_use_image"),
                "bubble_ai_bubble_water_glass" to
                    setOf("bubble_ai_bubble_liquid_glass", "bubble_ai_use_image"),
            ),
            NativeThemePreferenceRulesV1.booleanActivationRules.associate { rule ->
                rule.field.name to rule.disabledFields.map { field -> field.name }.toSet()
            },
        )
    }

    @Test
    fun enablingAFieldAppliesItsDeclaredRule() {
        NativeThemePreferenceRulesV1.booleanActivationRules.forEach { rule ->
            var values = ThemePreferenceValues.defaultVisual()
            rule.disabledFields.forEach { disabledField ->
                values = values.withBoolean(disabledField, true)
            }

            val updated =
                NativeThemePreferenceRulesV1.applyBooleanChange(
                    values = values,
                    field = rule.field,
                    value = true,
                )

            assertTrue(updated.requiredBoolean(rule.field))
            rule.disabledFields.forEach { disabledField ->
                assertFalse(updated.requiredBoolean(disabledField))
            }
        }
    }

    @Test
    fun disablingAFieldDoesNotActivateItsDeclaredRule() {
        val rule = NativeThemePreferenceRulesV1.booleanActivationRules.first()
        var values = ThemePreferenceValues.defaultVisual()
        rule.disabledFields.forEach { disabledField ->
            values = values.withBoolean(disabledField, true)
        }

        val updated =
            NativeThemePreferenceRulesV1.applyBooleanChange(
                values = values,
                field = rule.field,
                value = false,
            )

        assertFalse(updated.requiredBoolean(rule.field))
        rule.disabledFields.forEach { disabledField ->
            assertTrue(updated.requiredBoolean(disabledField))
        }
    }

    @Test
    fun rulesResolveByStableFieldName() {
        val canonicalField = NativeThemePreferenceSchemaV1.chatInputLiquidGlass
        val equivalentField =
            NativeThemeBooleanField(
                name = canonicalField.name,
                defaultValue = false,
                section = canonicalField.section,
            )
        val values =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.chatInputWaterGlass, true)

        val updated =
            NativeThemePreferenceRulesV1.applyBooleanChange(
                values = values,
                field = equivalentField,
                value = true,
            )

        assertTrue(updated.requiredBoolean(canonicalField))
        assertFalse(updated.requiredBoolean(NativeThemePreferenceSchemaV1.chatInputWaterGlass))
    }
}
