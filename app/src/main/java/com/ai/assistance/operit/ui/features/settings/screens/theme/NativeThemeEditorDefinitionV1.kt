package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.annotation.StringRes
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemeBooleanField
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.NativeThemeStringField
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

internal data class NativeThemeStringOptionDefinition(
    val value: String,
    @StringRes val titleRes: Int,
)

internal data class NativeThemeStringChoiceDefinition(
    val field: NativeThemeStringField,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val options: List<NativeThemeStringOptionDefinition>,
)

internal data class NativeThemeBooleanControlDefinition(
    val field: NativeThemeBooleanField,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val visibleWhenEnabled: NativeThemeBooleanField? = null,
) {
    fun isVisible(values: ThemePreferenceValues): Boolean =
        visibleWhenEnabled?.let { field -> values.requiredBoolean(field) } ?: true
}

internal data class NativeThemeBooleanGroupDefinition(
    @StringRes val titleRes: Int,
    val controls: List<NativeThemeBooleanControlDefinition>,
)

internal object NativeThemeEditorDefinitionV1 {
    val inputStyle =
        NativeThemeStringChoiceDefinition(
            field = NativeThemePreferenceSchemaV1.inputStyle,
            titleRes = R.string.input_style_title,
            descriptionRes = R.string.input_style_desc,
            options =
                listOf(
                    NativeThemeStringOptionDefinition(
                        value = UserPreferencesManager.INPUT_STYLE_CLASSIC,
                        titleRes = R.string.input_style_classic,
                    ),
                    NativeThemeStringOptionDefinition(
                        value = UserPreferencesManager.INPUT_STYLE_AGENT,
                        titleRes = R.string.input_style_agent,
                    ),
                ),
        )

    val inputAppearance =
        NativeThemeBooleanGroupDefinition(
            titleRes = R.string.theme_chat_input_transparent_title,
            controls =
                listOf(
                    NativeThemeBooleanControlDefinition(
                        field = NativeThemePreferenceSchemaV1.chatInputTransparent,
                        titleRes = R.string.theme_chat_input_transparent,
                        descriptionRes = R.string.theme_chat_input_transparent_desc,
                    ),
                    NativeThemeBooleanControlDefinition(
                        field = NativeThemePreferenceSchemaV1.chatInputFloating,
                        titleRes = R.string.theme_chat_input_floating,
                        descriptionRes = R.string.theme_chat_input_floating_desc,
                    ),
                    NativeThemeBooleanControlDefinition(
                        field = NativeThemePreferenceSchemaV1.chatInputLiquidGlass,
                        titleRes = R.string.theme_chat_input_liquid_glass,
                        descriptionRes = R.string.theme_chat_input_liquid_glass_desc,
                        visibleWhenEnabled = NativeThemePreferenceSchemaV1.chatInputTransparent,
                    ),
                    NativeThemeBooleanControlDefinition(
                        field = NativeThemePreferenceSchemaV1.chatInputWaterGlass,
                        titleRes = R.string.theme_chat_input_water_glass,
                        descriptionRes = R.string.theme_chat_input_water_glass_desc,
                        visibleWhenEnabled = NativeThemePreferenceSchemaV1.chatInputTransparent,
                    ),
                ),
        )
}
