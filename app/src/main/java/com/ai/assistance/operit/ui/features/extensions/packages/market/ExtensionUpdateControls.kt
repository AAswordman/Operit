package com.ai.assistance.operit.ui.features.extensions.packages.market

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.ui.features.extensions.packages.components.ExtensionSwitch
import com.ai.assistance.operit.ui.features.extensions.packages.market.transfer.MarketInstallProgressCard
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources
import com.ai.assistance.operit.data.resources.packs.PackResources
import kotlinx.coroutines.launch

@Composable
fun ExtensionUpdateToolbar(manager: PackageManager, scope: ExtensionUpdateScope = ExtensionUpdateScope(), modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updates by ExtensionUpdates.available.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    val checked by ExtensionUpdates.checkedScopes.collectAsState()
    val updating by PackageUpdateState.updating.collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (checked.any { (it.type.isEmpty() || it.type == scope.type) && (!it.builtInOnly || scope.builtInOnly) }) {
            Text(stringResource(R.string.extension_updates_count, updates.values.count(scope::accepts)),
                Modifier.widthIn(max = 80.dp), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
        FilledTonalButton(enabled = !checking && updating.isEmpty(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            onClick = { coroutineScope.launch {
                val result = ExtensionUpdates.check(context, manager, scope) ?: return@launch
                when {
                    result.failures.isNotEmpty() -> OKToast.error(context,
                        result.failures.joinToString("\n\n") { "${it.source}: ${it.reason}" }, OKToast.LENGTH_LONG).show()
                    result.sources == 0 -> OKToast.info(context, R.string.extension_updates_no_sources, OKToast.LENGTH_LONG).show()
                    result.updates == 0 -> OKToast.success(context, R.string.pkg_check_update_up_to_date).show()
                    else -> OKToast.success(context, context.getString(R.string.extension_updates_count, result.updates), OKToast.LENGTH_LONG).show()
                }
            } }) {
            if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.pkg_check_update), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Installed rows keep the version and the update marker together, without a clipped description. */
@Composable
fun ExtensionListVersion(packageName: String, version: String, builtIn: Boolean = false) {
    val updates by ExtensionUpdates.available.collectAsState()
    val context = LocalContext.current
    val revision by PackageUpdateState.revision.collectAsState()
    val resourceVersion by produceState<String?>(null, packageName, builtIn, version, revision) {
        value = null
        if (builtIn && version.isBlank()) {
            try {
                value = withContext(Dispatchers.IO) {
                    val spec = BuiltInExtensionResources.specs(context).firstOrNull { packageName in it.extension!!.packageNames }
                    if (spec == null) null
                    else if (PackResources.isBundled(context, spec.id)) spec.version
                    else PackResources.installedSpec(context, spec.id)?.version
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { AppLogger.e("ExtensionVersion", "Could not read installed resource version: $packageName", error) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (builtIn) Text(stringResource(R.string.builtin), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (version.isNotBlank()) Text("v$version", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            modifier = Modifier.weight(1f, fill = false), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        resourceVersion?.let {
            Text(stringResource(R.string.builtin_market_version, it), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        }
        if (packageName in updates) Text(stringResource(R.string.extension_new_version),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
fun ExtensionUpdateAction(packageName: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { PackageManager.getInstance(context, AIToolHandler.getInstance(context)) }
    val updates by ExtensionUpdates.available.collectAsState()
    val updating by PackageUpdateState.updating.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    val update = updates[packageName]
    val busy = packageName in updating
    Column(horizontalAlignment = Alignment.End) {
        if (update != null) {
            FilledTonalButton(enabled = enabled && !busy && !checking, onClick = {
                scope.launch {
                    try { ExtensionUpdates.install(context, manager, packageName, update) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        AppLogger.e("ExtensionUpdates", "Update failed: $packageName", error)
                        OKToast.error(context, extensionErrorDetails(error), OKToast.LENGTH_LONG).show()
                    }
                }
            }) { Text(stringResource(if (busy) R.string.extension_updating else R.string.extension_update_now)) }
        } else {
            ExtensionSwitch(checked = checked, enabled = enabled && !busy, onCheckedChange = {
                if (!PackageUpdateState.contains(packageName)) onToggle(it)
            })
        }
    }
}

@Composable
fun ExtensionUpdateProgress(packageName: String) {
    val updates by ExtensionUpdates.available.collectAsState()
    val transfers by MarketInstallStateStore.installStates.collectAsState()
    val key = when (val update = updates[packageName]) {
        is ExtensionUpdate.Market -> update.entry.id
        is ExtensionUpdate.BuiltIn -> update.spec.id
        null -> null
    }
    key?.let { transfers[it] }?.let { MarketInstallProgressCard(it, compact = true) }
}
