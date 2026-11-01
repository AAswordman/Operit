package com.ai.assistance.operit.ui.features.automation.websession.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptPageMenuCommand

@Composable
internal fun WebSessionMenuSheet(
    isDesktopMode: Boolean,
    downloadSummary: String?,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenUserscripts: () -> Unit,
    userscriptMenuCommands: List<UserscriptPageMenuCommand>,
    onInvokeUserscriptMenu: (String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onCloseCurrentTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    WebSessionSheetScaffold(
        title = stringResource(R.string.web_session_menu),
        subtitle =
            stringResource(
                R.string.web_session_current_mode,
                stringResource(
                    if (isDesktopMode) {
                        R.string.web_session_desktop_mode
                    } else {
                        R.string.web_session_mobile_mode
                    }
                )
            ),
        modifier = modifier
    ) {
        MenuActionCard(
            title = stringResource(R.string.web_session_history),
            icon = Icons.Filled.History,
            onClick = onOpenHistory
        )
        MenuActionCard(
            title = stringResource(R.string.web_session_bookmarks),
            icon = Icons.Filled.Bookmark,
            onClick = onOpenBookmarks
        )
        MenuActionCard(
            title = stringResource(R.string.web_session_downloads),
            subtitle = downloadSummary,
            icon = Icons.Filled.Download,
            onClick = onOpenDownloads
        )
        MenuActionCard(
            title = stringResource(R.string.web_session_userscripts),
            subtitle = stringResource(R.string.web_session_userscripts_subtitle),
            icon = Icons.Filled.Extension,
            onClick = onOpenUserscripts
        )
        if (userscriptMenuCommands.isNotEmpty()) {
            WebSessionSectionLabel(text = stringResource(R.string.web_session_userscript_page_menu))
            userscriptMenuCommands.forEach { command ->
                MenuActionCard(
                    title = command.title,
                    subtitle = stringResource(R.string.web_session_userscript_page_menu_subtitle),
                    icon = Icons.Filled.Extension,
                    onClick = { onInvokeUserscriptMenu(command.commandId) }
                )
            }
        }
        MenuActionCard(
            title =
                stringResource(
                    if (isDesktopMode) {
                        R.string.web_session_switch_to_mobile
                    } else {
                        R.string.web_session_switch_to_desktop
                    }
                ),
            subtitle =
                stringResource(
                    if (isDesktopMode) {
                        R.string.web_session_mobile_mode
                    } else {
                        R.string.web_session_desktop_mode
                    }
                ),
            icon =
                if (isDesktopMode) {
                    Icons.Filled.Smartphone
                } else {
                    Icons.Filled.DesktopWindows
                },
            onClick = onToggleDesktopMode
        )
        MenuActionCard(
            title = stringResource(R.string.web_session_close_current_tab),
            icon = Icons.Filled.Close,
            onClick = onCloseCurrentTab
        )
        MenuActionCard(
            title = stringResource(R.string.web_session_close_all_tabs),
            icon = Icons.Filled.Close,
            onClick = onCloseAllTabs,
            danger = true
        )
    }
}

@Composable
private fun MenuActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false
) {
    WebSessionItemCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color =
                    if (danger) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                            if (danger) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color =
                        if (danger) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
