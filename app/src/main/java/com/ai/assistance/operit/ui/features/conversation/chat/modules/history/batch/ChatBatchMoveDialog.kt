package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.batch

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.chat.folders.folderAncestors

/** 按稳定文件夹 ID 移动，同名子目录通过完整路径区分，根目录始终可选。 */
@Composable
internal fun ChatBatchMoveDialog(
    selectedCount: Int,
    folders: List<ChatFolder>,
    moving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    val destinations =
        remember(folders) {
            val byId = folders.associateBy { it.id }
            folders
                .map {
                    it to folderAncestors(it.id, byId).joinToString(" › ") { folder -> folder.name }
                }
                .sortedWith(
                    compareBy<Pair<ChatFolder, String>> { it.second }.thenBy { it.first.id }
                )
        }
    val rootLabel = stringResource(R.string.folder_root)
    AlertDialog(
        onDismissRequest = { if (!moving) onDismiss() },
        title = { Text(stringResource(R.string.chat_batch_move_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.chat_batch_move_message, selectedCount),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (moving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null)
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                LazyColumn(
                    Modifier.fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
                ) {
                    item(key = "root") {
                        ChatMoveDestination(rootLabel, null, enabled = !moving) { onMove(null) }
                    }
                    items(destinations, key = { "folder:${it.first.id}" }) { (folder, path) ->
                        ChatMoveDestination(folder.name, "$rootLabel › $path", enabled = !moving) {
                            onMove(folder.id)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !moving) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ChatMoveDestination(
    name: String,
    path: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Folder,
            null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (path != null)
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}
