package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class ImportedMemoryDocument(val title: String, val uri: String, val content: String)

/** 保留现有文本与二进制文档读取能力，临时副本使用独立文件并在读取后清理。 */
internal suspend fun readMemoryDocument(context: Context, uri: Uri): ImportedMemoryDocument = withContext(Dispatchers.IO) {
    val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else uri.lastPathSegment.orEmpty()
    } ?: uri.lastPathSegment.orEmpty()
    val mime = context.contentResolver.getType(uri)
    val content = if (mime?.startsWith("text/") == true || title.endsWith(".md", true) || title.endsWith(".txt", true)) {
        checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
    } else {
        val suffix = title.substringAfterLast('.', "bin").take(16)
        val temporary = File.createTempFile("memory-import-", ".$suffix", context.cacheDir)
        try {
            checkNotNull(context.contentResolver.openInputStream(uri)).use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val result = AIToolHandler.getInstance(context).executeTool(AITool(name = "read_file_full", parameters = listOf(ToolParameter("path", temporary.absolutePath))))
            check(result.success) { result.error.orEmpty() }
            val data = result.result
            if (data is StringResultData) data.value else data.toString()
        } finally { temporary.delete() }
    }
    require(content.isNotBlank()) { context.getString(com.ai.assistance.operit.R.string.memory_workbench_field_required) }
    ImportedMemoryDocument(title, uri.toString(), content)
}