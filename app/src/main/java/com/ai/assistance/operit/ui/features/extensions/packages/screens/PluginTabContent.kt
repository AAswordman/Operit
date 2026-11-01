package com.ai.assistance.operit.ui.features.extensions.packages.screens
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.metadata.PluginActivity
import com.ai.assistance.operit.ui.common.icons.rememberLogoPainter
import com.ai.assistance.operit.ui.features.extensions.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.extensions.packages.management.PluginSortEntry
import com.ai.assistance.operit.ui.features.extensions.packages.management.PluginSortSelection
import com.ai.assistance.operit.ui.features.extensions.packages.management.sortPluginEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    loadPluginLogo: suspend (String) -> PackageManager.ToolPkgLogoBytes?,
    pluginOrder: List<String> = emptyList(),
    onSavePluginOrder: (List<String>) -> Unit = {},
    sort: PluginSortSelection = PluginSortSelection(),
    activities: Map<String, PluginActivity> = emptyMap()
) {
    val glassEnabled = rememberGlassAppearance().packageCards
    val useCustomColors = LocalThemePreferenceSnapshot.current.useCustomColors
    val orderedPluginList = remember(plugins, pluginOrder, sort, activities, enabledPackageNames) {
        sortPluginEntries(
            entries = plugins.map { (name, details) ->
                val activity = activities[name]
                PluginSortEntry(
                    packageName = name,
                    displayName = details.displayName,
                    enabled = name in enabledPackageNames,
                    fileSizeBytes = details.fileSizeBytes,
                    installedAt = activity?.installedAt ?: 0L,
                    lastUsedAt = activity?.lastUsedAt ?: 0L
                )
            },
            selection = sort,
            customOrder = pluginOrder
        ).map { it.packageName }
    }
    var orderedItems by remember(orderedPluginList) { mutableStateOf(orderedPluginList) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderedItems = orderedItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
        onSavePluginOrder(orderedItems)
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            if (plugins.isEmpty() && !isLoading) {
                item(key = "empty_plugins_state") {
                    EmptyState(
                        message = stringResource(
                            if (isSearchActive) R.string.no_matching_plugins_found else R.string.no_plugins_available
                        )
                    )
                }
            }
            itemsIndexed(orderedItems, key = { _, name -> name }) { index, packageName ->
                val details = plugins.getValue(packageName)
                val isEnabled = packageName in enabledPackageNames
                val logo by produceState<PackageManager.ToolPkgLogoBytes?>(
                    initialValue = null, packageName, details.version, details.logoResourceKey
                ) {
                    value = if (details.logoResourceKey == null) null
                    else withContext(Dispatchers.IO) { loadPluginLogo(packageName) }
                }
                val logoPainter = rememberLogoPainter(
                    logoKey = "$packageName:${details.version}:${details.logoResourceKey}",
                    bytes = logo?.bytes,
                    mimeType = logo?.mimeType,
                    fileName = logo?.fileName,
                    size = 32.dp
                )
                ReorderableItem(
                    reorderableState,
                    key = packageName,
                    animateItemModifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                ) { isDragging ->
                    Surface(
                        onClick = { onPluginClick(packageName) },
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = if (glassEnabled) 3.dp else 0.dp)
                            .liquidGlass(glassEnabled, RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceContainer,
                                region = GlassRegion.PACKAGE_CARDS,
                            )
                            .longPressDraggableHandle().then(
                            if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)) else Modifier
                        ),
                        shape = RoundedCornerShape(if (isDragging || glassEnabled) 12.dp else 0.dp),
                        // 普通行延续页面底色，拖动中的行使用实色表面承载阴影。
                        color = if (glassEnabled) Color.Transparent else if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).alpha(if (isEnabled) 1f else 0.55f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (logoPainter != null) {
                                    Image(
                                        painter = logoPainter,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    // 默认图标跟随已启用的定制主色，自带 Logo 与禁用透明度沿用原样。
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = if (useCustomColors) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = details.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (details.isBuiltIn) Text(
                                    stringResource(R.string.builtin), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (details.description.isNotBlank()) {
                                    Text(
                                        text = details.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (details.subpackages.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.plugin_subpackage_count, details.subpackages.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }
                            Switch(checked = isEnabled, onCheckedChange = { onTogglePlugin(details, it) })
                        }
                    }
                    if (index < orderedItems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
