package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorColorsModeDefinitionV1 {
    val defaultPrimaryColor = 0xFFFF00FF.toInt()
    val defaultSecondaryColor = 0xFF0000FF.toInt()

    private val customColorsEnabled =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.useCustomColors,
            expected = true,
        )

    val appearanceMode =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("colors_mode.appearance"),
            title = NativeThemeEditorTextKey.APPEARANCE_MODE,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("colors_mode.appearance.follow_system"),
                        title = NativeThemeEditorTextKey.FOLLOW_SYSTEM,
                        description = NativeThemeEditorTextKey.FOLLOW_SYSTEM_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useSystemTheme,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("colors_mode.appearance.mode"),
                        title = NativeThemeEditorTextKey.APPEARANCE_MODE_SELECT,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.themeMode,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.THEME_MODE_LIGHT,
                                    title = NativeThemeEditorTextKey.APPEARANCE_MODE_LIGHT,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.THEME_MODE_DARK,
                                    title = NativeThemeEditorTextKey.APPEARANCE_MODE_DARK,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        visibleWhen =
                            NativeThemeEditorPredicateV1.BooleanEquals(
                                field = NativeThemePreferenceSchemaV1.useSystemTheme,
                                expected = false,
                            ),
                    ),
                ),
        )

    val palette =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("colors_mode.palette"),
            title = NativeThemeEditorTextKey.PALETTE,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("colors_mode.palette.custom"),
                        title = NativeThemeEditorTextKey.USE_CUSTOM_COLORS,
                        description = NativeThemeEditorTextKey.USE_CUSTOM_COLORS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useCustomColors,
                        enabledIntValues =
                            listOf(
                                NativeThemeIntValueV1(
                                    NativeThemePreferenceSchemaV1.customPrimaryColor,
                                    defaultPrimaryColor,
                                ),
                                NativeThemeIntValueV1(
                                    NativeThemePreferenceSchemaV1.customSecondaryColor,
                                    defaultSecondaryColor,
                                ),
                            ),
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("colors_mode.palette.primary"),
                        title = NativeThemeEditorTextKey.PRIMARY_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.PRIMARY,
                        displayDefault = defaultPrimaryColor,
                        visibleWhen = customColorsEnabled,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("colors_mode.palette.secondary"),
                        title = NativeThemeEditorTextKey.SECONDARY_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.SECONDARY,
                        displayDefault = defaultSecondaryColor,
                        visibleWhen = customColorsEnabled,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("colors_mode.palette.foreground"),
                        title = NativeThemeEditorTextKey.ON_COLOR_MODE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.onColorMode,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.ON_COLOR_MODE_AUTO,
                                    NativeThemeEditorTextKey.ON_COLOR_AUTO,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.ON_COLOR_MODE_LIGHT,
                                    NativeThemeEditorTextKey.ON_COLOR_LIGHT,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.ON_COLOR_MODE_DARK,
                                    NativeThemeEditorTextKey.ON_COLOR_DARK,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        visibleWhen = customColorsEnabled,
                        advanced = true,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("colors_mode"),
            title = NativeThemeEditorTextKey.COLORS_AND_MODE,
            groups = listOf(appearanceMode, palette),
        )
}
