package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
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
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.data.storage.formatStorageTimestamp

@Composable
fun MemoryLibraryStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { MemoryLibraryStorageViewModel.Factory(context) }
    val storageViewModel: MemoryLibraryStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val profileChips = buildList {
        add(StorageChip(MemoryLibraryUiState.ALL, stringResource(R.string.data_storage_filter_all_spaces)))
        state.snapshot?.folders.orEmpty()
            .map { it.profileId to it.profileName }
            .distinctBy { it.first }
            .forEach { (id, name) -> add(StorageChip(id, name)) }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        StorageManageScaffold(
            isBusy = state.isLoading || state.job.running,
            errorMessage = state.errorMessage,
            selectedCount = state.selectedCount,
            bottomBar = {
                StorageBottomBar(
                    selectedCount = state.selectedCount,
                    selectedBytes = state.selectedBytes,
                    enabled = state.selectedCount > 0 && !state.job.running,
                    actionLabel = stringResource(R.string.data_storage_delete_selected),
                    onAction = { showConfirm = true },
                )
            },
        ) {
            item {
                StorageSummaryCard(
                    icon = Icons.Default.Psychology,
                    title = stringResource(R.string.screen_title_memory_library_storage),
                    primaryValue = formatStorageSize(state.snapshot?.databaseBytes ?: 0L),
                    extras = listOf(
                        stringResource(
                            R.string.data_storage_memory_counts,
                            state.snapshot?.memoryCount ?: 0,
                            state.snapshot?.profileCount ?: 0,
                        ),
                        stringResource(
                            R.string.data_storage_memory_folder_count,
                            state.snapshot?.folderCount ?: 0,
                        ),
                    ),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = profileChips,
                    selectedId = state.profileFilter,
                    onSelect = storageViewModel::setProfileFilter,
                )
            }
            item { StorageJobCard(state.job) }
            if (state.folders.isEmpty() && !state.isLoading) {
                item {
                    StorageEmptyCard(
                        text = stringResource(R.string.data_storage_memory_empty_folders),
                        icon = Icons.Default.Psychology,
                    )
                }
            } else {
                state.folders.forEach { folder ->
                    val expanded = folder.key in state.expandedKeys
                    item(key = folder.key) {
                        val folderTitle = folder.folderName.ifBlank {
                            stringResource(R.string.data_storage_memory_uncategorized_folder)
                        }
                        StorageSelectableRow(
                            selected = folder.key in state.selectedKeys || folder.entries.all { it.key in state.selectedKeys },
                            enabled = !state.job.running,
                            locked = false,
                            title = folderTitle,
                            subtitle = folder.profileName,
                            bytes = folder.estimatedBytes,
                            leadingIcon = Icons.Default.Folder,
                            expanded = expanded,
                            onExpand = { storageViewModel.toggleExpanded(folder) },
                            statusTags = listOfNotNull(
                                StorageStatusTag(stringResource(R.string.data_storage_memory_folder_items, folder.memoryCount)),
                                if (folder.documentCount > 0) {
                                    StorageStatusTag(stringResource(R.string.data_storage_memory_document))
                                } else {
                                    null
                                },
                            ),
                            onToggle = { storageViewModel.toggleFolder(folder) },
                        )
                    }
                    if (expanded) {
                        items(folder.entries, key = { it.key }) { entry ->
                            StorageSelectableRow(
                                selected = entry.key in state.selectedKeys || folder.key in state.selectedKeys,
                                enabled = !state.job.running,
                                locked = false,
                                indent = true,
                                title = entry.title,
                                subtitle = if (entry.isDocument) {
                                    stringResource(R.string.data_storage_memory_document)
                                } else {
                                    stringResource(R.string.data_storage_updated_at, formatStorageTimestamp(entry.updatedAtMillis))
                                },
                                bytes = entry.estimatedBytes,
                                leadingIcon = if (entry.isDocument) Icons.Default.Description else Icons.Default.Psychology,
                                onToggle = { storageViewModel.toggleEntry(folder, entry) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_memory_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                state.selectedCount,
                formatStorageSize(state.selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_memory_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}
