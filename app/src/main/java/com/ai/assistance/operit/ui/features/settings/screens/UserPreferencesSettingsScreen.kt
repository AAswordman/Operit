package com.ai.assistance.operit.ui.features.settings.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserProfileDocumentRepository
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { UserProfileDocumentRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var savedMarkdown by remember { mutableStateOf("") }
    var draftMarkdown by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var archiveMarkdown by remember { mutableStateOf<String?>(null) }

    val hasUnsavedChanges = draftMarkdown != savedMarkdown
    val exceedsLimit = draftMarkdown.length > UserProfileDocumentRepository.MAX_CONTENT_CHARS

    LaunchedEffect(repository) {
        try {
            savedMarkdown = repository.load()
            draftMarkdown = savedMarkdown
            archiveMarkdown = repository.readLegacyArchive()
        } catch (error: Exception) {
            errorMessage = error.message
        } finally {
            loading = false
        }
    }

    fun navigateBackSafely() {
        if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
    }

    BackHandler(onBack = ::navigateBackSafely)

    CustomScaffold { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = UserProfileDocumentRepository.USER_FILE_NAME,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.user_md_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.user_md_edit_tab)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.user_md_preview_tab)) }
                )
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 0) {
                OutlinedTextField(
                    value = draftMarkdown,
                    onValueChange = { draftMarkdown = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = { Text(UserProfileDocumentRepository.USER_FILE_NAME) },
                    supportingText = {
                        Text(
                            text =
                                "${draftMarkdown.length} / ${UserProfileDocumentRepository.MAX_CONTENT_CHARS}",
                            color =
                                if (exceedsLimit) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = exceedsLimit
                )
            } else {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                ) {
                    MarkdownTextComposable(
                        text = draftMarkdown,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { showResetDialog = true }) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Text(stringResource(R.string.user_md_reset))
                }
                if (archiveMarkdown != null) {
                    OutlinedButton(onClick = { showArchiveDialog = true }) {
                        Icon(Icons.Default.Archive, contentDescription = null)
                        Text(stringResource(R.string.user_md_legacy_archive))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                repository.save(draftMarkdown)
                                savedMarkdown = draftMarkdown
                                errorMessage = null
                            } catch (error: Exception) {
                                errorMessage = error.message
                            }
                        }
                    },
                    enabled = hasUnsavedChanges && !exceedsLimit
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text(stringResource(R.string.save_action))
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
                        draftMarkdown = UserProfileDocumentRepository.DEFAULT_TEMPLATE
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

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(UserProfileDocumentRepository.LEGACY_ARCHIVE_FILE_NAME) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                ) {
                    MarkdownTextComposable(
                        text = archiveMarkdown.orEmpty(),
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}
