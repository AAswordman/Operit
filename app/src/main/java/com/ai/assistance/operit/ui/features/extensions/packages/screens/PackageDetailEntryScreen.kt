package com.ai.assistance.operit.ui.features.extensions.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.ui.features.extensions.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Route-scoped presentation; all activation and UI registration remain owned by PackageManager. */
@Composable
fun PackageDetailEntryScreen(
    packageName: String,
    onNavigateBack: () -> Unit,
    onOpenToolPkgPluginConfig: (String, String, String, Boolean) -> Unit,
    onOpenMarketDetail: (MarketV2Entry) -> Unit,
) {
    val context = LocalContext.current
    val packageManager = remember(context) {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()
    val isCurrent = LocalIsCurrentScreen.current
    var packages by remember(packageName) { mutableStateOf<Map<String, ToolPackage>?>(null) }
    var enabled by remember(packageName) { mutableStateOf(false) }
    var loadFailed by remember(packageName) { mutableStateOf(false) }
    var refresh by remember(packageName) { mutableIntStateOf(0) }
    val updateRevision by com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState.revision.collectAsState()
    val updating by com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState.updating.collectAsState()

    var toggling by remember(packageName) { mutableStateOf(false) }
    var toggleError by remember(packageName) { mutableStateOf<String?>(null) }
    var execution by remember(packageName) { mutableStateOf<Pair<String, PackageTool>?>(null) }
    var executionResult by remember(packageName) { mutableStateOf<ToolResult?>(null) }

    LaunchedEffect(updateRevision) {
        // A retained execution dialog must not dispatch a script captured from the previous version.
        execution = null
        executionResult = null
    }

    LaunchedEffect(packageName, isCurrent, refresh, updateRevision) {
        if (!isCurrent) return@LaunchedEffect
        loadFailed = false
        try {
            val snapshot = withContext(Dispatchers.IO) {
                packageManager.getAvailablePackages(forceRefresh = true) to
                    packageManager.getEnabledPackageNames()
            }
            packages = snapshot.first
            enabled = packageName in snapshot.second
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e("PackageDetailEntryScreen", "Failed to load $packageName", error)
            loadFailed = true
        }
    }

    val loaded = packages
    if (loadFailed || (loaded != null && packageName !in loaded)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.extension_detail_load_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.extension_detail_retry)) }
        }
    } else if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        val toolPackage = loaded.getValue(packageName)
        PackageDetailsScreen(
            packageName = packageName,
            packageDescription = toolPackage.description.resolve(context),
            toolPackage = toolPackage,
            packageManager = packageManager,
            deletionScope = scope,
            isContainerEnabled = enabled,
            isToggling = toggling || packageName in updating,
            toggleError = toggleError,
            onToggleEnabled = { next ->
                if (!toggling && packageName !in updating) {
                    toggling = true
                    toggleError = null
                    scope.launch {
                        try {
                            val (names, message) = withContext(Dispatchers.IO) {
                                val message = if (next) packageManager.enablePackage(packageName)
                                    else packageManager.disablePackage(packageName)
                                packageManager.getEnabledPackageNames() to message
                            }
                            enabled = packageName in names
                            // PackageManager reports dependency rejection as text, without throwing.
                            if (enabled != next) {
                                AppLogger.d("PackageDetailEntryScreen", "Activation change rejected for $packageName: $message")
                                toggleError = context.getString(R.string.extension_toggle_failed)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            AppLogger.e("PackageDetailEntryScreen", "Failed to change activation of $packageName", error)
                            toggleError = context.getString(R.string.extension_toggle_failed)
                        } finally {
                            toggling = false
                        }
                    }
                }
            },
            onRunScript = { name, tool ->
                if (enabled && !toggling && packageManager.packageAvailabilityError(name) == null) {
                    execution = name to tool
                }
            },
            onOpenToolPkgPluginConfig = onOpenToolPkgPluginConfig,
            onPackageDeleted = onNavigateBack,
            onOpenMarketDetail = onOpenMarketDetail,
        )
    }

    execution?.let { (name, tool) ->
        ScriptExecutionDialog(
            packageName = name,
            tool = tool,
            environmentVariables = packages?.get(name)?.env.orEmpty(),
            packageManager = packageManager,
            packageEnabled = enabled && !toggling && packageName !in updating,
            initialResult = executionResult,
            onExecuted = { executionResult = it },
            onDismiss = { execution = null; executionResult = null },
        )
    }
}