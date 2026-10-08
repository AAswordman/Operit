package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import com.ai.assistance.operit.util.AppLogger
import android.view.MotionEvent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.chat.components.rememberCompactDialogMetrics
import com.ai.assistance.operit.ui.features.chat.components.attachments.AudioAttachmentPlayer
import com.ai.assistance.operit.ui.features.chat.components.attachments.VideoAttachmentPlayer
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeFormatter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.EditorKeyCommand
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.LanguageDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.ai.assistance.operit.ui.features.chat.webview.workspace.search.*
import com.ai.assistance.operit.ui.features.chat.webview.workspace.links.WorkspaceFileLink
import com.ai.assistance.operit.ui.features.chat.webview.workspace.links.WorkspaceFileLinkDialog
import com.ai.assistance.operit.ui.features.chat.webview.workspace.links.findWorkspaceMarkdownAnchorLine
import com.ai.assistance.operit.ui.features.chat.webview.workspace.markdown.WorkspaceMarkdownPreview

private fun WebView.installWorkspaceTouchInterceptor() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        false
    }
}

private fun WebView.releaseWorkspaceWebView() {
    stopLoading()
    removeAllViews()
    destroy()
}

private fun isLocalPreviewUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return runCatching {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase()
        (uri.scheme == "http" || uri.scheme == "https") &&
            (host == "localhost" || host == "127.0.0.1")
    }.getOrDefault(false)
}

private fun previewWebViewOptions(url: String): WebViewHandler.WebViewOptions {
    if (!isLocalPreviewUrl(url)) {
        return WebViewHandler.WebViewOptions()
    }
    return WebViewHandler.WebViewOptions(
        preferDesktopSite = false,
        enableWorkspaceCorsProxy = false,
        supportZoom = false,
        useWideViewPort = false,
        loadWithOverviewMode = false
    )
}

