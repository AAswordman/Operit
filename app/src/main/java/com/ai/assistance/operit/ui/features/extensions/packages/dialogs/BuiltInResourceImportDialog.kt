package com.ai.assistance.operit.ui.features.extensions.packages.dialogs

import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.extensions.packages.market.ExtensionUpdates
import com.ai.assistance.operit.ui.features.extensions.packages.market.extensionErrorDetails
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resource ZIPs have a fixed identity and file manifest; they must not enter the ordinary JS/ToolPkg importer. */
@Composable
internal fun BuiltInResourceImportDialog(isPluginTab: Boolean, manager: PackageManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updating by PackageUpdateState.updating.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    var specs by remember(isPluginTab) { mutableStateOf<List<ResourcePackSpec>>(emptyList()) }
    var loading by remember(isPluginTab) { mutableStateOf(true) }
    var loadFailed by remember(isPluginTab) { mutableStateOf(false) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(isPluginTab) {
        try {
            specs = withContext(Dispatchers.IO) {
                BuiltInExtensionResources.specs(context).filter {
                    (it.extension!!.kind == "plugin") == isPluginTab && !PackResources.isBundled(context, it.id)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            loadFailed = true
            AppLogger.e("BuiltInImport", "Could not load resources", error)
            OKToast.error(context, extensionErrorDetails(error), OKToast.LENGTH_LONG).show()
        } finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingId
        pendingId = null
        if (uri != null && id != null) {
            importing = true
            progress = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val spec = PackResources.spec(context, id)
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: error("Cannot read the archive name")
                        checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            ExtensionUpdates.importBuiltIn(context, manager, spec, input, name) { done, total ->
                                progress = done to total
                            }
                        }
                    }
                    OKToast.success(context, R.string.import_success).show()
                    onDismiss()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    AppLogger.e("BuiltInImport", "Import failed: $id", error)
                    OKToast.error(context, extensionErrorDetails(error), OKToast.LENGTH_LONG).show()
                } finally { importing = false; progress = null }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(R.string.builtin_resource_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.builtin_resource_import_hint))
                if (loading || importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                progress?.let { (done, total) ->
                    Text(Formatter.formatShortFileSize(context, done) + " / " + Formatter.formatShortFileSize(context, total),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (!loading && !loadFailed && specs.isEmpty()) Text(stringResource(R.string.builtin_resource_no_targets))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(specs, key = { it.id }) { spec ->
                        OutlinedCard(enabled = !importing && pendingId == null && !checking && updating.isEmpty(), onClick = {
                            pendingId = spec.id
                            picker.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                        }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(spec.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.builtin_market_version, spec.version),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !importing, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
