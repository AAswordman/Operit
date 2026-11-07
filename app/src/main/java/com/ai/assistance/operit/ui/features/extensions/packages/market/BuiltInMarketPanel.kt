package com.ai.assistance.operit.ui.features.extensions.packages.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
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
import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import com.ai.assistance.operit.ui.features.extensions.packages.market.transfer.MarketInstallProgressCard
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BuiltInMarketPanel(query: String, type: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { PackageManager.getInstance(context, AIToolHandler.getInstance(context)) }
    val revision by PackageUpdateState.revision.collectAsState()
    val updating by PackageUpdateState.updating.collectAsState()
    val transfers by MarketInstallStateStore.installStates.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    var specs by remember { mutableStateOf<List<ResourcePackSpec>>(emptyList()) }
    var ready by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var current by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision, refresh, checking) {
        try {
            withContext(Dispatchers.IO) {
                val catalog = BuiltInExtensionResources.specs(context)
                Triple(catalog, catalog.associate { it.id to PackResources.isReady(context, it.id) },
                    catalog.associate { it.id to PackResources.isCurrentReady(context, it.id) })
            }.let { (catalog, installed, upToDate) -> specs = catalog; ready = installed; current = upToDate }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { AppLogger.e("BuiltInMarket", "Catalog could not be loaded", failure); error = true }
    }
    Column(Modifier.fillMaxSize()) {
        ExtensionUpdateToolbar(manager, Modifier.padding(horizontal = 16.dp))
        if (error) Text(stringResource(R.string.extension_update_failed), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(specs.filter {
                (type.isEmpty() || (if (it.extension!!.kind == "plugin") "package" else "script") == type) &&
                    (query.isBlank() || it.name.contains(query, true) || it.extension!!.packageNames.any { name -> name.contains(query, true) })
            }, key = { it.id }) { spec ->
                val progress = transfers[spec.id]
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(spec.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.builtin), style = MaterialTheme.typography.labelSmall)
                                Text(spec.extension!!.packageNames.first(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val installed = ready[spec.id] == true
                            val upToDate = current[spec.id] == true
                            FilledTonalButton(enabled = progress == null && !upToDate && !checking &&
                                spec.extension!!.packageNames.none { it in updating }, onClick = {
                                scope.launch {
                                    error = false
                                    try { ExtensionUpdates.installBuiltIn(context, manager, spec); refresh++ }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { AppLogger.e("BuiltInMarket", "Install failed: ${spec.id}", failure); error = true }
                                }
                            }) { Text(stringResource(when {
                                progress != null -> R.string.extension_updating
                                upToDate -> R.string.extension_installed
                                installed -> R.string.extension_update_now
                                else -> R.string.extension_install
                            })) }
                        }
                        if (progress != null) MarketInstallProgressCard(progress, compact = true)
                    }
                }
            }
        }
    }
}
