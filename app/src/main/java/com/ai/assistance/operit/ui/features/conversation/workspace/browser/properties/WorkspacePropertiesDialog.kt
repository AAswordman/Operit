package com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 名称和目录使用普通点击复制，不安装文本选择或输入控件，长按也不会唤起系统复制菜单。 */
@Composable
internal fun WorkspacePropertiesDialog(
    target: WorkspacePropertiesTarget,
    onDismiss: () -> Unit,
    onModified: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repository by remember(target) { mutableStateOf<WorkspacePropertiesRepository?>(null) }
    var properties by remember(target) { mutableStateOf<WorkspaceProperties?>(null) }
    var totals by remember(target) { mutableStateOf<WorkspaceDirectoryTotals?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var notice by remember(target) { mutableStateOf<String?>(null) }
    var revision by remember(target) { mutableIntStateOf(0) }
    var more by remember(target) { mutableStateOf(false) }
    var editingTime by remember(target) { mutableStateOf(false) }
    var saving by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target, revision) {
        loading = true
        error = null
        totals = null
        properties = null
        repository = null
        try {
            val reader = WorkspacePropertiesRepository.open(context.applicationContext, target)
            repository = reader
            val loaded = reader.read()
            properties = loaded
            if (loaded.node.directory && !loaded.node.symbolicLink) totals = reader.totals()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceProperties", "读取工作区属性失败", failure)
            error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }
    val info = properties
    val unknown = stringResource(R.string.workspace_properties_unknown)
    val calculating = stringResource(R.string.workspace_properties_calculating)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.workspace_properties_title)) },
        text = {
            DisableSelection {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PropertyRow(stringResource(R.string.workspace_properties_name), target.name) { copyProperty(context, target.name) }
                    PropertyRow(stringResource(R.string.workspace_properties_directory), target.directory) { copyProperty(context, target.directory) }
                    if (info != null) {
                        val node = info.node
                        val type = when {
                            node.symbolicLink -> stringResource(R.string.workspace_properties_symlink)
                            node.directory -> stringResource(R.string.workspace_properties_folder)
                            target.name.substringAfterLast('.', "").isNotBlank() -> stringResource(
                                R.string.workspace_properties_extension_type, target.name.substringAfterLast('.').uppercase(Locale.ROOT),
                            )
                            else -> stringResource(R.string.workspace_properties_file)
                        }
                        PropertyRow(stringResource(R.string.workspace_properties_type), type)
                        val bytes = if (node.directory && !node.symbolicLink) totals?.bytes else node.size
                        PropertyRow(stringResource(R.string.workspace_properties_size), when {
                            bytes != null -> propertySize(bytes)
                            loading -> calculating
                            else -> unknown
                        })
                        PropertyRow(stringResource(R.string.workspace_properties_modified), node.modified?.let(::propertyTime) ?: unknown)
                        if (node.directory && !node.symbolicLink) {
                            PropertyRow(stringResource(R.string.workspace_properties_file_count), totals?.files?.let(::propertyCount) ?: if (loading) calculating else unknown)
                            PropertyRow(stringResource(R.string.workspace_properties_folder_count), totals?.directories?.let(::propertyCount) ?: if (loading) calculating else unknown)
                            Text(stringResource(R.string.workspace_properties_recursive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!info.canModifyTime) Text(
                            stringResource(R.string.workspace_properties_time_unsupported),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (info == null) PropertyRow(stringResource(R.string.workspace_properties_size), if (loading) calculating else unknown)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    totals?.let { total ->
                        if (total.unreadable > 0 || total.unknownSizes > 0) Text(
                            stringResource(R.string.workspace_properties_incomplete, total.unreadable, total.unknownSizes),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    error?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { revision++ }, enabled = !loading && !saving) { Text(stringResource(R.string.workspace_properties_retry)) }
                    }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        dismissButton = {
            Box {
                TextButton(onClick = { more = true }, enabled = !loading && !saving && info?.canModifyTime == true) {
                    Text(stringResource(R.string.workspace_properties_more))
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workspace_properties_change_time)) },
                        onClick = { more = false; editingTime = true },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.ok)) } },
    )
    if (editingTime && info != null) WorkspaceModifiedTimeDialog(
        initial = info.node.modified,
        directory = info.node.directory,
        busy = saving,
        onDismiss = { if (!saving) editingTime = false },
        onConfirm = { millis, recursive ->
            val writer = repository
            if (writer != null && !saving) {
                saving = true
                error = null
                scope.launch {
                    try {
                        val result = writer.changeModified(millis, recursive)
                        notice = context.getString(R.string.workspace_properties_time_result, result.changed, result.failed)
                        onModified()
                        editingTime = false
                        revision++
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (failure: Exception) {
                        AppLogger.e("WorkspaceProperties", "更新时间失败", failure)
                        error = failure.localizedMessage ?: failure.javaClass.simpleName
                        editingTime = false
                    } finally {
                        saving = false
                    }
                }
            }
        },
    )
}

@Composable
private fun PropertyRow(label: String, value: String, copy: (() -> Unit)? = null) {
    val modifier = Modifier.fillMaxWidth().then(if (copy == null) Modifier else Modifier.clickable(onClick = copy))
    Row(modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, modifier = Modifier.width(68.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = if (copy == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
    }
}

private fun copyProperty(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("", value))
    Toast.makeText(context, R.string.workspace_properties_copied, Toast.LENGTH_SHORT).show()
}

private val propertyTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
private fun propertyTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(propertyTimeFormat)
private fun propertyCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)
private fun propertySize(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var amount = bytes.toDouble()
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
    val formatted = if (unit == 0) bytes.toString() else String.format(Locale.getDefault(), "%.2f", amount)
    return "$formatted ${units[unit]} (${propertyCount(bytes)} byte)"
}

@Composable
private fun WorkspaceModifiedTimeDialog(
    initial: Long?,
    directory: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(propertyTime(initial ?: System.currentTimeMillis())) }
    var recursive by remember { mutableStateOf(false) }
    val instant = remember(text) {
        try { LocalDateTime.parse(text, propertyTimeFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        catch (_: java.time.DateTimeException) { null }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.workspace_properties_change_time)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.workspace_properties_modified)) },
                    supportingText = { Text(stringResource(R.string.workspace_properties_time_format)) },
                    isError = instant == null, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { text = propertyTime(System.currentTimeMillis()) }, enabled = !busy) { Text(stringResource(R.string.workspace_properties_now)) }
                if (directory) Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { recursive = !recursive }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recursive, onCheckedChange = { recursive = it }, enabled = !busy)
                    Text(stringResource(R.string.workspace_properties_apply_children), modifier = Modifier.weight(1f))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { instant?.let { onConfirm(it, recursive) } }, enabled = !busy && instant != null) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } },
    )
}