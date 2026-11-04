package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.DocumentChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentViewDialogAndroidTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun longSingleBlock_rendersBoundedEditor_andSavesEdits() {
        val chunkId = 7L
        val longContent =
            buildString {
                repeat(15_000) { line ->
                    append("line ").append(line).append('\n')
                }
            }
        val chunkStates = mutableStateMapOf(chunkId to longContent)
        var saveInvocations = 0

        composeTestRule.setContent {
            // A small density with a large font scale produces text much taller than the 262143 px
            // limit of Compose Constraints without printing a multi-megabyte string.
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    DocumentViewDialog(
                        memoryTitle = "Long document",
                        onTitleChange = {},
                        chunks =
                            listOf(
                                DocumentChunk(
                                    id = chunkId,
                                    content = longContent,
                                    chunkIndex = 0
                                )
                            ),
                        chunkStates = chunkStates,
                        onChunkChange = { id, content -> chunkStates[id] = content },
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onPerformSearch = {},
                        onDismiss = {},
                        onSave = { saveInvocations++ },
                        onDelete = {},
                        folderPath = ""
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val editor = composeTestRule.onNodeWithTag("document_chunk_editor_$chunkId")
        editor.assertIsDisplayed()
        assertTrue(
            "A long block editor must stay inside its bounded viewport",
            editor.getUnclippedBoundsInRoot().height <= 280.dp
        )

        editor.performTextReplacement("edited")
        composeTestRule.runOnIdle {
            assertEquals("edited", chunkStates[chunkId])
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.memory_save_all))
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, saveInvocations)
        }
    }

    @Test
    fun shortBlocks_scrollAndStayEditable() {
        val chunks =
            (1..12).map { index ->
                DocumentChunk(
                    id = index.toLong(),
                    content = "Block $index",
                    chunkIndex = index - 1
                )
            }
        val chunkStates = mutableStateMapOf<Long, String>().apply {
            chunks.forEach { put(it.id, it.content) }
        }
        var saveInvocations = 0

        composeTestRule.setContent {
            MaterialTheme {
                DocumentViewDialog(
                    memoryTitle = "Short document",
                    onTitleChange = {},
                    chunks = chunks,
                    chunkStates = chunkStates,
                    onChunkChange = { id, content -> chunkStates[id] = content },
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onPerformSearch = {},
                    onDismiss = {},
                    onSave = { saveInvocations++ },
                    onDelete = {},
                    folderPath = ""
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("document_chunk_list").performScrollToIndex(11)
        val lastEditor = composeTestRule.onNodeWithTag("document_chunk_editor_12")
        lastEditor.assertIsDisplayed()

        lastEditor.performTextReplacement("Block 12 edited")
        composeTestRule.runOnIdle {
            assertEquals("Block 12 edited", chunkStates[12L])
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.memory_save_all))
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, saveInvocations)
        }
    }
}