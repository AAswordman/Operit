package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PluginTabContent(
    plugins: Map<String, PackageManager.ToolPkgContainerDetails>,
    enabledPackageNames: List<String>,
    isLoading: Boolean,
    isSearchActive: Boolean,
    onPluginClick: (String) -> Unit,
    onTogglePlugin: (PackageManager.ToolPkgContainerDetails, Boolean) -> Unit,
    pluginOrder: List<String> = emptyList(),
    onSavePluginOrder: (List<String>) -> Unit = {},
) {
    // Convert map to ordered list, applying saved order if available
    val orderedPluginList = remember(plugins, pluginOrder) {
        val entries = plugins.entries.toList()
        if (pluginOrder.isNotEmpty()) {
            val orderIndex = pluginOrder.withIndex().associate { (i, name) -> name to i }
            entries.sortedBy { (name, _) -> orderIndex[name] ?: Int.MAX_VALUE }
        } else {
            entries
        }
    }

    var orderedItems by remember(orderedPluginList) {
        mutableStateOf(orderedPluginList)
    }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderedItems = orderedItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        val newOrder = orderedItems.map { (name, _) -> name }
        onSavePluginOrder(newOrder)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (plugins.isEmpty() && isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
            ) {
                if (plugins.isEmpty()) {
                    item(key = "empty_plugins_state") {
                        EmptyState(
                            message =
                                stringResource(
                                    if (isSearchActive) {
                                        R.string.no_matching_plugins_found
                                    } else {
                                        R.string.no_plugins_available
                                    }
                                )
                        )
                    }
                }

                itemsIndexed(
                    items = orderedItems,
                    key = { _, (packageName, _) -> packageName }
                ) { index, (packageName, details) ->
                    val isEnabled = enabledPackageNames.contains(packageName)
                    ReorderableItem(
                        reorderableState,
                        key = packageName,
                        animateItemModifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            placementSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            fadeOutSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                        )
                    ) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isDragging) {
                                        Modifier.shadow(elevation, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Card(
                                onClick = { onPluginClick(packageName) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .longPressDraggableHandle(),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = details.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = details.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.plugin_subpackage_count,
                                                    details.subpackages.size
                                                ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked -> onTogglePlugin(details, checked) },
                                        modifier = Modifier.size(width = 32.dp, height = 20.dp),
                                        colors =
                                            SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                    )
                                }
                            }
                        }
                        if (index < orderedItems.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && plugins.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
