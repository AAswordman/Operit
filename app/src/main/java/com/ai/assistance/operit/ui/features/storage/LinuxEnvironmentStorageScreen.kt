package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.LinuxStorageUnitKind
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.common.icons.rememberAssetLogoPainter

@Composable
fun LinuxEnvironmentStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { LinuxEnvironmentStorageViewModel.Factory(context) }
    val storageViewModel: LinuxEnvironmentStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val needsHomePhrase = state.selectedUnits.any { it.kind == LinuxStorageUnitKind.HOME }
    val tuxPainter = rememberAssetLogoPainter("linux/tux.svg", 56.dp)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        StorageManageScaffold(
            isBusy = state.isLoading || state.job.running,
            errorMessage = state.errorMessage,
            selectedCount = state.selectedUnits.size,
            bottomBar = {
                StorageBottomBar(
                    selectedCount = state.selectedUnits.size,
                    selectedBytes = state.selectedBytes,
                    enabled = state.selectedUnits.isNotEmpty() && !state.job.running,
                    actionLabel = stringResource(R.string.data_storage_delete_selected),
                    onAction = { showConfirm = true },
                )
            },
        ) {
            item {
                StorageSummaryCard(
                    icon = Icons.Default.Terminal,
                    title = stringResource(R.string.screen_title_linux_environment_storage),
                    primaryValue = formatStorageSize(state.snapshot?.totalBytes ?: 0L),
                    extras = listOf(
                        if (state.snapshot?.ubuntuInstalled == true) {
                            stringResource(R.string.data_storage_linux_ubuntu_ready)
                        } else {
                            stringResource(R.string.data_storage_linux_ubuntu_missing)
                        },
                    ),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                    leadingContent = {
                        StorageLeadingMark(
                            painter = tuxPainter,
                            icon = Icons.Default.Terminal,
                            size = 56.dp,
                            circular = false,
                        )
                    },
                )
            }
            item { StorageJobCard(state.job) }
            item {
                Text(
                    text = stringResource(R.string.data_storage_linux_units_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (state.units.isEmpty() && !state.isLoading) {
                item {
                    StorageEmptyCard(
                        text = stringResource(R.string.data_storage_linux_no_data),
                        icon = Icons.Default.Terminal,
                    )
                }
            } else {
                items(state.units, key = { it.kind.name }) { unit ->
                    val empty = !unit.exists || unit.bytes <= 0L
                    StorageSelectableRow(
                        selected = unit.kind in state.selectedKinds,
                        enabled = !unit.locked && !empty && !state.job.running,
                        locked = unit.locked,
                        empty = empty && !unit.locked,
                        title = stringResource(unit.kind.titleRes),
                        subtitle = if (empty && !unit.locked) {
                            stringResource(R.string.data_storage_nothing_deletable)
                        } else {
                            stringResource(R.string.data_storage_linux_files_count, unit.fileCount)
                        },
                        bytes = unit.bytes,
                        leadingIcon = unit.kind.icon,
                        statusTags = listOfNotNull(
                            if (unit.locked) {
                                StorageStatusTag(stringResource(R.string.data_storage_linux_locked_note), emphasis = true)
                            } else if (empty) {
                                StorageStatusTag(stringResource(unit.kind.emptyRes))
                            } else {
                                null
                            },
                        ),
                        onToggle = { storageViewModel.toggle(unit) },
                    )
                }
            }
        }
    }

    if (showConfirm) {
        val paths = state.selectedUnits.flatMap { it.deletionPaths() }.joinToString("\n") { it.absolutePath }
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_linux_delete_title),
            message = buildString {
                append(
                    stringResource(
                        R.string.data_storage_linux_delete_message,
                        state.selectedUnits.size,
                        formatStorageSize(state.selectedBytes),
                    ),
                )
                if (paths.isNotBlank()) {
                    append("\n\n")
                    append(stringResource(R.string.data_storage_linux_delete_paths))
                    append('\n')
                    append(paths)
                }
            },
            warnings = listOf(stringResource(R.string.data_storage_linux_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            countdownSeconds = 5,
            typedPhrase = if (needsHomePhrase) "HOME" else null,
            typedHint = if (needsHomePhrase) stringResource(R.string.data_storage_linux_type_home) else null,
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private val LinuxStorageUnitKind.titleRes: Int
    get() = when (this) {
        LinuxStorageUnitKind.ROOT -> R.string.data_storage_linux_unit_root
        LinuxStorageUnitKind.HOME -> R.string.data_storage_linux_unit_home
        LinuxStorageUnitKind.APT_CACHE -> R.string.data_storage_linux_unit_apt
        LinuxStorageUnitKind.LOGS_TEMP -> R.string.data_storage_linux_unit_logs
        LinuxStorageUnitKind.TERMINAL_PACKAGES -> R.string.data_storage_linux_unit_packages
    }

private val LinuxStorageUnitKind.emptyRes: Int
    get() = when (this) {
        LinuxStorageUnitKind.ROOT -> R.string.data_storage_linux_empty_root
        LinuxStorageUnitKind.HOME -> R.string.data_storage_linux_empty_home
        LinuxStorageUnitKind.APT_CACHE -> R.string.data_storage_linux_empty_apt
        LinuxStorageUnitKind.LOGS_TEMP -> R.string.data_storage_linux_empty_logs
        LinuxStorageUnitKind.TERMINAL_PACKAGES -> R.string.data_storage_linux_empty_packages
    }

private val LinuxStorageUnitKind.icon
    get() = when (this) {
        LinuxStorageUnitKind.ROOT -> Icons.Default.Terminal
        LinuxStorageUnitKind.HOME -> Icons.Default.Home
        LinuxStorageUnitKind.APT_CACHE -> Icons.Default.CleaningServices
        LinuxStorageUnitKind.LOGS_TEMP -> Icons.Default.Description
        LinuxStorageUnitKind.TERMINAL_PACKAGES -> Icons.Default.Archive
    }
