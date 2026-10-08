package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.workspace.FileBrowser
import com.ai.assistance.operit.ui.features.chat.webview.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.browser.FileBrowserOperations
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceMimeTypeForPath
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceShouldOpenAsDirectPreview
import com.ai.assistance.operit.ui.features.chat.webview.workspace.text.WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES
import com.ai.assistance.operit.ui.features.chat.webview.workspace.text.readWorkspaceTextWithinLimit
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 打开请求属于当前聊天页面，避免全局事件同时打开多个文件窗口。 */
internal val LocalWorkspaceFileLinkOpener = staticCompositionLocalOf<(WorkspaceFileLink) -> Unit> {
    { error("文件链接需要聊天页面的工作区查看入口") }
}

@Composable
internal fun WorkspaceFileLinkHost(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var target by remember(currentChat?.id) { mutableStateOf<WorkspaceFileLink?>(null) }
    CompositionLocalProvider(LocalWorkspaceFileLinkOpener provides { target = it }) {
        Box(modifier = modifier, content = content)
        val link = target
        if (link != null && currentChat != null) {
            key(link) {
                WorkspaceFileLinkDialog(actualViewModel, currentChat, link, onDismiss = { target = null })
            }
        }
    }
}

/** 共用工作区编辑与预览；二进制文件显示信息并提供实际文件的分享入口。 */
@Composable
internal fun WorkspaceFileLinkDialog(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory,
    target: WorkspaceFileLink,
    onDismiss: () -> Unit,
    onFileOpen: ((OpenFileInfo) -> Unit)? = null,
) {
    val context = LocalContext.current
    val handler = remember(context) { AIToolHandler.getInstance(context) }
    val operations = remember(context) { FileBrowserOperations(context) }
    val scope = rememberCoroutineScope()
    val browserState = rememberSaveableStateHolder()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<OpenFileInfo?>(null) }
    var directory by remember { mutableStateOf<String?>(null) }
    var fileInfo by remember { mutableStateOf<FileExistsData?>(null) }

    LaunchedEffect(target) {
        try {
            val (info, openedFile) = withContext(Dispatchers.IO) {
                val parameters = listOf(
                    ToolParameter("path", target.path),
                    ToolParameter("environment", target.environment),
                )
                val exists = handler.executeTool(AITool("file_exists", parameters))
                check(exists.success) { exists.error.orEmpty() }
                val info = exists.result
                check(info is FileExistsData && info.exists) {
                    context.getString(R.string.cannot_open_file, target.path)
                }
                val openedFile = when {
                    info.isDirectory -> null
                    workspaceShouldOpenAsDirectPreview(target.path) ->
                        OpenFileInfo(target.path, "", System.currentTimeMillis(),
                            mimeType = workspaceMimeTypeForPath(target.path), environment = target.environment)
                    FileUtils.isTextBasedFileName(File(target.path).name) -> readWorkspaceTextWithinLimit(info.size) {
                        val read = handler.executeTool(AITool("read_file_full", parameters + ToolParameter("text_only", "true")))
                        check(read.success) { read.error.orEmpty() }
                        val result = read.result
                        check(result is FileContentData) { context.getString(R.string.file_error_open_failed) }
                        val initialLine = if (target.anchor == null) target.line else {
                            checkNotNull(findWorkspaceMarkdownAnchorLine(result.content, target.anchor)) {
                                context.getString(R.string.workspace_markdown_anchor_not_found, target.anchor)
                            }
                        }
                        OpenFileInfo(target.path, result.content, System.currentTimeMillis(),
                            mimeType = workspaceMimeTypeForPath(target.path), environment = target.environment,
                            initialLine = initialLine)
                    }
                    else -> null
                }
                info to openedFile
            }
            fileInfo = info
            if (info.isDirectory) directory = target.path
            if (openedFile != null && onFileOpen != null) {
                onFileOpen(openedFile)
                onDismiss()
            } else {
                file = openedFile
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("WorkspaceFileLink", "打开文件链接失败", e)
            error = e.message ?: context.getString(R.string.file_error_open_failed)
        } finally {
            loading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // 编辑器通过关闭标签处理未保存内容，系统返回不能直接销毁文件窗口。
        properties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            val openedFile = file
            val openedDirectory = directory
            when {
                openedFile != null -> WorkspaceManager(
                    actualViewModel = actualViewModel,
                    currentChat = currentChat,
                    workspacePath = openedDirectory ?: File(openedFile.path).parent.orEmpty(),
                    workspaceEnv = openedFile.environment,
                    isVisible = true,
                    onExportClick = {},
                    initialFile = openedFile,
                    onReturnToBrowser = { if (openedDirectory != null) file = null else onDismiss() },
                )
                openedDirectory != null -> browserState.SaveableStateProvider("directory") {
                    FileBrowser(
                        initialPath = openedDirectory,
                        environment = target.environment,
                        onCancel = onDismiss,
                        onFileOpen = { opened ->
                            if (onFileOpen == null) file = opened else {
                                onFileOpen(opened)
                                onDismiss()
                            }
                        },
                    )
                    BackHandler { onDismiss() }
                }
                else -> {
                    BackHandler { onDismiss() }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.file_manager_return))
                            }
                            Text(File(target.path).name, style = MaterialTheme.typography.titleMedium)
                        }
                        if (loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(target.path)
                                val info = fileInfo
                                if (info != null) {
                                    Text("${stringResource(R.string.ffmpeg_file_size)}: ${info.size}")
                                    if (FileUtils.isTextBasedFileName(File(target.path).name) &&
                                        info.size > WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES) {
                                        Text(stringResource(R.string.workspace_text_preview_too_large,
                                            (WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES / (1024 * 1024)).toInt()))
                                    }
                                    fun openExternal(share: Boolean) {
                                        loading = true
                                        scope.launch {
                                            try {
                                                operations.externalOpen(target.path, target.environment, share)
                                                error = null
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                AppLogger.w("WorkspaceFileLink", "打开或分享文件失败", e)
                                                error = e.message ?: context.getString(R.string.file_error_open_failed)
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                    OutlinedButton(onClick = { openExternal(false) }) {
                                        Text(stringResource(R.string.workspace_open_with))
                                    }
                                    OutlinedButton(onClick = { openExternal(true) }) {
                                        Text(stringResource(R.string.file_menu_share))
                                    }
                                    TextButton(onClick = { directory = File(target.path).parent ?: "/" }) {
                                        Text(stringResource(R.string.files))
                                    }
                                }
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}