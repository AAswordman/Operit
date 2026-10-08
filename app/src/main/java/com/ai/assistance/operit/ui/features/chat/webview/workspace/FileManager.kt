package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.features.chat.webview.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceMimeTypeForPath
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceShouldOpenAsDirectPreview
import com.ai.assistance.operit.ui.features.chat.webview.workspace.text.WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES
import com.ai.assistance.operit.ui.features.chat.webview.workspace.text.readWorkspaceTextWithinLimit
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.ui.features.chat.webview.workspace.browser.*

// 目录条目数据类
data class DirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: String,
        val permissions: String
)

// 快速路径条目
data class QuickPathEntry(
        val name: String,
        val path: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/** 文件浏览器组件 - VSCode风格 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(
        initialPath: String,
        environment: String? = null,
        onBindWorkspace: ((String, String?) -> Unit)? = null,
        onCancel: () -> Unit,
        showHeader: Boolean = true,
        onFileOpen: (OpenFileInfo) -> Unit
) {
    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())
    var currentPath by rememberSaveable(initialPath, environment) { mutableStateOf(initialPath) }
    var currentEnvironment by rememberSaveable(initialPath, environment) { mutableStateOf(environment) }
    var fileList by remember { mutableStateOf<List<DirectoryEntry>>(emptyList()) }
    var isOperationLoading by remember { mutableStateOf(false) }
    var isDirectoryLoading by remember { mutableStateOf(false) }
    var directoryLoadJob by remember { mutableStateOf<Job?>(null) }
    var directoryLoadVersion by remember { mutableIntStateOf(0) }
    val isLoading = isOperationLoading || isDirectoryLoading
    val coroutineScope = rememberCoroutineScope()
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileNameError by remember { mutableStateOf(false) }
    var pendingRepoBookmarkUri by remember { mutableStateOf<Uri?>(null) }
    var repoBookmarkNameInput by remember { mutableStateOf("") }
    var showRepoBookmarkNameDialog by remember { mutableStateOf(false) }
    var repoBookmarkNameError by remember { mutableStateOf<String?>(null) }
    // 用于控制长按上下文菜单的状态
    var contextMenuExpandedFor by remember { mutableStateOf<DirectoryEntry?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(0) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var foldersFirst by rememberSaveable { mutableStateOf(true) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    var openWithTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    val listState = rememberLazyListState()
    val operations = remember(context) { FileBrowserOperations(context) }
    val clipboard by FileBrowserClipboard.content.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    // 是否显示隐藏文件（以.开头）
    var showHiddenFiles by remember { mutableStateOf(false) }


    fun queryRepoBookmarkName(uri: Uri): String {
        fun normalizeName(raw: String): String {
            return raw.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), "_")
                .ifBlank { "repo" }
        }

        val providerLabel =
            runCatching {
                val authority = uri.authority ?: return@runCatching null
                val provider = context.packageManager.resolveContentProvider(authority, 0)
                provider?.applicationInfo?.loadLabel(context.packageManager)?.toString()?.trim()
            }.getOrNull()

        val raw = providerLabel?.takeIf { it.isNotBlank() } ?: uri.authority ?: "repo"
        return normalizeName(raw)
    }

    val isSafEnv = remember(currentEnvironment) { currentEnvironment?.startsWith("repo:", ignoreCase = true) == true }

    fun joinPath(parent: String, child: String): String {
        val p = if (parent.isBlank()) "/" else parent
        return if (p.endsWith("/")) "$p$child" else "$p/$child"
    }

    fun parentPath(path: String): String? {
        val normalized = path.trimEnd('/').ifBlank { "/" }
        if (normalized == "/") return null
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isBlank()) "/" else parent
    }

    // 快速路径定义
    val quickPaths = remember {
        listOf(
            QuickPathEntry(
                name = "Linux",
                path = "/",
                icon = Icons.Default.Terminal
            ),
            QuickPathEntry(
                name = "SDCard",
                path = Environment.getExternalStorageDirectory().absolutePath,
                icon = Icons.Default.SdCard
            ),
            QuickPathEntry(
                name = "Workspace",
                path = File(context.filesDir, "workspace").absolutePath,
                icon = Icons.Default.Folder
            )
        )
    }

    // 本机 Android 路径直接使用 File API，避免权限级文件工具的 shell 列表解析漏掉普通文件。
    suspend fun listLocalDirectory(path: String): List<DirectoryEntry>? = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.isDirectory) return@withContext null
        val entries = directory.listFiles() ?: return@withContext null
        entries
            .filter { it.name != "." && it.name != ".." }
            .map { file ->
                DirectoryEntry(
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified().toString(),
                    permissions = ""
                )
            }
    }

    fun loadDirectory(path: String, targetEnvironment: String? = currentEnvironment) {
        // 新导航替换旧读取；旧任务的 finally 不得清除新任务的加载状态。
        val requestVersion = ++directoryLoadVersion
        directoryLoadJob?.cancel()
        isDirectoryLoading = true
        directoryLoadJob = coroutineScope.launch {
            try {
                val localEntries =
                    if (targetEnvironment.isNullOrBlank() || targetEnvironment.equals("android", ignoreCase = true)) {
                        listLocalDirectory(path)
                    } else {
                        null
                    }
                if (localEntries != null) {
                    fileList = localEntries
                    currentPath = path
                    currentEnvironment = targetEnvironment
                    errorMessage = null
                    AppLogger.d(
                        "WorkspaceFileBrowser",
                        "listed local path=$path directories=${localEntries.count { it.isDirectory }} files=${localEntries.count { !it.isDirectory }}"
                    )
                    return@launch
                }

                val parameters = buildList {
                    add(ToolParameter("path", path))
                    targetEnvironment?.let { add(ToolParameter("environment", it)) }
                }
                val tool = AITool("list_files", parameters)
                AppLogger.d("WorkspaceFileBrowser", "execute list_files path=$path env=$currentEnvironment")
                val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }
                AppLogger.d("WorkspaceFileBrowser", "result list_files success=${result.success} error=${result.error}")
                if (result.success && result.result is DirectoryListingData) {
                    val entries = (result.result as DirectoryListingData).entries
                    fileList =
                            entries.map {
                                DirectoryEntry(
                                        name = it.name,
                                        isDirectory = it.isDirectory,
                                        size = it.size,
                                        lastModified = it.lastModified,
                                        permissions = it.permissions
                                )
                            }
                    currentPath = path
                    currentEnvironment = targetEnvironment
                    errorMessage = null
                } else {
                    errorMessage = result.error
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "读取目录失败", e)
                errorMessage = e.message
            } finally {
                if (requestVersion == directoryLoadVersion) isDirectoryLoading = false
            }
        }
    }

    val addSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            pendingRepoBookmarkUri = uri
            repoBookmarkNameInput = queryRepoBookmarkName(uri)
            showRepoBookmarkNameDialog = true
        }
    }

    if (showRepoBookmarkNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRepoBookmarkNameDialog = false
                pendingRepoBookmarkUri = null
                repoBookmarkNameError = null
            },
            title = { Text(stringResource(R.string.repo_bookmark_name)) },
            text = {
                TextField(
                    value = repoBookmarkNameInput,
                    onValueChange = {
                        repoBookmarkNameInput = it
                        repoBookmarkNameError = null
                    },
                    label = { Text(stringResource(R.string.repo_bookmark_name_label)) },
                    singleLine = true,
                    isError = repoBookmarkNameError != null,
                    supportingText = {
                        repoBookmarkNameError?.let { Text(it) }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRepoBookmarkUri
                        val name = repoBookmarkNameInput.trim()
                        if (uri == null) {
                            showRepoBookmarkNameDialog = false
                            pendingRepoBookmarkUri = null
                            repoBookmarkNameError = null
                            return@TextButton
                        }

                        if (name.isEmpty()) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_empty)
                            return@TextButton
                        }

                        val nameExists = safBookmarks.any {
                            it.uri != uri.toString() && it.name.equals(name, ignoreCase = true)
                        }
                        if (nameExists) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_exists)
                            return@TextButton
                        }

                        coroutineScope.launch {
                            apiPreferences.addSafBookmark(uri.toString(), name)
                            loadDirectory("/", "repo:$name")
                        }

                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    fun createNewFile(fileName: String, isDirectory: Boolean) {
        if (isLoading) return
        if (!isValidWorkspaceEntryName(fileName)) {
            newFileNameError = true
            return
        }
        val createPath = currentPath
        val createEnvironment = currentEnvironment
        val createInRepository = isSafEnv
        isOperationLoading = true
        coroutineScope.launch {
            var succeeded = false
            try {
                val filePath =
                    if (createInRepository) {
                        joinPath(createPath, fileName)
                    } else {
                        File(createPath, fileName).path
                    }
                val tool =
                    if (isDirectory) {
                        AITool("make_directory", listOf(ToolParameter("path", filePath)) + listOfNotNull(createEnvironment?.let { ToolParameter("environment", it) }))
                    } else {
                        AITool(
                            "write_file",
                            listOf(
                                ToolParameter("path", filePath),
                                ToolParameter("content", "")
                            ) + listOfNotNull(createEnvironment?.let { ToolParameter("environment", it) })
                        )
                    }
                AppLogger.d("WorkspaceFileBrowser", "execute ${tool.name} path=$filePath env=$createEnvironment")
                val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }
                check(result.success) { result.error.orEmpty() }
                errorMessage = null
                succeeded = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "创建文件失败", e)
                errorMessage = e.message
            } finally {
                isOperationLoading = false
            }
            if (succeeded) loadDirectory(currentPath)
        }
    }

    fun runOperation(block: suspend () -> Unit) {
        if (isLoading) return
        isOperationLoading = true
        coroutineScope.launch {
            try {
                block()
                errorMessage = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "文件操作失败", e)
                errorMessage = e.message
            } finally {
                isOperationLoading = false
            }
            loadDirectory(currentPath)
        }
    }

    fun openFile(filePath: String, asText: Boolean = false) {
        if (isLoading) return
        val fileEnvironment = currentEnvironment ?: "android"
        isOperationLoading = true
        coroutineScope.launch {
            try {
                val mimeType = if (asText) "text/plain" else workspaceMimeTypeForPath(filePath)
                val result = withContext(Dispatchers.IO) {
                    if (!asText && workspaceShouldOpenAsDirectPreview(filePath)) {
                        OpenFileInfo(filePath, "", System.currentTimeMillis(), mimeType = mimeType, environment = fileEnvironment)
                    } else {
                        val parameters = listOf(ToolParameter("path", filePath),
                            ToolParameter("environment", fileEnvironment))
                        val exists = toolHandler.executeTool(AITool("file_exists", parameters))
                        check(exists.success) { exists.error.orEmpty() }
                        val info = exists.result
                        check(info is FileExistsData && info.exists && !info.isDirectory) {
                            context.getString(R.string.cannot_open_file, filePath)
                        }
                        checkNotNull(readWorkspaceTextWithinLimit(info.size) {
                            val read = toolHandler.executeTool(AITool("read_file_full",
                                parameters + ToolParameter("text_only", "true")))
                            check(read.success && read.result is FileContentData) { read.error.orEmpty() }
                            OpenFileInfo(filePath, (read.result as FileContentData).content,
                                System.currentTimeMillis(), mimeType = mimeType, environment = fileEnvironment)
                        }) {
                            context.getString(R.string.workspace_text_preview_too_large,
                                (WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES / (1024 * 1024)).toInt())
                        }
                    }
                }
                onFileOpen(result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("WorkspaceFileBrowser", "打开文件失败", e)
                errorMessage = e.message
            } finally {
                isOperationLoading = false
            }
        }
    }

    LaunchedEffect(initialPath, environment) { loadDirectory(initialPath, environment) }

    if (showCreateFileDialog) {
        AlertDialog(
                onDismissRequest = { showCreateFileDialog = false },
                title = { Text(stringResource(R.string.file_manager_create_new_file)) },
                text = {
                    Column {
                        TextField(
                                value = newFileName,
                                onValueChange = { newFileName = it; newFileNameError = false },
                                isError = newFileNameError,
                                supportingText = {
                                    if (newFileNameError) Text(stringResource(R.string.workspace_rename_name_invalid))
                                },
                                label = { Text(stringResource(R.string.file_manager_file_name)) },
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor =
                                                        MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor =
                                                        MaterialTheme.colorScheme.surface
                                        )
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                                onClick = {
                                    val name = newFileName.trim()
                                    if (!isValidWorkspaceEntryName(name)) {
                                        newFileNameError = true
                                        return@TextButton
                                    }
                                    createNewFile(name, false)
                                    showCreateFileDialog = false
                                    newFileName = ""
                                    newFileNameError = false
                                }
                        ) { Text(stringResource(R.string.file_manager_create_file)) }
                        TextButton(
                                onClick = {
                                    val name = newFileName.trim()
                                    if (!isValidWorkspaceEntryName(name)) {
                                        newFileNameError = true
                                        return@TextButton
                                    }
                                    createNewFile(name, true)
                                    showCreateFileDialog = false
                                    newFileName = ""
                                    newFileNameError = false
                                }
                        ) { Text(stringResource(R.string.file_manager_create_folder)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFileDialog = false }) { Text(stringResource(R.string.file_manager_cancel)) }
                }
        )
    }

    if (showSortMenu) {
        FileBrowserSortDialog(sortMode, descending, ignoreCase, foldersFirst,
            onDismiss = { showSortMenu = false },
            onConfirm = { mode, reverse, insensitive, folders ->
                sortMode = mode
                descending = reverse
                ignoreCase = insensitive
                foldersFirst = folders
                showSortMenu = false
            })
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.file_menu_rename)) },
            text = { OutlinedTextField(renameInput, { renameInput = it }, singleLine = true,
                label = { Text(stringResource(R.string.file_dialog_new_name)) }) },
            confirmButton = { TextButton(onClick = {
                val name = renameInput.trim()
                if (renameInput == target.name || name == target.name) {
                    renameTarget = null
                    return@TextButton
                }
                if (isValidWorkspaceEntryName(name)) {
                    renameTarget = null
                    runOperation { operations.rename(joinPath(currentPath, target.name), joinPath(currentPath, name), currentEnvironment) }
                }
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.file_op_confirm_delete)) },
            text = { Text(stringResource(R.string.file_dialog_delete_single, target.name)) },
            confirmButton = { TextButton(onClick = {
                deleteTarget = null
                runOperation { operations.delete(joinPath(currentPath, target.name), currentEnvironment, target.isDirectory) }
            }) { Text(stringResource(R.string.file_manager_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    openWithTarget?.let { target ->
        AlertDialog(onDismissRequest = { openWithTarget = null },
            title = { Text(stringResource(R.string.workspace_open_with)) },
            text = { Column {
                TextButton(onClick = { openWithTarget = null; openFile(joinPath(currentPath, target.name)) }) {
                    Text(stringResource(R.string.workspace_default_open))
                }
                if (FileUtils.isTextBasedFileName(target.name)) {
                    TextButton(onClick = { openWithTarget = null; openFile(joinPath(currentPath, target.name), true) }) {
                        Text(stringResource(R.string.workspace_open_as_text))
                    }
                }
                TextButton(onClick = {
                    openWithTarget = null
                    runOperation { operations.externalOpen(joinPath(currentPath, target.name), currentEnvironment, false) }
                }) { Text(stringResource(R.string.workspace_open_external)) }
            } }, confirmButton = {})
    }
    contextMenuExpandedFor?.let { target ->
        FileBrowserContextMenu(target.name, target.isDirectory, clipboard != null,
            onDismiss = { contextMenuExpandedFor = null },
            onCopy = { cut ->
                FileBrowserClipboard.set(FileBrowserClipboardItem(joinPath(currentPath, target.name), currentEnvironment, cut, target.isDirectory))
                contextMenuExpandedFor = null
            },
            onPaste = {
                contextMenuExpandedFor = null
                val destination = if (target.isDirectory) joinPath(currentPath, target.name) else currentPath
                runOperation { operations.paste(destination, currentEnvironment) }
            },
            onRename = { contextMenuExpandedFor = null; renameTarget = target; renameInput = target.name },
            onOpenWith = { contextMenuExpandedFor = null; openWithTarget = target },
            onShare = { contextMenuExpandedFor = null; runOperation {
                operations.externalOpen(joinPath(currentPath, target.name), currentEnvironment, true)
            } },
            onDelete = { contextMenuExpandedFor = null; deleteTarget = target })
    }

    // 浏览工具栏使用文件页面的主题前景，不继承聊天背景上的颜色。
    Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier =
                    Modifier.fillMaxSize()
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, // 移除点击时的涟漪效果
                                    enabled = true,
                                    onClick = {}
                            ) // 拦截点击事件，防止穿透
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, stringResource(R.string.file_manager_return)) }
                    Text(stringResource(if (onBindWorkspace == null) R.string.files else R.string.select_existing_workspace),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searchVisible = !searchVisible }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                }
            }
            if (searchVisible) {
                OutlinedTextField(searchQuery, { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                    label = { Text(stringResource(R.string.file_manager_search_hint)) },
                    trailingIcon = { IconButton(onClick = { searchQuery = ""; searchVisible = false }) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    } })
            }
            errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Text(currentPath, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!showHeader) IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(Icons.Default.Search, stringResource(R.string.search))
                }
                IconButton(onClick = { parentPath(currentPath)?.let { loadDirectory(it) } }, enabled = currentPath != "/") {
                    Icon(Icons.Default.ArrowUpward, stringResource(R.string.file_manager_navigate_up))
                }
                if (clipboard != null) {
                    IconButton(onClick = { runOperation { operations.paste(currentPath, currentEnvironment) } }, enabled = !isLoading) {
                        Icon(Icons.Default.ContentPaste, stringResource(R.string.file_manager_paste))
                    }
                }
                // 显示/隐藏隐藏文件按钮
                IconButton(
                        onClick = { showHiddenFiles = !showHiddenFiles },
                        modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                            if (showHiddenFiles) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showHiddenFiles) stringResource(R.string.file_manager_hide_dot_files) else stringResource(R.string.file_manager_show_dot_files),
                            modifier = Modifier.size(18.dp),
                            tint = if (showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 排序按钮
                Box {
                    IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.file_manager_sort),
                                modifier = Modifier.size(18.dp)
                        )
                    }

                }

                run {
                    IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.file_manager_new),
                                modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                            onClick = { loadDirectory(currentPath) },
                            modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.file_manager_refresh),
                                modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // 快速路径栏
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPaths) { quickPath ->
                    QuickPathChip(
                        entry = quickPath,
                        isActive =
                            if (quickPath.name == "Linux") {
                                currentEnvironment == "linux" && currentPath.startsWith(quickPath.path)
                            } else {
                                currentEnvironment == null && currentPath.startsWith(quickPath.path)
                            },
                        onClick = {
                            if (quickPath.name == "Linux") {
                                loadDirectory("/", "linux")
                                return@QuickPathChip
                            }

                            val pathFile = File(quickPath.path)
                            if (pathFile.exists() && pathFile.isDirectory) {
                                loadDirectory(quickPath.path, null)
                            }
                        }
                    )
                }

                items(safBookmarks) { bookmark ->
                    var menuExpanded by remember(bookmark.uri) { mutableStateOf(false) }
                    val repoEnv = remember(bookmark.name) { "repo:${bookmark.name}" }
                    Box {
                        QuickPathChipWithLongPress(
                            entry = QuickPathEntry(name = bookmark.name, path = "/", icon = Icons.Default.Folder),
                            isActive = currentEnvironment == repoEnv,
                            onClick = {
                                loadDirectory("/", repoEnv)
                            },
                            onLongPress = { menuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.repo_bookmark_delete)) },
                                onClick = {
                                    menuExpanded = false
                                    val uri = runCatching { Uri.parse(bookmark.uri) }.getOrNull()
                                    if (uri != null) {
                                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                                    }
                                    coroutineScope.launch {
                                        apiPreferences.removeSafBookmark(bookmark.uri)
                                        if (currentEnvironment == repoEnv) {
                                            loadDirectory(initialPath, environment)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    QuickPathChip(
                        entry = QuickPathEntry(name = "+", path = "", icon = Icons.Default.Add),
                        isActive = false,
                        onClick = { addSafLauncher.launch(null) }
                    )
                }
            }

            // 文件列表
            if (isLoading) {
                Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 8.dp)
                ) {
                    // 使用更健壮的方式来判断是否应该显示返回上一级的选项
                    val canGoUp = if (isSafEnv) parentPath(currentPath) != null else File(currentPath).parent != null
                    if (canGoUp) {
                        item {
                            FileListItem(
                                    name = "..",
                                    icon = Icons.Default.FolderOpen,
                                    isDirectory = true,
                                    onClick = {
                                        if (isSafEnv) {
                                            val p = parentPath(currentPath)
                                            if (p != null) loadDirectory(p)
                                        } else {
                                            File(currentPath).parent?.let { parentPath ->
                                                loadDirectory(parentPath)
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // 根据showHiddenFiles过滤文件列表
                    val filteredList = if (showHiddenFiles) {
                        fileList
                    } else {
                        fileList.filter { !it.name.startsWith(".") }
                    }
                    
                    val visibleFiles = sortDirectoryEntries(filteredList.filter { it.name.contains(searchQuery, ignoreCase = true) },
                        sortMode, descending, ignoreCase, foldersFirst)
                    if (visibleFiles.isEmpty()) {
                        item { Text(stringResource(R.string.file_manager_empty_folder),
                            modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(visibleFiles, key = { it.name }) { item ->
                        Box { // 使用Box来定位上下文菜单
                            FileListItem(
                                    name = item.name,
                                    icon =
                                            if (item.isDirectory) Icons.Default.Folder
                                            else getFileIcon(item.name),
                                    isDirectory = item.isDirectory,
                                    detail = listOf(formatLastModified(item.lastModified), if (item.isDirectory) "" else formatSize(item.size)).filter { it.isNotBlank() }.joinToString(" · "),
                                    onClick = {
                                        if (item.isDirectory) {
                                            val newPath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            loadDirectory(newPath)
                                        } else {
                                            val filePath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            openFile(filePath)
                                        }
                                    },
                                    onLongPress = {
                                        if (!isLoading) {
                                            contextMenuExpandedFor = item
                                        }
                                    }
                            )

                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }

            // 底部操作栏
            if (onBindWorkspace != null) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showHeader) {
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.file_manager_cancel)) }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            AppLogger.d(
                                "WorkspaceFileBrowser",
                                "bind workspace path=$currentPath env=$currentEnvironment"
                            )
                            onBindWorkspace(currentPath, currentEnvironment)
                        }
                    ) {
                        Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.file_manager_bind_current_folder))
                    }
                }
            }
        }
    }
}

/** 快速路径芯片组件 */
@Composable
private fun QuickPathChip(
    entry: QuickPathEntry,
    isActive: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                if (entry.name.isNotBlank() && entry.name != "+") {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isActive,
            borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun QuickPathChipWithLongPress(
    entry: QuickPathEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var suppressClickOnce by remember(entry.name, entry.path) { mutableStateOf(false) }
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    Box(
        modifier = Modifier.pointerInput(entry.name, entry.path) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
                    waitForUpOrCancellation()
                    false
                } ?: true

                if (longPressed) {
                    suppressClickOnce = true
                    latestOnLongPress()
                    waitForUpOrCancellation()
                }
            }
        }
    ) {
        QuickPathChip(
            entry = entry,
            isActive = isActive,
            onClick = {
                if (suppressClickOnce) {
                    suppressClickOnce = false
                    return@QuickPathChip
                }
                onClick()
            }
        )
    }
}

