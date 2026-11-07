package com.ai.assistance.operit.ui.features.extensions.packages.screens

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.ui.features.extensions.packages.components.*
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.ui.features.extensions.packages.market.readMarketInstallMarkerForPackage
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageDialogHeader
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageDialogLayout
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageEnvironmentSection
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageToolCard
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.ToolPkgRuntimeMonitor
import com.ai.assistance.operit.ui.common.icons.rememberLogoPainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InstalledExtensionOrigin(val marketEntryId: String?, val scriptPath: String?)

private sealed interface PackageUpdateCheckState {
    data object UpToDate : PackageUpdateCheckState
    data class UpdateAvailable(
        val entry: MarketV2Entry,
        val latestVersion: String
    ) : PackageUpdateCheckState
    data object NotFromMarket : PackageUpdateCheckState
    data object EntryMissing : PackageUpdateCheckState
    data object Failed : PackageUpdateCheckState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackageDetailsScreen(
        packageName: String,
        packageDescription: String,
        toolPackage: ToolPackage?,
        packageManager: PackageManager,
        isContainerEnabled: Boolean,
        isToggling: Boolean,
        toggleError: String?,
        onToggleEnabled: (Boolean) -> Unit,
        onRunScript: (String, PackageTool) -> Unit,
        onOpenToolPkgPluginConfig: (String, String, String, Boolean) -> Unit,
        onPackageDeleted: () -> Unit,
        onOpenMarketDetail: (MarketV2Entry) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember(packageName) { mutableStateOf(false) }
    var deleteFailed by remember(packageName) { mutableStateOf(false) }
    var showRuntimeMonitorDialog by remember(packageName) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var updateCheckState by remember(packageName) { mutableStateOf<PackageUpdateCheckState?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    fun runUpdateCheck() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        updateCheckState = null
        scope.launch {
            try {
                val marker =
                    withContext(Dispatchers.IO) {
                        readMarketInstallMarkerForPackage(packageManager, packageName)
                    }
                if (marker == null) {
                    updateCheckState = PackageUpdateCheckState.NotFromMarket
                    return@launch
                }
                val entryResult =
                    withContext(Dispatchers.IO) {
                        MarketStatsApiService().getEntry(marker.entryId)
                    }
                if (entryResult.isFailure) {
                    updateCheckState = PackageUpdateCheckState.Failed
                    return@launch
                }
                val entry = entryResult.getOrNull()
                if (entry == null) {
                    updateCheckState = PackageUpdateCheckState.EntryMissing
                    return@launch
                }
                val latest = entry.latestVersion
                if (latest == null || latest.id.isBlank() || latest.id == marker.versionId) {
                    updateCheckState = PackageUpdateCheckState.UpToDate
                } else {
                    updateCheckState =
                        PackageUpdateCheckState.UpdateAvailable(
                            entry = entry,
                            latestVersion = latest.version.ifBlank { latest.id }
                        )
                }
            } catch (error: Exception) {
                AppLogger.e("PackageDetailsScreen", "Update check failed", error)
                updateCheckState = PackageUpdateCheckState.Failed
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    val resolvedPackage by produceState<ToolPackage?>(initialValue = toolPackage, packageName, toolPackage) {
        value = toolPackage

        val resolved =
            try {
                withContext(Dispatchers.IO) { packageManager.resolvePackageForDisplay(packageName) }
            } catch (e: Exception) {
                AppLogger.e("PackageDetailsScreen", "Failed to load package details", e)
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
    var subpackageDialogEnv by remember { mutableStateOf<List<EnvVar>>(emptyList()) }
    var isLoadingSubpackageTools by remember { mutableStateOf(false) }

    val isCurrentScreen = LocalIsCurrentScreen.current
    LaunchedEffect(packageName, toolPackage, isContainerEnabled, isCurrentScreen) {
        if (!isCurrentScreen) return@LaunchedEffect
        // A retained detail route must reread child states after its configuration UI closes.
        toolPkgDetails =
            try {
                withContext(Dispatchers.IO) {
                    packageManager.getToolPkgContainerDetails(
                        packageName = packageName,
                        resolveContext = context
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("PackageDetailsScreen", "Failed to load toolpkg details", e)
                null
            }
    }

    val logo by produceState<PackageManager.ToolPkgLogoBytes?>(
        initialValue = null,
        packageName,
        toolPkgDetails?.logoResourceKey,
        toolPkgDetails?.version
    ) {
        value =
            if (toolPkgDetails?.logoResourceKey == null) {
                null
            } else {
                try {
                    withContext(Dispatchers.IO) { packageManager.readToolPkgLogoBytes(packageName) }
                } catch (error: Exception) {
                    AppLogger.e("PackageDetailsScreen", "Failed to load toolpkg logo", error)
                    null
                }
            }
    }
    val logoPainter =
        rememberLogoPainter(
            logoKey = "${packageName}:${toolPkgDetails?.version}:${toolPkgDetails?.logoResourceKey}",
            bytes = logo?.bytes,
            mimeType = logo?.mimeType,
            fileName = logo?.fileName,
            size = 80.dp
        )

    val activeStateId by produceState<String?>(initialValue = null, packageName, resolvedPackage) {
        value =
            try {
                withContext(Dispatchers.IO) { packageManager.getActivePackageStateId(packageName) }
            } catch (_: Exception) {
                null
            }
    }

    val metaPackage = toolPackage ?: resolvedPackage
    val isToolPkgContainer = remember(packageName) { packageManager.isToolPkgContainer(packageName) }
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
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.pkg_confirm_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.pkg_delete_warning, packageName))
                    if (deleteFailed) Text(
                        stringResource(R.string.pkg_delete_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDeleting,
                    onClick = {
                        isDeleting = true
                        deleteFailed = false
                        scope.launch {
                            try {
                                val deleted = withContext(Dispatchers.IO) { packageManager.deletePackage(packageName) }
                                if (deleted) {
                                    showDeleteConfirmDialog = false
                                    onPackageDeleted()
                                } else deleteFailed = true
                            } catch (error: kotlinx.coroutines.CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                AppLogger.e("PackageDetailsScreen", "Package deletion failed: $packageName", error)
                                deleteFailed = true
                            } finally {
                                isDeleting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.pkg_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !isDeleting, onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.pkg_cancel))
                }
            },
        )
    }

    if (showRuntimeMonitorDialog) {
        ToolPkgRuntimeMonitorDialog(
            packageName = packageName,
            packageDisplayName = packageDisplayName,
            packageManager = packageManager,
            onDismiss = { showRuntimeMonitorDialog = false }
        )
    }

    val currentUpdateCheckState = updateCheckState
    if (currentUpdateCheckState != null) {
        AlertDialog(
            onDismissRequest = { updateCheckState = null },
            title = { Text(stringResource(R.string.pkg_check_update)) },
            text = {
                Text(
                    when (currentUpdateCheckState) {
                        is PackageUpdateCheckState.UpToDate ->
                            stringResource(R.string.pkg_check_update_up_to_date)
                        is PackageUpdateCheckState.UpdateAvailable ->
                            stringResource(
                                R.string.pkg_check_update_available,
                                currentUpdateCheckState.latestVersion
                            )
                        PackageUpdateCheckState.NotFromMarket ->
                            stringResource(R.string.pkg_check_update_not_market)
                        PackageUpdateCheckState.EntryMissing ->
                            stringResource(R.string.pkg_check_update_entry_missing)
                        PackageUpdateCheckState.Failed ->
                            stringResource(R.string.pkg_check_update_failed)
                    }
                )
            },
            confirmButton = {
                if (currentUpdateCheckState is PackageUpdateCheckState.UpdateAvailable) {
                    Button(
                        onClick = {
                            updateCheckState = null
                            onOpenMarketDetail(currentUpdateCheckState.entry)
                        }
                    ) {
                        Text(stringResource(R.string.pkg_check_update_go))
                    }
                } else {
                    Button(onClick = { updateCheckState = null }) {
                        Text(stringResource(R.string.pkg_ok))
                    }
                }
            },
            dismissButton = {
                if (currentUpdateCheckState is PackageUpdateCheckState.UpdateAvailable) {
                    TextButton(onClick = { updateCheckState = null }) {
                        Text(stringResource(R.string.pkg_cancel))
                    }
                }
            }
        )
    }

    val details = toolPkgDetails
    val resolvedAuthors = (details?.author ?: metaPackage?.author.orEmpty()).map(String::trim).filter(String::isNotBlank)
    val resolvedDescription = if (isToolPkgContainer && details != null) details.description else packageDescription
    val version = if (isToolPkgContainer) details?.version.orEmpty() else metaPackage?.version.orEmpty()
    val typeLabel = stringResource(if (isToolPkgContainer) R.string.nav_group_plugins else R.string.packages)
    val enabledLabel = stringResource(if (isContainerEnabled) R.string.extension_detail_enabled else R.string.extension_detail_disabled)
    var origin by remember(packageName) { mutableStateOf<InstalledExtensionOrigin?>(null) }
    var originFailed by remember(packageName) { mutableStateOf(false) }
    var showInterfacePicker by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(packageName, isCurrentScreen) {
        if (!isCurrentScreen) return@LaunchedEffect
        try {
            origin = withContext(Dispatchers.IO) {
                val marker = readMarketInstallMarkerForPackage(packageManager, packageName)
                val scriptPath = if (!isToolPkgContainer) {
                    packageManager.getPublishablePackageSources().firstOrNull { it.packageName == packageName }?.sourcePath
                } else null
                InstalledExtensionOrigin(marker?.entryId, scriptPath)
            }
            originFailed = false
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e("PackageDetailsScreen", "Failed to read installed metadata for $packageName", error)
            originFailed = true
        }
    }
    val sourceLabel = when {
        metaPackage?.isBuiltIn == true -> stringResource(R.string.builtin)
        originFailed -> stringResource(R.string.extension_detail_source_unavailable)
        origin == null -> "…"
        origin?.marketEntryId != null -> stringResource(R.string.extension_detail_market_source)
        else -> stringResource(R.string.extension_detail_imported)
    }
    val modules = details?.uiModules.orEmpty()
    fun openModule(module: PackageManager.ToolPkgUiModule) {
        onOpenToolPkgPluginConfig(module.containerPackageName, module.uiModuleId, module.title, module.keepAlive)
    }
    if (showInterfacePicker) {
        AlertDialog(
            onDismissRequest = { showInterfacePicker = false },
            title = { Text(stringResource(R.string.extension_interfaces)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    modules.forEach { module ->
                        TextButton(onClick = { showInterfacePicker = false; openModule(module) }) { Text(module.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInterfacePicker = false }) { Text(stringResource(R.string.pkg_close)) } },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(contentScrollState).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            ExtensionDetailHeader(
                title = packageDisplayName,
                subtitle = sourceLabel,
                badges = buildList { add(typeLabel); if (version.isNotBlank()) add("v$version") },
                logo = logoPainter,
                actions = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (modules.isNotEmpty()) {
                            Button(onClick = {
                                if (modules.size == 1) openModule(modules.single()) else showInterfacePicker = true
                            }, enabled = !isDeleting, shape = RoundedCornerShape(999.dp)) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.open))
                            }
                        }
                        Surface(shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(enabledLabel, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(5.dp))
                                ExtensionSwitch(checked = isContainerEnabled, onCheckedChange = onToggleEnabled,
                                    modifier = Modifier.semantics { contentDescription = packageDisplayName },
                                    enabled = !isToggling && !isDeleting)
                            }
                        }
                    }
                },
            )
            if (toggleError != null) Text(toggleError, color = MaterialTheme.colorScheme.error)
            ExtensionDetailStats(buildList {
                add(ExtensionDetailStat(stringResource(R.string.extension_detail_type), typeLabel,
                    if (isToolPkgContainer) Icons.Default.Extension else Icons.Default.Code))
                add(ExtensionDetailStat(stringResource(R.string.extension_detail_status), enabledLabel))
                add(ExtensionDetailStat(stringResource(R.string.extension_detail_version), version.ifBlank { "—" }))
                if (isToolPkgContainer) {
                    add(ExtensionDetailStat(stringResource(R.string.extension_detail_subpackages), details?.subpackages?.size?.toString() ?: "…"))
                    add(ExtensionDetailStat(stringResource(R.string.extension_interfaces), details?.uiModuleCount?.toString() ?: "…"))
                } else {
                    add(ExtensionDetailStat(stringResource(R.string.extension_detail_tools), resolvedPackage?.tools?.size?.toString() ?: "…"))
                }
            })
            if (resolvedDescription.isNotBlank()) {
                ExtensionDetailDescription(stringResource(R.string.extension_detail_brief), resolvedDescription,
                    stateKey = "$packageName:description", previewLines = 4, markdown = true)
            }
            if (modules.isNotEmpty()) {
                ExtensionDetailSection(stringResource(R.string.extension_interfaces), modules.size) {
                    ToolPkgPluginConfigCard(modules, onOpenToolPkgPluginConfig)
                }
            }
            if (metaPackage != null && metaPackage.env.isNotEmpty()) PackageEnvironmentSection(metaPackage.env)
            if (isToolPkgContainer) {
                ExtensionDetailSection(stringResource(R.string.extension_detail_subpackages), details?.subpackages?.size) {
                    if (details == null) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        if (details.subpackages.isEmpty()) {
                            ExtensionDetailEmpty(stringResource(R.string.extension_detail_empty_subpackages))
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                details.subpackages.forEach { subpackage ->
                                    fun applySubpackageToggle(enabled: Boolean) {
                                        // 父插件未启用时禁止改动子包，避免开关先亮起又被有效状态刷新关闭。
                                        if (!isContainerEnabled || isToggling) return
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
                                                val subpackageInfo =
                                                    withContext(Dispatchers.IO) {
                                                        packageManager.getEffectivePackageTools(subpackage.packageName)
                                                    }
                                                subpackageDialogPackageName = subpackage.packageName
                                                subpackageDialogTitle = subpackage.displayName
                                                subpackageDialogDescription = subpackage.description
                                                subpackageDialogTools = subpackageInfo?.tools.orEmpty()
                                                subpackageDialogEnv = subpackageInfo?.env.orEmpty()
                                                isLoadingSubpackageTools = false
                                                showSubpackageToolsDialog = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
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
                                            ExtensionSwitch(
                                                checked = isContainerEnabled && subpackage.enabled,
                                                onCheckedChange = { enabled -> applySubpackageToggle(enabled) },
                                                enabled = isContainerEnabled && !isToggling,
                                                modifier = Modifier.semantics { contentDescription = subpackage.displayName }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!toolPkgToggleError.isNullOrBlank()) Text(toolPkgToggleError.orEmpty(), color = MaterialTheme.colorScheme.error)
                if (details != null) {
                    if (details.requires.isNotEmpty()) {
                        ExtensionDetailSection(stringResource(R.string.extension_detail_dependencies), details.requires.size) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(horizontal = 16.dp)) {
                                    details.requires.forEachIndexed { index, requirement ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(requirement.id, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                val description = buildString {
                                                    if (requirement.description.isNotBlank()) append(requirement.description)
                                                    requirement.minVersion?.let { append("  ≥ "); append(it) }
                                                    requirement.maxVersion?.let { append("  ≤ "); append(it) }
                                                }.trim()
                                                if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (index < details.requires.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    }
                                }
                            }
                        }
                    }
                    ExtensionDetailContents(listOf(
                        ExtensionDetailStat(stringResource(R.string.extension_detail_resources), details.resourceCount.toString()),
                        ExtensionDetailStat(stringResource(R.string.extension_detail_wasm), details.wasmModuleCount.toString()),
                        ExtensionDetailStat(stringResource(R.string.extension_interfaces), details.uiModuleCount.toString()),
                        ExtensionDetailStat(stringResource(R.string.extension_detail_navigation), details.navigationEntryCount.toString()),
                        ExtensionDetailStat(stringResource(R.string.extension_detail_widgets), details.desktopWidgetCount.toString()),
                        ExtensionDetailStat(stringResource(R.string.extension_detail_workflows), details.workflowTemplateCount.toString()),
                        ExtensionDetailStat(stringResource(R.string.extension_detail_workspaces), details.workspaceTemplateCount.toString()),
                    ))
                    if (details.workflowTemplates.isNotEmpty()) {
                        ExtensionDetailSection(stringResource(R.string.pkg_toolpkg_registered_workflow_templates)) {
                            ToolPkgWorkflowTemplatesCard(details.workflowTemplates)
                        }
                    }
                    if (details.workspaceTemplates.isNotEmpty()) {
                        ExtensionDetailSection(stringResource(R.string.pkg_toolpkg_registered_workspace_templates)) {
                            ToolPkgWorkspaceTemplatesCard(details.workspaceTemplates)
                        }
                    }
                }
            } else {
                ExtensionDetailSection(stringResource(R.string.pkg_tool_list)) {
                    if (!hasStates) {
                        val tools = resolvedPackage?.tools.orEmpty()
                        if (tools.isEmpty()) {
                            EmptyToolsCard(message = stringResource(R.string.pkg_no_tools))
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tools.forEach { tool ->
                                    PackageToolCard(
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

                            val toolsForTab = remember(selectedTabIndex, states, baseTools) {
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
                                        PackageToolCard(
                                            tool = tool,
                                            toolIdPrefix = packageName,
                                            onExecute = { onRunScript(packageName, tool) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ExtensionDetailFacts(stringResource(R.string.extension_detail_information), buildList {
                add(ExtensionDetailFact("ID", packageName, monospace = true))
                add(ExtensionDetailFact(stringResource(R.string.extension_detail_type), typeLabel))
                if (version.isNotBlank()) add(ExtensionDetailFact(stringResource(R.string.extension_detail_version), version))
                add(ExtensionDetailFact(stringResource(R.string.extension_detail_source), sourceLabel))
                if (resolvedAuthors.isNotEmpty()) add(ExtensionDetailFact(stringResource(R.string.market_detail_author_role), resolvedAuthors.joinToString(", ")))
                if (isToolPkgContainer) {
                    add(ExtensionDetailFact(stringResource(R.string.market_detail_category_label), "ToolPkg"))
                    if (details != null) {
                        add(ExtensionDetailFact("ToolPkg API", details.apiVersion))
                        if (details.mainEntry.isNotEmpty()) add(ExtensionDetailFact(stringResource(R.string.extension_detail_entry), details.mainEntry, true))
                        if (details.sourcePath.isNotEmpty()) add(ExtensionDetailFact(stringResource(R.string.extension_detail_path), details.sourcePath, true))
                    }
                } else {
                    metaPackage?.let { add(ExtensionDetailFact(stringResource(R.string.market_detail_category_label), it.category)) }
                    origin?.scriptPath?.let { add(ExtensionDetailFact(stringResource(R.string.extension_detail_path), it, true)) }
                }
                origin?.marketEntryId?.let { add(ExtensionDetailFact(stringResource(R.string.extension_detail_market_entry), it, true)) }
                metaPackage?.let {
                    add(ExtensionDetailFact(stringResource(R.string.extension_detail_default_state),
                        stringResource(if (it.enabledByDefault) R.string.pkg_details_default_enabled else R.string.pkg_details_default_disabled)))
                }
            })
            ExtensionDetailSection(stringResource(R.string.extension_detail_manage)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showRuntimeMonitorDialog = true }) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pkg_runtime_monitor_action))
                    }
                    if (metaPackage != null && !metaPackage.isBuiltIn) {
                        OutlinedButton(onClick = { runUpdateCheck() }, enabled = !isCheckingUpdate && !isDeleting) {
                            if (isCheckingUpdate) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pkg_check_update))
                        }
                    }
                }
                if (metaPackage != null && !metaPackage.isBuiltIn) {
                    Surface(onClick = { deleteFailed = false; showDeleteConfirmDialog = true },
                        enabled = !isDeleting && packageManager.isTopLevelPackage(packageName),
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.extension_detail_remove), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.extension_detail_remove_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.pkg_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showSubpackageToolsDialog) {
        val dismissSubpackage = {
            showSubpackageToolsDialog = false
            subpackageDialogPackageName = null
            subpackageDialogDescription = ""
            subpackageDialogTools = emptyList()
            subpackageDialogEnv = emptyList()
        }
        PackageDialogLayout(onDismiss = dismissSubpackage) { compact ->
            PackageDialogHeader(
                title = subpackageDialogTitle.ifBlank { subpackageDialogPackageName.orEmpty() },
                icon = Icons.Default.Widgets,
                compact = compact,
                onDismiss = dismissSubpackage,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "ID: ${subpackageDialogPackageName.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subpackageDialogDescription.isNotBlank()) {
                    Text(
                        text = subpackageDialogDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PackageEnvironmentSection(subpackageDialogEnv)
                if (isLoadingSubpackageTools) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (subpackageDialogTools.isEmpty()) {
                    EmptyToolsCard(message = stringResource(R.string.pkg_no_tools))
                } else {
                    Text(
                        text = stringResource(R.string.pkg_tool_list),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    subpackageDialogTools.forEach { tool ->
                        PackageToolCard(
                            tool = tool,
                            toolIdPrefix = subpackageDialogPackageName.orEmpty(),
                            onExecute = {
                                val targetPackage = subpackageDialogPackageName
                                if (!targetPackage.isNullOrBlank()) onRunScript(targetPackage, tool)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = dismissSubpackage) { Text(stringResource(R.string.pkg_close)) }
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
private fun ToolPkgRuntimeMonitorDialog(
    packageName: String,
    packageDisplayName: String,
    packageManager: PackageManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var snapshot by remember(packageName) {
        mutableStateOf<ToolPkgRuntimeMonitor.Snapshot?>(null)
    }

    LaunchedEffect(packageName) {
        while (true) {
            snapshot =
                withContext(Dispatchers.IO) {
                    packageManager.getToolPkgRuntimeMonitorSnapshot(packageName)
                }
            delay(1_000L)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pkg_runtime_monitor_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = packageDisplayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val currentSnapshot = snapshot
                if (currentSnapshot == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "note") {
                            Text(
                                text = stringResource(R.string.pkg_runtime_monitor_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        item(key = "runtime") {
                            RuntimeMonitorSection(
                                title = stringResource(R.string.pkg_runtime_monitor_summary_title),
                                icon = Icons.Default.Assessment
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_active_calls),
                                        value = currentSnapshot.activeCalls.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_started_calls),
                                        value = currentSnapshot.startedCalls.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_completed_calls),
                                        value = currentSnapshot.completedCalls.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_failed_calls),
                                        value = currentSnapshot.failedCalls.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_avg_duration),
                                        value = formatRuntimeDuration(currentSnapshot.averageDurationMs),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_max_duration),
                                        value = formatRuntimeDuration(currentSnapshot.maxDurationMs),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_last_duration),
                                    value = formatRuntimeDuration(currentSnapshot.lastDurationMs)
                                )
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_last_function),
                                    value = currentSnapshot.lastFunctionName ?: "-"
                                )
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_last_event),
                                    value = currentSnapshot.lastEvent ?: "-"
                                )
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_last_finish),
                                    value = formatRuntimeTimestamp(
                                        currentSnapshot.lastFinishedAtMs,
                                        timeFormatter
                                    )
                                )
                            }
                        }

                        item(key = "memory") {
                            RuntimeMonitorSection(
                                title = stringResource(R.string.pkg_runtime_monitor_memory_title),
                                icon = Icons.Default.Memory
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_heap_used),
                                        value = formatRuntimeMemory(
                                            currentSnapshot.currentMemory?.jsHeapUsedKb
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_pss_total),
                                        value = formatRuntimeMemory(
                                            currentSnapshot.currentMemory?.jsMallocUsedKb
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_native_heap),
                                        value = formatRuntimeMemory(
                                            currentSnapshot.currentMemory?.jsPeakMallocUsedKb
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RuntimeMetricTile(
                                        label = stringResource(R.string.pkg_runtime_monitor_heap_limit),
                                        value = formatRuntimeMemoryDelta(
                                            currentSnapshot.peakMemoryPeakDeltaKb
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_active_heap_delta),
                                    value = formatRuntimeMemoryDelta(currentSnapshot.currentMemoryDeltaKb)
                                )
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_active_pss_delta),
                                    value = formatRuntimeMemoryDelta(
                                        currentSnapshot.currentMemoryPeakDeltaKb
                                    )
                                )
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_last_heap_delta),
                                    value = formatRuntimeMemoryDelta(currentSnapshot.lastMemoryDeltaKb)
                                )
                                RuntimeInfoLine(
                                    label = stringResource(R.string.pkg_runtime_monitor_last_pss_delta),
                                    value = formatRuntimeMemoryDelta(
                                        currentSnapshot.lastMemoryPeakDeltaKb
                                    )
                                )
                            }
                        }

                        item(key = "logs") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.pkg_runtime_monitor_logs_title),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    packageManager.clearToolPkgRuntimeMonitorLogs(packageName)
                                                }
                                                snapshot =
                                                    withContext(Dispatchers.IO) {
                                                        packageManager.getToolPkgRuntimeMonitorSnapshot(packageName)
                                                    }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.pkg_runtime_monitor_clear_logs))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (currentSnapshot.logs.isEmpty()) {
                                    EmptyToolsCard(
                                        message = stringResource(R.string.pkg_runtime_monitor_logs_empty)
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().height(240.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            items = currentSnapshot.logs.asReversed(),
                                            key = { entry -> entry.sequence }
                                        ) { entry ->
                                            RuntimeLogEntryCard(entry, timeFormatter)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(R.string.pkg_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeMonitorSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RuntimeMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RuntimeInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(118.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RuntimeLogEntryCard(
    entry: ToolPkgRuntimeMonitor.LogEntry,
    timeFormatter: SimpleDateFormat
) {
    val levelColor =
        when (entry.level.lowercase(Locale.getDefault())) {
            "error" -> MaterialTheme.colorScheme.errorContainer
            "warn" -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    val levelTextColor =
        when (entry.level.lowercase(Locale.getDefault())) {
            "error" -> MaterialTheme.colorScheme.onErrorContainer
            "warn" -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = levelColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                text =
                    buildString {
                        append(formatRuntimeTimestamp(entry.timestampMs, timeFormatter))
                        append(" [")
                        append(entry.level.uppercase(Locale.getDefault()))
                        append("]")
                        if (!entry.pluginId.isNullOrBlank()) {
                            append(" ")
                            append(entry.pluginId)
                        }
                    },
                style = MaterialTheme.typography.labelSmall,
                color = levelTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    buildString {
                        append(entry.functionName)
                        if (!entry.event.isNullOrBlank()) {
                            append(" / ")
                            append(entry.event)
                        }
                    },
                style = MaterialTheme.typography.labelSmall,
                color = levelTextColor.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = levelTextColor,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatRuntimeMemory(kb: Long?): String {
    if (kb == null) {
        return "-"
    }
    val absoluteKb = abs(kb)
    val sign = if (kb < 0L) "-" else ""
    return if (absoluteKb >= 1024L) {
        String.format(Locale.getDefault(), "%s%.1f MB", sign, absoluteKb / 1024.0)
    } else {
        "$sign$absoluteKb KB"
    }
}

private fun formatRuntimeMemoryDelta(kb: Long?): String {
    if (kb == null) {
        return "-"
    }
    val prefix = if (kb > 0L) "+" else ""
    return prefix + formatRuntimeMemory(kb)
}

private fun formatRuntimeDuration(durationMs: Long?): String {
    if (durationMs == null) {
        return "-"
    }
    return if (durationMs >= 1_000L) {
        String.format(Locale.getDefault(), "%.2f s", durationMs / 1_000.0)
    } else {
        "$durationMs ms"
    }
}

private fun formatRuntimeTimestamp(timestampMs: Long?, formatter: SimpleDateFormat): String {
    if (timestampMs == null) {
        return "-"
    }
    return formatter.format(Date(timestampMs))
}

@Composable
private fun ToolPkgPluginConfigCard(
    modules: List<PackageManager.ToolPkgUiModule>,
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
                            text = stringResource(R.string.open),
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
