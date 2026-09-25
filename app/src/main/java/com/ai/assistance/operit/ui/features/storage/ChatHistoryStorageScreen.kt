package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
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
fun ChatHistoryStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { ChatHistoryStorageViewModel.Factory(context) }
    val storageViewModel: ChatHistoryStorageViewModel = viewModel(factory = factory)
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
                    icon = Icons.Default.Forum,
                    title = stringResource(R.string.screen_title_chat_history_storage),
                    primaryValue = formatStorageSize(state.snapshot?.databaseBytes ?: 0L),
                    extras = listOf(
                        stringResource(
                            R.string.data_storage_chat_counts,
                            state.snapshot?.chatCount ?: 0,
                            state.snapshot?.messageCount ?: 0,
                        ),
                        stringResource(
                            R.string.data_storage_estimated_usage,
                            formatStorageSize(state.snapshot?.estimatedBytes ?: 0L),
                        ),
                    ),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = ChatStorageSort.entries.map { sort ->
                        StorageChip(sort.name, stringResource(sort.labelRes))
                    },
                    selectedId = state.sort.name,
                    onSelect = { storageViewModel.setSort(ChatStorageSort.valueOf(it)) },
                )
            }
            item { StorageJobCard(state.job) }
            if (state.chats.isEmpty() && !state.isLoading) {
                item {
                    StorageEmptyCard(
                        text = stringResource(R.string.data_storage_chats_empty),
                        icon = Icons.Default.Forum,
                    )
                }
            } else {
                items(state.chats, key = { it.id }) { chat ->
                    val tags = buildList {
                        chat.characterName?.takeIf { it.isNotBlank() }?.let { name ->
                            add(StorageStatusTag(name))
                        }
                        if (chat.hasWorkspace) {
                            add(StorageStatusTag(stringResource(R.string.data_storage_chat_has_workspace)))
                        }
                        if (chat.chat.locked) {
                            add(StorageStatusTag(stringResource(R.string.data_storage_locked), emphasis = true))
                        }
                        if (chat.isCurrent) {
                            add(StorageStatusTag(stringResource(R.string.data_storage_current_item), emphasis = true))
                        }
                    }
                    StorageSelectableRow(
                        selected = chat.id in state.selectedIds,
                        enabled = chat.canDelete && !state.job.running,
                        locked = chat.chat.locked,
                        title = chat.title,
                        subtitle = stringResource(R.string.data_storage_chat_message_count, chat.messageCount),
                        bytes = chat.estimatedBytes,
                        leadingPainter = rememberStorageAvatarPainter(chat.avatarUri),
                        leadingInitial = chat.characterName ?: chat.title,
                        statusTags = tags,
                        note = if (chat.isCurrent) {
                            stringResource(R.string.data_storage_current_item)
                        } else {
                            stringResource(R.string.data_storage_updated_at, formatStorageTimestamp(chat.updatedAtMillis))
                        },
                        onToggle = { storageViewModel.toggle(chat) },
                    )
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_chats_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                state.selected.size,
                formatStorageSize(state.selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_chats_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private val ChatStorageSort.labelRes: Int
    get() = when (this) {
        ChatStorageSort.SIZE_DESC -> R.string.data_storage_sort_size
        ChatStorageSort.UPDATED_DESC -> R.string.data_storage_sort_updated
        ChatStorageSort.MESSAGES_DESC -> R.string.data_storage_sort_messages
    }
