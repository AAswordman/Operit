package com.ai.assistance.operit.ui.features.management.databackup.themes

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.themesharing.ThemeTransferError
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupDialog
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.*
import com.ai.assistance.operit.ui.features.management.settings.components.ImportExportRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchPreview
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTargetPicker
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTargetSelection
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTransferCodeBlock
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTransferFileCard
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTransferIdentity
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTransferMethodSelector
import com.ai.assistance.operit.ui.features.management.databackup.themes.components.ThemeTransferTextField

/** 单项入口复用其他备份行；抽屉只管理展示，继续调用现有主题导入导出状态。 */
@Composable
internal fun ThemeTransferEntry() {
    val context = LocalContext.current
    // 路由容器不提供 AndroidViewModel 工厂，显式传入 Application 创建主题分享状态。
    val factory =
        remember(context) {
            ViewModelProvider.AndroidViewModelFactory.getInstance(
                context.applicationContext as Application
            )
        }
    val vm: ThemeTransferViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    var launchedArchive by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingTarget by rememberSaveable { mutableStateOf(false) }
    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) {
            vm.saveExport(it)
        }
    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.readFile(uri)
        }
    LaunchedEffect(state.panel) {
        if (state.panel != ThemeTransferPanel.PREVIEW) choosingTarget = false
    }
    LaunchedEffect(state.pendingArchive, state.busy) {
        val file = state.pendingArchive
        if (file != null && !state.busy && launchedArchive != file.absolutePath) {
            launchedArchive = file.absolutePath
            val safeName = state.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80)
            saveLauncher.launch("$safeName.oktheme")
        }
    }
    LaunchedEffect(state.notification) {
        if (state.notification) {
            Toast.makeText(context, R.string.theme_transfer_success, Toast.LENGTH_SHORT).show()
            vm.consumeNotification()
        }
    }
    ImportExportRow(
        title = stringResource(R.string.theme_transfer_title),
        subtitle = stringResource(R.string.theme_transfer_entry_subtitle),
        icon = Icons.Default.Palette,
        onExport = vm::openExport,
        onImport = vm::openImport,
        enabled = !state.busy,
    )
    if (state.panel == ThemeTransferPanel.CLOSED) return
    val title =
        if (choosingTarget) R.string.theme_transfer_target
        else
            when (state.panel) {
                ThemeTransferPanel.EXPORT -> R.string.theme_transfer_export
                ThemeTransferPanel.GENERATED -> R.string.theme_transfer_generated
                ThemeTransferPanel.PREVIEW -> R.string.theme_transfer_preview
                else -> R.string.theme_transfer_import
            }
    val dismiss: () -> Unit = {
        if (!state.busy) {
            if (choosingTarget) choosingTarget = false else vm.close()
        }
    }
    // 选择面板关闭时重建抽屉状态，避免系统返回或下滑后停留在 Hidden 状态。
    key(choosingTarget) {
        DataBackupDialog(
            onDismissRequest = dismiss,
            dismissEnabled = !state.busy,
            title = { Text(stringResource(title)) },
            text = {
                if (choosingTarget) {
                    ThemeTargetPicker(state.targets, state.selectedId, !state.busy) { id ->
                        choosingTarget = false
                        vm.select(id)
                    }
                } else
                    when (state.panel) {
                        ThemeTransferPanel.EXPORT -> {
                            ThemeTransferMethodSelector(state.method, !state.busy, vm::method)
                            state.source?.let { source ->
                                ThemeTransferIdentity(
                                    source.name,
                                    "",
                                    stringResource(R.string.theme_transfer_source),
                                    source.values.string("custom_ai_avatar_uri"),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ThemeTransferTextField(
                                    state.name,
                                    vm::name,
                                    stringResource(R.string.theme_transfer_name),
                                    enabled = !state.busy,
                                )
                                ThemeTransferTextField(
                                    state.author,
                                    vm::author,
                                    stringResource(R.string.theme_transfer_author),
                                    enabled = !state.busy,
                                )
                            }
                            if (state.method == ThemeTransferMethod.FILE) {
                                Text(
                                    stringResource(R.string.theme_transfer_full_contents),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ThemeTransferPanel.GENERATED -> {
                            ThemeTransferIdentity(
                                state.name,
                                state.author,
                                stringResource(R.string.theme_transfer_code),
                            )
                            ThemeTransferCodeBlock(state.generated)
                        }
                        ThemeTransferPanel.IMPORT -> {
                            ThemeTransferMethodSelector(state.method, !state.busy, vm::method)
                            if (state.method == ThemeTransferMethod.CODE) {
                                ThemeTransferTextField(
                                    state.input,
                                    vm::input,
                                    stringResource(R.string.theme_transfer_code),
                                    placeholder = stringResource(R.string.theme_transfer_paste),
                                    multiline = true,
                                    code = true,
                                    enabled = !state.busy,
                                )
                            } else {
                                val document = state.prepared?.document
                                ThemeTransferFileCard(
                                    document?.name,
                                    document?.author.orEmpty(),
                                    !state.busy,
                                ) {
                                    openLauncher.launch(arrayOf("*/*"))
                                }
                            }
                        }
                        ThemeTransferPanel.PREVIEW ->
                            ThemeImportPreview(state, vm) { choosingTarget = true }
                        else -> Unit
                    }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(errorResource(error)),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (choosingTarget) {
                    FilledTonalButton(
                        onClick = dismiss,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.theme_transfer_back_preview))
                    }
                } else {
                    val enabled =
                        !state.busy &&
                            when (state.panel) {
                                ThemeTransferPanel.EXPORT ->
                                    state.source != null &&
                                        state.name.isNotBlank() &&
                                        state.pendingArchive == null
                                ThemeTransferPanel.IMPORT ->
                                    if (state.method == ThemeTransferMethod.CODE)
                                        state.input.isNotBlank()
                                    else state.prepared != null
                                ThemeTransferPanel.PREVIEW ->
                                    state.selectedId != null && state.previewValues != null
                                else -> true
                            }
                    val actionLabel =
                        when (state.panel) {
                            ThemeTransferPanel.GENERATED -> R.string.theme_transfer_copy
                            ThemeTransferPanel.PREVIEW -> R.string.theme_transfer_apply
                            ThemeTransferPanel.IMPORT -> R.string.theme_transfer_validate
                            else ->
                                if (state.method == ThemeTransferMethod.CODE)
                                    R.string.theme_transfer_generate
                                else R.string.theme_transfer_export_file
                        }
                    val actionIcon =
                        when (state.panel) {
                            ThemeTransferPanel.GENERATED -> Icons.Default.ContentCopy
                            ThemeTransferPanel.PREVIEW -> Icons.Default.Check
                            ThemeTransferPanel.IMPORT -> Icons.Default.Visibility
                            else ->
                                if (state.method == ThemeTransferMethod.CODE) Icons.Default.Link
                                else Icons.Default.FileOpen
                        }
                    // 按钮在本地 Row 作用域中分配宽度，避免依赖外部抽屉回调的布局作用域。
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = dismiss,
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            enabled = enabled,
                            modifier = Modifier.weight(2f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                when (state.panel) {
                                    ThemeTransferPanel.GENERATED -> {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                context.getString(R.string.theme_transfer_code),
                                                state.generated,
                                            )
                                        )
                                        Toast.makeText(
                                                context,
                                                R.string.theme_transfer_copied,
                                                Toast.LENGTH_SHORT,
                                            )
                                            .show()
                                    }
                                    ThemeTransferPanel.IMPORT -> vm.preview()
                                    ThemeTransferPanel.PREVIEW -> vm.apply()
                                    ThemeTransferPanel.EXPORT ->
                                        if (state.method == ThemeTransferMethod.CODE) vm.generate()
                                        else vm.prepareExport()
                                    else -> Unit
                                }
                            },
                        ) {
                            Icon(actionIcon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(actionLabel))
                        }
                    }
                }
            },
        )
    }
    state.overwriteTargetId?.let { id ->
        val name = state.targets.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { if (!state.busy) vm.cancelOverwrite() },
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Palette, null) },
            title = { Text(stringResource(R.string.theme_transfer_overwrite_title)) },
            text = { Text(stringResource(R.string.theme_transfer_overwrite_message, name)) },
            dismissButton = {
                TextButton(onClick = vm::cancelOverwrite, enabled = !state.busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.apply(overwrite = true) },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.theme_transfer_overwrite))
                }
            },
        )
    }
}

