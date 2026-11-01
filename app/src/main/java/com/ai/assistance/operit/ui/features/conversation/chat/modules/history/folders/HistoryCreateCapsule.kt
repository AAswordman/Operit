package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

/** 两个独立操作共用外观，布局宽度由原先新建对话所在槽位决定。 */
@Composable
internal fun HistoryCreateCapsule(onNewChat: () -> Unit, onNewFolder: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val content = getTextColorForBackground(color)
    Surface(modifier.height(36.dp), shape = RoundedCornerShape(50), color = color, contentColor = content) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onNewChat).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.new_chat), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VerticalDivider(Modifier.height(16.dp), color = content.copy(alpha = 0.25f))
            IconButton(onClick = onNewFolder, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.folder_create), Modifier.size(18.dp))
            }
        }
    }
}