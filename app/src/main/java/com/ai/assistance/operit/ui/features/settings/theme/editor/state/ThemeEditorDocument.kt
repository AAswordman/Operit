package com.ai.assistance.operit.ui.features.settings.theme.editor.state

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal enum class ThemeEditorSaveMode {
    REPLACE,
    RESET_VISUAL,
}

internal data class ThemeEditorDocument(
    val baseline: ThemePreferenceValues,
    val draft: ThemePreferenceValues,
    val saveMode: ThemeEditorSaveMode = ThemeEditorSaveMode.REPLACE,
    val savingValues: ThemePreferenceValues? = null,
) {
    val hasUnsavedChanges: Boolean
        get() = saveMode == ThemeEditorSaveMode.RESET_VISUAL || draft != baseline
}

internal data class ThemeEditorSaveRequest(
    val values: ThemePreferenceValues,
    val saveMode: ThemeEditorSaveMode,
)

internal sealed interface ThemeEditorDocumentAction {
    data class Edit(val values: ThemePreferenceValues) : ThemeEditorDocumentAction

    data class ResetVisual(val values: ThemePreferenceValues) : ThemeEditorDocumentAction

    data object Discard : ThemeEditorDocumentAction

    data class BeginSave(val values: ThemePreferenceValues) : ThemeEditorDocumentAction

    data class SaveSucceeded(val values: ThemePreferenceValues) : ThemeEditorDocumentAction

    data object SaveFailed : ThemeEditorDocumentAction
}

internal fun reduceThemeEditorDocument(
    document: ThemeEditorDocument,
    action: ThemeEditorDocumentAction,
): ThemeEditorDocument =
    when (action) {
        is ThemeEditorDocumentAction.Edit -> {
            if (action.values == document.draft) {
                document
            } else {
                document.copy(
                    draft = action.values,
                    saveMode =
                        if (
                            document.saveMode == ThemeEditorSaveMode.RESET_VISUAL &&
                                NativeThemePreferenceSchemaV1.haveSameVisualValues(
                                    document.draft,
                                    action.values,
                                )
                        ) {
                            ThemeEditorSaveMode.RESET_VISUAL
                        } else {
                            ThemeEditorSaveMode.REPLACE
                        },
                )
            }
        }

        is ThemeEditorDocumentAction.ResetVisual ->
            document.copy(
                draft = action.values,
                saveMode = ThemeEditorSaveMode.RESET_VISUAL,
            )

        ThemeEditorDocumentAction.Discard ->
            document.copy(
                draft = document.baseline,
                saveMode = ThemeEditorSaveMode.REPLACE,
            )

        is ThemeEditorDocumentAction.BeginSave ->
            document.copy(savingValues = action.values)

        is ThemeEditorDocumentAction.SaveSucceeded ->
            document.copy(
                baseline = action.values,
                saveMode =
                    if (document.draft == action.values) {
                        ThemeEditorSaveMode.REPLACE
                    } else {
                        document.saveMode
                    },
                savingValues = null,
            )

        ThemeEditorDocumentAction.SaveFailed -> document.copy(savingValues = null)
    }
