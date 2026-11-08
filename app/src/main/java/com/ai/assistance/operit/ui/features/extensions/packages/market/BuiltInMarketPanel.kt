package com.ai.assistance.operit.ui.features.extensions.packages.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BuiltInMarketPanel(query: String, type: String, onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val manager = remember(context) { PackageManager.getInstance(context, AIToolHandler.getInstance(context)) }
    val revision by PackageUpdateState.revision.collectAsState()
    val updating by PackageUpdateState.updating.collectAsState()
    val transfers by MarketInstallStateStore.installStates.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    val currentScreen = LocalIsCurrentScreen.current
    val checkScope = remember(type) { ExtensionUpdateScope(type, builtInOnly = true) }
    var specs by remember { mutableStateOf<List<ResourcePackSpec>>(emptyList()) }
    var ready by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var current by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revision, checking, currentScreen) {
        if (!currentScreen) return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                val catalog = BuiltInExtensionResources.specs(context)
                Triple(catalog, catalog.associate { it.id to PackResources.isReady(context, it.id) },
                    catalog.associate { it.id to PackResources.isCurrentReady(context, it.id) })
            }.let { (catalog, installed, upToDate) -> specs = catalog; ready = installed; current = upToDate; error = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { AppLogger.e("BuiltInMarket", "Catalog could not be loaded", failure); error = failure.message }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            ExtensionUpdateToolbar(manager, checkScope)
        }
        ExtensionUpdateFailureNotice(checkScope, Modifier.padding(horizontal = 12.dp))
        listOfNotNull(error, installError).forEach { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(specs.filter {
                checkScope.accepts(ExtensionUpdate.BuiltIn(it)) &&
                    (query.isBlank() || it.name.contains(query, true) || it.extension!!.packageNames.any { name -> name.contains(query, true) })
            }, key = { it.id }) { spec ->
                val progress = transfers[spec.id]
                val installed = ready[spec.id] == true
                val upToDate = current[spec.id] == true
                val blocked = checking || spec.extension!!.packageNames.any { it in updating }
                val state = when {
                    progress != null -> MarketBrowseActionState.Installing(progress.progress)
                    upToDate -> MarketBrowseActionState.Installed
                    blocked -> MarketBrowseActionState.Unavailable()
                    installed -> MarketBrowseActionState.Updatable
                    else -> MarketBrowseActionState.Available
                }
                MarketBrowseCard(
                    model = MarketBrowseCardModel(title = spec.name,
                        description = stringResource(R.string.builtin) + " · " + stringResource(
                            if (spec.extension!!.kind == "plugin") R.string.extension_detail_plugin else R.string.extension_detail_script),
                        actionState = state, installProgress = progress, showStats = false),
                    onViewDetails = { onOpenDetail(spec.id) },
                    onInstall = {
                        coroutineScope.launch {
                            try { installError = null; ExtensionUpdates.installBuiltIn(context, manager, spec) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { AppLogger.e("BuiltInMarket", "Install failed: ${spec.id}", failure); installError = failure.message ?: failure.javaClass.simpleName }
                        }
                    },
                )
            }
        }
    }
}
