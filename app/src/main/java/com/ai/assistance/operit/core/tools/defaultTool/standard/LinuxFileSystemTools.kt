package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.GrepResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Linux文件系统工具类，专门处理Linux环境下的文件操作
 * 使用SSH/本地文件系统提供者来操作Linux文件系统
 */
class LinuxFileSystemTools(context: Context) : StandardFileSystemTools(context) {
    companion object {
        private const val TAG = "LinuxFileSystemTools"
    }

    // 动态获取文件系统（支持SSH切换）
    private val fs get() = getLinuxFileSystem()

    /** 列出Linux目录中的文件 */
    override suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!fs.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Directory does not exist: $path"
                )
            }

            if (!fs.isDirectory(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a directory: $path"
                )
            }

            val fileInfoList = fs.listDirectory(path)
            if (fileInfoList == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to list directory: $path"
                )
            }

            val entries = fileInfoList.map { fileInfo ->
                DirectoryListingData.FileEntry(
                    name = fileInfo.name,
                    isDirectory = fileInfo.isDirectory,
                    size = fileInfo.size,
                    permissions = fileInfo.permissions,
                    lastModified = fileInfo.lastModified
                )
            }

            AppLogger.d(TAG, "Listed ${entries.size} entries in directory $path")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = DirectoryListingData(path = path, entries = entries, env = "linux"),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error listing directory: ${e.message}"
            )
        }
    }

    /** 读取Linux文件的完整内容 */
    override suspend fun readFileFull(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val textOnly = tool.parameters.find { it.name == "text_only" }?.value?.toBoolean() ?: false
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            if (!fs.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!fs.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = path.substringAfterLast('.', "").lowercase()
            
            // 特殊文件类型处理（图片、PDF等）暂时不支持在Linux环境
            // 因为这些需要Android本地文件访问
            if (fileExt in listOf("doc", "docx", "pdf", "jpg", "jpeg", "png", "gif", "bmp")) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Special file types (images, PDF, Word) are not supported in Linux environment. Please use local environment."
                )
            }

            // 检查文件是否是文本文件（如果启用了 text_only）
            if (textOnly) {
                val sample = fs.readFileSample(path, 512)
                if (sample == null || !FileUtils.isTextLike(sample)) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Skipped non-text file: $path"
                    )
                }
            }

            val content = fs.readFile(path)
            if (content == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to read file: $path"
                )
            }

            val fileSize = fs.getFileSize(path)
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileContentData(
                    path = path,
                    content = content,
                    size = fileSize,
                    env = "linux"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file (full)", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 读取Linux二进制文件内容并返回Base64编码数据 */
    override suspend fun readFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!fs.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!fs.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val bytes = fs.readFileBytes(path)
            if (bytes == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to read file bytes: $path"
                )
            }

            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val size = fs.getFileSize(path)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = BinaryFileContentData(
                    path = path,
                    contentBase64 = base64,
                    size = size,
                    env = "linux"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading binary file", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading binary file: ${e.message}"
            )
        }
    }

    /** 检查Linux文件是否存在 */
    override suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val exists = fs.exists(path)

            if (!exists) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileExistsData(path = path, exists = false, env = "linux"),
                    error = ""
                )
            }

            val isDirectory = fs.isDirectory(path)
            val size = fs.getFileSize(path)

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileExistsData(
                    path = path,
                    exists = true,
                    isDirectory = isDirectory,
                    size = size,
                    env = "linux"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking file existence", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileExistsData(
                    path = path,
                    exists = false,
                    isDirectory = false,
                    size = 0,
                    env = "linux"
                ),
                error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** 读取Linux文件（基础版本，带大小限制） */
    override suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            if (!fs.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!fs.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = path.substringAfterLast('.', "").lowercase()

            // 特殊文件类型不支持
            if (fileExt in listOf("doc", "docx", "pdf", "jpg", "jpeg", "png", "gif", "bmp")) {
                // 对于特殊类型，先尝试读取完整文件
                return readFileFull(tool)
            }

            // 检查文件大小
            val fileSize = fs.getFileSize(path)
            val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES

            if (fileSize > maxFileSizeBytes) {
                // 文件过大，读取限制大小
                val content = fs.readFileWithLimit(path, maxFileSizeBytes.toInt())
                if (content == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read file: $path"
                    )
                }

                val truncatedMsg = "\n\n[File truncated. Size: $fileSize bytes, showing first $maxFileSizeBytes bytes]"
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = content + truncatedMsg,
                        size = fileSize,
                        env = "linux"
                    ),
                    error = ""
                )
            } else {
                // 文件大小合适，读取完整内容
                return readFileFull(tool)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 按行号范围读取Linux文件内容（行号从1开始，包括开始行和结束行） */
    override suspend fun readFilePart(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val startLineParam = tool.parameters.find { it.name == "start_line" }?.value?.toIntOrNull() ?: 1
        val endLineParam = tool.parameters.find { it.name == "end_line" }?.value?.toIntOrNull()
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!fs.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist or is not a regular file: $path"
                )
            }

            if (!fs.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            // 获取总行数
            val totalLines = fs.getLineCount(path)

            // 计算实际的行号范围（行号从1开始）
            val startLine = maxOf(1, startLineParam).coerceIn(1, maxOf(1, totalLines))
            val endLine =
                (endLineParam
                        ?: (startLine + ToolExecutionLimits.DEFAULT_FILE_READ_PART_LINES - 1))
                    .coerceIn(startLine, maxOf(1, totalLines))

            val partContent = if (totalLines > 0) {
                fs.readFileLines(path, startLine, endLine) ?: ""
            } else {
                ""
            }

            val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES
            var truncatedPartContent = partContent
            val isTruncated = truncatedPartContent.length > maxFileSizeBytes
            if (isTruncated) {
                truncatedPartContent = truncatedPartContent.substring(0, maxFileSizeBytes)
            }

            var contentWithLineNumbers = addLineNumbers(truncatedPartContent, startLine - 1, totalLines)
            if (isTruncated) {
                contentWithLineNumbers += "\n\n... (file content truncated) ..."
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = FilePartContentData(
                    path = path,
                    content = contentWithLineNumbers,
                    partIndex = 0, // 保留兼容性，但不再使用
                    totalParts = 1, // 保留兼容性，但不再使用
                    startLine = startLine - 1, // 转为0-based
                    endLine = endLine,
                    totalLines = totalLines,
                    env = "linux"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file part", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file part: ${e.message}"
            )
        }
    }

    /** 写入Linux文件 */
    override suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write",
                    env = "linux",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val result = fs.writeFile(path, content, append)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = if (append) "append" else "write",
                        env = "linux",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            val operation = if (append) "append" else "write"
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = operation,
                    env = "linux",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing to file", e)
            val errorMessage = "Error writing to file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = if (append) "append" else "write",
                    env = "linux",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 写入二进制文件到Linux */
    override suspend fun writeFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write_binary",
                    env = "linux",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            // 解码base64内容
            val bytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            val result = fs.writeFileBytes(path, bytes)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "write_binary",
                        env = "linux",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "write_binary",
                    env = "linux",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing binary file", e)
            val errorMessage = "Error writing binary file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write_binary",
                    env = "linux",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 删除Linux文件或目录 */
    override suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    env = "linux",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!fs.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "delete",
                        env = "linux",
                        path = path,
                        successful = false,
                        details = "Path does not exist: $path"
                    ),
                    error = "Path does not exist: $path"
                )
            }

            val result = fs.delete(path, recursive)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "delete",
                        env = "linux",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "delete",
                    env = "linux",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting file", e)
            val errorMessage = "Error deleting file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    env = "linux",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 移动/重命名Linux文件 */
    override suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        PathValidator.validateLinuxPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateLinuxPath(destPath, tool.name, "destination")?.let { return it }

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    env = "linux",
                    path = sourcePath,
                    successful = false,
                    details = "Both sourcePath and destPath parameters are required"
                ),
                error = "Both sourcePath and destPath parameters are required"
            )
        }

        return try {
            if (!fs.exists(sourcePath)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "move",
                        env = "linux",
                        path = sourcePath,
                        successful = false,
                        details = "Source path does not exist: $sourcePath"
                    ),
                    error = "Source path does not exist: $sourcePath"
                )
            }

            val result = fs.move(sourcePath, destPath)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "move",
                        env = "linux",
                        path = sourcePath,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "move",
                    env = "linux",
                    path = sourcePath,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error moving file", e)
            val errorMessage = "Error moving file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    env = "linux",
                    path = sourcePath,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 复制Linux文件或目录 */
    override suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true
        PathValidator.validateLinuxPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateLinuxPath(destPath, tool.name, "destination")?.let { return it }

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    env = "linux",
                    path = sourcePath,
                    successful = false,
                    details = "Both sourcePath and destPath parameters are required"
                ),
                error = "Both sourcePath and destPath parameters are required"
            )
        }

        return try {
            val result = fs.copy(sourcePath, destPath, recursive)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        env = "linux",
                        path = sourcePath,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "copy",
                    env = "linux",
                    path = sourcePath,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying file", e)
            val errorMessage = "Error copying file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    env = "linux",
                    path = sourcePath,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 创建Linux目录 */
    override suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val createParents = tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean() ?: false
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "mkdir",
                    env = "linux",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val result = fs.createDirectory(path, createParents)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "mkdir",
                        env = "linux",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "mkdir",
                    env = "linux",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating directory", e)
            val errorMessage = "Error creating directory: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "mkdir",
                    env = "linux",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 在Linux文件系统中查找文件 */
    override suspend fun findFiles(tool: AITool): ToolResult {
        val basePath = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        PathValidator.validateLinuxPath(basePath, tool.name, "path")?.let { return it }

        if (basePath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(path = basePath, pattern = pattern, files = emptyList(), env = "linux"),
                error = "basePath parameter is required"
            )
        }

        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(path = basePath, pattern = pattern, files = emptyList(), env = "linux"),
                error = "pattern parameter is required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, -1f, "Searching...")
            if (!fs.exists(basePath)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FindFilesResultData(path = basePath, pattern = pattern, files = emptyList(), env = "linux"),
                    error = "Base path does not exist: $basePath"
                )
            }

            if (!fs.isDirectory(basePath)) {
                val fileName = basePath.substringAfterLast('/')
                val regex = globToRegex(pattern, caseInsensitive = false)
                val files = if (regex.matches(fileName)) listOf(basePath) else emptyList()

                ToolProgressBus.update(tool.name, 1f, "Search completed, found ${files.size}")

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FindFilesResultData(path = basePath, pattern = pattern, files = files, env = "linux"),
                    error = ""
                )
            }

            val files = fs.findFiles(
                basePath = basePath,
                pattern = pattern,
                maxDepth = -1,
                caseInsensitive = false
            )

            ToolProgressBus.update(tool.name, 1f, "Search completed, found ${files.size}")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FindFilesResultData(path = basePath, pattern = pattern, files = files, env = "linux"),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error finding files", e)
            ToolProgressBus.update(tool.name, 1f, "Search failed")
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(path = basePath, pattern = pattern, files = emptyList(), env = "linux"),
                error = "Error finding files: ${e.message}"
            )
        }
    }

    /** 获取Linux文件信息 */
    override suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileInfoData(
                    path = "",
                    exists = false,
                    fileType = "",
                    size = 0,
                    permissions = "",
                    owner = "",
                    group = "",
                    lastModified = "",
                    rawStatOutput = "",
                    env = "linux"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val fileInfo = fs.getFileInfo(path)
            if (fileInfo == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileInfoData(
                        path = path,
                        exists = false,
                        fileType = "",
                        size = 0,
                        permissions = "",
                        owner = "",
                        group = "",
                        lastModified = "",
                        rawStatOutput = "",
                        env = "linux"
                    ),
                    error = "File does not exist: $path"
                )
            }

            val fileType = if (fileInfo.isDirectory) "directory" else "file"
            val rawInfo = buildString {
                appendLine("File: $path")
                appendLine("Size: ${fileInfo.size} bytes")
                appendLine("Type: $fileType")
                appendLine("Permissions: ${fileInfo.permissions}")
                appendLine("Last Modified: ${fileInfo.lastModified}")
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileInfoData(
                    path = path,
                    exists = true,
                    fileType = fileType,
                    size = fileInfo.size,
                    permissions = fileInfo.permissions,
                    owner = "",
                    group = "",
                    lastModified = fileInfo.lastModified,
                    rawStatOutput = rawInfo,
                    env = "linux"
                ),
                error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file info", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileInfoData(
                    path = path,
                    exists = false,
                    fileType = "",
                    size = 0,
                    permissions = "",
                    owner = "",
                    group = "",
                    lastModified = "",
                    rawStatOutput = "",
                    env = "linux"
                ),
                error = "Error getting file info: ${e.message}"
            )
        }
    }

    /** 打开Linux文件（暂不支持） */
    override suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = "Opening files is not supported in Linux environment. Use readFile instead."
        )
    }

    /** 在Linux代码中搜索（grep） */
    override suspend fun grepCode(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
        val caseInsensitive =
            tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
        val contextLines = tool.parameters.find { it.name == "context_lines" }?.value?.toIntOrNull() ?: 3
        val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 100
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Pattern parameter is required"
            )
        }

        return grepCodeWithNativeRipgrep(
            toolName = tool.name,
            path = path,
            pattern = pattern,
            filePattern = filePattern,
            caseInsensitive = caseInsensitive,
            contextLines = contextLines,
            maxResults = maxResults,
            envLabel = "linux"
        )
    }

    /** Linux上下文搜索 - 基于意图字符串查找相关文件或文件内的相关代码段 */
    override suspend fun grepContext(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val intent = tool.parameters.find { it.name == "intent" }?.value ?: ""
        val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
        val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 10
        
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (intent.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Intent parameter is required"
            )
        }

        val isFile = fs.isFile(path)

        if (isFile) {
            val parent = path.substringBeforeLast('/', "")
            val fileName = path.substringAfterLast('/')
            val searchPath = if (parent.isNotBlank()) parent else "/"
            return grepContextAgentic(
                toolName = tool.name,
                displayPath = path,
                searchPath = searchPath,
                environment = "linux",
                intent = intent,
                filePattern = fileName,
                maxResults = maxResults,
                envLabel = "linux"
            )
        }

        return grepContextAgentic(
            toolName = tool.name,
            displayPath = path,
            searchPath = path,
            environment = "linux",
            intent = intent,
            filePattern = filePattern,
            maxResults = maxResults,
            envLabel = "linux"
        )
    }

    /** 分享Linux文件（暂不支持） */
    override suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateLinuxPath(path, tool.name)?.let { return it }

        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = "File sharing is not supported in Linux environment"
        )
    }
}
