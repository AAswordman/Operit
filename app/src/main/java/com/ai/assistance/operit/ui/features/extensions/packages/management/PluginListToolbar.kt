package com.ai.assistance.operit.ui.features.extensions.packages.management

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ActionSheet
import com.ai.assistance.operit.ui.components.ActionSheetItem

@Composable
fun PluginListToolbar(
    count: Int,
    sort: PluginSortSelection,
    filter: PluginFilter,
    onSortChanged: (PluginSortSelection) -> Unit,
    onFilterChanged: (PluginFilter) -> Unit,
    showSorting: Boolean = true,
) {
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(filterLabel(filter)), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.plugin_list_count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showSorting) IconButton(onClick = { showSort = true }) {
            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.plugin_sort_action))
        }
        IconButton(onClick = { showFilter = true }) {
            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.plugin_filter_action),
                tint = if (filter == PluginFilter.ALL) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface)
        }
    }
    if (showSort) {
        ActionSheet(stringResource(R.string.plugin_sort_action), onDismissRequest = { showSort = false }) {
            PluginSortMode.entries.forEach { mode ->
                ActionSheetItem(stringResource(sortLabel(mode)), sortIcon(mode), selected = sort.mode == mode) {
                    // 保留时间排序默认倒序的规则，面板内可继续切换正反方向。
                    onSortChanged(if (sort.mode == mode) sort else PluginSortSelection(
                        mode = mode,
                        descending = mode == PluginSortMode.INSTALLED || mode == PluginSortMode.LAST_USED,
                    ))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            listOf(false, true).forEach { descending ->
                ActionSheetItem(
                    stringResource(if (descending) R.string.plugin_sort_reverse else R.string.plugin_sort_forward),
                    if (descending) Icons.Default.South else Icons.Default.North,
                    selected = sort.descending == descending,
                ) {
                    onSortChanged(sort.copy(descending = descending))
                    showSort = false
                }
            }
        }
    }
    if (showFilter) {
        ActionSheet(stringResource(R.string.plugin_filter_action), onDismissRequest = { showFilter = false }) {
            PluginFilter.entries.forEach { option ->
                ActionSheetItem(stringResource(filterLabel(option)),
                    when (option) {
                        PluginFilter.ALL -> Icons.Default.Apps
                        PluginFilter.ENABLED -> Icons.Default.ToggleOn
                        PluginFilter.DISABLED -> Icons.Default.ToggleOff
                        PluginFilter.BUILT_IN -> Icons.Default.Apps
                    }, selected = filter == option) {
                    onFilterChanged(option)
                    showFilter = false
                }
            }
        }
    }
}

private fun sortIcon(mode: PluginSortMode): ImageVector = when (mode) {
    PluginSortMode.CUSTOM -> Icons.Default.DragIndicator
    PluginSortMode.NAME -> Icons.Default.SortByAlpha
    PluginSortMode.SIZE -> Icons.Default.Storage
    PluginSortMode.INSTALLED -> Icons.Default.Event
    PluginSortMode.LAST_USED -> Icons.Default.History
    PluginSortMode.ENABLED -> Icons.Default.PowerSettingsNew
}

private fun sortLabel(mode: PluginSortMode): Int = when (mode) {
    PluginSortMode.CUSTOM -> R.string.plugin_sort_custom
    PluginSortMode.NAME -> R.string.plugin_sort_name
    PluginSortMode.SIZE -> R.string.plugin_sort_size
    PluginSortMode.INSTALLED -> R.string.plugin_sort_installed
    PluginSortMode.LAST_USED -> R.string.plugin_sort_last_used
    PluginSortMode.ENABLED -> R.string.plugin_sort_enabled
}

private fun filterLabel(filter: PluginFilter): Int = when (filter) {
    PluginFilter.ALL -> R.string.plugin_filter_all
    PluginFilter.ENABLED -> R.string.plugin_filter_enabled
    PluginFilter.DISABLED -> R.string.plugin_filter_disabled
    PluginFilter.BUILT_IN -> R.string.hub_filter_builtin
}
