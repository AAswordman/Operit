package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorTypographyDefinitionV1 {
    private val customFontEnabled =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.useCustomFont,
            expected = true,
        )
    private val systemFontSelected =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                customFontEnabled,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.fontType,
                    expected = NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                ),
            )
        )
    private val fontFileSelected =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                customFontEnabled,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.fontType,
                    expected = NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                ),
            )
        )

    val family =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("typography.family"),
            title = NativeThemeEditorTextKey.FONT_FAMILY,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("typography.family.custom"),
                        title = NativeThemeEditorTextKey.USE_CUSTOM_FONT,
                        description = NativeThemeEditorTextKey.USE_CUSTOM_FONT_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useCustomFont,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("typography.family.source"),
                        title = NativeThemeEditorTextKey.FONT_SOURCE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.fontType,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                                    NativeThemeEditorTextKey.FONT_SOURCE_SYSTEM,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                                    NativeThemeEditorTextKey.FONT_SOURCE_FILE,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        visibleWhen = customFontEnabled,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("typography.family.system_font"),
                        title = NativeThemeEditorTextKey.SYSTEM_FONT_NAME,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.systemFontName,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_DEFAULT,
                                    NativeThemeEditorTextKey.SYSTEM_FONT_DEFAULT,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_SERIF,
                                    NativeThemeEditorTextKey.SYSTEM_FONT_SERIF,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_SANS_SERIF,
                                    NativeThemeEditorTextKey.SYSTEM_FONT_SANS_SERIF,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_MONOSPACE,
                                    NativeThemeEditorTextKey.SYSTEM_FONT_MONOSPACE,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_CURSIVE,
                                    NativeThemeEditorTextKey.SYSTEM_FONT_CURSIVE,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.RADIO,
                        visibleWhen = systemFontSelected,
                    ),
                    NativeThemeAssetControlDefinitionV1(
                        id = NativeThemeEditorItemId("typography.family.file"),
                        title = NativeThemeEditorTextKey.FONT_FILE,
                        description = NativeThemeEditorTextKey.FONT_FILE_DESCRIPTION,
                        action = NativeThemeAssetActionV1.APP_FONT,
                        selectLabel = NativeThemeEditorTextKey.SELECT_FONT_FILE,
                        clearLabel = NativeThemeEditorTextKey.CLEAR_FONT_FILE,
                        currentValueLabel = NativeThemeEditorTextKey.CURRENT_FONT_FILE,
                        visibleWhen = fontFileSelected,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("typography.scale"),
                        title = NativeThemeEditorTextKey.FONT_SCALE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.fontScale,
                        minimum = 0.8f,
                        maximum = 1.5f,
                        steps = 6,
                        format = NativeThemeFloatFormatV1.DECIMAL_UP_TO_TWO,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("typography"),
            title = NativeThemeEditorTextKey.TYPOGRAPHY,
            groups = listOf(family),
        )
}
