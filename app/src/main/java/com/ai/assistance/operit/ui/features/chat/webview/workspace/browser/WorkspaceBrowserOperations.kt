package com.ai.assistance.operit.ui.features.chat.webview.workspace.browser

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceMimeTypeForPath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class FileBrowserClipboardItem(val path: String, val environment: String?, val cut: Boolean, val isDirectory: Boolean)

/** 页面切换时保留剪贴板及源文件所在环境。 */
internal object FileBrowserClipboard {
    private val state = MutableStateFlow<FileBrowserClipboardItem?>(null)
    val content = state.asStateFlow()
    fun set(item: FileBrowserClipboardItem) { state.value = item }
    fun clear(item: FileBrowserClipboardItem) { state.compareAndSet(item, null) }
}

/** 界面操作使用现有文件工具，保留每次工具调用的成功或失败结果。 */
internal class FileBrowserOperations(context: Context) {
    private val context = context.applicationContext
    private val handler = AIToolHandler.getInstance(this.context)

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

    suspend fun delete(path: String, environment: String?, directory: Boolean) = withContext(Dispatchers.IO) {
        execute("delete_file", parameters(environment, "path" to path, "recursive" to directory.toString()))
        Unit
    }

    suspend fun paste(directory: String, environment: String?) = withContext(Dispatchers.IO) {
        val item = FileBrowserClipboard.content.value ?: return@withContext
        val sourceEnv = item.environment ?: "android"
        val destinationEnv = environment ?: "android"
        val destination = directory.trimEnd('/') + "/" + item.path.substringAfterLast('/')
        check(sourceEnv != destinationEnv || (destination != item.path && !destination.startsWith(item.path.trimEnd('/') + "/"))) {
            context.getString(R.string.workspace_invalid_paste)
        }
        val parent = execute("file_exists", parameters(environment, "path" to directory)).result
        check(parent is FileExistsData && parent.exists && parent.isDirectory) {
            context.getString(R.string.workspace_error_invalid_path, directory)
        }
        requireNewDestination(destination, environment)
        if (item.cut && sourceEnv == destinationEnv) {
            execute("move_file", parameters(environment, "source" to item.path, "destination" to destination))
        } else {
            execute("copy_file", listOf(ToolParameter("source", item.path), ToolParameter("destination", destination),
                ToolParameter("source_environment", sourceEnv), ToolParameter("dest_environment", destinationEnv),
                ToolParameter("recursive", item.isDirectory.toString())))
            // 跨环境剪切在复制成功后才删除来源，失败时保留剪贴板和原文件。
            if (item.cut) delete(item.path, item.environment, item.isDirectory)
        }
        if (item.cut) FileBrowserClipboard.clear(item)
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