package com.ai.assistance.operit.ui.features.chat.webview.workspace.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.workspace.DirectoryEntry

internal fun sortDirectoryEntries(entries: List<DirectoryEntry>, mode: Int, descending: Boolean,
    ignoreCase: Boolean, foldersFirst: Boolean): List<DirectoryEntry> {
    val names = Comparator<DirectoryEntry> { a, b -> a.name.compareTo(b.name, ignoreCase) }
    val modifiedTimes = if (mode == 3) entries.associateWith { directoryModifiedTime(it.lastModified) } else emptyMap()
    val values = when (mode) {
        1 -> Comparator<DirectoryEntry> { a, b ->
            a.name.substringAfterLast('.', "").compareTo(b.name.substringAfterLast('.', ""), ignoreCase)
        }
        2 -> compareBy<DirectoryEntry> { it.size }
        3 -> compareBy<DirectoryEntry> { modifiedTimes[it] }
        else -> names
    }.then(names)
    val ordered = if (descending) values.reversed() else values
    // 文件夹置顶与倒序分别控制，倒序时仍可将文件夹排在前面。
    return entries.sortedWith(if (foldersFirst) compareBy<DirectoryEntry> { !it.isDirectory }.then(ordered) else ordered)
}

@Composable
internal fun FileBrowserContextMenu(name: String, isDirectory: Boolean, canPaste: Boolean,
    onDismiss: () -> Unit, onCopy: (Boolean) -> Unit, onPaste: () -> Unit,
    onRename: () -> Unit, onOpenWith: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row {
                    Action(Modifier.weight(1f), Icons.Default.ContentCopy, R.string.file_manager_copy) { onCopy(false) }
                    Action(Modifier.weight(1f), Icons.Default.ContentCut, R.string.file_manager_cut) { onCopy(true) }
                }
                Row {
                    Action(Modifier.weight(1f), Icons.Default.ContentPaste, R.string.file_manager_paste, canPaste, onClick = onPaste)
                    Action(Modifier.weight(1f), Icons.Default.DriveFileRenameOutline, R.string.file_menu_rename, onClick = onRename)
                }
                Row {
                    Action(Modifier.weight(1f), Icons.Default.OpenInNew, R.string.workspace_open_with, !isDirectory, onClick = onOpenWith)
                    Action(Modifier.weight(1f), Icons.Default.Share, R.string.file_menu_share, !isDirectory, onClick = onShare)
                }
                Row {
                    Action(Modifier.weight(1f), Icons.Default.Delete, R.string.file_manager_delete, destructive = true, onClick = onDelete)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Action(modifier: Modifier, icon: ImageVector, label: Int, enabled: Boolean = true,
    destructive: Boolean = false, onClick: () -> Unit) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier.heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(stringResource(label), color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FileBrowserSortDialog(mode: Int, descending: Boolean, ignoreCase: Boolean, foldersFirst: Boolean,
    onDismiss: () -> Unit, onConfirm: (Int, Boolean, Boolean, Boolean) -> Unit) {
    var selected by remember { mutableIntStateOf(mode) }
    var reverse by remember { mutableStateOf(descending) }
    var insensitive by remember { mutableStateOf(ignoreCase) }
    var folders by remember { mutableStateOf(foldersFirst) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.file_manager_sort_by)) },
        text = { Column {
            listOf(R.string.file_manager_sort_name, R.string.file_manager_sort_type,
                R.string.file_manager_sort_size, R.string.file_manager_sort_by_modified).forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().clickable { selected = index }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == index, onClick = { selected = index })
                    Text(stringResource(label))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SortOption(R.string.workspace_sort_descending, reverse) { reverse = it }
            SortOption(R.string.workspace_sort_ignore_case, insensitive) { insensitive = it }
            SortOption(R.string.workspace_sort_folders_first, folders) { folders = it }
        } },
        confirmButton = { TextButton(onClick = { onConfirm(selected, reverse, insensitive, folders) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SortOption(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(stringResource(label))
    }
}