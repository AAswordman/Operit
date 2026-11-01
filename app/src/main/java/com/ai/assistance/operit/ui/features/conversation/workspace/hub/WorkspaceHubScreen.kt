package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.ui.features.management.resources.OfflineResourcesActivity
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.automation.terminal.ComputerScreen
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceScreen
import java.io.File
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.ai.assistance.operit.terminal.data.TerminalSessionData
import com.ai.assistance.operit.ui.features.automation.terminal.EmbeddedTerminalPage
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkspaceHubScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    chatHistories: List<ChatHistory>,
    isVisible: Boolean,
    onOpenHtmlPackager: () -> Unit,
    reserveChatControlsSpace: Boolean = false,
    onExportClick: (File) -> Unit,
) {
    key(currentChat?.id) {
        WorkspaceHubContent(
            actualViewModel = actualViewModel,
            currentChat = currentChat,
            chatHistories = chatHistories,
            isVisible = isVisible,
            onOpenHtmlPackager = onOpenHtmlPackager,
            reserveChatControlsSpace = reserveChatControlsSpace,
            onExportClick = onExportClick,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun WorkspaceHubContent(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    chatHistories: List<ChatHistory>,
    isVisible: Boolean,
    onOpenHtmlPackager: () -> Unit,
    reserveChatControlsSpace: Boolean = false,
    onExportClick: (File) -> Unit,
) {
    val requestedPage by actualViewModel.workspaceHubPage.collectAsState()
    var lastOpenPage by remember { mutableStateOf(WorkspaceHubPage.HOME) }
    // 关闭时继续绘制离开前的页面，使退出动画保留完整内容。
    val page = if (requestedPage == WorkspaceHubPage.CHAT) lastOpenPage else requestedPage
    SideEffect { if (requestedPage != WorkspaceHubPage.CHAT) lastOpenPage = requestedPage }
    val entryRequest by actualViewModel.workspaceEntryRequest.collectAsState()
    val context = LocalContext.current
    val terminalManager = remember(context) { TerminalManager.getInstance(context) }
    val terminalState by terminalManager.terminalState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val terminalPage by actualViewModel.embeddedTerminalPage.collectAsState()
    var creatingSession by remember { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var sessionToClose by remember { mutableStateOf<TerminalSessionData?>(null) }
    fun enterTerminal(createNewSession: Boolean = false, sessionId: String? = null) {
        if (creatingSession) return
        creatingSession = true
        keyboard?.hide()
        scope.launch {
            try {
                // 先检查实际安装环境；归档可用时由终端完成安装，缺失时直接引导下载。
                val installed = withContext(Dispatchers.IO) { OfflineResources.ubuntuInstalled(context) }
                val resourcesReady = installed || OfflineResources.isReady(context, OfflineResources.UBUNTU)
                // 资源校验期间已返回聊天时，不再打开资源页或终端。
                if (actualViewModel.workspaceHubPage.value == WorkspaceHubPage.CHAT) return@launch
                if (!resourcesReady) {
                    context.startActivity(
                        Intent(context, OfflineResourcesActivity::class.java)
                            .putExtra(OfflineResourcesActivity.EXTRA_GROUP, OfflineResources.UBUNTU)
                    )
                    return@launch
                }
                if (sessionId != null) terminalManager.switchToSession(sessionId)
                actualViewModel.openTerminalFromHub()
                if (createNewSession || terminalManager.terminalState.value.sessions.isEmpty()) {
                    terminalManager.createNewSession(context.getString(
                        R.string.workspace_terminal_session_title, terminalManager.terminalState.value.sessions.size + 1,
                    ))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("WorkspaceTerminal", "Opening terminal session failed", error)
                sessionError = error.message ?: context.getString(R.string.workspace_terminal_failed)
            } finally { creatingSession = false }
        }
    }
    // 切换对话重置访问记录，同一对话的界面重建则保留已打开页签。
    var hasOpenedWorkspace by rememberSaveable { mutableStateOf(false) }
    var hasOpenedTerminal by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentChat?.id, page) {
        if (page == WorkspaceHubPage.WORKSPACE) hasOpenedWorkspace = true
        if (page == WorkspaceHubPage.TERMINAL) hasOpenedTerminal = true
    }

    BackHandler(enabled = isVisible && requestedPage != WorkspaceHubPage.CHAT) {
        if (page == WorkspaceHubPage.TERMINAL && terminalPage != EmbeddedTerminalPage.CONSOLE) {
            actualViewModel.showEmbeddedTerminalPage(EmbeddedTerminalPage.CONSOLE)
        } else actualViewModel.onWorkspaceHubBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f))
            .padding(top = if (reserveChatControlsSpace) 52.dp else 0.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
    ) {
        WorkspaceSessionTabs(
            page = page,
            hasWorkspace = hasOpenedWorkspace,
            hasTerminal = hasOpenedTerminal || page == WorkspaceHubPage.TERMINAL,
            sessions = terminalState.sessions,
            currentSessionId = terminalState.currentSessionId,
            creatingSession = creatingSession,
            onHome = { keyboard?.hide(); actualViewModel.showWorkspaceHubHome() },
            onWorkspace = { keyboard?.hide(); actualViewModel.openWorkspaceFromHub() },
            onTerminal = { id -> enterTerminal(sessionId = id) },
            onCloseTerminal = { id -> sessionToClose = terminalManager.terminalState.value.sessions.firstOrNull { it.id == id } },
            onNewTerminal = { enterTerminal(createNewSession = true) },
            onTerminalPage = { keyboard?.hide(); actualViewModel.showEmbeddedTerminalPage(it) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (page == WorkspaceHubPage.HOME) {
                WorkspaceHubHome(
                    currentChat = currentChat,
                    chatHistories = chatHistories,
                    terminalSessionCount = terminalState.sessions.size,
                    onOpenWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.FILES) },
                    onCreateWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.CREATE) },
                    onSelectWorkspace = { actualViewModel.openWorkspaceFromHub(WorkspaceEntryAction.SELECT) },
                    onOpenTerminal = { enterTerminal() },
                    onOpenHtmlPackager = onOpenHtmlPackager,
                )
            }
            val workspaceVisible = isVisible && page == WorkspaceHubPage.WORKSPACE
            // 工作区隐藏时保留编辑状态；未绑定工作区的选择器仅在当前页面组合。
            Layout(
                modifier = if (workspaceVisible) Modifier.fillMaxSize() else {
                    Modifier.size(0.dp).clearAndSetSemantics {}
                },
                content = {
                    if ((hasOpenedWorkspace && currentChat?.workspace != null) || workspaceVisible) {
                        WorkspaceScreen(
                            actualViewModel = actualViewModel,
                            currentChat = currentChat,
                            isVisible = workspaceVisible,
                            entryRequest = entryRequest,
                            onExportClick = onExportClick,
                        )
                    }
                },
            ) { measurables, constraints ->
                if (!workspaceVisible || measurables.isEmpty()) {
                    layout(0, 0) {}
                } else {
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
            }
            // 离开终端即释放显示层，会话仍由终端管理器持有。
            if (isVisible && page == WorkspaceHubPage.TERMINAL) {
                ComputerScreen(page = terminalPage, onPageChange = actualViewModel::showEmbeddedTerminalPage)
            }
        }
    }
    sessionToClose?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToClose = null },
            title = { Text(stringResource(R.string.workspace_terminal_close_title)) },
            text = { Text(stringResource(R.string.workspace_terminal_close_message, session.title)) },
            confirmButton = { TextButton(onClick = {
                terminalManager.closeSession(session.id)
                sessionToClose = null
                if (terminalManager.terminalState.value.sessions.isEmpty()) { keyboard?.hide(); actualViewModel.showWorkspaceHubHome() }
            }) { Text(stringResource(R.string.close)) } },
            dismissButton = { TextButton(onClick = { sessionToClose = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    sessionError?.let { error ->
        AlertDialog(onDismissRequest = { sessionError = null },
            title = { Text(stringResource(R.string.workspace_terminal_failed)) },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { sessionError = null }) { Text(stringResource(android.R.string.ok)) } })
    }
}
