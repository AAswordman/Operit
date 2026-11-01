package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager

/** 对照参考主页展示概览，所有入口都连接现有的工作区和终端能力。 */
@Composable
fun WorkspaceHubHome(
    currentChat: ChatHistory?,
    chatHistories: List<ChatHistory>,
    terminalSessionCount: Int,
    onOpenWorkspace: () -> Unit,
    onCreateWorkspace: () -> Unit,
    onSelectWorkspace: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenHtmlPackager: () -> Unit,
) {
    val context = LocalContext.current
    val groupManager = remember(context) { CharacterGroupCardManager.getInstance(context) }
    val groups by groupManager.allCharacterGroupCardsFlow.collectAsState(initial = emptyList())
    val workspace = currentChat?.workspace?.takeIf { it.isNotBlank() }
    val workspaceChats = remember(chatHistories, workspace, currentChat?.workspaceEnv) {
        if (workspace == null) {
            emptyList()
        } else {
            chatHistories.filter {
                it.workspace == workspace && it.workspaceEnv == currentChat?.workspaceEnv
            }
        }
    }
    val characterCount = remember(workspaceChats, groups) {
        val cards = workspaceChats.filter { it.characterGroupId == null }
            .mapNotNull { it.characterCardName }.toMutableSet()
        val groupIds = workspaceChats.mapNotNull { it.characterGroupId }.toSet()
        groups.filter { it.id in groupIds }.flatMap { it.members }.forEach {
            cards.add(it.characterCardId)
        }
        cards.size
    }
    val shape = RoundedCornerShape(8.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            border = border,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WorkspaceActionIcon(Icons.Default.WorkOutline, large = true)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            text = stringResource(R.string.workspace_hub_overview),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = workspace ?: stringResource(R.string.workspace_hub_unbound),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.workspace_hub_usage,
                        workspaceChats.size,
                        characterCount,
                        if (workspace == null) 0 else 1,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onOpenTerminal)
                        .heightIn(min = 44.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.workspace_hub_terminal_sessions, terminalSessionCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (workspace != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = onOpenWorkspace)
                            .heightIn(min = 44.dp)
                            .padding(top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = workspace,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
        if (workspace == null) {
            WorkspacePrimaryAction(
                icon = Icons.Default.CreateNewFolder,
                title = stringResource(R.string.workspace_hub_create),
                subtitle = stringResource(R.string.create_new_workspace_in_app),
                onClick = onCreateWorkspace,
            )
            WorkspacePrimaryAction(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.workspace_hub_select),
                subtitle = stringResource(R.string.select_folder_from_device),
                onClick = onSelectWorkspace,
            )
        }
        WorkspacePrimaryAction(
            icon = Icons.Default.Terminal,
            title = stringResource(R.string.open_terminal),
            subtitle = stringResource(R.string.workspace_hub_terminal_description),
            onClick = onOpenTerminal,
        )
        WorkspacePrimaryAction(
            icon = Icons.Default.FolderOpen,
            title = stringResource(R.string.workspace_hub_open),
            subtitle = stringResource(R.string.workspace_hub_open_description),
            onClick = onOpenWorkspace,
        )
        WorkspacePrimaryAction(
            icon = Icons.Default.Html,
            title = stringResource(R.string.tool_html_packager),
            subtitle = stringResource(R.string.workspace_html_export_desc),
            onClick = onOpenHtmlPackager,
        )
    }
}

@Composable
private fun WorkspacePrimaryAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WorkspaceActionIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun WorkspaceActionIcon(icon: ImageVector, large: Boolean = false) {
    Surface(
        modifier = Modifier.size(if (large) 46.dp else 34.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (large) 25.dp else 20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
