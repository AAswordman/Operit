package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CodeOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubPage
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.script.*

/** 主界面、嵌入式聊天和主题预览共用布局，业务动作由各自宿主提供。 */
@Composable
internal fun MainChatControlsContent(
    title: String,
    runningTaskCount: Int,
    isFloatingMode: Boolean,
    isWorkspacePreparing: Boolean,
    workspacePage: WorkspaceHubPage,
    hasWorkspace: Boolean,
    onOpenSidebar: () -> Unit,
    onLaunchFloatingWindow: () -> Unit,
    onOpenWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val workspaceVisible = workspacePage != WorkspaceHubPage.CHAT
    val contentColor = themeScriptContentColor("appBar", getTextColorForBackground(if (workspaceVisible) colors.surface else colors.background))
    val shape = themeScriptShape("appBar", androidx.compose.ui.graphics.RectangleShape)
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp)
            .background(themeScriptFill("appBar", if (workspaceVisible) colors.surface.copy(alpha = 1f) else Color.Transparent), shape)
            .themeScriptDecoration("appBar", drawFill = false, originalShape = shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeScriptIconButton(
            actionId = "appBar.menu",
            onClick = onOpenSidebar,
            modifier = Modifier.size(40.dp),
        ) {
            BadgedBox(badge = { if (runningTaskCount >= 2) Badge { Text(runningTaskCount.toString()) } }) {
                ThemeScriptIcon("appBar.menu", Icons.Default.Menu, stringResource(R.string.menu), tint = contentColor, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            style = themeScriptTextStyle("appBar", MaterialTheme.typography.titleSmall),
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ThemeScriptIconButton(
            actionId = "appBar.floating",
            selected = isFloatingMode,
            onClick = onLaunchFloatingWindow,
            modifier = Modifier.size(36.dp),
        ) {
            ThemeScriptIcon(
                "appBar.floating", Icons.Default.PictureInPicture,
                stringResource(if (isFloatingMode) R.string.close_floating_window else R.string.open_floating_window),
                tint = if (isFloatingMode) colors.primary else contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        ThemeScriptIconButton(
            actionId = "appBar.workspace",
            selected = workspaceVisible,
            onClick = onOpenWorkspace,
            modifier = Modifier.size(36.dp),
        ) {
            if (isWorkspacePreparing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = contentColor)
            } else {
                ThemeScriptIcon(
                    "appBar.workspace", if (hasWorkspace) Icons.Default.Code else Icons.Default.CodeOff,
                    stringResource(R.string.workspace_hub_entry),
                    tint = if (workspaceVisible) colors.primary else contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
