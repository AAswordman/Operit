package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorComposerDefinitionV1 {
    val style =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("composer.style"),
            title = NativeThemeEditorTextKey.COMPOSER_STYLE,
            description = NativeThemeEditorTextKey.COMPOSER_STYLE_DESCRIPTION,
            items =
                listOf(
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("composer.style.mode"),
                        title = NativeThemeEditorTextKey.COMPOSER_STYLE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.inputStyle,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.INPUT_STYLE_CLASSIC,
                                    title = NativeThemeEditorTextKey.COMPOSER_STYLE_CLASSIC,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.INPUT_STYLE_AGENT,
                                    title = NativeThemeEditorTextKey.COMPOSER_STYLE_AGENT,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        displayTitle = false,
                    )
                ),
        )

    val appearance =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("composer.appearance"),
            title = NativeThemeEditorTextKey.COMPOSER_APPEARANCE,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("composer.appearance.transparent"),
                        title = NativeThemeEditorTextKey.COMPOSER_TRANSPARENT,
                        description = NativeThemeEditorTextKey.COMPOSER_TRANSPARENT_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.chatInputTransparent,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("composer.appearance.floating"),
                        title = NativeThemeEditorTextKey.COMPOSER_FLOATING,
                        description = NativeThemeEditorTextKey.COMPOSER_FLOATING_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.chatInputFloating,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("composer.appearance.liquid_glass"),
                        title = NativeThemeEditorTextKey.COMPOSER_LIQUID_GLASS,
                        description = NativeThemeEditorTextKey.COMPOSER_LIQUID_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.chatInputLiquidGlass,
                        visibleWhen =
                            NativeThemeEditorPredicateV1.BooleanEquals(
                                field = NativeThemePreferenceSchemaV1.chatInputTransparent,
                                expected = true,
                            ),
                        advanced = true,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("composer.appearance.water_glass"),
                        title = NativeThemeEditorTextKey.COMPOSER_WATER_GLASS,
                        description = NativeThemeEditorTextKey.COMPOSER_WATER_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.chatInputWaterGlass,
                        visibleWhen =
                            NativeThemeEditorPredicateV1.BooleanEquals(
                                field = NativeThemePreferenceSchemaV1.chatInputTransparent,
                                expected = true,
                            ),
                        advanced = true,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("composer"),
            title = NativeThemeEditorTextKey.COMPOSER,
            groups = listOf(style, appearance),
        )
}