/** 抽取出的文件列表项，实现紧凑布局和长按手势 */
@Composable
private fun FileListItem(
        name: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isDirectory: Boolean,
        onClick: () -> Unit,
        detail: String = "",
        onLongPress: (() -> Unit)? = null
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            // 回调随重组更新，手势会话只在文件项身份改变时重启。
                            .pointerInput(name, isDirectory) {
                                detectTapGestures(
                                        onTap = { latestOnClick() },
                                        onLongPress = { latestOnLongPress?.invoke() }
                                )
                            }
                            .heightIn(min = 64.dp).padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp), // 缩小图标
                tint =
                        if (isDirectory) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(12.dp)) // 减少间距
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (detail.isNotBlank()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "%.1f MB".format(size / (1024.0 * 1024.0))
    }
}

@SuppressLint("SimpleDateFormat")
private fun formatLastModified(dateString: String): String {
    val millis = dateString.toLongOrNull()
    if (millis != null) {
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(millis))
    }
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

/** 根据文件名获取对应的图标 */
@Composable
fun getFileIcon(fileName: String) =
        when {
            fileName.endsWith(".html", true) || fileName.endsWith(".htm", true) ->
                    Icons.Default.Code
            fileName.endsWith(".css", true) -> Icons.Default.Brush
            fileName.endsWith(".js", true) -> Icons.Default.Code // 使用通用的代码图标替代Javascript
            fileName.endsWith(".json", true) -> Icons.Default.DataObject
            fileName.endsWith(".jpg", true) ||
                    fileName.endsWith(".png", true) ||
                    fileName.endsWith(".gif", true) ||
                    fileName.endsWith(".jpeg", true) -> Icons.Default.Image
            fileName.endsWith(".txt", true) -> Icons.AutoMirrored.Filled.TextSnippet
            fileName.endsWith(".md", true) -> Icons.AutoMirrored.Filled.Article
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
