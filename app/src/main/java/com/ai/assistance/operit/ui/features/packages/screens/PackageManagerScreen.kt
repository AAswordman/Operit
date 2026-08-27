package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.util.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgMarketOrigin
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPEnvironmentVariablesDialog
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.QuickPluginCreatorDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.PackagesList
import com.ai.assistance.operit.ui.features.packages.market.BindMarketSearchToTopBar
import com.ai.assistance.operit.ui.features.packages.market.PluginCreationIntent
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.R
private data class ExternalPackageImportResult(
    val message: String,
    val marketOrigin: ToolPkgMarketOrigin?,
    val availablePackages: Map<String, ToolPackage>,
    val allAvailablePackages: Map<String, ToolPackage>,
    val pluginContainers: Map<String, PackageManager.ToolPkgContainerDetails>,
    val importedPackages: List<String>,
    val packageLoadErrors: Map<String, String>,
    val packageLoadErrorInfos: List<PackageManager.PackageLoadErrorInfo>,
    val newPackageLoadErrors: Map<String, String>
)

private data class PackageManagerSnapshot(
    val availablePackages: Map<String, ToolPackage>,
    val allAvailablePackages: Map<String, ToolPackage>,
    val pluginContainers: Map<String, PackageManager.ToolPkgContainerDetails>,
    val importedPackages: List<String>,
    val packageLoadErrors: Map<String, String>,
    val packageLoadErrorInfos: List<PackageManager.PackageLoadErrorInfo>
)

