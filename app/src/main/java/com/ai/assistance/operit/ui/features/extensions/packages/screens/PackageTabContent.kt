package com.ai.assistance.operit.ui.features.extensions.packages.screens

import com.ai.assistance.operit.ui.features.extensions.packages.components.ExtensionSwitch
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.ui.features.extensions.packages.components.EmptyState

@Composable
fun PackageTabContent(
    packages: Map<String, ToolPackage>,
    enabledPackageNames: List<String>,
    isLoading: Boolean,
    isSearchActive: Boolean,
    onQuickPluginCreatorClick: () -> Unit,
    onPackageClick: (String) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (packages.isEmpty() && isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.medium
            ) {
                val groupedPackagesRaw = packages.entries.groupBy { it.value.category }
                val categoryOrder = listOf("Automatic", "Experimental", "Draw", "Other")
                val sortedCategories =
                    groupedPackagesRaw.keys.sortedWith { a, b ->
                        val indexA = categoryOrder.indexOf(a)
                        val indexB = categoryOrder.indexOf(b)
                        when {
                            indexA == -1 && indexB == -1 -> a.compareTo(b)
                            indexA == -1 -> 1
                            indexB == -1 -> -1
                            else -> indexA - indexB
                        }
                    }

                val groupedPackages = linkedMapOf<String, Map<String, ToolPackage>>()
                sortedCategories.forEach { category ->
                    val entries = groupedPackagesRaw[category].orEmpty()
                    val sortedEntries = entries.sortedBy { it.key }
                    groupedPackages[category] =
                        sortedEntries.associate { entry -> entry.key to entry.value }
                }

                val automaticColor = MaterialTheme.colorScheme.primary
                val experimentalColor = MaterialTheme.colorScheme.tertiary
                val drawColor = MaterialTheme.colorScheme.secondary
                val otherColor = MaterialTheme.colorScheme.onSurfaceVariant

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
                ) {
                    if (!isSearchActive) {
                        item(key = "quick_plugin_creator_entry") {
                            QuickPluginCreatorEntry(onClick = onQuickPluginCreatorClick)
                        }
                    }

                    if (packages.isEmpty()) {
                        item(key = "empty_packages_state") {
                            EmptyState(
                                message =
                                    context.getString(
                                        if (isSearchActive) {
                                            R.string.no_matching_packages_found
                                        } else {
                                            R.string.no_packages_available
                                        }
                                    )
                            )
                        }
                    }

                    groupedPackages.forEach { (category, packagesInCategory) ->
                        val categoryColor = when (category) {
                            "Automatic" -> automaticColor
                            "Experimental" -> experimentalColor
                            "Draw" -> drawColor
                            else -> otherColor
                        }

                        items(
                            packagesInCategory.keys.toList(),
                            key = { it }
                        ) { packageName ->
                            val isFirstInCategory = packageName == packagesInCategory.keys.first()

                            PackageListItemWithTag(
                                packageName = packageName,
                                toolPackage = packagesInCategory[packageName],
                                isImported = enabledPackageNames.contains(packageName),
                                categoryTag = if (isFirstInCategory) category else null,
                                category = category,
                                categoryColor = categoryColor,
                                onPackageClick = { onPackageClick(packageName) },
                                onToggleImport = { isChecked ->
                                    onTogglePackage(packageName, isChecked)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && packages.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun QuickPluginCreatorEntry(
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        onClick = onClick,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_plugin_creator_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.quick_plugin_creator_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PackageListItemWithTag(
    packageName: String,
    toolPackage: ToolPackage?,
    isImported: Boolean,
    categoryTag: String?,
    category: String,
    categoryColor: Color,
    onPackageClick: () -> Unit,
    onToggleImport: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val packageDisplayName =
        toolPackage
            ?.displayName
            ?.resolve(context)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val displayName = packageDisplayName ?: toolPackage?.name ?: packageName
    val glassEnabled = rememberGlassAppearance().packageCards

    Column(modifier = Modifier.fillMaxWidth()) {
        if (categoryTag != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(12.dp),
                    color = categoryColor,
                    shape = RoundedCornerShape(1.5.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = categoryTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            onClick = onPackageClick,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = if (glassEnabled) 8.dp else 0.dp, vertical = if (glassEnabled) 3.dp else 0.dp)
                .liquidGlass(glassEnabled, RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceContainer,
                    region = GlassRegion.PACKAGE_CARDS,
                ),
            color = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = RoundedCornerShape(if (glassEnabled) 12.dp else 0.dp)
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (categoryTag != null) 4.dp else 8.dp
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (category) {
                        "Automatic" -> Icons.Default.AutoMode
                        "Experimental" -> Icons.Default.Science
                        "Draw" -> Icons.Default.Palette
                        "Other" -> Icons.Default.Widgets
                        else -> Icons.Default.Extension
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = categoryColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val description = toolPackage?.description?.resolve(context).orEmpty()
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ExtensionSwitch(
                    checked = isImported,
                    onCheckedChange = onToggleImport,
                )
            }
        }
    }
}
