package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange

/** Text, selection and the inner viewport belong to the field, including while its view is hidden. */
@Stable
internal class PromptEditorState(
    val ownerId: String,
    val fieldId: String,
    val textFieldState: TextFieldState,
    val scrollState: ScrollState,
) {
    val text: String
        get() = textFieldState.text.toString()

    fun replaceText(text: String) {
        textFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    fun applyDraft(draft: PromptEditorState) {
        val text = draft.text
        val selection = draft.textFieldState.selection
        textFieldState.edit {
            replace(0, length, text)
            this.selection = selection
        }
    }
}

@Composable
internal fun rememberPromptEditorState(
    ownerId: String,
    fieldId: String,
    initialText: String,
    initialSelection: TextRange = TextRange.Zero,
): PromptEditorState {
    val textFieldState =
        rememberSaveable(ownerId, fieldId, saver = TextFieldState.Saver) {
            TextFieldState(initialText, initialSelection)
        }
    val scrollState =
        rememberSaveable(ownerId, fieldId, saver = ScrollState.Saver) { ScrollState(0) }
    return remember(ownerId, fieldId, textFieldState, scrollState) {
        PromptEditorState(ownerId, fieldId, textFieldState, scrollState)
    }
}
