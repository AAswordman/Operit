package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.storage.ExtensionCategory
import com.ai.assistance.operit.data.storage.ExtensionItem
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.common.icons.rememberLogoPainter
import com.ai.assistance.operit.ui.common.icons.rememberProviderLogoPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConfigurationResourceStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { ConfigurationResourceStorageViewModel.Factory(context) }
    val storageViewModel: ConfigurationResourceStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

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
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.screen_title_configuration_resource_storage),
                    primaryValue = formatStorageSize(
                        (state.snapshot?.totalBytes ?: 0L) + (state.packages?.totalBytes ?: 0L),
                    ),
                    extras = listOf(
                        stringResource(
                            R.string.data_storage_configuration_counts,
                            state.displayedCards.size,
                            state.displayedConfigs.size + (state.packages?.items.orEmpty().size),
                        ),
                    ),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = ConfigurationResourceTab.entries.map { tab ->
                        StorageChip(tab.name, stringResource(tab.labelRes))
                    },
                    selectedId = state.tab.name,
                    onSelect = { storageViewModel.setTab(ConfigurationResourceTab.valueOf(it)) },
                )
            }
            item { StorageJobCard(state.job) }
            when (state.tab) {
                ConfigurationResourceTab.CARDS -> {
                    if (state.displayedCards.isEmpty() && !state.isLoading) {
                        item {
                            StorageEmptyCard(
                                text = stringResource(R.string.data_storage_cards_empty),
                                icon = Icons.Default.Person,
                            )
                        }
                    } else {
                        items(state.displayedCards, key = { it.id }) { entry ->
                            StorageSelectableRow(
                                selected = entry.id in state.selectedIds,
                                enabled = !entry.locked && !state.job.running,
                                locked = entry.locked,
                                title = entry.name,
                                subtitle = stringResource(R.string.data_storage_bound_chats, entry.boundCount),
                                bytes = entry.bytes,
                                leadingPainter = rememberStorageAvatarPainter(entry.avatarUri),
                                leadingInitial = entry.name,
                                statusTags = listOfNotNull(
                                    if (entry.inUse) {
                                        StorageStatusTag(stringResource(R.string.data_storage_in_use), emphasis = true)
                                    } else if (entry.boundCount > 0) {
                                        StorageStatusTag(stringResource(R.string.data_storage_bound), emphasis = true)
                                    } else {
                                        null
                                    },
                                ),
                                onToggle = { storageViewModel.toggle(entry.id, entry.locked) },
                            )
                        }
                    }
                }
                ConfigurationResourceTab.CONFIGS -> {
                    if (state.displayedConfigs.isEmpty() && !state.isLoading) {
                        item {
                            StorageEmptyCard(
                                text = stringResource(R.string.data_storage_configs_empty),
                                icon = Icons.Default.Tune,
                            )
                        }
                    } else {
                        items(state.displayedConfigs, key = { it.id }) { entry ->
                            StorageSelectableRow(
                                selected = entry.id in state.selectedIds,
                                enabled = !entry.locked && !state.job.running,
                                locked = entry.locked,
                                title = entry.name,
                                subtitle = listOfNotNull(
                                    entry.providerDisplayName,
                                    entry.primaryModelName?.takeIf { it.isNotBlank() }?.let {
                                        stringResource(R.string.data_storage_config_primary_model, it)
                                    },
                                ).joinToString(" · "),
                                bytes = entry.bytes,
                                showBytes = false,
                                leadingPainter = rememberProviderLogoPainter(entry.providerTypeId, 32.dp),
                                leadingInitial = entry.providerDisplayName ?: entry.name,
                                leadingCircular = false,
                                statusTags = listOfNotNull(
                                    if (entry.inUse) {
                                        StorageStatusTag(stringResource(R.string.data_storage_in_use), emphasis = true)
                                    } else {
                                        null
                                    },
                                ),
                                onToggle = { storageViewModel.toggle(entry.id, entry.locked) },
                            )
                        }
                    }
                }
                ConfigurationResourceTab.EXTENSIONS -> {
                    if (state.extensionGroups.isEmpty() && state.bridgeItems.isEmpty() && !state.isLoading) {
                        item {
                            StorageEmptyCard(
                                text = stringResource(R.string.data_storage_extensions_empty),
                                icon = Icons.Default.Extension,
                            )
                        }
                    } else {
                        state.extensionGroups.forEach { group ->
                            val expanded = group.category in state.expandedCategories
                            val empty = group.items.isEmpty()
                            item(key = "ext-group:${group.category.name}") {
                                StorageSelectableRow(
                                    selected = group.category.name in state.selectedIds ||
                                        (group.items.isNotEmpty() && group.items.all { it.id in state.selectedIds }),
                                    enabled = !empty && !state.job.running,
                                    locked = false,
                                    empty = empty,
                                    title = stringResource(group.category.titleRes),
                                    subtitle = if (empty) {
                                        stringResource(R.string.data_storage_nothing_deletable)
                                    } else {
                                        stringResource(R.string.data_storage_extension_item_count, group.itemCount)
                                    },
                                    bytes = group.bytes,
                                    leadingIcon = group.category.icon,
                                    expanded = expanded,
                                    onExpand = { storageViewModel.toggleExpanded(group.category) },
                                    onToggle = { storageViewModel.toggleCategory(group) },
                                )
                            }
                            if (expanded) {
                                if (group.items.isEmpty()) {
                                    item(key = "ext-empty:${group.category.name}") {
                                        StorageEmptyCard(
                                            text = stringResource(group.category.emptyRes),
                                            icon = group.category.icon,
                                        )
                                    }
                                } else {
                                    items(group.items, key = { it.id }) { entry ->
                                        ExtensionItemRow(
                                            entry = entry,
                                            selected = entry.id in state.selectedIds || group.category.name in state.selectedIds,
                                            enabled = !state.job.running,
                                            loadLogo = storageViewModel::readPluginLogo,
                                            onToggle = { storageViewModel.toggleItem(group, entry) },
                                        )
                                    }
                                }
                            }
                        }
                        items(state.bridgeItems, key = { it.id }) { entry ->
                            StorageSelectableRow(
                                selected = entry.id in state.selectedIds,
                                enabled = !state.job.running,
                                locked = false,
                                title = stringResource(R.string.data_storage_extension_bridge),
                                subtitle = stringResource(R.string.data_storage_extension_bridge_desc),
                                bytes = entry.bytes,
                                leadingIcon = Icons.Default.Cable,
                                onToggle = { storageViewModel.toggle(entry.id, false) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_config_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                state.selectedCount,
                formatStorageSize(state.selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_config_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun ExtensionItemRow(
    entry: ExtensionItem,
    selected: Boolean,
    enabled: Boolean,
    loadLogo: (String) -> PackageManager.ToolPkgLogoBytes?,
    onToggle: () -> Unit,
) {
    val packageName = entry.packageName
    val logo by produceState<PackageManager.ToolPkgLogoBytes?>(
        initialValue = null,
        packageName,
        entry.hasLogo,
    ) {
        value = if (!entry.hasLogo || packageName.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { loadLogo(packageName) }
        }
    }
    val painter = rememberLogoPainter(
        logoKey = "${entry.packageName}:${logo?.resourceKey}",
        bytes = logo?.bytes,
        mimeType = logo?.mimeType,
        fileName = logo?.fileName,
        size = 32.dp,
    )
    StorageSelectableRow(
        selected = selected,
        enabled = enabled,
        locked = false,
        indent = true,
        title = entry.name,
        subtitle = entry.subtitle,
        bytes = entry.bytes,
        leadingPainter = painter,
        leadingInitial = entry.name,
        leadingCircular = false,
        onToggle = onToggle,
    )
}

private val ConfigurationResourceTab.labelRes: Int
    get() = when (this) {
        ConfigurationResourceTab.CARDS -> R.string.data_storage_tab_cards
        ConfigurationResourceTab.CONFIGS -> R.string.data_storage_tab_configs
        ConfigurationResourceTab.EXTENSIONS -> R.string.data_storage_tab_extensions
    }

private val ExtensionCategory.titleRes: Int
    get() = when (this) {
        ExtensionCategory.PLUGIN -> R.string.data_storage_extension_plugins
        ExtensionCategory.SCRIPT -> R.string.data_storage_extension_scripts
        ExtensionCategory.MCP -> R.string.data_storage_extension_mcp
        ExtensionCategory.SKILL -> R.string.data_storage_extension_skills
        ExtensionCategory.PLUGIN_SOURCE -> R.string.data_storage_extension_plugin_source
        ExtensionCategory.DEV_PACKAGE -> R.string.data_storage_extension_dev_package
        ExtensionCategory.THEME -> R.string.data_storage_extension_themes
        ExtensionCategory.MCP_BRIDGE -> R.string.data_storage_extension_bridge
    }

private val ExtensionCategory.emptyRes: Int
    get() = when (this) {
        ExtensionCategory.PLUGIN -> R.string.data_storage_extension_plugins_empty
        ExtensionCategory.SCRIPT -> R.string.data_storage_extension_scripts_empty
        ExtensionCategory.MCP -> R.string.data_storage_extension_mcp_empty
        ExtensionCategory.SKILL -> R.string.data_storage_extension_skills_empty
        ExtensionCategory.PLUGIN_SOURCE -> R.string.data_storage_extension_plugin_source_empty
        ExtensionCategory.DEV_PACKAGE -> R.string.data_storage_extension_dev_package_empty
        ExtensionCategory.THEME -> R.string.data_storage_extension_themes_empty
        ExtensionCategory.MCP_BRIDGE -> R.string.data_storage_extension_bridge_empty
    }

private val ExtensionCategory.icon: ImageVector
    get() = when (this) {
        ExtensionCategory.PLUGIN -> Icons.Default.Extension
        ExtensionCategory.SCRIPT -> Icons.Default.Code
        ExtensionCategory.MCP -> Icons.Default.Hub
        ExtensionCategory.SKILL -> Icons.Default.AutoAwesome
        ExtensionCategory.PLUGIN_SOURCE -> Icons.Default.Folder
        ExtensionCategory.DEV_PACKAGE -> Icons.Default.Build
        ExtensionCategory.THEME -> Icons.Default.Palette
        ExtensionCategory.MCP_BRIDGE -> Icons.Default.Cable
    }
