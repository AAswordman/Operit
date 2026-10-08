package com.ai.assistance.operit.ui.features.chat.webview.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

/**
 * 主工作区屏幕组件
 * 根据聊天状态显示不同的工作区界面
 */
@Composable
fun WorkspaceScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    isVisible: Boolean,
    onExportClick: (workDir: File) -> Unit
) {
    var temporaryFile by remember(currentChat?.id, currentChat?.workspace) { mutableStateOf<OpenFileInfo?>(null) }
    val setupState = rememberSaveableStateHolder()
    val file = temporaryFile
    val boundWorkspace = currentChat?.workspace
    if (currentChat != null && boundWorkspace != null) {
        WorkspaceManager(
            actualViewModel = actualViewModel,
            currentChat = currentChat,
            workspacePath = boundWorkspace,
            workspaceEnv = currentChat.workspaceEnv,
            isVisible = isVisible,
            onExportClick = onExportClick
        )
    } else if (currentChat != null && file != null) {
        key(file.key) {
            WorkspaceManager(
                actualViewModel = actualViewModel,
                currentChat = currentChat,
                workspacePath = File(file.path).parent.orEmpty(),
                workspaceEnv = file.environment.takeUnless { it == "android" },
                isVisible = isVisible,
                onExportClick = onExportClick,
                initialFile = file,
                // 保留临时浏览器实例，关闭文件后回到打开文件所在目录。
                onReturnToBrowser = {}
            )
        }
    } else if (currentChat != null) {
        key(currentChat.id) {
            setupState.SaveableStateProvider("setup") {
                WorkspaceSetup(
                    chatId = currentChat.id,
                    onBindWorkspace = { workspacePath, workspaceEnv ->
                        actualViewModel.bindChatToWorkspace(currentChat.id, workspacePath, workspaceEnv)
                    },
                    onFileOpen = { temporaryFile = it }
                )
            }
        }
    } else {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = context.getString(R.string.please_select_or_create_conversation), style = MaterialTheme.typography.headlineMedium)
        }
    }
}