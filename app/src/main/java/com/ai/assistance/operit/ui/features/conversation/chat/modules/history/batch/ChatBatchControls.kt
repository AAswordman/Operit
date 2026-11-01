package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.batch

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchAction
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchProgress

@Composable
internal fun ChatBatchSelectionHeader(
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    busy: Boolean,
    contentColor: Color,
    onSelectAll: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.selected_count, selectedCount), Modifier.weight(1f),
            color = contentColor, style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onSelectAll, enabled = canSelect && !busy) {
            Text(stringResource(if (allSelected) R.string.cancel_selection else R.string.select_all_current_list))
        }
        IconButton(onClick = onExit, enabled = !busy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.chat_batch_exit), tint = contentColor,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ChatBatchActionBar(
    selectedChats: List<ChatHistory>,
    archived: Boolean,
    progress: ChatBatchProgress?,
    containerColor: Color,
    contentColor: Color,
    onAction: (ChatBatchAction) -> Unit,
    onMove: () -> Unit,
    moving: Boolean,
) {
    val busy = progress != null || moving
    val enabled = selectedChats.isNotEmpty() && !busy
    val unlock = selectedChats.isNotEmpty() && selectedChats.all { it.locked }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(Modifier.padding(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                ChatBatchButton(Modifier.weight(1f), Icons.Default.AutoAwesome,
                    stringResource(R.string.chat_batch_summarize), enabled) { onAction(ChatBatchAction.SUMMARIZE_TITLES) }
                ChatBatchButton(Modifier.weight(1f), if (archived) Icons.Default.Restore else Icons.Default.Archive,
                    stringResource(if (archived) R.string.chat_unarchive else R.string.chat_archive), enabled) {
                    onAction(if (archived) ChatBatchAction.UNARCHIVE else ChatBatchAction.ARCHIVE)
                }
                ChatBatchButton(Modifier.weight(1f), if (unlock) Icons.Outlined.LockOpen else Icons.Default.Lock,
                    stringResource(if (unlock) R.string.unlock_chat else R.string.lock_chat), enabled) {
                    onAction(if (unlock) ChatBatchAction.UNLOCK else ChatBatchAction.LOCK)
                }
                ChatBatchButton(Modifier.weight(1f), Icons.Default.DriveFileMove,
                    stringResource(R.string.chat_batch_move), enabled, onMove)
                ChatBatchButton(Modifier.weight(1f), Icons.Default.Delete, stringResource(R.string.delete),
                    enabled && selectedChats.any { !it.locked }) { onAction(ChatBatchAction.DELETE) }
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                Text(stringResource(R.string.chat_batch_progress, progress.completed, progress.total),
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall)
            } else if (selectedChats.any { it.locked }) {
                Text(stringResource(R.string.chat_batch_locked_hint),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ChatBatchButton(
    modifier: Modifier, icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ChatBatchDeleteDialog(
    chats: List<ChatHistory>, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_batch_delete_title)) },
        text = { Text(stringResource(R.string.chat_batch_delete_message,
            chats.count { !it.locked }, chats.count { it.locked })) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = chats.any { !it.locked }) {
                Text(stringResource(R.string.confirm_delete_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
