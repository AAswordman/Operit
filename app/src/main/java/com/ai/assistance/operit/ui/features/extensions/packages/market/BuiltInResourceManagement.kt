package com.ai.assistance.operit.ui.features.extensions.packages.market

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState
import com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun builtInResourceForPackage(context: Context, manager: PackageManager, packageName: String): ResourcePackSpec {
    check(manager.isTopLevelPackage(packageName) && manager.getAvailablePackages()[packageName]?.isBuiltIn == true)
    return BuiltInExtensionResources.specs(context).first { it.extension!!.packageNames.first() == packageName }
}

private suspend fun exportBuiltInResource(context: Context, id: String, uri: Uri) {
    try {
        withContext(Dispatchers.IO) {
            requireNotNull(PackResources.spec(context, id).extension)
            checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use {
                PackResources.exportArchive(context, id, it) { _, _ -> }
            }
        }
    } catch (failure: Throwable) {
        // A failed/cancelled export must not leave a ZIP that appears to be a complete backup.
        withContext(NonCancellable + Dispatchers.IO) {
            try { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            catch (cleanup: Exception) { AppLogger.w("BuiltInExport", "Could not remove incomplete archive", cleanup) }
        }
        throw failure
    }
}

/** Appended directly to the detail FlowRow after Performance; the painted buttons retain this order. */
@Composable
internal fun BuiltInResourceDetailActions(
    packageName: String, toolPackage: ToolPackage?, manager: PackageManager, busy: Boolean, onDelete: () -> Unit,
) {
    if (toolPackage?.isBuiltIn != true || !manager.isTopLevelPackage(packageName)) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by PackageUpdateState.revision.collectAsState()
    val checking by ExtensionUpdates.checking.collectAsState()
    var resource by remember(packageName) { mutableStateOf<ResourcePackSpec?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExportId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(packageName, revision) {
        try {
            resource = withContext(Dispatchers.IO) {
                BuiltInExtensionResources.specs(context).first { it.extension!!.packageNames.first() == packageName }
                    .takeIf { PackResources.isReady(context, it.id) && !PackResources.isBundled(context, it.id) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            resource = null
            AppLogger.e("BuiltInExport", "Could not resolve resource: $packageName", error)
            OKToast.error(context, extensionErrorDetails(error), OKToast.LENGTH_LONG).show()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = pendingExportId
        pendingExportId = null
        if (uri != null && id != null) {
            exporting = true
            scope.launch {
                try {
                    exportBuiltInResource(context, id, uri)
                    OKToast.success(context, R.string.export_success).show()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    AppLogger.e("BuiltInExport", "Export failed: $id", error)
                    OKToast.error(context, extensionErrorDetails(error), OKToast.LENGTH_LONG).show()
                } finally { exporting = false }
            }
        }
    }
    val spec = resource ?: return
    val enabled = !busy && !exporting && pendingExportId == null && !checking
    OutlinedButton(enabled = enabled, onClick = {
        pendingExportId = spec.id
        picker.launch("operit-${spec.id}-backup.zip")
    }) {
        if (exporting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export))
    }
    OutlinedButton(enabled = enabled, onClick = onDelete,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pkg_delete))
    }
}
