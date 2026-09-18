package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.WorkspaceMediaKind
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.data.storage.formatStorageTimestamp

@Composable
fun WorkspaceMediaStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { WorkspaceMediaStorageViewModel.Factory(context) }
    val storageViewModel: WorkspaceMediaStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        StorageManageScaffold(
            isBusy = state.isLoading || state.job.running,
            errorMessage = state.errorMessage,
            selectedCount = state.selected.size,
            bottomBar = {
                StorageBottomBar(
                    selectedCount = state.selected.size,
                    selectedBytes = state.selectedBytes,
                    enabled = state.selected.isNotEmpty() && !state.job.running,
                    actionLabel = stringResource(R.string.data_storage_delete_selected),
                    onAction = { showConfirm = true },
                )
            },
        ) {
            item {
                StorageSummaryCard(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.screen_title_workspace_media_storage),
                    primaryValue = formatStorageSize(state.snapshot?.totalBytes ?: 0L),
                    extras = listOf(
                        stringResource(
                            R.string.data_storage_workspace_counts,
                            state.snapshot?.workspaces.orEmpty().size,
                            state.snapshot?.media.orEmpty().sumOf { it.fileCount },
                        ),
                    ),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = WorkspaceMediaTab.entries.map { tab ->
                        StorageChip(tab.name, stringResource(tab.labelRes))
                    },
                    selectedId = state.tab.name,
                    onSelect = { storageViewModel.setTab(WorkspaceMediaTab.valueOf(it)) },
                )
            }
            item { StorageJobCard(state.job) }
            if (state.displayed.isEmpty() && !state.isLoading) {
                item {
                    StorageEmptyCard(
                        text = stringResource(R.string.data_storage_workspaces_empty),
                        icon = Icons.Default.Folder,
                    )
                }
            } else {
                items(state.displayed, key = { it.id }) { entry ->
                    StorageSelectableRow(
                        selected = entry.id in state.selectedIds,
                        enabled = !entry.locked && !state.job.running,
                        locked = entry.locked,
                        title = stringResource(entry.kind.titleRes).takeIf { entry.kind.name.startsWith("MEDIA") }
                            ?: entry.name,
                        subtitle = buildString {
                            append(entry.path.absolutePath)
                            entry.boundChatTitle?.let { title ->
                                append("\n")
                                append(title)
                            }
                        },
                        bytes = entry.bytes,
                        tags = listOfNotNull(
                            if (entry.boundChatCount > 0) {
                                stringResource(R.string.data_storage_bound_chats, entry.boundChatCount)
                            } else {
                                null
                            },
                        ),
                        note = if (entry.lastModifiedMillis > 0L) {
                            stringResource(R.string.data_storage_updated_at, formatStorageTimestamp(entry.lastModifiedMillis))
                        } else {
                            stringResource(R.string.data_storage_file_count, entry.fileCount)
                        },
                        onToggle = { storageViewModel.toggle(entry) },
                    )
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_workspaces_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                state.selected.size,
                formatStorageSize(state.selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_workspaces_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private val WorkspaceMediaTab.labelRes: Int
    get() = when (this) {
        WorkspaceMediaTab.WORKSPACES -> R.string.data_storage_tab_workspaces
        WorkspaceMediaTab.MEDIA -> R.string.data_storage_tab_media
    }

private val WorkspaceMediaKind.titleRes: Int
    get() = when (this) {
        WorkspaceMediaKind.INTERNAL_WORKSPACE -> R.string.data_storage_detail_internal_workspaces
        WorkspaceMediaKind.SHARED_WORKSPACE -> R.string.data_storage_detail_shared_workspaces
        WorkspaceMediaKind.MEDIA_IMAGES -> R.string.data_storage_media_images
        WorkspaceMediaKind.MEDIA_AUDIO -> R.string.data_storage_media_audio
        WorkspaceMediaKind.MEDIA_VIDEO -> R.string.data_storage_media_video
        WorkspaceMediaKind.MEDIA_TRANSCODED -> R.string.data_storage_media_transcoded
    }
