package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorDefinitionV1 {
    val colorsAndMode = NativeThemeEditorColorsModeDefinitionV1.section
    val appearanceMode = NativeThemeEditorColorsModeDefinitionV1.appearanceMode
    val palette = NativeThemeEditorColorsModeDefinitionV1.palette
    val typography = NativeThemeEditorTypographyDefinitionV1.section
    val typographyFamily = NativeThemeEditorTypographyDefinitionV1.family
    val background = NativeThemeEditorBackgroundDefinitionV1.section
    val backgroundMedia = NativeThemeEditorBackgroundDefinitionV1.media
    val messageDetails = NativeThemeEditorMessageDetailsDefinitionV1.section
    val messageDetailsReasoning = NativeThemeEditorMessageDetailsDefinitionV1.reasoning
    val messageDetailsIdentity = NativeThemeEditorMessageDetailsDefinitionV1.identity
    val messageDetailsDiagnostics = NativeThemeEditorMessageDetailsDefinitionV1.diagnostics
    val messageDetailsActivity = NativeThemeEditorMessageDetailsDefinitionV1.activity
    val composer = NativeThemeEditorComposerDefinitionV1.section
    val composerStyle = NativeThemeEditorComposerDefinitionV1.style
    val composerAppearance = NativeThemeEditorComposerDefinitionV1.appearance
    val appChrome = NativeThemeEditorAppChromeDefinitionV1.section
    val sections = listOf(colorsAndMode, typography, background, messageDetails, composer, appChrome)

    init {
        validateNativeThemeEditorSectionsV1(sections)
    }
}

internal fun validateNativeThemeEditorSectionsV1(
    sections: List<NativeThemeEditorSectionDefinitionV1>,
) {
    require(sections.isNotEmpty()) { "The native theme editor must define sections." }
    require(sections.all { section -> section.groups.isNotEmpty() }) {
        "Native theme editor sections must define groups."
    }
    require(sections.map { section -> section.id.value }.distinct().size == sections.size) {
        "Native theme editor section IDs must be unique."
    }
    val groups = sections.flatMap { section -> section.groups }
    require(groups.all { group -> group.items.isNotEmpty() }) {
        "Native theme editor groups must define items."
    }
    require(groups.map { group -> group.id.value }.distinct().size == groups.size) {
        "Native theme editor group IDs must be globally unique."
    }
    val items = groups.flatMap { group -> group.items }
    require(items.map { item -> item.id.value }.distinct().size == items.size) {
        "Native theme editor item IDs must be globally unique."
    }
    items.filterIsInstance<NativeThemeStringChoiceDefinitionV1>().forEach { choice ->
        require(choice.options.isNotEmpty()) { "Choice ${choice.id.value} must define options." }
        require(choice.options.map { option -> option.value }.distinct().size == choice.options.size) {
            "Choice ${choice.id.value} option values must be unique."
        }
        val defaultValue = requireNotNull(choice.field.defaultValue) {
            "Choice ${choice.id.value} must bind a field with a default value."
        }
        require(choice.options.any { option -> option.value == defaultValue }) {
            "Choice ${choice.id.value} must include its field default value."
        }
    }
    items.filterIsInstance<NativeThemeFloatControlDefinitionV1>().forEach { control ->
        require(control.minimum < control.maximum) {
            "Slider ${control.id.value} must define an increasing range."
        }
        require(control.steps >= 0) { "Slider ${control.id.value} steps cannot be negative." }
        val defaultValue = requireNotNull(control.field.defaultValue) {
            "Slider ${control.id.value} must bind a field with a default value."
        }
        require(defaultValue in control.minimum..control.maximum) {
            "Slider ${control.id.value} must include its field default value."
        }
    }
    items.filterIsInstance<NativeThemeAssetControlDefinitionV1>().forEach { control ->
        require(control.field.storageRole == com.ai.assistance.operit.data.preferences.NativeThemePreferenceStorageRole.VISUAL) {
            "Asset ${control.id.value} must bind a visual preference field."
        }
    }
    val colorFields =
        items.filterIsInstance<NativeThemeColorControlDefinitionV1>().map { item -> item.target.field }
    require(
        colorFields.size == colorFields.toSet().size &&
            colorFields.all { field -> field in NativeThemePreferenceSchemaV1.intFields }
    ) {
        "Native theme color controls must bind unique V1 integer fields."
    }
}
