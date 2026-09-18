package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.BackupExportKind
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.data.storage.formatStorageTimestamp

@Composable
fun BackupExportStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { BackupExportStorageViewModel.Factory(context) }
    val storageViewModel: BackupExportStorageViewModel = viewModel(factory = factory)
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
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.screen_title_backup_export_storage),
                    primaryValue = formatStorageSize(state.snapshot?.totalBytes ?: 0L),
                    extras = listOf(stringResource(R.string.data_storage_item_count, state.snapshot?.entries.orEmpty().size)),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = BackupExportTab.entries.map { tab ->
                        StorageChip(tab.name, stringResource(tab.labelRes))
                    },
                    selectedId = state.tab.name,
                    onSelect = { storageViewModel.setTab(BackupExportTab.valueOf(it)) },
                )
            }
            item { StorageJobCard(state.job) }
            if (state.displayed.isEmpty() && !state.isLoading) {
                item {
                    StorageEmptyCard(
                        text = if (state.tab == BackupExportTab.EXPORTS) {
                            stringResource(R.string.data_storage_exports_empty)
                        } else {
                            stringResource(R.string.data_storage_backups_empty)
                        },
                        icon = if (state.tab == BackupExportTab.EXPORTS) {
                            Icons.Default.IosShare
                        } else {
                            Icons.Default.Backup
                        },
                    )
                }
            } else {
                items(state.displayed, key = { it.id }) { entry ->
                    StorageSelectableRow(
                        selected = entry.id in state.selectedIds,
                        enabled = !state.job.running,
                        locked = false,
                        title = entry.name,
                        subtitle = stringResource(entry.kind.labelRes),
                        bytes = entry.bytes,
                        leadingIcon = entry.kind.icon,
                        statusTags = listOfNotNull(
                            if (!entry.valid) {
                                StorageStatusTag(stringResource(R.string.data_storage_invalid_file), emphasis = true)
                            } else {
                                null
                            },
                        ),
                        note = stringResource(R.string.data_storage_updated_at, formatStorageTimestamp(entry.lastModifiedMillis)),
                        onToggle = { storageViewModel.toggle(entry) },
                    )
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_backups_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                state.selected.size,
                formatStorageSize(state.selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_backups_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private val BackupExportTab.labelRes: Int
    get() = when (this) {
        BackupExportTab.BACKUPS -> R.string.data_storage_tab_backups
        BackupExportTab.EXPORTS -> R.string.data_storage_tab_exports
    }

private val BackupExportKind.labelRes: Int
    get() = when (this) {
        BackupExportKind.CHAT_BACKUP -> R.string.data_storage_backup_chat
        BackupExportKind.DATABASE_BACKUP -> R.string.data_storage_backup_database
        BackupExportKind.RAW_SNAPSHOT -> R.string.data_storage_backup_snapshot
        BackupExportKind.CHARACTER_CARDS -> R.string.data_storage_backup_cards
        BackupExportKind.MEMORY -> R.string.data_storage_backup_memory
        BackupExportKind.MODEL_CONFIG -> R.string.data_storage_backup_config
        BackupExportKind.EXPORT_FILE -> R.string.data_storage_backup_export
        BackupExportKind.UNKNOWN -> R.string.data_storage_backup_unknown
    }

private val BackupExportKind.icon: ImageVector
    get() = when (this) {
        BackupExportKind.CHAT_BACKUP -> Icons.Default.Forum
        BackupExportKind.DATABASE_BACKUP -> Icons.Default.Storage
        BackupExportKind.RAW_SNAPSHOT -> Icons.Default.Inventory
        BackupExportKind.CHARACTER_CARDS -> Icons.Default.Person
        BackupExportKind.MEMORY -> Icons.Default.Psychology
        BackupExportKind.MODEL_CONFIG -> Icons.Default.Tune
        BackupExportKind.EXPORT_FILE -> Icons.Default.IosShare
        BackupExportKind.UNKNOWN -> Icons.Default.InsertDriveFile
    }
