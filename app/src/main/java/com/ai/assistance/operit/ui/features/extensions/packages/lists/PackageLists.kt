package com.ai.assistance.operit.ui.features.extensions.packages.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.ui.features.extensions.packages.components.PackageItem

@Composable
fun PackagesList(
        packages: Map<String, ToolPackage>,
        importedPackages: List<String>,
        onPackageClick: (String) -> Unit,
        onToggleImport: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 4.dp,
                top = 8.dp,
                bottom = 88.dp // 增加底部边距避免被FAB遮挡
            )
    ) {
        items(items = packages.entries.toList(), key = { (name, _) -> name }) { (name, pack) ->
            val isImported = importedPackages.contains(name)
            val displayName = pack.displayName.resolve(context).ifBlank { name }
            PackageItem(
                    name = displayName,
                    description = pack.description.resolve(context),
                    isImported = isImported,
                    onClick = { onPackageClick(name) },
                    onToggleImport = { checked -> onToggleImport(name, checked) }
            )
        }
    }
}

// 保留这些函数以防其他地方还在使用，但标记为已弃用
@Deprecated("Use PackagesList instead")
@Composable
fun AvailablePackagesList(
        packages: Map<String, ToolPackage>,
        onPackageClick: (String) -> Unit,
        onImportClick: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(items = packages.entries.toList(), key = { (name, _) -> name }) { (name, pack) ->
            val displayName = pack.displayName.resolve(context).ifBlank { name }
            PackageItem(
                    name = displayName,
                    description = pack.description.resolve(context),
                    isImported = false,
                    onClick = { onPackageClick(name) },
                    onToggleImport = { if (it) onImportClick(name) }
            )
        }
    }
}

@Deprecated("Use PackagesList instead")
@Composable
fun ImportedPackagesList(
        packages: List<String>,
        availablePackages: Map<String, ToolPackage>,
        onPackageClick: (String) -> Unit,
        onRemoveClick: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(items = packages, key = { name -> name }) { name ->
            val displayName = availablePackages[name]?.displayName?.resolve(context).orEmpty().ifBlank { name }
            PackageItem(
                    name = displayName,
                    description = availablePackages[name]?.description?.resolve(context) ?: "",
                    isImported = true,
                    onClick = { onPackageClick(name) },
                    onToggleImport = { if (!it) onRemoveClick(name) }
            )
        }
    }
}
