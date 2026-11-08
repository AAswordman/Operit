package com.ai.assistance.operit.ui.features.extensions.packages.market

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import com.ai.assistance.operit.ui.features.extensions.packages.components.ExtensionDetailFact
import com.ai.assistance.operit.ui.features.extensions.packages.components.ExtensionDetailFacts
import com.ai.assistance.operit.ui.features.extensions.packages.market.transfer.MarketInstallProgressCard
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A normal market detail route; it exposes only information provided by this resource source. */
@Composable
internal fun BuiltInMarketDetailScreen(resourceId: String, onOpenInstalled: (String, String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val manager = remember(context) { PackageManager.getInstance(context, AIToolHandler.getInstance(context)) }
    val revision by PackageUpdateState.revision.collectAsState()
    val updating by PackageUpdateState.updating.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    val transfers by MarketInstallStateStore.installStates.collectAsState()
    val currentScreen = LocalIsCurrentScreen.current
    var spec by remember(resourceId) { mutableStateOf<ResourcePackSpec?>(null) }
    var installed by remember(resourceId) { mutableStateOf(false) }
    var upToDate by remember(resourceId) { mutableStateOf(false) }
    var canOpenInstalled by remember(resourceId) { mutableStateOf(false) }
    var loadError by remember(resourceId) { mutableStateOf<String?>(null) }
    var installError by remember(resourceId) { mutableStateOf<String?>(null) }
    LaunchedEffect(resourceId, revision, checking, currentScreen) {
        if (!currentScreen) return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) {
                val current = PackResources.spec(context, resourceId)
                val extension = requireNotNull(current.extension) { "This resource is not a built-in extension" }
                val ready = PackResources.isReady(context, resourceId)
                val latest = PackResources.isCurrentReady(context, resourceId)
                val open = ready && manager.getPackageTools(extension.packageNames.first()) != null
                current to Triple(ready, latest, open)
            }
            spec = result.first
            installed = result.second.first
            upToDate = result.second.second
            canOpenInstalled = result.second.third
            loadError = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            AppLogger.e("BuiltInMarket", "Could not load detail: $resourceId", failure)
            loadError = failure.message ?: failure.javaClass.simpleName
        }
    }
    val current = spec
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val error = loadError
            if (error == null) CircularProgressIndicator() else Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val extension = requireNotNull(current.extension)
    val rootName = extension.packageNames.first()
    val progress = transfers[resourceId]
    val busy = rootName in updating || progress != null
    val type = stringResource(if (extension.kind == "plugin") R.string.extension_detail_plugin else R.string.extension_detail_script)
    val source = stringResource(R.string.builtin)
    val downloadSize = Formatter.formatShortFileSize(context, current.archive.bytes)
    BindUnifiedMarketDetailTopBarTitle(current.name, visible = true)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            UnifiedMarketDetailHeaderCard(
                header = UnifiedMarketDetailHeader(title = current.name, fallbackAvatarText = current.name.take(1),
                    subtitle = source, badges = listOf(type), metrics = listOf(
                        UnifiedMarketDetailMetric(type, context.getString(R.string.builtin_market_type)),
                        UnifiedMarketDetailMetric(downloadSize, context.getString(R.string.builtin_market_download_size)),
                        UnifiedMarketDetailMetric(extension.packageNames.size.toString(), context.getString(R.string.builtin_market_packages)),
                    )),
                primaryAction = UnifiedMarketDetailAction(
                    label = stringResource(when {
                        busy -> R.string.extension_updating
                        upToDate -> R.string.extension_installed
                        installed -> R.string.extension_update_now
                        else -> R.string.extension_install
                    }), enabled = !busy && !checking && !upToDate, isLoading = busy,
                    icon = Icons.Default.Download,
                    onClick = {
                        coroutineScope.launch {
                            installError = null
                            try { ExtensionUpdates.installBuiltIn(context, manager, current) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                AppLogger.e("BuiltInMarket", "Install failed: $resourceId", failure)
                                installError = failure.message ?: failure.javaClass.simpleName
                            }
                        }
                    }),
                secondaryAction = if (canOpenInstalled) UnifiedMarketDetailAction(
                    stringResource(R.string.builtin_market_open_installed),
                    onClick = { onOpenInstalled(rootName, current.name) }, enabled = !busy,
                    icon = Icons.Default.OpenInNew) else null,
            )
        }
        progress?.let { item { MarketInstallProgressCard(it) } }
        listOfNotNull(loadError, installError).forEach { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        item { UnifiedMarketDetailSectionCard(UnifiedMarketDetailSection(
            title = stringResource(R.string.builtin_market_about), body = stringResource(R.string.builtin_market_description))) }
        item { UnifiedMarketDetailSectionCard(UnifiedMarketDetailSection(
            title = stringResource(R.string.builtin_market_packages), body = extension.packageNames.joinToString("\n"),
            isCodeBlock = true, collapsible = true)) }
        item {
            ExtensionDetailFacts(stringResource(R.string.builtin_market_information), listOf(
                ExtensionDetailFact(stringResource(R.string.builtin_market_type), type),
                ExtensionDetailFact(stringResource(R.string.builtin_market_source), source),
                ExtensionDetailFact(stringResource(R.string.builtin_market_resource_version), current.version, monospace = true),
                ExtensionDetailFact(stringResource(R.string.builtin_market_download_size), downloadSize),
            ))
        }
    }
}