@Composable
private fun ThemeImportPreview(
    state: ThemeTransferUiState,
    vm: ThemeTransferViewModel,
    onPickTarget: () -> Unit,
) {
    val theme = state.prepared ?: return
    val context = LocalContext.current
    ThemeTransferIdentity(
        theme.document.name,
        theme.document.author,
        if (theme.document.full) ".oktheme" else stringResource(R.string.theme_transfer_code),
    )
    ThemeTargetSelection(
        state.targets.firstOrNull { it.id == state.selectedId },
        !state.busy,
        onPickTarget,
    )
    state.previewValues?.let { values ->
        val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
        val userName = stringResource(R.string.theme_transfer_preview_user)
        val session =
            remember(values, theme.document.layout) {
                ThemeEditorSession(preferences, values, userName, theme.document.layout)
            }
        DisposableEffect(session) { onDispose { session.dispose() } }
        ThemeWorkbenchPreview(session, full = true, showDescription = false)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Tune,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.theme_transfer_global_layout),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun errorResource(error: ThemeTransferError): Int =
    when (error) {
        ThemeTransferError.INVALID_CODE -> R.string.theme_transfer_error_code
        ThemeTransferError.INVALID_CONFIG -> R.string.theme_transfer_error_config
        ThemeTransferError.UNSUPPORTED_VERSION -> R.string.theme_transfer_error_version
        ThemeTransferError.MISSING_MANIFEST -> R.string.theme_transfer_error_manifest
        ThemeTransferError.INVALID_ARCHIVE -> R.string.theme_transfer_error_archive
        ThemeTransferError.MISSING_ASSET -> R.string.theme_transfer_error_asset
        ThemeTransferError.TOO_LARGE -> R.string.theme_transfer_error_size
        ThemeTransferError.CHARACTER_MISSING -> R.string.theme_transfer_error_target
        ThemeTransferError.OVERWRITE_REQUIRED -> R.string.theme_transfer_overwrite_title
        ThemeTransferError.IO_ERROR -> R.string.theme_transfer_error_io
    }
