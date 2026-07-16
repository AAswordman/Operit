package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserProfileDocumentRepository
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.settings.components.rememberMarkdownSyntaxOutputTransformation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { UserProfileDocumentRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State-based input commits a touch selection before focus-driven scrolling. The value-based
    // field can instead bring the stale cursor at the document start back into view.
    val draftEditorState = rememberTextFieldState()
    val editorScrollState = rememberScrollState()
    val markdownSyntaxOutputTransformation = rememberMarkdownSyntaxOutputTransformation()
    var savedMarkdown by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var archiveMarkdown by remember { mutableStateOf<String?>(null) }
    var archiveSheetMarkdown by remember { mutableStateOf<String?>(null) }

    val draftMarkdown = draftEditorState.text.toString()
    val hasUnsavedChanges = draftMarkdown != savedMarkdown
    val exceedsLimit = draftMarkdown.length > UserProfileDocumentRepository.MAX_CONTENT_CHARS

    LaunchedEffect(repository) {
        try {
            val loadedMarkdown = repository.load()
            savedMarkdown = loadedMarkdown
            draftEditorState.edit {
                replace(0, length, loadedMarkdown)
                selection = TextRange(0)
            }
            archiveMarkdown = repository.readLegacyArchive()
        } catch (error: Exception) {
            snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
        } finally {
            loading = false
        }
    }

    fun navigateBackSafely() {
        if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
    }

    BackHandler(onBack = ::navigateBackSafely)

    CustomScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = 840.dp)
                        .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.user_md_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text(stringResource(R.string.user_md_edit_tab)) }
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text(stringResource(R.string.user_md_preview_tab)) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (hasUnsavedChanges) {
                            Text(
                                text = stringResource(R.string.workspace_unsaved),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    try {
                                        repository.save(draftMarkdown)
                                        savedMarkdown = draftMarkdown
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.save_successful)
                                        )
                                    } catch (error: Exception) {
                                        snackbarHostState.showSnackbar(
                                            error.message ?: error.javaClass.simpleName
                                        )
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = hasUnsavedChanges && !exceedsLimit && !saving
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_action))
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    if (loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                if (selectedTab == 0) {
                                    BasicTextField(
                                        state = draftEditorState,
                                        scrollState = editorScrollState,
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        textStyle =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                        outputTransformation = markdownSyntaxOutputTransformation,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorator = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                if (draftMarkdown.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.user_md_editor_placeholder),
                                                        style =
                                                            MaterialTheme.typography.bodyMedium.copy(
                                                                fontFamily = FontFamily.Monospace
                                                            ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                } else if (draftMarkdown.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.user_md_preview_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                                    )
                                } else {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp)
                                    ) {
                                        MarkdownTextComposable(
                                            text = draftMarkdown,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text =
                                        "${draftMarkdown.length} / ${UserProfileDocumentRepository.MAX_CONTENT_CHARS}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (exceedsLimit) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.more)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.user_md_reset)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Restore, contentDescription = null)
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                showResetDialog = true
                                            }
                                        )
                                        archiveMarkdown?.let { archive ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.user_md_legacy_archive))
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Archive, contentDescription = null)
                                                },
                                                onClick = {
                                                    showMoreMenu = false
                                                    archiveSheetMarkdown = archive
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.user_md_unsaved_title)) },
            text = { Text(stringResource(R.string.user_md_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.user_md_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.user_md_reset_title)) },
            text = { Text(stringResource(R.string.user_md_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftEditorState.edit {
                            replace(0, length, UserProfileDocumentRepository.DEFAULT_TEMPLATE)
                            selection = TextRange(0)
                        }
                        selectedTab = 0
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.user_md_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    archiveSheetMarkdown?.let { archive ->
        val clipboardManager = LocalClipboardManager.current
        val archiveScrollState = rememberScrollState()
        ModalBottomSheet(onDismissRequest = { archiveSheetMarkdown = null }) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = UserProfileDocumentRepository.LEGACY_ARCHIVE_FILE_NAME,
                    style = MaterialTheme.typography.titleLarge
                )
                SelectionContainer(
                    modifier =
                        Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(archiveScrollState)
                ) {
                    Text(
                        text = archive,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(archive))
                            Toast.makeText(
                                context,
                                context.getString(R.string.copied_to_clipboard),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.copy_content))
                    }
                }
            }
        }
    }
}
