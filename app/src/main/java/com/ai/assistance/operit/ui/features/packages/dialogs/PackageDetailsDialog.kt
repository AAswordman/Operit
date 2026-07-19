package com.ai.assistance.operit.ui.features.packages.dialogs

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PackageDetailsDialog(
        packageName: String,
        packageDescription: String,
        toolPackage: ToolPackage?,
        packageManager: PackageManager,
        onRunScript: (String, PackageTool) -> Unit,
        onOpenToolPkgPluginConfig: (String, String, String, Boolean) -> Unit,
        onDismiss: () -> Unit,
        onPackageDeleted: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val resolvedPackage by produceState<ToolPackage?>(initialValue = toolPackage, packageName, toolPackage) {
        value = toolPackage

        val resolved =
            try {
                withContext(Dispatchers.IO) { packageManager.resolvePackageForDisplay(packageName) }
            } catch (e: Exception) {
                AppLogger.e("PackageDetailsDialog", "Failed to load package details", e)
                null
            }

        if (resolved != null) {
            value = resolved
        }
    }

    var toolPkgDetails by remember(packageName, toolPackage) {
        mutableStateOf<PackageManager.ToolPkgContainerDetails?>(null)
    }
    var toolPkgToggleError by remember { mutableStateOf<String?>(null) }
    var showSubpackageToolsDialog by remember { mutableStateOf(false) }
    var subpackageDialogPackageName by remember { mutableStateOf<String?>(null) }
    var subpackageDialogTitle by remember { mutableStateOf("") }
    var subpackageDialogDescription by remember { mutableStateOf("") }
    var subpackageDialogTools by remember { mutableStateOf<List<PackageTool>>(emptyList()) }
    var isLoadingSubpackageTools by remember { mutableStateOf(false) }

    LaunchedEffect(packageName, toolPackage) {
        toolPkgDetails =
            try {
                withContext(Dispatchers.IO) {
                    packageManager.getToolPkgContainerDetails(
                        packageName = packageName,
                        resolveContext = context
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("PackageDetailsDialog", "Failed to load toolpkg details", e)
                null
            }
    }

    val activeStateId by produceState<String?>(initialValue = null, packageName, resolvedPackage) {
        value =
            try {
                withContext(Dispatchers.IO) { packageManager.getActivePackageStateId(packageName) }
            } catch (_: Exception) {
                null
            }
    }

    val metaPackage = toolPackage ?: resolvedPackage
    val isToolPkgContainer = toolPkgDetails != null
    val packageDisplayName =
        toolPkgDetails?.displayName?.takeIf { it.isNotBlank() }
            ?: metaPackage
                ?.displayName
                ?.resolve(context)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: packageName

    val states = (toolPackage ?: resolvedPackage)?.states.orEmpty()
    val hasStates = states.isNotEmpty()
    val baseTools = (toolPackage ?: resolvedPackage)?.tools.orEmpty()
    val contentScrollState = rememberScrollState()

    var selectedTabIndex by remember(packageName, activeStateId, hasStates) {
        val initialIndex = if (!hasStates) {
            0
        } else {
            val idx = states.indexOfFirst { it.id == activeStateId }
            if (idx >= 0) idx + 1 else 0
        }
        mutableStateOf(initialIndex)
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(stringResource(R.string.pkg_confirm_delete)) },
                text = { Text(stringResource(R.string.pkg_delete_warning, packageName)) },
                confirmButton = {
                    Button(
                            onClick = {
                                scope.launch {
                                    val deleted =
                                        withContext(Dispatchers.IO) {
                                            packageManager.deletePackage(packageName)
                                        }
                                    if (deleted) {
                                        showDeleteConfirmDialog = false
                                        onPackageDeleted()
                                    } else {
                                        showDeleteConfirmDialog = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.pkg_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(stringResource(R.string.pkg_cancel))
                    }
                }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            val resolvedAuthors =
                metaPackage
                    ?.author
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    .orEmpty()

            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // 紧凑的标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = packageDisplayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (resolvedAuthors.isNotEmpty()) {
                            Text(
                                text = "作者：${resolvedAuthors.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "ID: $packageName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (metaPackage?.isBuiltIn == true) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (metaPackage?.isBuiltIn == true) stringResource(R.string.builtin) else stringResource(R.string.external),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (metaPackage?.isBuiltIn == true)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                val resolvedDescription =
                    toolPkgDetails?.description?.takeIf { it.isNotBlank() } ?: packageDescription

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(contentScrollState)
                ) {
                    if (resolvedDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = resolvedDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isToolPkgContainer) {
                            stringResource(R.string.pkg_toolpkg_subpackages)
                        } else {
                            stringResource(R.string.pkg_tool_list)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isToolPkgContainer) {
                        val details = toolPkgDetails
                        if (details == null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (details.version.isNotBlank()) {
                                            Text(
                                                text = stringResource(R.string.pkg_toolpkg_version, details.version),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.pkg_toolpkg_resources, details.resourceCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.pkg_toolpkg_wasm_modules, details.wasmModuleCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.pkg_toolpkg_ui_modules, details.uiModuleCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.pkg_toolpkg_workflow_templates, details.workflowTemplateCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.pkg_toolpkg_workspace_templates, details.workspaceTemplateCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (details.toolboxUiModules.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ToolPkgPluginConfigCard(
                                        modules = details.toolboxUiModules,
                                        onOpenToolPkgPluginConfig = onOpenToolPkgPluginConfig
                                    )
                                }

                                if (!toolPkgToggleError.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = toolPkgToggleError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (details.workflowTemplates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.pkg_toolpkg_registered_workflow_templates),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ToolPkgWorkflowTemplatesCard(
                                        templates = details.workflowTemplates
                                    )
                                }

                                if (details.workspaceTemplates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.pkg_toolpkg_registered_workspace_templates),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ToolPkgWorkspaceTemplatesCard(
                                        templates = details.workspaceTemplates
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (details.subpackages.isEmpty()) {
                                    EmptyToolsCard(message = stringResource(R.string.pkg_toolpkg_empty_subpackages))
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        details.subpackages.forEach { subpackage ->
                                            fun applySubpackageToggle(enabled: Boolean) {
                                                toolPkgToggleError = null
                                                val previousDetails = details
                                                toolPkgDetails =
                                                    previousDetails.copy(
                                                        subpackages =
                                                            previousDetails.subpackages.map {
                                                                if (it.packageName == subpackage.packageName) {
                                                                    it.copy(enabled = enabled)
                                                                } else {
                                                                    it
                                                                }
                                                            }
                                                    )

                                                scope.launch {
                                                    val success =
                                                        withContext(Dispatchers.IO) {
                                                            packageManager.setToolPkgSubpackageEnabled(
                                                                subpackage.packageName,
                                                                enabled
                                                            )
                                                        }
                                                    if (!success) {
                                                        toolPkgToggleError = context.getString(R.string.pkg_toolpkg_subpackage_toggle_failed)
                                                    }
                                                    toolPkgDetails =
                                                        withContext(Dispatchers.IO) {
                                                            packageManager.getToolPkgContainerDetails(
                                                                packageName = packageName,
                                                                resolveContext = context
                                                            )
                                                        }
                                                }
                                            }

                                            Surface(
                                                onClick = {
                                                    toolPkgToggleError = null
                                                    scope.launch {
                                                        isLoadingSubpackageTools = true
                                                        val tools =
                                                            withContext(Dispatchers.IO) {
                                                                packageManager
                                                                    .getEffectivePackageTools(subpackage.packageName)
                                                                    ?.tools
                                                                    .orEmpty()
                                                            }
                                                        subpackageDialogPackageName = subpackage.packageName
                                                        subpackageDialogTitle = subpackage.displayName
                                                        subpackageDialogDescription = subpackage.description
                                                        subpackageDialogTools = tools
                                                        isLoadingSubpackageTools = false
                                                        showSubpackageToolsDialog = true
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Widgets,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = subpackage.displayName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (subpackage.description.isNotBlank()) {
                                                            Text(
                                                                text = subpackage.description,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Switch(
                                                        checked = subpackage.enabled,
                                                        onCheckedChange = { enabled -> applySubpackageToggle(enabled) },
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!hasStates) {
                        val tools = resolvedPackage?.tools.orEmpty()
                        if (tools.isEmpty()) {
                            EmptyToolsCard(message = stringResource(R.string.pkg_no_tools))
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tools.forEach { tool ->
                                    ToolCard(
                                        tool = tool,
                                        toolIdPrefix = packageName,
                                        onExecute = { onRunScript(packageName, tool) }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTabIndex,
                                edgePadding = 0.dp
                            ) {
                                Tab(
                                    selected = selectedTabIndex == 0,
                                    onClick = { selectedTabIndex = 0 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = stringResource(R.string.default_option))
                                        if (activeStateId.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                states.forEachIndexed { index, state ->
                                    val tabIndex = index + 1
                                    val isActive = activeStateId == state.id
                                    Tab(
                                        selected = selectedTabIndex == tabIndex,
                                        onClick = { selectedTabIndex = tabIndex }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = state.id)
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val toolsForTab = remember(selectedTabIndex, toolPackage) {
                                if (selectedTabIndex == 0) {
                                    baseTools
                                } else {
                                    val state = states.getOrNull(selectedTabIndex - 1)
                                    if (state == null) {
                                        emptyList()
                                    } else {
                                        val toolMap = linkedMapOf<String, PackageTool>()
                                        if (state.inheritTools) {
                                            baseTools.forEach { toolMap[it.name] = it }
                                        }
                                        state.excludeTools.forEach { toolMap.remove(it) }
                                        state.tools.forEach { toolMap[it.name] = it }
                                        toolMap.values.toList()
                                    }
                                }
                            }

                            if (toolsForTab.isEmpty()) {
                                EmptyToolsCard(message = stringResource(R.string.mcp_no_available_tools))
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    toolsForTab.forEach { tool ->
                                        ToolCard(
                                            tool = tool,
                                            toolIdPrefix = packageName,
                                            onExecute = { onRunScript(packageName, tool) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (metaPackage != null && !metaPackage.isBuiltIn) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.pkg_delete))
                        }
                    }
                    
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(R.string.pkg_close))
                    }
                }
            }
        }
    }

    if (showSubpackageToolsDialog) {
        Dialog(
            onDismissRequest = {
                showSubpackageToolsDialog = false
                subpackageDialogPackageName = null
                subpackageDialogDescription = ""
                subpackageDialogTools = emptyList()
            }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = subpackageDialogTitle.ifBlank { subpackageDialogPackageName.orEmpty() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!subpackageDialogPackageName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ID: ${subpackageDialogPackageName.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (subpackageDialogDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subpackageDialogDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isLoadingSubpackageTools) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (subpackageDialogTools.isEmpty()) {
                        EmptyToolsCard(message = stringResource(R.string.pkg_no_tools))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = true),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(items = subpackageDialogTools, key = { tool -> tool.name }) { tool ->
                                ToolCard(
                                    tool = tool,
                                    toolIdPrefix = subpackageDialogPackageName ?: packageName,
                                    onExecute = {
                                        val targetPackage = subpackageDialogPackageName
                                        if (!targetPackage.isNullOrBlank()) {
                                            onRunScript(targetPackage, tool)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = {
                                showSubpackageToolsDialog = false
                                subpackageDialogPackageName = null
                                subpackageDialogDescription = ""
                                subpackageDialogTools = emptyList()
                            }
                        ) {
                            Text(stringResource(R.string.pkg_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyToolsCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ToolPkgPluginConfigCard(
    modules: List<PackageManager.ToolPkgToolboxUiModule>,
    onOpenToolPkgPluginConfig: (String, String, String, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modules.forEach { module ->
            Surface(
                onClick = {
                    onOpenToolPkgPluginConfig(
                        module.containerPackageName,
                        module.uiModuleId,
                        module.title,
                        module.keepAlive
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = module.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pkg_plugin_config_open_action),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPkgWorkflowTemplatesCard(
    templates: List<PackageManager.ToolPkgWorkflowTemplate>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        templates.forEach { template ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (template.description.isNotBlank()) {
                            Text(
                                text = template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPkgWorkspaceTemplatesCard(
    templates: List<PackageManager.ToolPkgWorkspaceTemplate>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        templates.forEach { template ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (template.description.isNotBlank()) {
                            Text(
                                text = template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = template.projectType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ToolCard(
    tool: PackageTool,
    toolIdPrefix: String,
    onExecute: (PackageTool) -> Unit
) {
    val context = LocalContext.current
    val fullToolId = if (toolIdPrefix.isBlank()) tool.name else "$toolIdPrefix:${tool.name}"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = tool.description.resolve(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: $fullToolId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                FilledTonalButton(
                    onClick = { onExecute(tool) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.run),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            // 参数信息
            if (tool.parameters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    tool.parameters.take(3).forEach { param ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = param.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (tool.parameters.size > 3) {
                        Text(
                            text = "+${tool.parameters.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
