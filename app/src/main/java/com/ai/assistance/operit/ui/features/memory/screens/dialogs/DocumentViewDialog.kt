package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.DocumentChunk

/**
 * Height bounds for a single document block editor.
 *
 * Document blocks are imported by splitting on blank lines, so a document without blank lines can
 * arrive as one very large block. A LazyColumn measures its items with an unbounded main-axis
 * constraint; an OutlinedTextField that is allowed to grow to its full text height can then exceed
 * the maximum height representable by [androidx.compose.ui.unit.Constraints] (262143 px) and crash
 * inside OutlinedTextFieldMeasurePolicy. Keep every block editor inside a bounded viewport and let
 * the text field scroll its own content instead.
 */
private val ChunkEditorMinHeight = 120.dp
private val ChunkEditorMaxHeight = 280.dp

@Composable
fun DocumentViewDialog(
    memoryTitle: String,
    onTitleChange: (String) -> Unit,
    chunks: List<DocumentChunk>,
    chunkStates: Map<Long, String>,
    onChunkChange: (Long, String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    folderPath: String = "" // 添加文件夹路径参数
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        modifier = Modifier.fillMaxHeight(0.85f),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = memoryTitle,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.memory_document_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true
                )
                if (folderPath.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.memory_folder_label)}: $folderPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text(stringResource(R.string.memory_search_in_document)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            onPerformSearch()
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onPerformSearch()
                            keyboardController?.hide()
                        }
                    )
                )
                if (chunks.isEmpty() && searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.document_no_content_found))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("document_chunk_list"),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chunks, key = { it.id }) { chunk ->
                            val content = chunkStates[chunk.id] ?: ""
                            OutlinedTextField(
                                value = content,
                                onValueChange = { newContent -> onChunkChange(chunk.id, newContent) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = ChunkEditorMinHeight,
                                        max = ChunkEditorMaxHeight
                                    )
                                    .testTag("document_chunk_editor_${chunk.id}"),
                                label = { Text(stringResource(R.string.document_block_label, chunk.chunkIndex + 1)) }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
             OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_close))
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
            ) {
                Button(onClick = onSave) {
                    Text(stringResource(R.string.memory_save_all))
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.memory_delete_document))
                }
            }
        }
    )
} 