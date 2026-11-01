package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.files.transfer.FileCopyObserver
import com.ai.assistance.operit.core.files.transfer.FileCopyObservers
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceCopyProgress
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceDeleteOperation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.workspaceMimeTypeForPath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 界面操作使用现有文件工具，保留每次工具调用的成功或失败结果。 */
internal class FileBrowserOperations(context: Context) {
    private val context = context.applicationContext
    private val handler = AIToolHandler.getInstance(this.context)
    private val copyState = MutableStateFlow<WorkspaceCopyProgress?>(null)
    val copyProgress = copyState.asStateFlow()
    private val deletion = WorkspaceDeleteOperation { target ->
        deleteEntry(target.path, target.environment, target.isDirectory)
    }
    val deleteProgress = deletion.progress

    private fun parameters(environment: String?, vararg values: Pair<String, String>) = buildList {
        values.forEach { (name, value) -> add(ToolParameter(name, value)) }
        environment?.let { add(ToolParameter("environment", it)) }
    }

    private fun execute(name: String, parameters: List<ToolParameter>) =
        handler.executeTool(AITool(name, parameters)).also { check(it.success) { it.error.orEmpty() } }

    private fun requireNewDestination(path: String, environment: String?) {
        val result = execute("file_exists", parameters(environment, "path" to path))
        check(result.result is FileExistsData && !(result.result as FileExistsData).exists) {
            context.getString(R.string.file_op_confirm_overwrite)
        }
    }

    suspend fun rename(source: String, destination: String, environment: String?) = withContext(Dispatchers.IO) {
        if (source == destination) return@withContext
        requireNewDestination(destination, environment)
        execute("move_file", parameters(environment, "source" to source, "destination" to destination))
        Unit
    }

    private fun deleteEntry(path: String, environment: String?, directory: Boolean) {
        execute("delete_file", parameters(environment, "path" to path, "recursive" to directory.toString()))
    }

    suspend fun delete(path: String, environment: String?, directory: Boolean): Unit =
        delete(listOf(FileBrowserClipboardItem(path, environment, cut = false, isDirectory = directory)))

    suspend fun delete(
        targets: List<FileBrowserClipboardItem>,
        onDeleted: suspend (FileBrowserClipboardItem) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        deletion.run(targets, onDeleted)
    }

    suspend fun paste(directory: String, environment: String?) = withContext(Dispatchers.IO) {
        val items = FileBrowserClipboard.content.value
        if (items.isEmpty()) return@withContext
        val destinationEnv = environment ?: "android"
        val executionContext = currentCoroutineContext()
        fun progress(item: FileBrowserClipboardItem, index: Int) = WorkspaceCopyProgress(
            source = item.path,
            destination = directory.trimEnd('/') + "/" + item.path.trimEnd('/').substringAfterLast('/'),
            sourceEnvironment = item.environment ?: "android",
            destinationEnvironment = destinationEnv,
            itemIndex = index + 1,
            itemCount = items.size,
            moving = item.cut,
        )
        copyState.value = progress(items.first(), 0)
        try {
            val parent = execute("file_exists", parameters(environment, "path" to directory)).result
            check(parent is FileExistsData && parent.exists && parent.isDirectory) {
                context.getString(R.string.workspace_error_invalid_path, directory)
            }
            val targets = items.map { item ->
                val sourceEnv = item.environment ?: "android"
                val destination = directory.trimEnd('/') + "/" + item.path.trimEnd('/').substringAfterLast('/')
                check(sourceEnv != destinationEnv ||
                    (destination != item.path && !destination.startsWith(item.path.trimEnd('/') + "/"))) {
                    context.getString(R.string.workspace_invalid_paste)
                }
                item to destination
            }
            check(targets.map { it.second }.distinct().size == targets.size) {
                context.getString(R.string.file_op_confirm_overwrite)
            }
            // 全批次先检查目标，避免遇到重名时已经移动了前面的文件。
            targets.forEach { (_, destination) -> requireNewDestination(destination, environment) }
            for ((index, target) in targets.withIndex()) {
                executionContext.ensureActive()
                val (item, destination) = target
                val sourceEnv = item.environment ?: "android"
                val itemProgress = progress(item, index)
                copyState.value = itemProgress
                requireNewDestination(destination, environment)
                if (item.cut && sourceEnv == destinationEnv) {
                    execute("move_file", parameters(environment, "source" to item.path, "destination" to destination))
                } else {
                    val token = FileCopyObservers.register(FileCopyObserver(
                        onProgress = { copyState.value = itemProgress.copy(detail = it) },
                        checkCancelled = { executionContext.ensureActive() },
                    ))
                    try {
                        execute("copy_file", listOf(ToolParameter("source", item.path), ToolParameter("destination", destination),
                            ToolParameter("source_environment", sourceEnv), ToolParameter("dest_environment", destinationEnv),
                            ToolParameter("recursive", item.isDirectory.toString()), ToolParameter(FileCopyObservers.PARAMETER, token)))
                    } finally {
                        FileCopyObservers.remove(token)
                    }
                    // 取消或复制失败时不删除来源；整项成功后才允许完成跨环境剪切。
                    executionContext.ensureActive()
                    if (item.cut) {
                        copyState.value = checkNotNull(copyState.value).copy(finishing = true)
                        // 剪切收尾仍属于移动任务，不弹出第二个删除进度窗口。
                        deleteEntry(item.path, item.environment, item.isDirectory)
                    }
                }
                if (item.cut) FileBrowserClipboard.clear(item)
            }
        } finally {
            copyState.value = null
        }
    }

    suspend fun externalOpen(path: String, environment: String?, share: Boolean) {
        val file = withContext(Dispatchers.IO) {
            pruneWorkspaceShareCache(File(context.cacheDir, "workspace_shared_files"))
            if (environment.isNullOrBlank() || environment == "android") File(path)
            else {
                val result = execute("read_file_binary", parameters(environment, "path" to path))
                check(result.result is BinaryFileContentData) { context.getString(R.string.file_error_share_failed) }
                val directory = File(context.cacheDir, "workspace_shared_files/${("$environment:$path").hashCode()}")
                check(directory.isDirectory || directory.mkdirs()) { context.getString(R.string.file_error_share_failed) }
                File(directory, path.substringAfterLast('/')).apply {
                    writeBytes(Base64.decode((result.result as BinaryFileContentData).contentBase64, Base64.DEFAULT))
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = if (share) Intent(Intent.ACTION_SEND).apply {
            type = workspaceMimeTypeForPath(path)
            putExtra(Intent.EXTRA_STREAM, uri)
        } else Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, workspaceMimeTypeForPath(path)) }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(file.name, uri)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