/** VSCode风格的工作区管理器组件 集成了WebView预览和文件管理功能 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceManager(
        actualViewModel: ChatViewModel,
        currentChat: ChatHistory,
        workspacePath: String,
        workspaceEnv: String? = null,
        isVisible: Boolean,
        onExportClick: (workDir: File) -> Unit,
        initialFile: OpenFileInfo? = null,
        onReturnToBrowser: (() -> Unit)? = null
) {
    val standalone = onReturnToBrowser != null
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()
    val workspaceCommandExecutionState by actualViewModel.workspaceCommandExecutionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val workspaceServer = remember(context) {
        LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
    }
    
    val isSafEnv = remember(workspaceEnv) { workspaceEnv?.startsWith("repo:", ignoreCase = true) == true }

    // 读取工作区配置：在重新进入预览界面时从磁盘刷新
    var workspaceConfig by remember(workspacePath, workspaceEnv) {
        mutableStateOf(if (isSafEnv || standalone) WorkspaceConfig() else WorkspaceConfigReader.readConfig(workspacePath))
    }

    LaunchedEffect(isVisible, workspacePath, workspaceEnv) {
        if (isVisible && !isSafEnv && !standalone) {
            workspaceConfig = WorkspaceConfigReader.readConfig(workspacePath)
        }
    }

    LaunchedEffect(isVisible, workspacePath, workspaceEnv, workspaceServer, workspaceConfig.server.enabled) {
        if (!isVisible || standalone) return@LaunchedEffect

        runCatching {
            withContext(Dispatchers.IO) {
                if (workspaceConfig.server.enabled) {
                    if (!workspaceServer.isRunning()) {
                        workspaceServer.start()
                    }
                    workspaceServer.updateChatWorkspace(workspacePath, workspaceEnv)
                } else if (workspaceServer.isRunning()) {
                    workspaceServer.stop()
                }
            }
        }.onFailure { error ->
            AppLogger.e("WorkspaceManager", "Failed to prepare workspace preview server", error)
        }
    }

    fun withWorkspaceEnvParams(base: List<ToolParameter>): List<ToolParameter> {
        if (workspaceEnv.isNullOrBlank()) return base
        return base + ToolParameter("environment", workspaceEnv)
    }

    // 将 webViewHandler 和 webView 实例提升到 remember 中，使其在重组中保持稳定
    val webViewHandler =
            remember(context) {
                WebViewHandler(context).apply {
                    onFileChooserRequest = { intent, callback ->
                        actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                            callback(resultCode, data)
                        }
                    }
                }
            }
    var workspaceWebView by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedWorkspacePreviewUrl by remember(workspacePath, workspaceEnv) {
        mutableStateOf<String?>(null)
    }
    val workspacePreviewUrl = workspaceConfig.preview.url.ifEmpty { "http://localhost:8093" }
    val workspacePreviewOptions = remember(workspacePreviewUrl) { previewWebViewOptions(workspacePreviewUrl) }

    var canWebViewGoBack by remember { mutableStateOf(false) }
    var canWebViewGoForward by remember { mutableStateOf(false) }
    var showCommandBrowserPreview by remember(workspacePath, workspaceEnv, workspaceConfig.preview.url) {
        mutableStateOf(false)
    }
    var commandPreviewWebView by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedCommandPreviewUrl by remember(workspacePath, workspaceEnv, workspaceConfig.preview.url) {
        mutableStateOf<String?>(null)
    }
    var lastHandledWebViewRefreshCounter by remember(workspacePath, workspaceEnv) {
        mutableIntStateOf(webViewRefreshCounter)
    }
    var canCommandPreviewGoBack by remember { mutableStateOf(false) }
    var canCommandPreviewGoForward by remember { mutableStateOf(false) }
    val commandPreviewUrl = workspaceConfig.preview.url
    val commandPreviewOptions = remember(commandPreviewUrl) { previewWebViewOptions(commandPreviewUrl) }

    LaunchedEffect(webViewHandler) {
        webViewHandler.onCanGoBackChanged = { canGoBack ->
            canWebViewGoBack = canGoBack
        }
        webViewHandler.onCanGoForwardChanged = { canGoForward ->
            canWebViewGoForward = canGoForward
        }
    }

    val commandPreviewHandler =
        remember(context) {
            WebViewHandler(context).apply {
                onFileChooserRequest = { intent, callback ->
                    actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                        callback(resultCode, data)
                    }
                }
            }
        }

    LaunchedEffect(commandPreviewHandler) {
        commandPreviewHandler.onCanGoBackChanged = { canGoBack ->
            canCommandPreviewGoBack = canGoBack
        }
        commandPreviewHandler.onCanGoForwardChanged = { canGoForward ->
            canCommandPreviewGoForward = canGoForward
        }
    }

    // 文件管理和标签状态 - 使用内存态，避免编辑大文件时频繁持久化整份内容
    var showWorkspaceMenu by remember { mutableStateOf(false) }
    var openFiles by remember(workspacePath, workspaceEnv) { mutableStateOf(initialFile?.let { listOf(it) } ?: emptyList()) }
    var currentFileIndex by remember(workspacePath, workspaceEnv) { mutableStateOf(if (initialFile == null) -2 else 0) }
    var filePreviewStates by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var fileLineNavigationRequests by remember(workspacePath, workspaceEnv) { mutableStateOf(mapOf<String, Int>()) }
    var markdownLinkTarget by remember(workspacePath, workspaceEnv) { mutableStateOf<WorkspaceFileLink?>(null) }
    var markdownNavigationJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(workspacePath, workspaceEnv) { markdownNavigationJob?.cancel() }
    var unsavedFiles by remember(workspacePath, workspaceEnv) { mutableStateOf(emptySet<String>()) }
    val isBrowserPreviewVisible =
        isVisible && currentFileIndex == -1 && workspaceConfig.preview.type == "browser"
    val isCommandPreviewVisible =
        isVisible &&
            currentFileIndex == -1 &&
            workspaceConfig.preview.type != "browser" &&
            showCommandBrowserPreview &&
            commandPreviewUrl.isNotEmpty()
    val activePreviewWebView =
        when {
            isBrowserPreviewVisible -> workspaceWebView
            isCommandPreviewVisible -> commandPreviewWebView
            else -> null
        }
    val activePreviewCanGoBack =
        when {
            isBrowserPreviewVisible -> canWebViewGoBack
            isCommandPreviewVisible -> canCommandPreviewGoBack
            else -> false
        }
    val activePreviewCanGoForward =
        when {
            isBrowserPreviewVisible -> canWebViewGoForward
            isCommandPreviewVisible -> canCommandPreviewGoForward
            else -> false
        }
    
    val browserStateHolder = rememberSaveableStateHolder()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf(emptyList<IntRange>()) }
    var activeMatch by remember { mutableIntStateOf(-1) }
    var searchNavigationRequest by remember { mutableIntStateOf(0) }
    var searchContext by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var showRenameWorkspaceDialog by remember { mutableStateOf(false) }
    var renameWorkspaceInput by remember(workspacePath) { mutableStateOf(File(workspacePath).name) }
    var renameWorkspaceError by remember { mutableStateOf<String?>(null) }
    var isRenamingWorkspace by remember { mutableStateOf(false) }
    val canRenameWorkspace by remember(workspacePath, workspaceEnv) {
        mutableStateOf(
            !isSafEnv &&
                runCatching {
                    val workspaceRoot = File(context.filesDir, "workspace").canonicalFile
                    File(workspacePath).canonicalFile.parentFile?.canonicalFile == workspaceRoot
                }.getOrDefault(false)
        )
    }
    
    // 解绑确认对话框状态
    var showUnbindConfirmDialog by remember { mutableStateOf(false) }
    
    // 关闭文件确认对话框状态
    var fileToCloseIndex by remember { mutableStateOf(-1) }
    
    // 当前活动的编辑器引用
    var activeEditor by remember { mutableStateOf<com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.NativeCodeEditor?>(null) }
    val searchableFile = openFiles.getOrNull(currentFileIndex)?.takeUnless { it.isReadOnlyPreview }
    LaunchedEffect(searchableFile?.key, searchableFile?.content, searchQuery, searchVisible) {
        val nextContext = searchableFile?.key to searchQuery
        val matches = withContext(Dispatchers.Default) {
            if (searchVisible) findTextMatches(searchableFile?.content.orEmpty(), searchQuery) else emptyList()
        }
        // 同一文件和查询的编辑只更新高亮，不重置用户选中的匹配序号。
        activeMatch = activeSearchMatchAfterUpdate(if (searchContext == nextContext) activeMatch else 0, matches.size)
        searchMatches = matches
        searchContext = nextContext
    }

    // 监听WebView刷新计数器变化并触发刷新
    LaunchedEffect(
        webViewRefreshCounter,
        isBrowserPreviewVisible,
        isCommandPreviewVisible,
        workspaceWebView,
        commandPreviewWebView
    ) {
        if (webViewRefreshCounter <= lastHandledWebViewRefreshCounter) {
            return@LaunchedEffect
        }

        lastHandledWebViewRefreshCounter = webViewRefreshCounter
        AppLogger.d("WorkspaceManager", "WebView refresh triggered, counter: $webViewRefreshCounter")

        // 仅处理新的刷新事件，避免重新进入页面时把旧计数误判成一次刷新。
        kotlinx.coroutines.delay(100)
        when {
            isBrowserPreviewVisible -> workspaceWebView?.reload()
            isCommandPreviewVisible -> commandPreviewWebView?.reload()
        }
    }

    // 当工作区可见时，检查文件更新
    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (isSafEnv) {
                return@LaunchedEffect
            }
            val updatedFiles = openFiles.map { fileInfo ->
                if (fileInfo.environment != "android" || fileInfo.key in unsavedFiles) return@map fileInfo
                val currentFile = File(fileInfo.path)
                if (currentFile.exists() && currentFile.lastModified() > fileInfo.lastModified) {
                    if (fileInfo.isReadOnlyPreview) {
                        return@map fileInfo.copy(lastModified = currentFile.lastModified())
                    }

                    // 文件已在外部被修改，重新加载内容
                    val tool = AITool(
                        "read_file_full",
                        listOf(ToolParameter("path", fileInfo.path), ToolParameter("environment", fileInfo.environment), ToolParameter("text_only", "true"))
                    )
                    val result = toolHandler.executeTool(tool)
                    if (result.success && result.result is FileContentData) {
                        val newContent = (result.result as FileContentData).content
                        
                        // 如果当前文件就是这个被修改的文件，则更新编辑器内容
                        if (openFiles.getOrNull(currentFileIndex)?.key == fileInfo.key) {
                             activeEditor?.replaceAllText(newContent)
                        }
                        
                        // 返回更新后的文件信息
                        fileInfo.copy(
                            content = newContent,
                            lastModified = currentFile.lastModified()
                        )
                    } else {
                        fileInfo // 加载失败，保留旧信息
                    }
                } else {
                    fileInfo // 文件未更改
                }
            }
            openFiles = updatedFiles
        }
    }

    LaunchedEffect(
        isVisible,
        isSafEnv,
        workspacePath,
        workspaceEnv,
        workspaceConfig.preview.type,
        currentFileIndex,
        workspaceWebView
    ) {
        if (!isVisible || isSafEnv) {
            return@LaunchedEffect
        }

        WorkspacePreviewRefreshBus.events.collect { event ->
            val isSameWorkspace = event.workspacePath == workspacePath
            val isSameEnvironment =
                event.workspaceEnv?.trim().orEmpty().equals(
                    workspaceEnv?.trim().orEmpty(),
                    ignoreCase = true
                )
            val shouldRefreshBrowserPreview =
                currentFileIndex == -1 && workspaceConfig.preview.type == "browser"

            if (!isSameWorkspace || !isSameEnvironment || !shouldRefreshBrowserPreview) {
                return@collect
            }

            AppLogger.d(
                "WorkspaceManager",
                "Workspace preview refresh requested by ${event.source}: ${event.affectedPaths.joinToString()}"
            )
            workspaceWebView?.reload()
        }
    }
    
    // 保存文件函数
    fun saveFile(fileInfo: OpenFileInfo, afterSave: (() -> Unit)? = null) {
        if (fileInfo.isReadOnlyPreview || saving) return
        saving = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    toolHandler.executeTool(AITool("write_file", listOf(
                        ToolParameter("path", fileInfo.path), ToolParameter("content", fileInfo.content),
                        ToolParameter("environment", fileInfo.environment)
                    )))
                }
                check(result.success) { result.error.orEmpty() }
                // 保存期间继续输入时，较新的内容仍保持未保存状态。
                if (openFiles.firstOrNull { it.key == fileInfo.key }?.content == fileInfo.content) {
                    unsavedFiles = unsavedFiles - fileInfo.key
                    afterSave?.invoke()
                }
                saveError = null
                if (fileInfo.isHtml && filePreviewStates[fileInfo.key] == true) actualViewModel.refreshWebView()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceManager", "保存文件失败", e)
                saveError = e.message
            } finally { saving = false }
        }
    }

    // 实际执行关闭文件操作
    fun confirmCloseFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val fileToClose = openFiles[index]
            val updatedFiles = openFiles.toMutableList()
            updatedFiles.removeAt(index)
            openFiles = updatedFiles

            // 从未保存集合中移除
            unsavedFiles = unsavedFiles - fileToClose.key
            
            // 更新当前选中的标签
            currentFileIndex = when {
                updatedFiles.isEmpty() -> -2
                index >= updatedFiles.size -> updatedFiles.size - 1
                else -> index
            }
        }
        fileToCloseIndex = -1
        if (openFiles.isEmpty()) onReturnToBrowser?.invoke()
    }

    // 关闭文件标签
    fun closeFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val fileToClose = openFiles[index]
            // 如果文件有未保存的更改，显示确认对话框
            if (unsavedFiles.contains(fileToClose.key)) {
                fileToCloseIndex = index
            } else {
                // 否则直接关闭
                confirmCloseFile(index)
            }
        }
    }

    // 键盘命令统一复用现有文件状态和未保存确认流程。
    fun handleEditorKeyCommand(command: EditorKeyCommand): Boolean {
        return when (command) {
            EditorKeyCommand.SAVE -> {
                val file = openFiles.getOrNull(currentFileIndex)
                if (file == null || file.isReadOnlyPreview) false else {
                    saveFile(file)
                    true
                }
            }
            EditorKeyCommand.SAVE_AS -> false
            EditorKeyCommand.CLOSE -> {
                if (currentFileIndex !in openFiles.indices) false else {
                    closeFile(currentFileIndex)
                    true
                }
            }
            EditorKeyCommand.FIND -> {
                if (searchableFile == null) false else {
                    searchVisible = true
                    true
                }
            }
            EditorKeyCommand.ESCAPE -> when {
                fileToCloseIndex != -1 -> {
                    fileToCloseIndex = -1
                    true
                }
                searchVisible -> {
                    searchVisible = false
                    searchQuery = ""
                    true
                }
                currentFileIndex in openFiles.indices -> {
                    closeFile(currentFileIndex)
                    true
                }
                else -> false
            }
        }
    }

    // 切换文件预览状态
    fun togglePreview(path: String) {
        filePreviewStates =
                filePreviewStates.toMutableMap().apply { this[path] = !(this[path] ?: false) }
    }

    // 打开文件
    fun openFile(fileInfo: OpenFileInfo) {
        // 检查文件是否已经打开
        val existingIndex = openFiles.indexOfFirst { it.key == fileInfo.key }

        if (existingIndex != -1) {
            // 已打开的文件保留内存内容，仅更新显式定位和活动标签。
            if (fileInfo.initialLine != null) {
                openFiles = openFiles.toMutableList().apply {
                    this[existingIndex] = this[existingIndex].copy(initialLine = fileInfo.initialLine)
                }
                fileLineNavigationRequests = fileLineNavigationRequests +
                    (fileInfo.key to ((fileLineNavigationRequests[fileInfo.key] ?: 0) + 1))
                filePreviewStates = filePreviewStates + (fileInfo.key to false)
            }
            currentFileIndex = existingIndex
        } else {
            // 否则添加到打开的文件列表
            openFiles = openFiles + fileInfo
            currentFileIndex = openFiles.size - 1

            // 初始化预览状态
            filePreviewStates =
                    filePreviewStates.toMutableMap().apply {
                        // 文本文件默认进入现有编辑器，预览由用户主动切换。
                        this[fileInfo.key] = false
                    }
        }
    }

    fun openMarkdownFileLink(target: WorkspaceFileLink) {
        markdownNavigationJob?.cancel()
        val existing = openFiles.firstOrNull { it.environment == target.environment && it.path == target.path }
        if (existing == null) {
            markdownLinkTarget = target
            return
        }
        // 标题扫描在后台进行；定位依据已打开文件的实际内容，包括未保存修改。
        markdownNavigationJob = coroutineScope.launch {
            try {
                val line = withContext(Dispatchers.Default) {
                    if (target.anchor == null) target.line else {
                        checkNotNull(findWorkspaceMarkdownAnchorLine(existing.content, target.anchor)) {
                            context.getString(R.string.workspace_markdown_anchor_not_found, target.anchor)
                        }
                    }
                }
                if (openFiles.any { it.key == existing.key }) openFile(existing.copy(initialLine = line))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("WorkspaceMarkdown", "定位文档链接失败", error)
                Toast.makeText(context, error.message ?: context.getString(R.string.file_error_open_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val markdownTarget = markdownLinkTarget
    if (markdownTarget != null) {
        key(markdownTarget) {
            WorkspaceFileLinkDialog(
                actualViewModel = actualViewModel,
                currentChat = currentChat,
                target = markdownTarget,
                onDismiss = { markdownLinkTarget = null },
                onFileOpen = ::openFile,
            )
        }
    }

    // 不透明底色与主题前景色一起提供，避免编辑按钮沿用外层聊天颜色。
    Surface(
        modifier = Modifier.fillMaxSize().imePadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BackHandler(enabled = currentFileIndex != -2 && !activePreviewCanGoBack) {
            if (standalone && currentFileIndex in openFiles.indices) closeFile(currentFileIndex)
            else currentFileIndex = -2
        }
        BackHandler(enabled = activePreviewCanGoBack) {
            if (activePreviewCanGoBack) {
                try {
                    activePreviewWebView?.goBack()
                } catch (e: Exception) {
                    AppLogger.e("WorkspaceManager", "Failed to navigate WebView back", e)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            val currentTextFile = openFiles.getOrNull(currentFileIndex)?.takeUnless { it.isReadOnlyPreview }
            if (currentTextFile != null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { activeEditor?.undo() }) { Icon(Icons.Default.Undo, stringResource(R.string.undo)) }
                    IconButton(onClick = { activeEditor?.redo() }) { Icon(Icons.Default.Redo, stringResource(R.string.redo)) }
                    IconButton(onClick = { searchVisible = !searchVisible }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                    val language = LanguageDetector.detectLanguage(currentTextFile.name).lowercase()
                    if (language in listOf("javascript", "js", "css", "html", "htm")) {
                        IconButton(onClick = {
                            val formatted = CodeFormatter.format(currentTextFile.content, language)
                            val updated = openFiles.toMutableList()
                            updated[currentFileIndex] = currentTextFile.copy(content = formatted)
                            openFiles = updated
                            activeEditor?.replaceAllText(formatted)
                            unsavedFiles = unsavedFiles + currentTextFile.key
                        }) { Icon(Icons.Default.AutoFixHigh, stringResource(R.string.format_code)) }
                    }
                }
            }
            // 整合后的顶部栏：标签 + 动态操作
            Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shadowElevation = 2.dp,
                    modifier = Modifier.zIndex(1f) // 强制将标签栏置于顶层，防止被WebView覆盖
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (standalone) {
                        IconButton(onClick = { if (currentFileIndex in openFiles.indices) closeFile(currentFileIndex) else onReturnToBrowser?.invoke() }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.file_manager_return))
                        }
                    }
                    if (!standalone) Box {
                        IconButton(onClick = { showWorkspaceMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(
                            expanded = showWorkspaceMenu,
                            onDismissRequest = { showWorkspaceMenu = false }
                        ) {
                            if (workspaceConfig.export.enabled && !isSafEnv) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export)) },
                                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                    onClick = {
                                        showWorkspaceMenu = false
                                        onExportClick(File(workspacePath))
                                    }
                                )
                            }
                            if (canRenameWorkspace) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.workspace_rename_action)) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = {
                                        showWorkspaceMenu = false
                                        if (unsavedFiles.isNotEmpty()) {
                                            actualViewModel.showToast(context.getString(R.string.workspace_rename_save_first))
                                        } else {
                                            renameWorkspaceInput = File(workspacePath).name
                                            renameWorkspaceError = null
                                            showRenameWorkspaceDialog = true
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.unbind)) },
                                leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null) },
                                onClick = {
                                    showWorkspaceMenu = false
                                    showUnbindConfirmDialog = true
                                }
                            )
                        }
                    }
                    // 文件标签栏
                    Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        VSCodeTab(
                            title = stringResource(R.string.files),
                            icon = Icons.Default.Folder,
                            isActive = currentFileIndex == -2,
                            isUnsaved = false,
                            onClose = null,
                            onClick = { currentFileIndex = -2 }
                        )
                        // 临时打开文件不初始化项目预览和绑定状态。
                        if (!standalone) VSCodeTab(
                                title = stringResource(R.string.workspace_preview),
                                icon = Icons.Default.Visibility,
                                isActive = currentFileIndex == -1,
                                isUnsaved = false,
                                onClose = null,
                                onClick = { currentFileIndex = -1 }
                        )

                        // 打开的文件标签
                        openFiles.forEachIndexed { index, fileInfo ->
                            VSCodeTab(
                                    title = fileInfo.name,
                                    icon = getFileIcon(fileInfo.name), // 使用统一的 getFileIcon
                                    isActive = currentFileIndex == index,
                                    isUnsaved = unsavedFiles.contains(fileInfo.key),
                                    onClose = { closeFile(index) },
                                    onClick = { currentFileIndex = index }
                            )
                        }
                    }

                    // 动态操作区域
                    val currentFile = openFiles.getOrNull(currentFileIndex)

                    // 保存按钮
                    if (currentFile != null && unsavedFiles.contains(currentFile.key)) {
                        IconButton(
                            onClick = {
                                saveFile(currentFile)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save File"
                            )
                        }
                    }

                    if (currentFile != null && (currentFile.isHtml || currentFile.isMarkdown)) {
                        val isPreview = filePreviewStates[currentFile.key] ?: false
                        IconButton(
                                onClick = { togglePreview(currentFile.key) },
                                // 限制按钮大小，使其与标签高度(40.dp)保持一致，防止撑开父布局
                                modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                    if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                    contentDescription = "Toggle Preview"
                            )
                        }
                    } else if (isBrowserPreviewVisible || isCommandPreviewVisible) {
                        IconButton(
                            onClick = { activePreviewWebView?.goBack() },
                            enabled = activePreviewCanGoBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.web_session_back),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { activePreviewWebView?.goForward() },
                            enabled = activePreviewCanGoForward,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = stringResource(R.string.web_session_forward),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { activePreviewWebView?.reload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.web_session_refresh),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isCommandPreviewVisible) {
                            IconButton(
                                onClick = { showCommandBrowserPreview = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.workspace_close_preview),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (searchVisible && searchableFile != null) {
                FileSearchBar(searchQuery, { searchQuery = it }, searchMatches.size, activeMatch,
                    onPrevious = {
                        if (searchMatches.isNotEmpty()) {
                            activeMatch = (activeMatch - 1 + searchMatches.size) % searchMatches.size
                            searchNavigationRequest++
                        }
                    },
                    onNext = {
                        if (searchMatches.isNotEmpty()) {
                            activeMatch = (activeMatch + 1) % searchMatches.size
                            searchNavigationRequest++
                        }
                    },
                    onClose = { searchVisible = false })
            }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            // 主内容区域
            Box(
                    modifier =
                            Modifier.weight(1f)
                                    .background(MaterialTheme.colorScheme.surface) // 添加背景色防止闪烁
            ) {
                when {
                    // 文件浏览是绑定工作区后的默认页面，项目预览通过顶部“预览”标签进入。
                    currentFileIndex == -2 -> browserStateHolder.SaveableStateProvider("files") {
                        FileBrowser(
                            initialPath = workspacePath,
                            environment = workspaceEnv,
                            onCancel = {},
                            showHeader = false,
                            onFileOpen = { fileInfo -> openFile(fileInfo) }
                        )
                    }
                    // 显示WebView预览（仅当preview类型为browser时）
                    currentFileIndex == -1 && workspaceConfig.preview.type == "browser" -> {
                        key(workspacePath, workspaceEnv) {
                            AndroidView(
                                    factory = { androidContext ->
                                        WebView(androidContext).apply {
                                            installWorkspaceTouchInterceptor()
                                            webViewHandler.configureWebView(
                                                this,
                                                WebViewHandler.WebViewMode.WORKSPACE,
                                                "workspace_preview_webview",
                                                workspacePreviewOptions
                                            )
                                            loadUrl(workspacePreviewUrl)
                                            workspaceWebView = this
                                            webViewHandler.currentWebView = this
                                            lastLoadedWorkspacePreviewUrl = workspacePreviewUrl
                                        }
                                    },
                                    update = { view ->
                                        workspaceWebView = view
                                        webViewHandler.currentWebView = view
                                        if (lastLoadedWorkspacePreviewUrl != workspacePreviewUrl) {
                                            view.loadUrl(workspacePreviewUrl)
                                            lastLoadedWorkspacePreviewUrl = workspacePreviewUrl
                                        }
                                    },
                                    onRelease = { view ->
                                        if (workspaceWebView === view) {
                                            workspaceWebView = null
                                        }
                                        if (webViewHandler.currentWebView === view) {
                                            webViewHandler.currentWebView = null
                                        }
                                        canWebViewGoBack = false
                                        canWebViewGoForward = false
                                        lastLoadedWorkspacePreviewUrl = null
                                        view.releaseWorkspaceWebView()
                                    },
                                    modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 显示命令按钮界面（当preview类型不是browser时）
                    currentFileIndex == -1 && workspaceConfig.preview.type != "browser" -> {
                        if (isCommandPreviewVisible) {
                            key(workspacePath, workspaceEnv, commandPreviewUrl) {
                                AndroidView(
                                    factory = { androidContext ->
                                        WebView(androidContext).apply {
                                            installWorkspaceTouchInterceptor()
                                            commandPreviewHandler.configureWebView(
                                                this,
                                                WebViewHandler.WebViewMode.WORKSPACE,
                                                "workspace_preview_${workspacePath.hashCode()}",
                                                commandPreviewOptions
                                            )
                                            loadUrl(commandPreviewUrl)
                                            commandPreviewWebView = this
                                            commandPreviewHandler.currentWebView = this
                                            lastLoadedCommandPreviewUrl = commandPreviewUrl
                                        }
                                    },
                                    update = { webView ->
                                        commandPreviewWebView = webView
                                        commandPreviewHandler.currentWebView = webView
                                        if (lastLoadedCommandPreviewUrl != commandPreviewUrl) {
                                            webView.loadUrl(commandPreviewUrl)
                                            lastLoadedCommandPreviewUrl = commandPreviewUrl
                                        }
                                        webView.requestFocus()
                                    },
                                    onRelease = { webView ->
                                        if (commandPreviewWebView === webView) {
                                            commandPreviewWebView = null
                                        }
                                        if (commandPreviewHandler.currentWebView === webView) {
                                            commandPreviewHandler.currentWebView = null
                                        }
                                        canCommandPreviewGoBack = false
                                        canCommandPreviewGoForward = false
                                        lastLoadedCommandPreviewUrl = null
                                        webView.releaseWorkspaceWebView()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            CommandButtonsView(
                                config = workspaceConfig,
                                workspacePath = workspacePath,
                                onCommandExecute = { command ->
                                    // 在专属会话中执行命令
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        actualViewModel.executeCommandInWorkspace(command, workspacePath)
                                    } else {
                                        // 对于旧版本Android，显示不支持提示
                                        AppLogger.w("WorkspaceManager", "Terminal features require Android 8.0+")
                                    }
                                },
                                onOpenBrowserPreview = {
                                    showCommandBrowserPreview = true
                                }
                            )
                        }
                    }
                    // 显示打开的文件
                    currentFileIndex in openFiles.indices -> {
                        val fileInfo = openFiles[currentFileIndex]
                        val isPreviewMode = filePreviewStates[fileInfo.key] ?: false

                        when {
                            // 图片文件：显示图片预览
                            fileInfo.isImage -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                WorkspaceImagePreview(
                                    fileName = fileInfo.name,
                                    previewUri = previewUri,
                                    isSourceLoading = previewFileState.loading,
                                    errorMessage = previewFileState.errorMessage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            fileInfo.isAudio -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewFileState.loading) {
                                        CircularProgressIndicator()
                                    } else if (previewUri != null) {
                                        AudioAttachmentPlayer(
                                            uri = previewUri,
                                            modifier = Modifier.fillMaxWidth(),
                                            autoPlay = false
                                        )
                                    } else {
                                        Text(
                                            text = previewFileState.errorMessage
                                                ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            fileInfo.isVideo -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewFileState.loading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else if (previewUri != null) {
                                        VideoAttachmentPlayer(
                                            uri = previewUri,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 180.dp, max = 420.dp),
                                            autoPlay = false
                                        )
                                    } else {
                                        Text(
                                            text = previewFileState.errorMessage
                                                ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            fileInfo.isReadOnlyDocumentPreviewable -> {
                                WorkspaceReadOnlyDocumentPreview(
                                    fileInfo = fileInfo,
                                    workspaceEnv = fileInfo.environment.takeUnless { it == "android" },
                                    toolHandler = toolHandler,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            fileInfo.isMarkdown && isPreviewMode -> {
                                browserStateHolder.SaveableStateProvider("markdown:${fileInfo.key}") {
                                    WorkspaceMarkdownPreview(
                                        fileInfo = fileInfo,
                                        onFileLink = ::openMarkdownFileLink,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            // HTML文件的预览模式：使用WebView
                            fileInfo.isHtml && isPreviewMode -> {
                                AndroidView(
                                        factory = { context ->
                                            WebView(context).apply {
                                                installWorkspaceTouchInterceptor()
                                                webViewHandler.configureWebView(this, WebViewHandler.WebViewMode.WORKSPACE, "workspace_file_preview_${fileInfo.path}")
                                            }
                                         },
                                        update = { webView ->
                                            val baseUrl = "file://${File(fileInfo.path).parent}/"
                                            webView.loadDataWithBaseURL(
                                                    baseUrl,
                                                    fileInfo.content, // 使用最新的文件内容
                                                    "text/html",
                                                    "UTF-8",
                                                    null
                                            )
                                        },
                                        onRelease = { webView ->
                                            webView.releaseWorkspaceWebView()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                )
                            }
                            // 其他所有情况：使用CodeEditor
                            else -> {
                                key(fileInfo.key) {
                                    val fileLanguage = LanguageDetector.detectLanguage(fileInfo.name)
                                    CodeEditor(
                                            code = fileInfo.content,
                                            language = fileLanguage,
                                            fileKey = fileInfo.key,
                                            onCodeChange = { newContent ->
                                                val updatedFiles = openFiles.toMutableList()
                                                if (currentFileIndex in updatedFiles.indices) {
                                                    val updatedFile = openFiles[currentFileIndex].copy(content = newContent)
                                                    updatedFiles[currentFileIndex] = updatedFile
                                                    openFiles = updatedFiles

                                                    // 将文件标记为未保存
                                                    unsavedFiles = unsavedFiles + updatedFile.key
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            searchMatches = searchMatches,
                                            activeSearchMatch = activeMatch,
                                            searchNavigationRequest = searchNavigationRequest,
                                            initialLine = fileInfo.initialLine,
                                            initialLineRequest = fileLineNavigationRequests[fileInfo.key] ?: 0,
                                            editorRef = { editor -> activeEditor = editor },
                                            onKeyCommand = ::handleEditorKeyCommand
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 解绑确认对话框
        if (showUnbindConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUnbindConfirmDialog = false },
                title = { Text(context.getString(R.string.unbind_workspace_title)) },
                text = { Text(context.getString(R.string.unbind_workspace_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actualViewModel.unbindChatFromWorkspace(currentChat.id)
                            showUnbindConfirmDialog = false
                        }
                    ) {
                        Text(context.getString(R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnbindConfirmDialog = false }) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }

        if (showRenameWorkspaceDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isRenamingWorkspace) {
                        showRenameWorkspaceDialog = false
                        renameWorkspaceError = null
                    }
                },
                title = { Text(context.getString(R.string.workspace_rename_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = renameWorkspaceInput,
                            onValueChange = {
                                renameWorkspaceInput = it
                                renameWorkspaceError = null
                            },
                            label = { Text(context.getString(R.string.file_dialog_new_name)) },
                            singleLine = true,
                            isError = renameWorkspaceError != null,
                            supportingText = {
                                renameWorkspaceError?.let { Text(it) }
                            }
                        )
                        Text(
                            text = context.getString(R.string.workspace_rename_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isRenamingWorkspace,
                        onClick = {
                            if (unsavedFiles.isNotEmpty()) {
                                renameWorkspaceError =
                                    context.getString(R.string.workspace_rename_save_first)
                                return@TextButton
                            }
                            isRenamingWorkspace = true
                            coroutineScope.launch {
                                runCatching {
                                    actualViewModel.renameWorkspace(
                                        chatId = currentChat.id,
                                        newWorkspaceName = renameWorkspaceInput
                                    )
                                }.onSuccess { result ->
                                    actualViewModel.showToast(
                                        context.getString(
                                            R.string.workspace_rename_success,
                                            result.workspaceName
                                        )
                                    )
                                    showRenameWorkspaceDialog = false
                                    renameWorkspaceError = null
                                }.onFailure { error ->
                                    renameWorkspaceError =
                                        error.message
                                            ?: context.getString(R.string.workspace_rename_failed)
                                }
                                isRenamingWorkspace = false
                            }
                        }
                    ) {
                        Text(context.getString(R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isRenamingWorkspace,
                        onClick = {
                            showRenameWorkspaceDialog = false
                            renameWorkspaceError = null
                        }
                    ) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }
        
        // 关闭文件确认对话框
        if (fileToCloseIndex != -1) {
            val file = openFiles.getOrNull(fileToCloseIndex)
            if (file != null) {
                AlertDialog(
                    onDismissRequest = { fileToCloseIndex = -1 },
                    title = { Text(context.getString(R.string.save_changes_question)) },
                    text = { Text(context.getString(R.string.file_modified_save_prompt, file.name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val closingKey = file.key
                                saveFile(file) {
                                    confirmCloseFile(openFiles.indexOfFirst { it.key == closingKey })
                                }
                            }
                        ) {
                            Text(context.getString(R.string.save))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { fileToCloseIndex = -1 }) {
                                Text(context.getString(R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    confirmCloseFile(fileToCloseIndex)
                                }
                            ) {
                                Text(context.getString(R.string.dont_save))
                            }
                        }
                    }
                )
            }
        }

        val commandDialogState = workspaceCommandExecutionState
        if (!standalone && commandDialogState != null &&
            commandDialogState.workspacePath == workspacePath &&
            commandDialogState.isVisible
        ) {
            WorkspaceCommandExecutionDialog(
                state = commandDialogState,
                onClose = { actualViewModel.dismissWorkspaceCommandExecutionDialog(workspacePath) },
                onCancel = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        actualViewModel.cancelWorkspaceCommandExecution()
                    }
                },
                onCopyOutput = { output ->
                    clipboardManager.setText(AnnotatedString(output))
                    actualViewModel.showToast(context.getString(R.string.copied_to_clipboard))
                }
            )
        }
    }
}

@Composable
private fun WorkspaceCommandExecutionDialog(
    state: WorkspaceCommandExecutionState,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onCopyOutput: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val outputText = remember(state.outputEntries) {
        state.outputEntries.joinToString(separator = "\n")
    }

    LaunchedEffect(state.outputEntries.size) {
        if (state.outputEntries.isNotEmpty()) {
            listState.animateScrollToItem(state.outputEntries.lastIndex)
        }
    }

    val dialogMetrics = rememberCompactDialogMetrics()
    val outerPadding = if (dialogMetrics.isCompact) 8.dp else 20.dp
    val contentPadding = if (dialogMetrics.isCompact) 16.dp else 20.dp
    val outputMinHeight = if (dialogMetrics.isCompact) 96.dp else 180.dp
    val outputMaxHeight = if (dialogMetrics.isCompact) 220.dp else 360.dp
    val surfaceModifier =
        Modifier
            .fillMaxWidth()
            .padding(outerPadding)
            .let { base ->
                if (dialogMetrics.isCompact) {
                    base.heightIn(max = dialogMetrics.maxHeight - outerPadding * 2)
                } else {
                    base
                }
            }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = surfaceModifier,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = state.commandLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.commandText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (!state.isRunning) {
                            R.string.workspace_command_finished
                        } else if (state.isCancelling) {
                            R.string.workspace_command_cancelling
                        } else {
                            R.string.workspace_command_running
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.isRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Surface(
                    modifier =
                        if (dialogMetrics.isCompact) {
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(min = outputMinHeight, max = outputMaxHeight)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = outputMinHeight, max = outputMaxHeight)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    if (state.outputEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.isRunning) {
                                        R.string.workspace_command_waiting_output
                                    } else {
                                        R.string.workspace_command_no_output
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(
                                    items = state.outputEntries,
                                    key = { index, _ -> index }
                                ) { _, line ->
                                    Text(
                                        text = line.ifEmpty { " " },
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (state.isRunning) {
                        TextButton(onClick = onClose) {
                            Text(stringResource(R.string.workspace_command_hide))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onCancel,
                            enabled = !state.isCancelling
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        TextButton(
                            onClick = { onCopyOutput(outputText) },
                            enabled = state.outputEntries.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.copy_result))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onClose) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}

/** VSCode风格的标签组件 */
@Composable
fun VSCodeTab(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isActive: Boolean,
        isUnsaved: Boolean,
        onClose: (() -> Unit)? = null,
        onClick: () -> Unit
) {
    val backgroundColor =
            if (isActive) MaterialTheme.colorScheme.surface.copy(alpha = 1f) else Color.Transparent // 非活动标签使用顶部栏底色

    val contentColor =
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant

    val bottomBorderColor = if (isActive) contentColor else Color.Transparent

    Box(
            modifier =
                    Modifier.height(40.dp) // 增加高度
                            .background(
                                    backgroundColor,
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                            .clickable(onClick = onClick)
    ) {
        Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                        text = title,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                if (onClose != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(22.dp).padding(2.dp)
                    ) {
                        if (isUnsaved) {
                            Icon(
                                Icons.Filled.FiberManualRecord,
                                contentDescription = stringResource(R.string.workspace_unsaved),
                                modifier = Modifier.size(8.dp),
                                tint = contentColor.copy(alpha = 0.9f)
                            )
                        } else {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.workspace_close),
                                modifier = Modifier.size(14.dp),
                                tint = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp)) // 保持对齐
                }
            }
            // 活动标签下划线
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(bottomBorderColor))
        }
    }
}

/**
 * 命令按钮视图组件
 * 用于非 browser 类型的预览界面，显示 config.json 中定义的命令按钮
 */
@Composable
fun CommandButtonsView(
    config: WorkspaceConfig,
    workspacePath: String,
    onCommandExecute: (CommandConfig) -> Unit,
    onOpenBrowserPreview: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = config.title ?: "${config.projectType.uppercase()} ${stringResource(R.string.workspace_project_suffix)}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (config.description != null) {
            Text(
                text = config.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = stringResource(R.string.workspace_click_buttons_below),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 浏览器预览按钮（可选）
        if (config.preview.showPreviewButton && config.preview.url.isNotEmpty()) {
            Button(
                onClick = onOpenBrowserPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = config.preview.previewButtonLabel,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 显示命令按钮
        if (config.commands.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = stringResource(R.string.workspace_no_commands_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            config.commands.forEach { command ->
                Button(
                    onClick = { onCommandExecute(command) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = command.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 项目信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.workspace_path_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = workspacePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
