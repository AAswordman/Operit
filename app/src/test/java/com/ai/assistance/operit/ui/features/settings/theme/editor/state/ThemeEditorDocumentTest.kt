package com.ai.assistance.operit.ui.features.settings.theme.editor.state

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEditorDocumentTest {
    @Test
    fun initialDocumentIsClean() {
        val values = ThemePreferenceValues.defaultVisual()
        val document = ThemeEditorDocument(baseline = values, draft = values)

        assertFalse(document.hasUnsavedChanges)
        assertEquals(ThemeEditorSaveMode.REPLACE, document.saveMode)
        assertNull(document.savingValues)
    }

    @Test
    fun resetRemainsDirtyEvenWhenDefaultsEqualTheBaseline() {
        val values = ThemePreferenceValues.defaultVisual()
        val reset =
            reduceThemeEditorDocument(
                ThemeEditorDocument(baseline = values, draft = values),
                ThemeEditorDocumentAction.ResetVisual(values),
            )

        assertTrue(reset.hasUnsavedChanges)
        assertEquals(ThemeEditorSaveMode.RESET_VISUAL, reset.saveMode)

        val unchangedEdit =
            reduceThemeEditorDocument(reset, ThemeEditorDocumentAction.Edit(reset.draft))
        assertEquals(reset, unchangedEdit)
    }

    @Test
    fun editingAfterResetChangesTheSaveModeToReplace() {
        val baseline = ThemePreferenceValues.defaultVisual()
        val reset =
            reduceThemeEditorDocument(
                ThemeEditorDocument(baseline = baseline, draft = baseline),
                ThemeEditorDocumentAction.ResetVisual(baseline),
            )
        val editedValues =
            reset.draft.withBoolean(NativeThemePreferenceSchemaV1.chatInputFloating, true)
        val edited =
            reduceThemeEditorDocument(reset, ThemeEditorDocumentAction.Edit(editedValues))

        assertEquals(ThemeEditorSaveMode.REPLACE, edited.saveMode)
        assertTrue(edited.hasUnsavedChanges)
    }

    @Test
    fun metadataEditAfterResetKeepsTheVisualResetMode() {
        val baseline = ThemePreferenceValues.defaultVisual()
        val reset =
            reduceThemeEditorDocument(
                ThemeEditorDocument(baseline = baseline, draft = baseline),
                ThemeEditorDocumentAction.ResetVisual(baseline),
            )
        val metadataEdit =
            reset.draft.withString(
                NativeThemePreferenceSchemaV1.customChatTitle,
                "Renamed target",
            )
        val edited =
            reduceThemeEditorDocument(reset, ThemeEditorDocumentAction.Edit(metadataEdit))

        assertEquals(ThemeEditorSaveMode.RESET_VISUAL, edited.saveMode)
        assertTrue(edited.hasUnsavedChanges)
    }

    @Test
    fun metadataEditDuringResetSaveKeepsResetModeAfterSuccess() {
        val baseline = ThemePreferenceValues.defaultVisual()
        val reset =
            reduceThemeEditorDocument(
                ThemeEditorDocument(baseline = baseline, draft = baseline),
                ThemeEditorDocumentAction.ResetVisual(baseline),
            )
        val saving =
            reduceThemeEditorDocument(
                reset,
                ThemeEditorDocumentAction.BeginSave(reset.draft),
            )
        val metadataEdit =
            saving.draft.withString(
                NativeThemePreferenceSchemaV1.customChatTitle,
                "Renamed during save",
            )
        val edited =
            reduceThemeEditorDocument(saving, ThemeEditorDocumentAction.Edit(metadataEdit))
        val saved =
            reduceThemeEditorDocument(
                edited,
                ThemeEditorDocumentAction.SaveSucceeded(saving.draft),
            )

        assertEquals(saving.draft, saved.baseline)
        assertEquals(metadataEdit, saved.draft)
        assertEquals(ThemeEditorSaveMode.RESET_VISUAL, saved.saveMode)
        assertTrue(saved.hasUnsavedChanges)
    }

    @Test
    fun discardRestoresTheBaselineAndClearsResetMode() {
        val baseline = ThemePreferenceValues.defaultVisual()
        val editedValues =
            baseline.withBoolean(NativeThemePreferenceSchemaV1.chatInputFloating, true)
        val document =
            ThemeEditorDocument(
                baseline = baseline,
                draft = editedValues,
                saveMode = ThemeEditorSaveMode.RESET_VISUAL,
            )
        val discarded =
            reduceThemeEditorDocument(document, ThemeEditorDocumentAction.Discard)

        assertEquals(baseline, discarded.draft)
        assertEquals(ThemeEditorSaveMode.REPLACE, discarded.saveMode)
        assertFalse(discarded.hasUnsavedChanges)
    }

    @Test
    fun saveSuccessUsesTheFrozenPayloadAsBaseline() {
        val baseline = ThemePreferenceValues.defaultVisual()
        val savingValues =
            baseline.withBoolean(NativeThemePreferenceSchemaV1.chatInputFloating, true)
        val laterValues =
            savingValues.withBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent, true)
        val saving =
            reduceThemeEditorDocument(
                ThemeEditorDocument(baseline = baseline, draft = savingValues),
                ThemeEditorDocumentAction.BeginSave(savingValues),
            )
        val editedDuringSave =
            reduceThemeEditorDocument(saving, ThemeEditorDocumentAction.Edit(laterValues))
        assertEquals(savingValues, editedDuringSave.savingValues)
        val saved =
            reduceThemeEditorDocument(
                editedDuringSave,
                ThemeEditorDocumentAction.SaveSucceeded(savingValues),
            )

        assertEquals(savingValues, saved.baseline)
        assertEquals(laterValues, saved.draft)
        assertTrue(saved.hasUnsavedChanges)
        assertNull(saved.savingValues)
    }

    @Test
    fun saveFailureKeepsTheDraftAndResetIntent() {
        val baseline = ThemePreferenceValues.defaultVisual()
        val saving =
            reduceThemeEditorDocument(
                ThemeEditorDocument(
                    baseline = baseline,
                    draft = baseline,
                    saveMode = ThemeEditorSaveMode.RESET_VISUAL,
                ),
                ThemeEditorDocumentAction.BeginSave(baseline),
            )
        val failed =
            reduceThemeEditorDocument(saving, ThemeEditorDocumentAction.SaveFailed)

        assertEquals(baseline, failed.draft)
        assertEquals(ThemeEditorSaveMode.RESET_VISUAL, failed.saveMode)
        assertTrue(failed.hasUnsavedChanges)
        assertNull(failed.savingValues)
    }
}
