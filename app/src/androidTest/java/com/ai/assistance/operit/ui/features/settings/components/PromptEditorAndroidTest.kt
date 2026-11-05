package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptEditorAndroidTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fields =
        listOf(
            "characterSetting",
            "openingStatement",
            "otherContentChat",
            "otherContentVoice",
            "advancedCustomPrompt",
            "marks",
        )

    private fun longText(field: String): String =
        (0 until 300).joinToString("\n") { line -> "$field line $line" }

    @Composable
    private fun rememberEditors(ownerId: String): Map<String, PromptEditorState> {
        val editors = linkedMapOf<String, PromptEditorState>()
        for (field in fields) {
            key(field) {
                editors[field] = rememberPromptEditorState(ownerId, field, longText(field))
            }
        }
        return editors
    }

    @Test
    fun allPromptFields_keepVisibleTextAndSelection_whenTypingResizingAndHiding() {
        val activeField = mutableStateOf(fields.first())
        val viewportHeight = mutableStateOf(160.dp)
        val showEditor = mutableStateOf(true)
        lateinit var editors: Map<String, PromptEditorState>
        lateinit var focusManager: FocusManager
        lateinit var formScrollState: ScrollState

        composeTestRule.setContent {
            MaterialTheme {
                val currentEditors = rememberEditors("character-a")
                focusManager = LocalFocusManager.current
                val currentFormScrollState = rememberScrollState()
                SideEffect { editors = currentEditors }
                SideEffect { formScrollState = currentFormScrollState }
                Column(
                    Modifier.fillMaxWidth().height(320.dp).verticalScroll(currentFormScrollState)
                ) {
                    Spacer(Modifier.height(240.dp))
                    if (showEditor.value) {
                        CompactTextFieldWithExpand(
                            editor = currentEditors.getValue(activeField.value),
                            minLines = 2,
                            maxLines = 8,
                            onExpandClick = {},
                            modifier =
                                Modifier.height(viewportHeight.value).testTag("prompt-editor"),
                        )
                    }
                    Spacer(Modifier.height(300.dp))
                }
            }
        }
        composeTestRule.waitForIdle()

        for (field in fields) {
            composeTestRule.runOnIdle {
                focusManager.clearFocus(force = true)
                activeField.value = field
                viewportHeight.value = 160.dp
            }
            composeTestRule.waitForIdle()
            val editorNode = composeTestRule.onNodeWithTag("prompt-editor")
            editorNode.performScrollTo()
            repeat(3) { editorNode.performTouchInput { swipeUp(durationMillis = 400) } }
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle {
                assertTrue("$field should have scrolled inside its editor", editors.getValue(field).scrollState.value > 0)
            }

            // Tap the currently visible text after scrolling, rather than setting selection in code.
            editorNode.performTouchInput { click(center) }
            composeTestRule.waitForIdle()
            var selectionBeforeInput = TextRange.Zero
            var textBeforeInput = ""
            composeTestRule.runOnIdle {
                val state = editors.getValue(field)
                selectionBeforeInput = state.textFieldState.selection
                textBeforeInput = state.text
                assertTrue("$field must edit the visible text, not the first line", selectionBeforeInput.min > 0)
            }

            editorNode.performTextInput("[edited]")
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle {
                val state = editors.getValue(field)
                assertEquals(
                    textBeforeInput.replaceRange(selectionBeforeInput.min, selectionBeforeInput.max, "[edited]"),
                    state.text,
                )
                assertTrue("$field must stay scrolled after input", state.scrollState.value > 0)
                assertTrue("$field must not reset its parent form", formScrollState.value > 0)
                viewportHeight.value = 110.dp
            }
            composeTestRule.waitForIdle()

            var savedSelection = TextRange.Zero
            var savedOffset = 0
            composeTestRule.runOnIdle {
                val state = editors.getValue(field)
                assertTrue("$field must stay scrolled after its viewport shrinks", state.scrollState.value > 0)
                assertTrue("$field resizing must not reset its parent form", formScrollState.value > 0)
                focusManager.clearFocus(force = true)
                savedSelection = state.textFieldState.selection
                savedOffset = state.scrollState.value
                showEditor.value = false
            }
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle { showEditor.value = true }
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle {
                val state = editors.getValue(field)
                assertEquals("$field selection must survive hiding", savedSelection, state.textFieldState.selection)
                assertEquals("$field viewport must survive hiding", savedOffset, state.scrollState.value)
            }
        }
    }

    @Test
    fun allPromptFields_fullScreenSaveWritesTextAndSelection_toTheirOwnSource() {
        val activeField = mutableStateOf(fields.first())
        val showDialog = mutableStateOf(false)
        lateinit var editors: Map<String, PromptEditorState>
        var saveCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                val currentEditors = rememberEditors("character-a")
                SideEffect { editors = currentEditors }
                if (showDialog.value) {
                    FullScreenEditDialog(
                        title = activeField.value,
                        editor = currentEditors.getValue(activeField.value),
                        onDismiss = { showDialog.value = false },
                        onSave = {
                            saveCount++
                            showDialog.value = false
                        },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        for (field in fields) {
            val original = longText(field)
            val insertionOffset = original.indexOf("line 120")
            composeTestRule.runOnIdle {
                activeField.value = field
                editors.getValue(field).textFieldState.edit { selection = TextRange(insertionOffset) }
                showDialog.value = true
            }
            composeTestRule.waitForIdle()
            val draft = composeTestRule.onNode(hasSetTextAction())
            draft.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            draft.performTextInput("[full-screen]")
            composeTestRule.runOnIdle {
                assertEquals("Editing a draft must not change $field before save", original, editors.getValue(field).text)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle {
                val state = editors.getValue(field)
                assertEquals(original.replaceRange(insertionOffset, insertionOffset, "[full-screen]"), state.text)
                assertEquals(TextRange(insertionOffset + "[full-screen]".length), state.textFieldState.selection)
            }
        }
        composeTestRule.runOnIdle { assertEquals(fields.size, saveCount) }
    }

    @Test
    fun fullScreenCancelDiscardsDraft_andReopeningUsesTheSourceAgain() {
        val showDialog = mutableStateOf(false)
        lateinit var source: PromptEditorState
        val original = longText("marks")
        val originalSelection = TextRange(original.indexOf("line 80"))
        var saveCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                val currentSource = rememberPromptEditorState("character-a", "marks", original, originalSelection)
                SideEffect { source = currentSource }
                if (showDialog.value) {
                    FullScreenEditDialog(
                        title = "Marks",
                        editor = currentSource,
                        onDismiss = { showDialog.value = false },
                        onSave = {
                            saveCount++
                            showDialog.value = false
                        },
                    )
                }
            }
        }
        composeTestRule.runOnIdle { showDialog.value = true }
        composeTestRule.waitForIdle()
        val draft = composeTestRule.onNode(hasSetTextAction())
        draft.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        draft.performTextInput("[discarded]")
        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(original, source.text)
            assertEquals(originalSelection, source.textFieldState.selection)
            assertEquals(0, saveCount)
            showDialog.value = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(original, source.text)
            assertEquals(originalSelection, source.textFieldState.selection)
            assertEquals(1, saveCount)
        }
    }

    @Test
    fun changingCharacterDoesNotReuseAnotherCharactersDraftOrSelection() {
        val ownerId = mutableStateOf("character-a")
        lateinit var source: PromptEditorState

        composeTestRule.setContent {
            MaterialTheme {
                val currentSource =
                    rememberPromptEditorState(ownerId.value, "characterSetting", "${ownerId.value} original")
                SideEffect { source = currentSource }
                CompactTextFieldWithExpand(editor = currentSource, onExpandClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performClick().performTextInput("[character-a edit]")
        composeTestRule.runOnIdle { ownerId.value = "character-b" }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals("character-b original", source.text)
            assertEquals(TextRange.Zero, source.textFieldState.selection)
        }
    }
}
