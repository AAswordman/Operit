package com.ai.assistance.operit.ui.features.extensions.packages.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            onClick = { coroutineScope.launch { ExtensionUpdates.check(context, manager, scope) } }) {
            if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.pkg_check_update), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun ExtensionUpdateFailureNotice(scope: ExtensionUpdateScope, modifier: Modifier = Modifier) {
    val failures by ExtensionUpdates.failures.collectAsState()
    val errors = failures[scope].orEmpty()
    var expanded by remember(scope) { mutableStateOf(false) }
    if (errors.isNotEmpty()) {
        TextButton(onClick = { expanded = true }, modifier = modifier) {
            Text(stringResource(R.string.extension_updates_partial_failure), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        if (expanded) AlertDialog(onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.extension_updates_partial_failure)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    errors.forEach { error -> Text("${error.source}: ${error.reason}") }
                }
            }, confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.pkg_ok)) } })
    }
}

/** Installed rows keep the version and the update marker together, without a clipped description. */
@Composable
fun ExtensionListVersion(packageName: String, version: String, builtIn: Boolean = false) {
    val updates by ExtensionUpdates.available.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (builtIn) Text(stringResource(R.string.builtin), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (version.isNotBlank()) Text("v$version", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            modifier = Modifier.weight(1f, fill = false), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
