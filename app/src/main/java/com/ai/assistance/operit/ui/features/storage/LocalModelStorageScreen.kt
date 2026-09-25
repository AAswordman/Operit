package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
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
import com.ai.assistance.operit.data.mnn.DownloadState
import com.ai.assistance.operit.data.storage.LocalModelCompleteness
import com.ai.assistance.operit.data.storage.LocalModelEntry
import com.ai.assistance.operit.data.storage.LocalModelKind
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.data.storage.formatStorageTimestamp

@Composable
fun LocalModelStorageScreen(
    onDownloadMnn: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val factory = remember(context) { LocalModelStorageViewModel.Factory(context) }
    val storageViewModel: LocalModelStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val selected = state.displayedModels.filter { it.id in state.selectedIds && it.canDelete }
    val selectedBytes = selected.sumOf { it.bytes }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        StorageManageScaffold(
            isBusy = state.isLoading || state.isDeleting || state.job.running,
            errorMessage = state.errorMessage,
            selectedCount = selected.size,
            bottomBar = {
                StorageBottomBar(
                    selectedCount = selected.size,
                    selectedBytes = selectedBytes,
                    enabled = selected.isNotEmpty() && !state.isDeleting && !state.job.running,
                    actionLabel = stringResource(R.string.data_storage_delete_selected),
                    onAction = { showConfirm = true },
                )
            },
        ) {
            item {
                StorageSummaryCard(
                    icon = Icons.Default.SmartToy,
                    title = stringResource(R.string.screen_title_local_model_storage),
                    primaryValue = formatStorageSize(state.totalBytes),
                    extras = listOf(stringResource(R.string.data_storage_local_models_count, state.displayedModels.size)),
                    scannedAtMillis = state.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = LocalModelFilter.entries.map { filter ->
                        StorageChip(filter.name, stringResource(filter.labelRes))
                    },
                    selectedId = state.filter.name,
                    onSelect = { storageViewModel.setFilter(LocalModelFilter.valueOf(it)) },
                )
            }
            if (state.displayedModels.isEmpty() && !state.isLoading) {
                item {
                    StorageEmptyCard(
                        text = stringResource(R.string.data_storage_local_models_empty),
                        icon = Icons.Default.SmartToy,
                        actionLabel = if (onDownloadMnn != null) {
                            stringResource(R.string.data_storage_local_models_download_mnn)
                        } else {
                            null
                        },
                        onAction = onDownloadMnn,
                    )
                }
            } else {
                items(state.displayedModels, key = { it.id }) { model ->
                    StorageSelectableRow(
                        selected = model.id in state.selectedIds,
                        enabled = model.canDelete && !state.isDeleting && !state.job.running,
                        locked = !model.canDelete,
                        title = model.displayName,
                        subtitle = stringResource(model.kind.labelRes),
                        bytes = model.bytes,
                        leadingIcon = Icons.Default.SmartToy,
                        statusTags = listOf(
                            StorageStatusTag(modelStatusText(model), emphasis = model.inUse),
                        ),
                        note = model.lastUsedText(),
                        onToggle = { storageViewModel.toggle(model) },
                    )
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_local_models_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                selected.size,
                formatStorageSize(selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_local_models_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }

    StorageDeleteProgressDialog(state = state.job)
}

private val LocalModelFilter.labelRes: Int
    get() = when (this) {
        LocalModelFilter.ALL -> R.string.data_storage_local_models_filter_all
        LocalModelFilter.MNN -> R.string.data_storage_detail_mnn_models
        LocalModelFilter.LLAMA -> R.string.data_storage_detail_llama_models
        LocalModelFilter.SPEECH -> R.string.data_storage_detail_speech_models
    }

private val LocalModelKind.labelRes: Int
    get() = when (this) {
        LocalModelKind.MNN -> R.string.data_storage_detail_mnn_models
        LocalModelKind.LLAMA -> R.string.data_storage_detail_llama_models
        LocalModelKind.SPEECH -> R.string.data_storage_detail_speech_models
    }

@Composable
private fun modelStatusText(model: LocalModelEntry): String {
    if (model.inUse) return stringResource(R.string.data_storage_local_models_status_in_use)
    return when (val downloadState = model.downloadState) {
        is DownloadState.Connecting -> stringResource(R.string.data_storage_local_models_status_connecting)
        is DownloadState.Downloading -> stringResource(
            R.string.data_storage_local_models_status_downloading,
            (downloadState.progress * 100).toInt(),
        )
        is DownloadState.Paused -> stringResource(
            R.string.data_storage_local_models_status_paused,
            (downloadState.progress * 100).toInt(),
        )
        is DownloadState.Failed -> stringResource(R.string.data_storage_local_models_status_failed, downloadState.error)
        else -> when (model.completeness) {
            LocalModelCompleteness.COMPLETE -> stringResource(R.string.data_storage_local_models_status_complete)
            LocalModelCompleteness.INCOMPLETE -> stringResource(R.string.data_storage_local_models_status_incomplete)
            LocalModelCompleteness.DOWNLOADING -> stringResource(R.string.data_storage_local_models_status_connecting)
        }
    }
}

@Composable
private fun LocalModelEntry.lastUsedText(): String? {
    val millis = path.lastModified()
    if (millis <= 0L) return null
    return stringResource(R.string.data_storage_last_used, formatStorageTimestamp(millis))
}
