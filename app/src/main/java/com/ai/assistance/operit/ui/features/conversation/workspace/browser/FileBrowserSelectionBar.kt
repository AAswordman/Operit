package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
internal fun FileBrowserSelectionBar(
    count: Int,
    enabled: Boolean,
    onFinish: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFinish, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close))
            }
            Text(
                stringResource(R.string.workspace_selection_count, count),
                Modifier.weight(1f).padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSelectAll, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SelectAll, stringResource(R.string.select_all_current_list))
            }
            IconButton(onClick = { onCopy(false) }, enabled = enabled && count > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.file_manager_copy))
            }
            IconButton(onClick = { onCopy(true) }, enabled = enabled && count > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ContentCut, stringResource(R.string.file_manager_cut))
            }
            IconButton(onClick = onDelete, enabled = enabled && count > 0, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    stringResource(R.string.file_manager_delete),
                    tint = if (enabled && count > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}