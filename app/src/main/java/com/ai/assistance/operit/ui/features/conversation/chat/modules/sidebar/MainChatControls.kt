package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar


import com.ai.assistance.operit.ui.common.oktoast.OKToast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubPage
import kotlinx.coroutines.flow.collect

/** 主聊天页保留一行透明操作区，标题与入口共用菜单按钮的高度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatControls(modifier: Modifier = Modifier) {
    val viewModel = LocalMainChatViewModel.current
    val openSidebar = LocalOpenMainChatSidebar.current
    val currentChatId by viewModel.currentChatId.collectAsState()
    val histories by viewModel.chatHistories.collectAsState()
    val streamingIds by viewModel.activeStreamingChatIds.collectAsState()
    val isFloatingMode by viewModel.isFloatingMode.collectAsState()
    val isWorkspacePreparing by viewModel.isWorkspacePreparing.collectAsState()
    val workspacePage by viewModel.workspaceHubPage.collectAsState()
    val currentChat = histories.firstOrNull { it.id == currentChatId }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.launchWindowFloatingModeAfterMicPermissionGranted(
                colorScheme, typography, moveTaskToBackOnReady = true,
            )
        } else {
            OKToast.warning(context, context.getString(R.string.microphone_permission_denied)).show()
        }
    }
    val launchFloatingWindow = {
        viewModel.onFloatingButtonClick(
            com.ai.assistance.operit.ui.floating.FloatingMode.WINDOW,
            permissionLauncher, colorScheme, typography, moveTaskToBackOnReady = true,
        )
    }
    LaunchedEffect(viewModel, context) {
        viewModel.moveTaskToBackEvents.collect {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }
    fun dismissKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }

    MainChatControlsContent(
        title = when (workspacePage) {
            WorkspaceHubPage.CHAT -> currentChat?.title ?: stringResource(R.string.new_chat)
            WorkspaceHubPage.TERMINAL -> stringResource(R.string.terminal_title)
            else -> stringResource(R.string.workspace_hub_entry)
        },
        runningTaskCount = streamingIds.size,
        isFloatingMode = isFloatingMode,
        isWorkspacePreparing = isWorkspacePreparing,
        workspacePage = workspacePage,
        hasWorkspace = !currentChat?.workspace.isNullOrBlank(),
        onOpenSidebar = { dismissKeyboard(); openSidebar() },
        onLaunchFloatingWindow = { dismissKeyboard(); launchFloatingWindow() },
        onOpenWorkspace = { dismissKeyboard(); viewModel.onWorkspaceHubButtonClick() },
        modifier = modifier,
    )
}
