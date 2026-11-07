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
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.ui.features.extensions.packages.components.ExtensionSwitch
import com.ai.assistance.operit.ui.features.extensions.packages.market.transfer.MarketInstallProgressCard
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExtensionUpdateToolbar(manager: PackageManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updates by ExtensionUpdates.available.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    val hasChecked by ExtensionUpdates.hasChecked.collectAsState()
    val failures by ExtensionUpdates.failures.collectAsState()
    val updating by PackageUpdateState.updating.collectAsState()
    var failed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hasChecked) Text(stringResource(R.string.extension_updates_count, updates.size), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            else Spacer(Modifier.weight(1f))
            TextButton(enabled = !checking && updating.isEmpty(), onClick = {
                scope.launch {
                    failed = false
                    try { ExtensionUpdates.check(context, manager) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        AppLogger.e("ExtensionUpdates", "Update check failed", error)
                        failed = true
                    }
                }
            }) {
                if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pkg_check_update))
            }
        }
        if (failed || failures > 0) Text(stringResource(R.string.extension_updates_partial_failure),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    var failed by remember(packageName) { mutableStateOf(false) }
    val busy = packageName in updating
    Column(horizontalAlignment = Alignment.End) {
        if (update != null) {
            FilledTonalButton(enabled = enabled && !busy && !checking, onClick = {
                scope.launch {
                    failed = false
                    try { ExtensionUpdates.install(context, manager, packageName, update) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        AppLogger.e("ExtensionUpdates", "Update failed: $packageName", error)
                        failed = true
                    }
                }
            }) { Text(stringResource(if (busy) R.string.extension_updating else R.string.extension_update_now)) }
        } else {
            ExtensionSwitch(checked = checked, enabled = enabled && !busy, onCheckedChange = {
                if (!PackageUpdateState.contains(packageName)) onToggle(it)
            })
        }
        if (failed) Text(stringResource(R.string.extension_update_failed), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
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