private suspend fun runQuickPluginCreatorSetupAndPublishResult(
    context: android.content.Context,
    packageManager: PackageManager,
    toolHandler: AIToolHandler,
    onRunningChange: (Boolean) -> Unit,
    onResult: (ToolResult?) -> Unit,
    onMessage: suspend (String) -> Unit
) {
    onRunningChange(true)
    onResult(null)
    val result =
        withContext(Dispatchers.IO) {
            runQuickPluginCreatorSetup(
                context = context,
                packageManager = packageManager,
                toolHandler = toolHandler
            )
        }
    onResult(result)
    onRunningChange(false)
    onMessage(
        if (result.success) {
            result.result.toString()
        } else {
            result.error ?: context.getString(R.string.quick_plugin_creator_setup_failed)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageManagerScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToArtifactMarket: () -> Unit = {},
    onStartPluginCreation: (PluginCreationIntent) -> Unit = {},
    onOpenToolPkgPluginConfig: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val packageManager = remember {
        PackageManager.getInstance(context, toolHandler)
    }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }

    val envPreferences = remember { EnvPreferences.getInstance(context) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val allAvailablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val pluginContainers =
        remember { mutableStateOf<Map<String, PackageManager.ToolPkgContainerDetails>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    // UI展示用的导入状态列表，与后端状态分离
    val visibleImportedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var selectedToolPackageName by remember { mutableStateOf<String?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state
    var selectedTab by rememberSaveable { mutableStateOf(PackageTab.PLUGINS) }
    var pluginSearchInput by rememberSaveable { mutableStateOf("") }
    var pluginSearchQuery by rememberSaveable { mutableStateOf("") }
    val filteredPluginContainers by remember(pluginContainers.value, pluginSearchQuery) {
        derivedStateOf {
            val pluginsMap = pluginContainers.value
            val searchText = pluginSearchQuery.trim()
            if (searchText.isEmpty()) {
                pluginsMap
            } else {
                pluginsMap.filter { (packageName, details) ->
                    pluginMatchesSearch(
                        packageName = packageName,
                        details = details,
                        searchText = searchText
                    )
                }
            }
        }
    }
    var packageSearchInput by rememberSaveable { mutableStateOf("") }
    var packageSearchQuery by rememberSaveable { mutableStateOf("") }
    var filteredAvailablePackages by remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    var isPackageSearchFiltering by remember { mutableStateOf(false) }
    var skillSearchInput by rememberSaveable { mutableStateOf("") }
    var skillSearchQuery by rememberSaveable { mutableStateOf("") }
    var mcpSearchInput by rememberSaveable { mutableStateOf("") }
    var mcpSearchQuery by rememberSaveable { mutableStateOf("") }

    // Environment variables dialog state
    var showEnvDialog by remember { mutableStateOf(false) }
    var envVariables by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val packageLoadErrors = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val packageLoadErrorInfos =
        remember { mutableStateOf<List<PackageManager.PackageLoadErrorInfo>>(emptyList()) }
    var showPackageLoadErrorsDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var marketOriginImportNotice by remember { mutableStateOf<ToolPkgMarketOrigin?>(null) }
    var pluginOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var skillOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var showQuickPluginCreatorDialog by remember { mutableStateOf(false) }
    var quickPluginRequirement by rememberSaveable { mutableStateOf("") }
    var quickPluginSetupRunning by remember { mutableStateOf(false) }
    var quickPluginSetupResult by remember { mutableStateOf<ToolResult?>(null) }

    val requiredEnvByPackage by remember {
        derivedStateOf {
            val packagesMap = allAvailablePackages.value
            val imported = importedPackages.value.toSet()

            imported
                .mapNotNull { packageName ->
                    packagesMap[packageName]
                }
                .sortedBy { it.name }
                .associate { toolPackage ->
                    toolPackage.name to toolPackage.env
                }
                .filterValues { envVars -> envVars.isNotEmpty() }
        }
    }

    val requiredEnvKeys by remember {
        derivedStateOf {
            requiredEnvByPackage.values
                .flatten()
                .map { it.name }
                .toSet()
                .toList()
                .sorted()
        }
    }

    LaunchedEffect(pluginSearchInput) {
        delay(320)
        pluginSearchQuery = pluginSearchInput.trim()
    }

    LaunchedEffect(packageSearchInput) {
        delay(320)
        packageSearchQuery = packageSearchInput.trim()
    }

    LaunchedEffect(skillSearchInput) {
        delay(320)
        skillSearchQuery = skillSearchInput.trim()
    }

    LaunchedEffect(mcpSearchInput) {
        delay(320)
        mcpSearchQuery = mcpSearchInput.trim()
    }

    LaunchedEffect(availablePackages.value, packageSearchQuery) {
        val packagesMap = availablePackages.value
        val searchText = packageSearchQuery.trim()
        if (searchText.isEmpty()) {
            filteredAvailablePackages = packagesMap
            isPackageSearchFiltering = false
            return@LaunchedEffect
        }

        isPackageSearchFiltering = true
        filteredAvailablePackages =
            withContext(Dispatchers.Default) {
                packagesMap.filter { (packageName, toolPackage) ->
                    packageMatchesSearch(
                        context = context,
                        packageName = packageName,
                        toolPackage = toolPackage,
                        searchText = searchText
                    )
                }
            }
        isPackageSearchFiltering = false
    }

    // File picker launcher for importing external packages
    val packageFilePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val fileName: String? =
                            withContext(Dispatchers.IO) {
                                var name: String? = null
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex("_display_name")
                                    if (cursor.moveToFirst() && nameIndex >= 0) {
                                        name = cursor.getString(nameIndex)
                                    }
                                }
                                name
                            }

                        if (fileName == null) {
                            snackbarHostState.showSnackbar(context.getString(R.string.no_filename))
                            return@launch
                        }

                        // 根据当前选中的标签页处理不同类型的文件
                        when (selectedTab) {
                            PackageTab.PLUGINS,
                            PackageTab.PACKAGES -> {
                                val fileNameNonNull = fileName ?: return@launch
                                val lowerFileName = fileNameNonNull.lowercase()
                                val supported =
                                    when (selectedTab) {
                                        PackageTab.PLUGINS ->
                                            lowerFileName.endsWith(".toolpkg")
                                        PackageTab.PACKAGES ->
                                            lowerFileName.endsWith(".js") ||
                                                lowerFileName.endsWith(".ts") ||
                                                lowerFileName.endsWith(".hjson")
                                        else -> false
                                    }
                                if (!supported) {
                                    snackbarHostState.showSnackbar(
                                        message =
                                            context.getString(
                                                if (selectedTab == PackageTab.PLUGINS) {
                                                    R.string.plugin_toolpkg_only
                                                } else {
                                                    R.string.package_script_only
                                                }
                                            )
                                    )
                                    return@launch
                                }

                                isLoading = true
                                val loadResult =
                                    withContext(Dispatchers.IO) {
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val tempFile = File(context.cacheDir, fileNameNonNull)
                                        try {
                                            inputStream?.use { input ->
                                                tempFile.outputStream().use { output -> input.copyTo(output) }
                                            }

                                            val errorsBeforeImport = packageManager.getPackageLoadErrors()
                                            val importResult =
                                                packageManager.addPackageFileFromExternalStorage(tempFile.absolutePath)

                                            val available = packageManager.getExecutableAvailablePackages(forceRefresh = true)
                                            val allAvailable = packageManager.getAvailablePackages()
                                            val plugins =
                                                packageManager
                                                    .getToolPkgPluginContainerDetails(context)
                                                    .associateBy { it.packageName }
                                            val imported = packageManager.getEnabledPackageNames()
                                            val errors = packageManager.getPackageLoadErrors()
                                            val errorInfos = packageManager.getPackageLoadErrorInfos()
                                            val newErrors =
                                                errors.filter { (key, value) -> errorsBeforeImport[key] != value }

                                            ExternalPackageImportResult(
                                                message = importResult.message,
                                                marketOrigin = importResult.marketOrigin,
                                                availablePackages = available,
                                                allAvailablePackages = allAvailable,
                                                pluginContainers = plugins,
                                                importedPackages = imported,
                                                packageLoadErrors = errors,
                                                packageLoadErrorInfos = errorInfos,
                                                newPackageLoadErrors = newErrors
                                            )
                                        } finally {
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                        }
                                    }

                                availablePackages.value = loadResult.availablePackages
                                allAvailablePackages.value = loadResult.allAvailablePackages
                                pluginContainers.value = loadResult.pluginContainers
                                importedPackages.value = loadResult.importedPackages
                                packageLoadErrors.value = loadResult.packageLoadErrors
                                packageLoadErrorInfos.value = loadResult.packageLoadErrorInfos
                                visibleImportedPackages.value = importedPackages.value.toList()
                                isLoading = false

                                val importSucceeded =
                                    loadResult.message.startsWith(
                                        prefix = "Successfully imported",
                                        ignoreCase = true
                                    )

                                if (importSucceeded) {
                                    val marketOrigin = loadResult.marketOrigin
                                    if (marketOrigin != null) {
                                        marketOriginImportNotice = marketOrigin
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.external_package_imported)
                                        )
                                    }
                                } else {
                                    importErrorMessage =
                                        buildString {
                                            append(loadResult.message)
                                            if (loadResult.newPackageLoadErrors.isNotEmpty()) {
                                                append("\n\n")
                                                append(
                                                    loadResult.newPackageLoadErrors
                                                        .toSortedMap()
                                                        .entries
                                                        .joinToString(separator = "\n\n") { (packageName, errorText) ->
                                                            "$packageName:\n$errorText"
                                                        }
                                                )
                                            }
                                        }
                                }
                            }
                            else -> {
                                snackbarHostState.showSnackbar(context.getString(R.string.current_tab_not_support_import))
                            }
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        AppLogger.e("PackageManagerScreen", "Failed to import file", e)
                        importErrorMessage =
                            context.getString(
                                R.string.import_failed,
                                e.message ?: context.getString(R.string.unknown_error)
                            ) + "\n\n" + e.stackTraceToString()
                    }
                }
            }

        }

    // Load packages
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val loadResult =
                withContext(Dispatchers.IO) {
                    val available = packageManager.getExecutableAvailablePackages(forceRefresh = true)
                    val allAvailable = packageManager.getAvailablePackages()
                    val plugins =
                        packageManager
                            .getToolPkgPluginContainerDetails(context)
                            .associateBy { it.packageName }
                    val imported = packageManager.getEnabledPackageNames()
                    val errors = packageManager.getPackageLoadErrors()
                    val errorInfos = packageManager.getPackageLoadErrorInfos()
                    PackageManagerSnapshot(
                        availablePackages = available,
                        allAvailablePackages = allAvailable,
                        pluginContainers = plugins,
                        importedPackages = imported,
                        packageLoadErrors = errors,
                        packageLoadErrorInfos = errorInfos
                    )
                }

            availablePackages.value = loadResult.availablePackages
            allAvailablePackages.value = loadResult.allAvailablePackages
            pluginContainers.value = loadResult.pluginContainers
            importedPackages.value = loadResult.importedPackages
            packageLoadErrors.value = loadResult.packageLoadErrors
            packageLoadErrorInfos.value = loadResult.packageLoadErrorInfos
            // 初始化UI显示状态
            visibleImportedPackages.value = importedPackages.value.toList()
            // 加载插件排序
            pluginOrder = apiPreferences.getPluginOrder()
            // 加载技能排序
            skillOrder = apiPreferences.getSkillOrder()
        } catch (e: Exception) {
            AppLogger.e("PackageManagerScreen", "Failed to load packages", e)
        } finally {
            isLoading = false
        }
    }

    val activeSearchInput =
        when (selectedTab) {
            PackageTab.PLUGINS -> pluginSearchInput
            PackageTab.PACKAGES -> packageSearchInput
            PackageTab.SKILLS -> skillSearchInput
            PackageTab.MCP -> mcpSearchInput
        }
    val activeSearchPlaceholderRes =
        when (selectedTab) {
            PackageTab.PLUGINS -> R.string.plugin_market_search_placeholder
            PackageTab.PACKAGES -> R.string.package_market_search_placeholder
            PackageTab.SKILLS -> R.string.skill_market_search_placeholder
            PackageTab.MCP -> R.string.mcp_market_search_placeholder
        }
    val activeSearchApplying =
        when (selectedTab) {
            PackageTab.PLUGINS -> pluginSearchInput.trim() != pluginSearchQuery
            PackageTab.PACKAGES ->
                packageSearchInput.trim() != packageSearchQuery || isPackageSearchFiltering
            PackageTab.SKILLS -> skillSearchInput.trim() != skillSearchQuery
            PackageTab.MCP -> mcpSearchInput.trim() != mcpSearchQuery
        }

    BindMarketSearchToTopBar(
        enabled = true,
        searchQuery = activeSearchInput,
        onSearchQueryChanged = { query ->
            when (selectedTab) {
                PackageTab.PLUGINS -> pluginSearchInput = query
                PackageTab.PACKAGES -> packageSearchInput = query
                PackageTab.SKILLS -> skillSearchInput = query
                PackageTab.MCP -> mcpSearchInput = query
            }
        },
        searchPlaceholderRes = activeSearchPlaceholderRes,
        isSearching = activeSearchApplying
    )

    CustomScaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    snackbarData = data
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == PackageTab.PLUGINS || selectedTab == PackageTab.PACKAGES) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (packageLoadErrors.value.isNotEmpty()) {
                        SmallFloatingActionButton(
                            onClick = { showPackageLoadErrorsDialog = true },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = context.getString(R.string.error_occurred_simple)
                            )
                        }
                    }

                    // Environment variables management button
                    SmallFloatingActionButton(
                        onClick = {
                            envVariables =
                                requiredEnvKeys.associateWith { key ->
                                    envPreferences.getEnv(key) ?: ""
                                }
                            showEnvDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.pkg_manage_env_vars)
                        )
                    }

                    FloatingActionButton(
                        onClick = onNavigateToArtifactMarket,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier =
                            Modifier.shadow(
                                elevation = 6.dp,
                                shape = FloatingActionButtonDefaults.shape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Artifact Market"
                        )
                    }

                    // Existing import package button
                    FloatingActionButton(
                        onClick = { packageFilePicker.launch("*/*") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier =
                            Modifier.shadow(
                                elevation = 6.dp,
                                shape = FloatingActionButtonDefaults.shape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = when (selectedTab) {
                                PackageTab.PLUGINS -> context.getString(R.string.import_external_plugin)
                                PackageTab.PACKAGES -> context.getString(R.string.import_external_package)
                                else -> context.getString(R.string.import_action)
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            // 优化标签栏布局 - 直接使用TabRow，不再使用Card包裹，移除边距完全贴满
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                },
                indicator = { tabPositions ->
                    if (selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier =
                                Modifier.tabIndicatorOffset(
                                    tabPositions[selectedTab.ordinal]
                                ),
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                // 插件标签
                Tab(
                    selected = selectedTab == PackageTab.PLUGINS,
                    onClick = { selectedTab = PackageTab.PLUGINS },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.PLUGINS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.nav_group_plugins),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.PLUGINS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 包管理标签
                Tab(
                    selected = selectedTab == PackageTab.PACKAGES,
                    onClick = { selectedTab = PackageTab.PACKAGES },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.PACKAGES)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.packages),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.PACKAGES)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Skills标签
                Tab(
                    selected = selectedTab == PackageTab.SKILLS,
                    onClick = { selectedTab = PackageTab.SKILLS },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.SKILLS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.skills),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.SKILLS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // MCP标签
                Tab(
                    selected = selectedTab == PackageTab.MCP,
                    onClick = { selectedTab = PackageTab.MCP },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.MCP)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.mcp),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.MCP)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 内容区域添加水平padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    PackageTab.PLUGINS -> {
                        PluginTabContent(
                            plugins = filteredPluginContainers,
                            enabledPackageNames = visibleImportedPackages.value,
                            isLoading = isLoading,
                            isSearchActive = pluginSearchQuery.isNotBlank(),
                            onPluginClick = { packageName ->
                                selectedPackage = packageName
                                showDetails = true
                            },
                            onTogglePlugin = { details, isChecked ->
                                val currentImported =
                                    visibleImportedPackages.value.toMutableList()
                                if (isChecked) {
                                    if (!currentImported.contains(details.packageName)) {
                                        currentImported.add(details.packageName)
                                    }
                                } else {
                                    currentImported.remove(details.packageName)
                                    details.subpackages.forEach { subpackage ->
                                        currentImported.remove(subpackage.packageName)
                                    }
                                }
                                visibleImportedPackages.value = currentImported

                                scope.launch {
                                    try {
                                        val updatedImported =
                                            withContext(Dispatchers.IO) {
                                                if (isChecked) {
                                                    packageManager.enablePackage(details.packageName)
                                                } else {
                                                    packageManager.disablePackage(details.packageName)
                                                }
                                                packageManager.getEnabledPackageNames()
                                            }

                                        importedPackages.value = updatedImported
                                        visibleImportedPackages.value = updatedImported.toList()
                                    } catch (e: Exception) {
                                        AppLogger.e(
                                            "PackageManagerScreen",
                                            if (isChecked) "Failed to enable plugin" else "Failed to disable plugin",
                                            e
                                        )
                                        visibleImportedPackages.value = importedPackages.value
                                        snackbarHostState.showSnackbar(
                                            message =
                                                if (isChecked) {
                                                    context.getString(R.string.plugin_enable_failed)
                                                } else {
                                                    context.getString(R.string.plugin_disable_failed)
                                                }
                                        )
                                    }
                                }
                            },
                            pluginOrder = pluginOrder,
                            onSavePluginOrder = { newOrder ->
                                pluginOrder = newOrder
                                scope.launch {
                                    apiPreferences.savePluginOrder(newOrder)
                                }
                            },
                        )
                    }

                    PackageTab.PACKAGES -> {
                        PackageTabContent(
                            packages = filteredAvailablePackages,
                            enabledPackageNames = visibleImportedPackages.value,
                            isLoading = isLoading,
                            isSearchActive = packageSearchQuery.isNotBlank(),
                            onQuickPluginCreatorClick = {
                                showQuickPluginCreatorDialog = true
                            },
                            onPackageClick = { packageName ->
                                selectedPackage = packageName
                                showDetails = true
                            },
                            onTogglePackage = { packageName, isChecked ->
                                val currentImported =
                                    visibleImportedPackages.value.toMutableList()
                                if (isChecked) {
                                    if (!currentImported.contains(packageName)) {
                                        currentImported.add(packageName)
                                    }
                                } else {
                                    currentImported.remove(packageName)
                                }
                                visibleImportedPackages.value = currentImported

                                scope.launch {
                                    try {
                                        val updatedImported =
                                            withContext(Dispatchers.IO) {
                                                if (isChecked) {
                                                    packageManager.enablePackage(packageName)
                                                } else {
                                                    packageManager.disablePackage(packageName)
                                                }
                                                packageManager.getEnabledPackageNames()
                                            }

                                        importedPackages.value = updatedImported
                                        visibleImportedPackages.value = updatedImported.toList()
                                    } catch (e: Exception) {
                                        AppLogger.e(
                                            "PackageManagerScreen",
                                            if (isChecked) "Failed to import package" else "Failed to remove package",
                                            e
                                        )
                                        visibleImportedPackages.value = importedPackages.value
                                        snackbarHostState.showSnackbar(
                                            message =
                                                if (isChecked) {
                                                    context.getString(R.string.package_import_failed)
                                                } else {
                                                    context.getString(R.string.package_remove_failed)
                                                }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    PackageTab.SKILLS -> {
                        SkillConfigScreen(
                            skillRepository = skillRepository,
                            snackbarHostState = snackbarHostState,
                            onNavigateToSkillMarket = onNavigateToSkillMarket,
                            searchQuery = skillSearchQuery,
                            skillOrder = skillOrder,
                            onSaveSkillOrder = { newOrder ->
                                skillOrder = newOrder
                                scope.launch {
                                    apiPreferences.saveSkillOrder(newOrder)
                                }
                            },
                        )
                    }

                    PackageTab.MCP -> {
                        MCPConfigScreen(
                            onNavigateToMCPMarket = onNavigateToMCPMarket,
                            searchQuery = mcpSearchQuery
                        )
                    }
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                    packageName = selectedPackage!!,
                    packageDescription = allAvailablePackages.value[selectedPackage]?.description?.resolve(context)
                        ?: "",
                    toolPackage = allAvailablePackages.value[selectedPackage],
                    packageManager = packageManager,
                    onRunScript = { toolPackageName, tool ->
                        selectedToolPackageName = toolPackageName
                        selectedTool = tool
                        showScriptExecution = true
                    },
                    onOpenToolPkgPluginConfig = { containerPackageName, uiModuleId, title, keepAlive ->
                        showDetails = false
                        onOpenToolPkgPluginConfig(containerPackageName, uiModuleId, title, keepAlive)
                    },
                    onDismiss = {
                        showDetails = false
                        scope.launch {
                            val imported = withContext(Dispatchers.IO) { packageManager.getEnabledPackageNames() }
                            importedPackages.value = imported
                            visibleImportedPackages.value = imported.toList()
                        }
                    },
                    onPackageDeleted = {
                        showDetails = false
                        scope.launch {
                            AppLogger.d(
                                "PackageManagerScreen",
                                "onPackageDeleted callback triggered. Refreshing package lists."
                            )
                            // Refresh the package lists after deletion
                            isLoading = true
                            val loadResult =
                                withContext(Dispatchers.IO) {
                                    val available = packageManager.getExecutableAvailablePackages(forceRefresh = true)
                                    val allAvailable = packageManager.getAvailablePackages()
                                    val plugins =
                                        packageManager
                                            .getToolPkgPluginContainerDetails(context)
                                            .associateBy { it.packageName }
                                    val imported = packageManager.getEnabledPackageNames()
                                    PackageManagerSnapshot(
                                        availablePackages = available,
                                        allAvailablePackages = allAvailable,
                                        pluginContainers = plugins,
                                        importedPackages = imported,
                                        packageLoadErrors = packageManager.getPackageLoadErrors(),
                                        packageLoadErrorInfos = packageManager.getPackageLoadErrorInfos()
                                    )
                                }

                            availablePackages.value = loadResult.availablePackages
                            allAvailablePackages.value = loadResult.allAvailablePackages
                            pluginContainers.value = loadResult.pluginContainers
                            importedPackages.value = loadResult.importedPackages
                            packageLoadErrors.value = loadResult.packageLoadErrors
                            packageLoadErrorInfos.value = loadResult.packageLoadErrorInfos
                            visibleImportedPackages.value = importedPackages.value.toList()
                            AppLogger.d(
                                "PackageManagerScreen",
                                "Lists refreshed. Available: ${availablePackages.value.keys}, Imported: ${importedPackages.value}"
                            )
                            isLoading = false
                            snackbarHostState.showSnackbar("Package deleted successfully.")
                        }
                    }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                    packageName = selectedToolPackageName ?: selectedPackage!!,
                    tool = selectedTool!!,
                    packageManager = packageManager,
                    initialResult = scriptExecutionResult,
                    onExecuted = { result -> scriptExecutionResult = result },
                    onDismiss = {
                        showScriptExecution = false
                        scriptExecutionResult = null
                        selectedToolPackageName = null
                    }
                )
            }

            // Environment Variables Dialog for packages
            if (showEnvDialog) {
                PackageEnvironmentVariablesDialog(
                    requiredEnvByPackage = requiredEnvByPackage,
                    currentValues = envVariables,
                    onDismiss = { showEnvDialog = false },
                    onConfirm = { updated ->
                        val merged = envPreferences.getAllEnv().toMutableMap().apply {
                            updated.forEach { (key, value) ->
                                if (value.isBlank()) {
                                    remove(key)
                                } else {
                                    this[key] = value
                                }
                            }
                        }
                        envPreferences.setAllEnv(merged)
                        envVariables = updated
                        showEnvDialog = false
                    }
                )
            }

            if (showPackageLoadErrorsDialog) {
                PackageLoadErrorsDialog(
                    errorInfos = packageLoadErrorInfos.value,
                    onDeleteSource = { sourcePath ->
                        scope.launch {
                            val deleted =
                                withContext(Dispatchers.IO) {
                                    packageManager.deleteExternalPackageSource(sourcePath)
                                }
                            if (!deleted) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.package_conflict_delete_failed)
                                )
                                return@launch
                            }

                            val refreshed =
                                withContext(Dispatchers.IO) {
                                    PackageManagerSnapshot(
                                        availablePackages = packageManager.getExecutableAvailablePackages(forceRefresh = true),
                                        allAvailablePackages = packageManager.getAvailablePackages(),
                                        pluginContainers =
                                            packageManager
                                                .getToolPkgPluginContainerDetails(context)
                                                .associateBy { it.packageName },
                                        importedPackages = packageManager.getEnabledPackageNames(),
                                        packageLoadErrors = packageManager.getPackageLoadErrors(),
                                        packageLoadErrorInfos = packageManager.getPackageLoadErrorInfos()
                                    )
                                }
                            availablePackages.value = refreshed.availablePackages
                            allAvailablePackages.value = refreshed.allAvailablePackages
                            pluginContainers.value = refreshed.pluginContainers
                            importedPackages.value = refreshed.importedPackages
                            packageLoadErrors.value = refreshed.packageLoadErrors
                            packageLoadErrorInfos.value = refreshed.packageLoadErrorInfos
                            visibleImportedPackages.value = refreshed.importedPackages.toList()

                            if (packageLoadErrorInfos.value.isEmpty()) {
                                showPackageLoadErrorsDialog = false
                            }
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.package_conflict_delete_success)
                            )
                        }
                    },
                    onDismiss = { showPackageLoadErrorsDialog = false }
                )
            }

            importErrorMessage?.let { errorMessage ->
                ErrorDialog(
                    errorMessage = errorMessage,
                    onDismiss = { importErrorMessage = null }
                )
            }

            marketOriginImportNotice?.let { origin ->
                AlertDialog(
                    onDismissRequest = { marketOriginImportNotice = null },
                    title = { Text(stringResource(R.string.package_market_origin_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.package_market_origin_message,
                                origin.toolpkgId,
                                origin.version,
                                origin.author.joinToString(", ")
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { marketOriginImportNotice = null }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                )
            }

            if (showQuickPluginCreatorDialog) {
                QuickPluginCreatorDialog(
                    requirement = quickPluginRequirement,
                    onRequirementChange = { quickPluginRequirement = it },
                    setupRunning = quickPluginSetupRunning,
                    setupResult = quickPluginSetupResult,
                    onRunSetup = {
                        scope.launch {
                            runQuickPluginCreatorSetupAndPublishResult(
                                context = context,
                                packageManager = packageManager,
                                toolHandler = toolHandler,
                                onRunningChange = { quickPluginSetupRunning = it },
                                onResult = { quickPluginSetupResult = it },
                                onMessage = { message ->
                                    snackbarHostState.showSnackbar(message)
                                }
                            )
                        }
                    },
                    onDismiss = { showQuickPluginCreatorDialog = false },
                    onConfirm = {
                        val requirement = quickPluginRequirement.trim()
                        if (requirement.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.quick_plugin_creator_requirement_empty)
                                )
                            }
                        } else {
                            showQuickPluginCreatorDialog = false
                            quickPluginRequirement = ""
                            onStartPluginCreation(PluginCreationIntent.Fresh(requirement))
                        }
                    }
                )
            }
        }
    }
}

