package com.ai.assistance.operit.data.preferences

internal data class NativeThemeBooleanActivationRule(
    val field: NativeThemeBooleanField,
    val disabledFields: List<NativeThemeBooleanField>,
)

internal object NativeThemePreferenceRulesV1 {
    val booleanActivationRules =
        listOf(
            rule(
                NativeThemePreferenceSchemaV1.chatInputLiquidGlass,
                NativeThemePreferenceSchemaV1.chatInputWaterGlass,
            ),
            rule(
                NativeThemePreferenceSchemaV1.chatInputWaterGlass,
                NativeThemePreferenceSchemaV1.chatInputLiquidGlass,
            ),
            rule(
                NativeThemePreferenceSchemaV1.cursorUserBubbleLiquidGlass,
                NativeThemePreferenceSchemaV1.cursorUserBubbleWaterGlass,
            ),
            rule(
                NativeThemePreferenceSchemaV1.cursorUserBubbleWaterGlass,
                NativeThemePreferenceSchemaV1.cursorUserBubbleLiquidGlass,
            ),
            rule(
                NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                NativeThemePreferenceSchemaV1.bubbleUserUseImage,
            ),
            rule(
                NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                NativeThemePreferenceSchemaV1.bubbleUserUseImage,
            ),
            rule(
                NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                NativeThemePreferenceSchemaV1.bubbleAiUseImage,
            ),
            rule(
                NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                NativeThemePreferenceSchemaV1.bubbleAiUseImage,
            ),
        )

    private val booleanActivationRulesByName = booleanActivationRules.associateBy { rule -> rule.field.name }

    fun applyBooleanChange(
        values: ThemePreferenceValues,
        field: NativeThemeBooleanField,
        value: Boolean,
    ): ThemePreferenceValues {
        val registeredField = NativeThemePreferenceSchemaV1.requireBooleanField(field.name)
        var updated = values.withBoolean(registeredField, value)
        if (value) {
            booleanActivationRulesByName[registeredField.name]?.disabledFields?.forEach { disabledField ->
                updated = updated.withBoolean(disabledField, false)
            }
        }
        return updated
    }

    private fun rule(
        field: NativeThemeBooleanField,
        vararg disabledFields: NativeThemeBooleanField,
    ) = NativeThemeBooleanActivationRule(field, disabledFields.toList())
}
