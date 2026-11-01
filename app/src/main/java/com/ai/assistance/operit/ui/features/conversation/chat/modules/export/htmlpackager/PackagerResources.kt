package com.ai.assistance.operit.ui.features.conversation.chat.modules.export.htmlpackager

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.ui.features.management.resources.OfflineResourcesActivity
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val packagerResourceGroups = listOf(OfflineResources.ANDROID_EXPORT, OfflineResources.WINDOWS_EXPORT)

@Stable
internal class PackagerResourceState(private val context: Context, private val scope: CoroutineScope) {
    private val ready = mutableStateMapOf<String, Boolean>()
    var checking by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    val hasReadyTemplate: Boolean get() = ready.values.any { it }
    fun isReady(id: String): Boolean = ready[id] == true

    fun refresh() = check {
        packagerResourceGroups.forEach { ready[it] = OfflineResources.isReady(context, it) }
    }

    fun selectPlatform(id: String, onReady: () -> Unit) = check {
        // 选择平台时再次校验，避免进入页面后资源被清理或替换。
        val available = OfflineResources.isReady(context, id)
        ready[id] = available
        if (available) onReady() else openResources(id)
    }

    private fun check(action: suspend () -> Unit) {
        if (job?.isActive == true) return
        checking = true
        error = null
        job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                AppLogger.e("PackagerResources", "Checking export resources failed", failure)
                error = context.getString(R.string.offline_resources_failed, failure.message)
            } finally { checking = false }
        }
    }

    fun openResources(id: String) {
        context.startActivity(Intent(context, OfflineResourcesActivity::class.java)
            .putExtra(OfflineResourcesActivity.EXTRA_GROUP, id))
    }
}

@Composable
internal fun rememberPackagerResources(): PackagerResourceState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(context, scope) { PackagerResourceState(context, scope) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(state) { state.refresh() }
    DisposableEffect(owner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
internal fun PackagerResourceGate(state: PackagerResourceState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.Download, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.packager_resources_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.packager_resources_description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        packagerResourceGroups.forEach { id ->
            ResourcePlatformCard(id, state, onClick = { state.openResources(id) })
        }
        TextButton(onClick = state::refresh, enabled = !state.checking) { Text(stringResource(R.string.resource_refresh)) }
    }
}

@Composable
internal fun PackagerResourceSummary(state: PackagerResourceState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.packager_resources_available), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = {
                    state.openResources(if (state.isReady(OfflineResources.ANDROID_EXPORT)) OfflineResources.WINDOWS_EXPORT else OfflineResources.ANDROID_EXPORT)
                }, enabled = !state.checking) { Text(stringResource(R.string.resource_manage)) }
            }
            Text("Android · " + stringResource(if (state.isReady(OfflineResources.ANDROID_EXPORT)) R.string.packager_template_ready else R.string.packager_template_missing) +
                "    Windows · " + stringResource(if (state.isReady(OfflineResources.WINDOWS_EXPORT)) R.string.packager_template_ready else R.string.packager_template_missing),
                style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun PackagerPlatformDialog(onDismiss: () -> Unit, onSelectAndroid: () -> Unit, onSelectWindows: () -> Unit) {
    val state = rememberPackagerResources()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_export_platform)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                packagerResourceGroups.forEach { id ->
                    ResourcePlatformCard(id, state) {
                        state.selectPlatform(id) {
                            if (id == OfflineResources.ANDROID_EXPORT) onSelectAndroid() else onSelectWindows()
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ResourcePlatformCard(id: String, state: PackagerResourceState, onClick: () -> Unit) {
    val ready = state.isReady(id)
    OutlinedCard(onClick = onClick, enabled = !state.checking, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (id == OfflineResources.ANDROID_EXPORT) Icons.Default.Android else Icons.Default.DesktopWindows,
                null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (id == OfflineResources.ANDROID_EXPORT) "Android" else "Windows", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(when {
                    state.checking -> R.string.resource_checking
                    ready -> R.string.packager_template_ready_detail
                    else -> R.string.packager_template_missing_detail
                }), style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Download, null, Modifier.size(20.dp))
        }
    }
}
