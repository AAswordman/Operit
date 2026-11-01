package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.terminal.data.TerminalSessionData
import com.ai.assistance.operit.ui.features.automation.terminal.EmbeddedTerminalPage

@Composable
internal fun WorkspaceSessionTabs(
    page: WorkspaceHubPage,
    hasWorkspace: Boolean,
    hasTerminal: Boolean,
    sessions: List<TerminalSessionData>,
    currentSessionId: String?,
    creatingSession: Boolean,
    onHome: () -> Unit,
    onWorkspace: () -> Unit,
    onTerminal: (String) -> Unit,
    onCloseTerminal: (String) -> Unit,
    onNewTerminal: () -> Unit,
    onTerminalPage: (EmbeddedTerminalPage) -> Unit,
) {
    Row(
        Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionTab(stringResource(R.string.workspace_hub_home), page == WorkspaceHubPage.HOME, onHome)
            if (hasWorkspace) {
                SessionTab(stringResource(R.string.workspace), page == WorkspaceHubPage.WORKSPACE, onWorkspace)
            }
            if (hasTerminal) sessions.forEachIndexed { index, session ->
                key(session.id) {
                    val title = if (session.title == "Local") {
                        stringResource(R.string.workspace_terminal_session_title, index + 1)
                    } else session.title
                    SessionTab(
                        title = title,
                        selected = page == WorkspaceHubPage.TERMINAL && currentSessionId == session.id,
                        onClick = { onTerminal(session.id) },
                        onClose = { onCloseTerminal(session.id) },
                    )
                }
            }
        }
        if (hasTerminal) {
            val newTerminalIconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (creatingSession) 0.38f else 1f,
            )
            IconButton(onClick = onNewTerminal, enabled = !creatingSession) {
                if (creatingSession) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = newTerminalIconTint,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Add,
                        stringResource(R.string.workspace_terminal_new),
                        tint = newTerminalIconTint,
                    )
                }
            }
        }
        if (page == WorkspaceHubPage.TERMINAL) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        stringResource(R.string.workspace_terminal_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.widthIn(min = 228.dp, max = 280.dp),
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workspace_terminal_setup), style = MaterialTheme.typography.titleSmall) },
                        leadingIcon = {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Default.Build, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 6.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            menuOpen = false
                            onTerminalPage(EmbeddedTerminalPage.SETUP)
                        },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workspace_terminal_settings), style = MaterialTheme.typography.titleSmall) },
                        leadingIcon = {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(Icons.Default.Settings, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 6.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            menuOpen = false
                            onTerminalPage(EmbeddedTerminalPage.SETTINGS)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTab(title: String, selected: Boolean, onClick: () -> Unit, onClose: (() -> Unit)? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.heightIn(min = 44.dp).padding(start = 14.dp, end = if (onClose == null) 14.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.widthIn(max = 156.dp), style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.workspace_terminal_close, title), Modifier.size(18.dp))
                }
            }
        }
    }
}
