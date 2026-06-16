package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilityFileSystemTools
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.exp
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.util.AndroidUserPathUtils
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** 调试者级别的文件系统工具，继承无障碍版本 */
open class DebuggerFileSystemTools(context: Context) : AccessibilityFileSystemTools(context) {
    companion object {
        private const val TAG = "DebuggerFileSystemTools"
        internal fun isOperitInternalPath(path: String): Boolean {
            val normalizedPath = path.trim()
            val appPackage = BuildConfig.APPLICATION_ID
            return normalizedPath.startsWith("/data/data/$appPackage") ||
                AndroidUserPathUtils.isCurrentUserPackageDataPath(normalizedPath, appPackage)
        }
    }
    
    /**
     * 判断路径是否为Operit应用的内部存储路径
     * 仅针对 Operit 应用自身数据目录路径返回true
     */
    protected fun isOperitInternalPath(path: String): Boolean {
        return Companion.isOperitInternalPath(path)
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun smoothProgress(count: Int, scale: Float): Float {
        if (count <= 0) return 0f
        return (1f - exp(-count / scale)).coerceIn(0f, 0.99f)
    }

    private fun updateProgressFloor(progressFloor: AtomicInteger, candidate: Int) {
        while (true) {
            val old = progressFloor.get()
            if (candidate <= old) return
            if (progressFloor.compareAndSet(old, candidate)) return
        }
    }

    /** List files in a directory */
    override suspend fun listFiles(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.listFiles(tool)
        }
        if (isSafEnvironment(environment)) {
            return super.listFiles(tool)
        }

        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super（AccessibilityFileSystemTools）的高权限方法
        if (isOperitInternalPath(path)) {
            return super.listFiles(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            // 确保目录路径末尾有斜杠
            val normalizedPath = if (path.endsWith("/")) path else "$path/"

            // 使用ls -la命令获取详细的文件列表
            AppLogger.d(TAG, "Using ls -la command for path: $normalizedPath")
            val listResult = AndroidShellExecutor.executeShellCommand("ls -la '$normalizedPath'")

            if (listResult.success) {
                AppLogger.d(TAG, "ls -la command output: ${listResult.stdout}")

                // 解析ls -la命令输出
                val entries = parseDetailedDirectoryListing(listResult.stdout, normalizedPath)

                AppLogger.d(TAG, "Parsed ${entries.size} entries from ls -la output")

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = DirectoryListingData(path, entries),
                        error = ""
                )
            } else {
                AppLogger.w(TAG, "ls -la command failed: ${listResult.stderr}")

                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to list directory: ${listResult.stderr}"
                )
            }
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

    /** Parse the output of the ls -la command into structured data */
    protected fun parseDetailedDirectoryListing(
            output: String,
            path: String
    ): List<DirectoryListingData.FileEntry> {
        val lines = output.trim().split("\n")
        val entries = mutableListOf<DirectoryListingData.FileEntry>()

        AppLogger.d(TAG, "Parsing ${lines.size} lines from ls -la output")

        // 跳过第一行总计行
        val startIndex = if (lines.isNotEmpty() && lines[0].startsWith("total")) 1 else 0

        // 日期格式化器，用于解析日期时间字符串
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)

        for (i in startIndex until lines.size) {
            try {
                val line = lines[i]
                if (line.isBlank()) continue

                // 打印每一行以便调试
                AppLogger.d(TAG, "Parsing line: $line")

                // Android上ls -la输出格式: crwxrw--- 2 u0_a425 media_rw 4056 2025-03-14
                // 06:04 Android
                // 符号链接格式: lrwxrwxrwx 1 root root 12 2025-03-14 06:04 filename ->
                // /path/to/target

                // 使用正则表达式解析Android上的ls -la输出
                val androidRegex =
                        """^(\S+)\s+(\d+)\s+(\S+\s*\S*)\s+(\S+)\s+(\d+)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(.+)$""".toRegex()
                val androidMatch = androidRegex.find(line)

                if (androidMatch != null) {
                    // 特定于Android的格式解析
                    val permissions = androidMatch.groupValues[1]
                    val size = androidMatch.groupValues[5].toLongOrNull() ?: 0
                    val date = androidMatch.groupValues[6]
                    val time = androidMatch.groupValues[7]
                    var name = androidMatch.groupValues[8]
                    val isDirectory = permissions.startsWith("d") || permissions.startsWith("c")
                    val isSymlink = permissions.startsWith("l")

                    // 处理符号链接格式 "name -> target"
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                        AppLogger.d(TAG, "Found symlink: $name")
                    }

                    // 跳过 . 和 .. 条目
                    if (name == "." || name == "..") continue

                    // 将日期和时间转换为时间戳
                    val dateTimeStr = "$date $time"
                    val timestamp =
                            try {
                                val parsedDate = dateFormat.parse(dateTimeStr)
                                parsedDate?.time?.toString() ?: "0"
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Error parsing date: $dateTimeStr", e)
                                "0" // 解析失败时使用默认时间戳
                            }

                    AppLogger.d(
                            TAG,
                            "Successfully parsed $name with date $dateTimeStr -> timestamp $timestamp"
                    )

                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    size = size,
                                    permissions = permissions,
                                    lastModified = timestamp // 使用时间戳字符串
                            )
                    )
                    continue
                }

                // 如果Android特定格式不匹配，尝试通用格式
                val genericRegex =
                        """^([\-ld][\w-]{9})\s+(\d+)\s+(\w+)\s+(\w+)\s+(\d+)\s+([\w\d\s\-:\.]+)\s+(.+)$""".toRegex()
                val match = genericRegex.find(line)

                if (match != null) {
                    val permissions = match.groupValues[1]
                    val size = match.groupValues[5].toLongOrNull() ?: 0
                    val dateTimeStr = match.groupValues[6].trim()
                    var name = match.groupValues[7]
                    val isDirectory = permissions.startsWith("d")
                    val isSymlink = permissions.startsWith("l")

                    // 处理符号链接格式 "name -> target"
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                        AppLogger.d(TAG, "Found symlink (generic): $name")
                    }

                    // 跳过 . 和 .. 条目
                    if (name == "." || name == "..") continue

                    // 尝试解析通用格式的日期时间
                    val timestamp =
                            try {
                                if (dateTimeStr.matches(
                                                """^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}$""".toRegex()
                                        )
                                ) {
                                    val parsedDate = dateFormat.parse(dateTimeStr)
                                    parsedDate?.time?.toString() ?: "0"
                                } else {
                                    // 如果不是YYYY-MM-DD HH:MM格式，返回当前时间
                                    System.currentTimeMillis().toString()
                                }
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Error parsing generic date: $dateTimeStr", e)
                                "0"
                            }

                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    size = size,
                                    permissions = permissions,
                                    lastModified = timestamp
                            )
                    )
                } else {
                    // 如果标准正则表达式也不匹配，使用更宽松的解析方法
                    // 权限字段始终是10个字符
                    if (line.length < 10) continue

                    val permissions = line.substring(0, 10).trim()
                    val isDirectory = permissions.startsWith("d") || permissions.startsWith("c")

                    // 解析剩余部分
                    val parts = line.substring(10).trim().split("\\s+".toRegex())

                    if (parts.size < 6) {
                        AppLogger.w(TAG, "Invalid ls -la format: $line")
                        continue
                    }

                    // 查找日期部分 - Android上通常是YYYY-MM-DD格式
                    val dateIndex =
                            parts.indexOfFirst { it.matches("""^\d{4}-\d{2}-\d{2}$""".toRegex()) }

                    if (dateIndex < 0 || dateIndex + 1 >= parts.size) {
                        AppLogger.w(TAG, "Cannot find date in line: $line")
                        continue
                    }

                    // 日期后面的字段通常是时间 (HH:MM)
                    val timeIndex = dateIndex + 1

                    // 时间后面的所有内容都是文件名
                    val nameStartIndex = timeIndex + 1
                    if (nameStartIndex >= parts.size) {
                        AppLogger.w(TAG, "Cannot find filename position: $line")
                        continue
                    }

                    var name = parts.subList(nameStartIndex, parts.size).joinToString(" ")
                    val isSymlink = permissions.startsWith("l")

                    // 处理符号链接格式 "name -> target"
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                        AppLogger.d(TAG, "Found symlink (fallback): $name")
                    }

                    // 跳过 . 和 .. 条目
                    if (name == "." || name == "..") continue

                    // 文件大小通常在用户和组之后，日期之前
                    val sizeIndex = dateIndex - 1
                    val size = if (sizeIndex >= 0) parts[sizeIndex].toLongOrNull() ?: 0 else 0

                    // 组合日期和时间，并转换为时间戳
                    val dateTimeStr = "${parts[dateIndex]} ${parts[timeIndex]}"
                    val timestamp =
                            try {
                                val parsedDate = dateFormat.parse(dateTimeStr)
                                parsedDate?.time?.toString() ?: "0"
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Error parsing fallback date: $dateTimeStr", e)
                                "0"
                            }

                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    size = size,
                                    permissions = permissions,
                                    lastModified = timestamp
                            )
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error parsing directory entry: ${lines[i]}", e)
                // 跳过这一行但继续处理其他行
            }
        }

        return entries
    }

    /** 将八进制权限格式转换为字符串表示 (如 "rwxr-xr-x") */
    protected fun convertOctalPermToString(octalPerm: String): String {
        try {
            val permInt = octalPerm.toInt(8)
            val permChars = CharArray(9)

            // 所有者权限
            permChars[0] = if (permInt and 0x100 != 0) 'r' else '-'
            permChars[1] = if (permInt and 0x80 != 0) 'w' else '-'
            permChars[2] = if (permInt and 0x40 != 0) 'x' else '-'

            // 组权限
            permChars[3] = if (permInt and 0x20 != 0) 'r' else '-'
            permChars[4] = if (permInt and 0x10 != 0) 'w' else '-'
            permChars[5] = if (permInt and 0x8 != 0) 'x' else '-'

            // 其他用户权限
            permChars[6] = if (permInt and 0x4 != 0) 'r' else '-'
            permChars[7] = if (permInt and 0x2 != 0) 'w' else '-'
            permChars[8] = if (permInt and 0x1 != 0) 'x' else '-'

            return String(permChars)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting octal permission: $octalPerm", e)
            return "???"
        }
    }

    /**
     * Reads the full content of a file as a new tool, handling different file types.
     * This function does not enforce a size limit.
     */
    override suspend fun readFileFull(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.readFileFull(tool)
        }
        if (isSafEnvironment(environment)) {
            return super.readFileFull(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val textOnly = tool.parameters.find { it.name == "text_only" }?.value?.toBoolean() ?: false
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.readFileFull(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }
        
        try {
            // First check if the file exists using shell command
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist: $path"
                )
            }

            // Check file extension
            val fileExt = path.substringAfterLast('.', "").lowercase()

            // Handle special file types by calling the parent's handler
            val specialReadResult = super.handleSpecialFileRead(tool, path, fileExt)
            if (specialReadResult != null) {
                // If the parent handled it, return its result.
                // But if it failed, we might want to fall back to shell `cat` for some types.
                 if (specialReadResult.success) {
                    return specialReadResult
                }
                 // Optional: Could add fallback logic here if superclass fails for some reason
            }

            // Check if file is text-like by reading first few bytes (if text_only is enabled)
            if (textOnly) {
                // First, get a sample of the file
                val sampleResult = AndroidShellExecutor.executeShellCommand("head -c 512 '$path'")
                if (!sampleResult.success) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to sample file: ${sampleResult.stderr}"
                    )
                }

                // Analyze the sample bytes
                val sampleBytes = sampleResult.stdout.toByteArray()
                if (!FileUtils.isTextLike(sampleBytes)) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Skipped non-text file: $path"
                    )
                }
            }

            // For text-like files, use shell `cat` to read full content
            val result = AndroidShellExecutor.executeShellCommand("cat '$path'")
            if (result.success) {
                val sizeResult =
                    AndroidShellExecutor.executeShellCommand("stat -c %s '$path'")
                val size =
                        sizeResult.stdout.trim().toLongOrNull()
                                ?: result.stdout.length.toLong()

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileContentData(
                                        path = path,
                                        content = result.stdout,
                                        size = size
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read file: ${result.stderr}"
                )
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

    /** Read file content */
    override suspend fun readFile(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.readFile(tool)
        }
        if (isSafEnvironment(environment)) {
            return super.readFile(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.readFile(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        try {
            val fileExt = path.substringAfterLast('.', "").lowercase()

            // For special types, full read then truncate text is the only way.
            if (isSpecialFileType(fileExt)) {
                val fullResult = readFileFull(tool)
                if (!fullResult.success) return fullResult

                val contentData = fullResult.result as FileContentData
                var content = contentData.content
                val isTruncated = content.length > ToolExecutionLimits.MAX_FILE_READ_BYTES
                if (isTruncated) {
                    content = content.substring(0, ToolExecutionLimits.MAX_FILE_READ_BYTES)
                }

                var contentWithLineNumbers = addLineNumbers(content)
                if (isTruncated) {
                    contentWithLineNumbers += "\n\n... (file content truncated) ..."
                }

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(path = path, content = contentWithLineNumbers, size = contentWithLineNumbers.length.toLong()),
                    error = ""
                )
            }

            // For text-based files, read only the beginning.
            // Check if file is text-like by analyzing a sample
            val sampleResult = AndroidShellExecutor.executeShellCommand("head -c 512 '$path'")
            if (sampleResult.success) {
                val sampleBytes = sampleResult.stdout.toByteArray()
                if (!FileUtils.isTextLike(sampleBytes)) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not appear to be a text file. Use readFileFull tool for special file types."
                    )
                }
            }

            // Check file size to see if truncation is needed
            val sizeResult = AndroidShellExecutor.executeShellCommand("stat -c %s '$path'")
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
            val truncated = size > ToolExecutionLimits.MAX_FILE_READ_BYTES

            val readCommand = "head -c ${ToolExecutionLimits.MAX_FILE_READ_BYTES} '$path'"
            val readResult = AndroidShellExecutor.executeShellCommand(readCommand)

            if (!readResult.success) {
                 return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read file: ${readResult.stderr}"
                )
            }

            var content = readResult.stdout
            var contentWithLineNumbers = addLineNumbers(content)
            if (truncated) {
                contentWithLineNumbers += "\n\n... (file content truncated) ..."
            }

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileContentData(
                                    path = path,
                                    content = contentWithLineNumbers,
                                    size = contentWithLineNumbers.length.toLong()
                            ),
                    error = ""
            )
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

    /** 按行号范围读取文件内容（行号从1开始，包括开始行和结束行） */
    override suspend fun readFilePart(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.readFilePart(tool)
        }
        if (isSafEnvironment(environment)) {
            return super.readFilePart(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.readFilePart(tool)
        }
        val startLineParam = tool.parameters.find { it.name == "start_line" }?.value?.toIntOrNull() ?: 1
        val endLineParam = tool.parameters.find { it.name == "end_line" }?.value?.toIntOrNull()

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            // 0. 特殊文件类型检查
            // 如果是Word/PDF/图片等特殊文件，使用父类（StandardFileSystemTools）的逻辑处理
            // 因为Shell命令(cat/sed)无法正确解析这些二进制格式
            val fileExt = path.substringAfterLast('.', "").lowercase()
            if (isSpecialFileType(fileExt)) {
                 return super.readFilePart(tool)
            }

            // 1. Check if file exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist: $path"
                )
            }

            // 2. Get total number of lines
            val wcResult = AndroidShellExecutor.executeShellCommand("cat '$path' | wc -l")
            if (!wcResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to count lines in file: ${wcResult.stderr}"
                )
            }

            val totalLines = wcResult.stdout.trim().split(" ")[0].toIntOrNull() ?: 0

            // 3. 计算实际的行号范围（行号从1开始）
            val startLine = maxOf(1, startLineParam).coerceIn(1, maxOf(1, totalLines))
            val endLine =
                (endLineParam
                        ?: (startLine + ToolExecutionLimits.DEFAULT_FILE_READ_PART_LINES - 1))
                    .coerceIn(startLine, maxOf(1, totalLines))

            if (totalLines == 0 || startLine > endLine) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FilePartContentData(
                                        path = path,
                                        content = "",
                                        partIndex = 0, // 保留兼容性，但不再使用
                                        totalParts = 1, // 保留兼容性，但不再使用
                                        startLine = startLine - 1, // 转为0-based
                                        endLine = endLine,
                                        totalLines = totalLines
                                ),
                        error = ""
                )
            }

            // 4. Extract the specific part using sed
            val sedCommand = "sed -n '${startLine},${endLine}p' '$path'"
            val partResult = AndroidShellExecutor.executeShellCommand(sedCommand)

            if (!partResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read file part: ${partResult.stderr}"
                )
            }

            val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES
            var content = partResult.stdout
            val isTruncated = content.length > maxFileSizeBytes
            if (isTruncated) {
                content = content.substring(0, maxFileSizeBytes)
            }

            var contentWithLineNumbers = addLineNumbers(content, startLine, totalLines)
            if (isTruncated) {
                contentWithLineNumbers += "\n\n... (file content truncated) ..."
            }

            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FilePartContentData(
                                    path = path,
                                    content = contentWithLineNumbers.trimEnd(),
                                    partIndex = 0, // 保留兼容性，但不再使用
                                    totalParts = 1, // 保留兼容性，但不再使用
                                    startLine = startLine - 1, // To 0-indexed for response
                                    endLine = endLine,
                                    totalLines = totalLines
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading file part", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading file part: ${e.message}"
            )
        }
    }

    /**
     * 重写特殊文件读取逻辑
     * 对于受保护的路径（如 /Android/data），先通过Shell命令复制到缓存目录，
     * 再调用父类的解析逻辑。
     */
    override suspend fun handleSpecialFileRead(
        tool: AITool,
        path: String,
        fileExt: String
    ): ToolResult? {
        val file = File(path)

        if (isOperitInternalPath(path)) {
            return super.handleSpecialFileRead(tool, path, fileExt)
        }
        
        // 如果文件可读，直接使用父类逻辑（更高效）
        if (file.exists() && file.canRead()) {
            return super.handleSpecialFileRead(tool, path, fileExt)
        }

        AppLogger.d(TAG, "File not directly readable (permission restricted), trying Shell copy for: $path")
        
        // 创建临时文件用于中转
        val tempFile = File(context.cacheDir, "shell_copy_${System.currentTimeMillis()}.$fileExt")
        
        return try {
            // 使用cat命令复制文件内容
            // 注意：使用cat而不是cp，因为cp可能保留权限属性导致仍然无法读取
            val copyResult = AndroidShellExecutor.executeShellCommand("cat '$path' > '${tempFile.absolutePath}'")
            
            if (!copyResult.success) {
                AppLogger.w(TAG, "Shell copy failed: ${copyResult.stderr}")
                // 复制失败，回退到父类逻辑（虽然很可能也失败，但能返回一致的错误信息）
                return super.handleSpecialFileRead(tool, path, fileExt)
            }
            
            // 检查临时文件是否有效
            if (!tempFile.exists() || tempFile.length() == 0L) {
                AppLogger.w(TAG, "Temp file is empty or does not exist after copy")
                return super.handleSpecialFileRead(tool, path, fileExt)
            }

            // 使用临时文件路径调用父类处理逻辑
            val tempToolResult = super.handleSpecialFileRead(tool, tempFile.absolutePath, fileExt)
            
            // 如果处理成功，修正返回结果中的 path 为原始路径
            if (tempToolResult != null && tempToolResult.success) {
                val resultData = tempToolResult.result
                if (resultData is FileContentData) {
                    return tempToolResult.copy(
                        result = resultData.copy(path = path)
                    )
                }
            }
            
            tempToolResult
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in shell copy strategy", e)
            super.handleSpecialFileRead(tool, path, fileExt)
        } finally {
            // 清理临时文件
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to clean up temp file", e)
            }
        }
    }

    /** Write content to a file */
    override suspend fun writeFile(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.writeFile(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        if (isSafEnvironment(environment)) {
            return super.writeFile(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.writeFile(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "write",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            // 确保目标目录存在
            val directory = File(path).parent
            if (directory != null) {
                val mkdirResult = AndroidShellExecutor.executeShellCommand("mkdir -p '$directory'")
                if (!mkdirResult.success) {
                    AppLogger.w(TAG, "Warning: Failed to create parent directory: ${mkdirResult.stderr}")
                }
            }

            // 直接使用echo命令写入内容
            // 对内容进行base64编码，避免特殊字符问题
            val contentBase64 =
                    android.util.Base64.encodeToString(
                            content.toByteArray(),
                            android.util.Base64.NO_WRAP
                    )

            // 使用两种写入方法中的一种:
            // 方法1: 使用base64命令解码并写入文件（大内容时分块，避免命令行过长）
            val redirectOperator = if (append) ">>" else ">"
            val maxInlineBase64 = 32768
            val base64ChunkSize = 16384 // 4的倍数，保证base64解码边界正确
            val writeResult =
                    if (contentBase64.length <= maxInlineBase64) {
                        AndroidShellExecutor.executeShellCommand(
                                "echo '$contentBase64' | base64 -d $redirectOperator '$path'"
                        )
                    } else {
                        var chunkSuccess = true
                        var firstChunk = true
                        var index = 0
                        while (index < contentBase64.length) {
                            val end = (index + base64ChunkSize).coerceAtMost(contentBase64.length)
                            val chunk = contentBase64.substring(index, end)
                            val chunkRedirect = if (firstChunk) redirectOperator else ">>"
                            val chunkResult =
                                    AndroidShellExecutor.executeShellCommand(
                                            "echo '$chunk' | base64 -d $chunkRedirect '$path'"
                                    )
                            if (!chunkResult.success) {
                                AppLogger.e(
                                        TAG,
                                        "Failed to write base64 chunk: ${chunkResult.stderr}"
                                )
                                chunkSuccess = false
                                break
                            }
                            firstChunk = false
                            index = end
                        }
                        if (chunkSuccess) {
                            AndroidShellExecutor.CommandResult(true, "", "", 0)
                        } else {
                            AndroidShellExecutor.CommandResult(false, "", "Failed to write base64 chunks")
                        }
                    }

            if (!writeResult.success) {
                AppLogger.e(TAG, "Failed to write with base64 method: ${writeResult.stderr}")
                if (content.length > maxInlineBase64) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = if (append) "append" else "write",
                                            path = path,
                                            successful = false,
                                            details =
                                                    "Failed to write to file: ${writeResult.stderr}"
                                    ),
                            error = "Failed to write to file: ${writeResult.stderr}"
                    )
                }
                // 方法2: 尝试直接写入，无需base64（仅适用于较小内容）
                val fallbackResult =
                        AndroidShellExecutor.executeShellCommand(
                                "printf '%s' '$content' $redirectOperator '$path'"
                        )
                if (!fallbackResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = if (append) "append" else "write",
                                            path = path,
                                            successful = false,
                                            details =
                                                    "Failed to write to file: ${fallbackResult.stderr}"
                                    ),
                            error = "Failed to write to file: ${fallbackResult.stderr}"
                    )
                }
            }

            // 验证写入是否成功
            val verifyResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (verifyResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = if (append) "append" else "write",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Write command completed but file does not exist. Possible permission issue."
                                ),
                        error =
                                "Write command completed but file does not exist. Possible permission issue."
                )
            }

            // 检查文件大小确认内容被写入
            val sizeResult =
                    AndroidShellExecutor.executeShellCommand(
                            "stat -c %s '$path' 2>/dev/null || echo '0'"
                    )
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
            if (size == 0L && content.isNotEmpty()) {
                // 文件存在但是大小为0，可能写入失败
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = if (append) "append" else "write",
                                        path = path,
                                        successful = false,
                                        details =
                                                "File was created but appears to be empty. Possible write failure."
                                ),
                        error = "File was created but appears to be empty. Possible write failure."
                )
            }

            val operation = if (append) "append" else "write"
            val details = if (append) "Content appended to $path" else "Content written to $path"

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileOperationData(
                                    operation = operation,
                                    path = path,
                                    successful = true,
                                    details = details
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing to file", e)

            // 提供更具体的错误信息
            val errorMessage =
                    when {
                        e is InterruptedException ||
                                e.message?.contains("interrupted", ignoreCase = true) == true ->
                                "ADB connection interrupted, possibly due to network instability. Please check ADB connection and retry. Error details: ${e.message}"
                        e is java.net.SocketException ||
                                e.message?.contains("socket", ignoreCase = true) == true ->
                                "ADB network connection exception, please check if device is still connected and retry. Error details: ${e.message}"
                        e is java.io.IOException -> "File IO error: ${e.message}. Please check if the file path has write permission."
                        e.message?.contains("permission", ignoreCase = true) == true ->
                                "Permission denied, cannot write to file: ${e.message}. Please check if the app has appropriate permissions."
                        else -> "Error writing to file: ${e.message}"
                    }

            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = if (append) "append" else "write",
                                    path = path,
                                    successful = false,
                                    details = errorMessage
                            ),
                    error = errorMessage
            )
        }
    }

    /** Delete a file or directory */
    override suspend fun deleteFile(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.deleteFile(tool)
        }

        if (isSafEnvironment(environment)) {
            return super.deleteFile(tool)
        }

        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.deleteFile(tool)
        }
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }


        return try {
            val quotedPath = shQuote(path)
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e $quotedPath && echo 'exists' || echo 'missing'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                ToolProgressBus.update(tool.name, 1f, "Delete failed")
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = false,
                                        details = "File or directory does not exist: $path"
                                ),
                        error = "File or directory does not exist: $path"
                )
            }

            val typeResult =
                    AndroidShellExecutor.executeShellCommand(
                            "if test -d $quotedPath; then echo 'directory'; elif test -f $quotedPath; then echo 'file'; else echo 'other'; fi"
                    )
            val isDirectory = typeResult.stdout.trim() == "directory"

            ToolProgressBus.update(
                    tool.name,
                    0.1f,
                    if (isDirectory) "Deleting directory..." else "Deleting file..."
            )

            val deleteCommand =
                    if (recursive) "rm -rf $quotedPath" else if (isDirectory) "rmdir $quotedPath" else "rm -f $quotedPath"
            val result = AndroidShellExecutor.executeShellCommand(deleteCommand)

            if (result.success && isDirectory && recursive) {
                ToolProgressBus.update(tool.name, 0.85f, "Finalizing directory deletion...")
                AndroidShellExecutor.executeShellCommand("rmdir $quotedPath")
            }

            val verifyDeletedResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e $quotedPath && echo 'exists' || echo 'missing'"
                    )
            val deleted = verifyDeletedResult.stdout.trim() != "exists"

            if (result.success && deleted) {
                ToolProgressBus.update(tool.name, 1f, "Delete completed")
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = true,
                                        details = "Successfully deleted $path"
                                ),
                        error = ""
                )
            } else {
                val errorMessage =
                        when {
                            !result.success && result.stderr.isNotBlank() ->
                                    "Failed to delete: ${result.stderr}"
                            !deleted -> "Failed to delete: target still exists after delete command"
                            else -> "Failed to delete: unknown error"
                        }
                ToolProgressBus.update(tool.name, 1f, "Delete failed")
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = false,
                                        details = errorMessage
                                ),
                        error = errorMessage
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting file/directory", e)
            ToolProgressBus.update(tool.name, 1f, "Delete failed")
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = path,
                                    successful = false,
                                    details = "Error deleting file/directory: ${e.message}"
                            ),
                    error = "Error deleting file/directory: ${e.message}"
            )
        }
    }

    /** Check if a file or directory exists */
    override suspend fun fileExists(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.fileExists(tool)
        }

        if (isSafEnvironment(environment)) {
            return super.fileExists(tool)
        }

        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.fileExists(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            // Check if the path exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$path' && echo 'exists' || echo 'not exists'"
                    )
            val exists = existsResult.success && existsResult.stdout.trim() == "exists"

            if (!exists) {
                // If it doesn't exist, return a simple FileExistsData with
                // exists=false
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileExistsData(path = path, exists = false),
                        error = ""
                )
            }

            // If it exists, check if it's a directory
            val isDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d '$path' && echo 'true' || echo 'false'"
                    )
            val isDirectory = isDirResult.success && isDirResult.stdout.trim() == "true"

            // Get the size
            val sizeResult =
                    AndroidShellExecutor.executeShellCommand(
                            "stat -c %s '$path' 2>/dev/null || echo '0'"
                    )
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileExistsData(
                                    path = path,
                                    exists = true,
                                    isDirectory = isDirectory,
                                    size = size
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking file existence", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileExistsData(
                                    path = path,
                                    exists = false,
                                    isDirectory = false,
                                    size = 0
                            ),
                    error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** Move or rename a file or directory */
    override suspend fun moveFile(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.moveFile(tool)
        }
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        if (isSafEnvironment(environment)) {
            return super.moveFile(tool)
        }
        PathValidator.validateAndroidPath(sourcePath, tool.name)?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name)?.let { return it }
        
        // 如果源文件或目标文件在Operit内部存储，使用super的高权限方法
        if (isOperitInternalPath(sourcePath) || isOperitInternalPath(destPath)) {
            return super.moveFile(tool)
        }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Source and destination parameters are required"
                            ),
                    error = "Source and destination parameters are required"
            )
        }


        return try {
            val result = AndroidShellExecutor.executeShellCommand("mv '$sourcePath' '$destPath'")

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully moved $sourcePath to $destPath"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Failed to move file: ${result.stderr}"
                                ),
                        error = "Failed to move file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error moving file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Error moving file: ${e.message}"
                            ),
                    error = "Error moving file: ${e.message}"
            )
        }
    }

    /** Copy a file or directory */
    override suspend fun copyFile(tool: AITool): ToolResult {
        // 检查是否是 Linux 环境或跨环境操作
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val sourceEnvironment = tool.parameters.find { it.name == "source_environment" }?.value
        val destEnvironment = tool.parameters.find { it.name == "dest_environment" }?.value
        
        // 确定源和目标环境
        val srcEnv = sourceEnvironment ?: environment ?: "android"
        val dstEnv = destEnvironment ?: environment ?: "android"
        
        // 如果是 Linux 环境或跨环境操作，委托给父类处理
        if (srcEnv.lowercase() == "linux" || dstEnv.lowercase() == "linux") {
            return super.copyFile(tool)
        }
        
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true
        if (sourcePath.startsWith("content://", ignoreCase = true) || destPath.startsWith("content://", ignoreCase = true)) {
            return super.copyFile(tool)
        }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }
        
        // 如果源文件或目标文件在Operit内部存储，使用super的高权限方法
        if (isOperitInternalPath(sourcePath) || isOperitInternalPath(destPath)) {
            return super.copyFile(tool)
        }

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Source and destination parameters are required"
                            ),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            // 首先检查源路径是否存在
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$sourcePath' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Source path does not exist: $sourcePath"
                                ),
                        error = "Source path does not exist: $sourcePath"
                )
            }

            // 检查是否为目录
            val isDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d '$sourcePath' && echo 'true' || echo 'false'"
                    )
            val isDirectory = isDirResult.stdout.trim() == "true"

            // 确保目标父目录存在
            val destParentDir = destPath.substringBeforeLast('/')
            if (destParentDir.isNotEmpty()) {
                AndroidShellExecutor.executeShellCommand("mkdir -p '$destParentDir'")
            }

            // 根据是否为目录选择不同的复制命令
            val copyCommand =
                    if (isDirectory && recursive) {
                        "cp -r '$sourcePath' '$destPath'"
                    } else if (!isDirectory) {
                        "cp '$sourcePath' '$destPath'"
                    } else {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "copy",
                                                path = sourcePath,
                                                successful = false,
                                                details =
                                                        "Cannot copy directory without recursive flag"
                                        ),
                                error = "Cannot copy directory without recursive flag"
                        )
                    }

            val result = AndroidShellExecutor.executeShellCommand(copyCommand)

            if (result.success) {
                // 验证复制是否成功
                val verifyResult =
                        AndroidShellExecutor.executeShellCommand(
                                "test -e '$destPath' && echo 'exists' || echo 'not exists'"
                        )
                if (verifyResult.stdout.trim() != "exists") {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "copy",
                                            path = sourcePath,
                                            successful = false,
                                            details =
                                                    "Copy command completed but destination does not exist"
                                    ),
                            error = "Copy command completed but destination does not exist"
                    )
                }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = true,
                                        details =
                                                "Successfully copied ${if (isDirectory) "directory" else "file"} $sourcePath to $destPath"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Failed to copy: ${result.stderr}"
                                ),
                        error = "Failed to copy: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying file/directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Error copying file/directory: ${e.message}"
                            ),
                    error = "Error copying file/directory: ${e.message}"
            )
        }
    }

    /** Create a directory */
    override suspend fun makeDirectory(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.makeDirectory(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        if (isSafEnvironment(environment)) {
            return super.makeDirectory(tool)
        }
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.makeDirectory(tool)
        }
        val createParents =
                tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "mkdir",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            // 首先检查目录是否已存在
            val checkDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d '$path' && echo 'exists' || echo 'not exists'"
                    )
            
            if (checkDirResult.success && checkDirResult.stdout.trim() == "exists") {
                // 目录已存在，返回成功
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = true,
                                        details = "Directory already exists: $path"
                                ),
                        error = ""
                )
            }

            val mkdirCommand = if (createParents) "mkdir -p '$path'" else "mkdir '$path'"
            val result = AndroidShellExecutor.executeShellCommand(mkdirCommand)

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = true,
                                        details = "Successfully created directory $path"
                                ),
                        error = ""
                )
            } else {
                // 创建失败后再次检查是否已存在（可能在执行过程中被创建）
                val recheckDirResult =
                        AndroidShellExecutor.executeShellCommand(
                                "test -d '$path' && echo 'exists' || echo 'not exists'"
                        )
                
                if (recheckDirResult.success && recheckDirResult.stdout.trim() == "exists") {
                    // 目录已存在，返回成功
                    return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    FileOperationData(
                                            operation = "mkdir",
                                            path = path,
                                            successful = true,
                                            details = "Directory already exists: $path"
                                    ),
                            error = ""
                    )
                }
                
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = false,
                                        details = "Failed to create directory: ${result.stderr}"
                                ),
                        error = "Failed to create directory: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "mkdir",
                                    path = path,
                                    successful = false,
                                    details = "Error creating directory: ${e.message}"
                            ),
                    error = "Error creating directory: ${e.message}"
            )
        }
    }

    /** Search for files matching a pattern */
    override suspend fun findFiles(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.findFiles(tool)
        }

        if (isSafEnvironment(environment)) {
            return super.findFiles(tool)
        }

        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

        if (isOperitInternalPath(path)) {
            return super.findFiles(tool)
        }

        if (path.isBlank() || pattern.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = emptyList()
                            ),
                    error = "Path and pattern parameters are required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, 0.02f, "Searching (device)...")
            // Add options for different search modes
            val usePathPattern =
                    tool.parameters.find { it.name == "use_path_pattern" }?.value?.toBoolean()
                            ?: false
            val caseInsensitive =
                    tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean()
                            ?: false

            val isFileResult =
                AndroidShellExecutor.executeShellCommand(
                    "test -f ${shQuote(path)} && echo file || echo not_file"
                )
            if (isFileResult.success && isFileResult.stdout.trim() == "file") {
                val fileName = path.substringAfterLast('/')
                val testString = if (usePathPattern) path else fileName
                val regex = globToRegex(pattern, caseInsensitive)
                val files = if (regex.matches(testString)) listOf(path) else emptyList()

                ToolProgressBus.update(tool.name, 1f, "Search completed, found ${files.size}")

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FindFilesResultData(path = path, pattern = pattern, files = files),
                    error = ""
                )
            }

            // Add depth control parameter (default to -1 for unlimited depth/fully
            // recursive)
            val maxDepth =
                    tool.parameters.find { it.name == "max_depth" }?.value?.toIntOrNull() ?: -1

            // Determine which search option to use
            val searchOption =
                    if (usePathPattern) {
                        if (caseInsensitive) "-ipath" else "-path"
                    } else {
                        if (caseInsensitive) "-iname" else "-name"
                    }

            // Properly escape the pattern if quotes are required
            val escapedPattern = pattern.replace("'", "'\\''")
            val patternForCommand = "'$escapedPattern'"

            // Build the command with depth control if specified
            val depthOption = if (maxDepth >= 0) "-maxdepth $maxDepth" else ""
            val command =
                    "find '${if(path.endsWith("/")) path else "$path/"}' $depthOption $searchOption $patternForCommand"

            val files = mutableListOf<String>()
            val foundCount = AtomicInteger(0)
            val progressFloor = AtomicInteger(2)
            val stderrBuilder = StringBuilder()

            val process = AndroidShellExecutor.startShellProcess(command)
            try {
                val exitCode = coroutineScope {
                    val stdoutJob = launch {
                        process.stdout.collect { line ->
                            val v = line.trim()
                            if (v.isNotEmpty()) {
                                files.add(v)
                                val found = foundCount.incrementAndGet()
                                val p = (smoothProgress(found, 250f) * 100).toInt().coerceIn(0, 99)
                                updateProgressFloor(progressFloor, p)
                                if (found % 20 == 0) {
                                    ToolProgressBus.update(
                                        tool.name,
                                        progressFloor.get() / 100f,
                                        "Searching... found $found"
                                    )
                                }
                            }
                        }
                    }
                    val stderrJob = launch {
                        process.stderr.collect { line ->
                            if (line.isNotBlank()) {
                                stderrBuilder.appendLine(line)
                            }
                        }
                    }
                    val tickerJob = launch {
                        while (isActive && process.isAlive) {
                            delay(500)
                            val current = progressFloor.get()
                            if (current < 95) {
                                progressFloor.compareAndSet(current, current + 1)
                                ToolProgressBus.update(
                                    tool.name,
                                    progressFloor.get() / 100f,
                                    "Searching... found ${foundCount.get()}"
                                )
                            }
                        }
                    }

                    val code = process.waitFor()
                    stdoutJob.cancelAndJoin()
                    stderrJob.cancelAndJoin()
                    tickerJob.cancelAndJoin()
                    code
                }

                if (exitCode != 0) {
                    val err = stderrBuilder.toString().trim()
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FindFilesResultData(path = path, pattern = pattern, files = emptyList()),
                        error = if (err.isNotBlank()) err else "find command failed with exitCode=$exitCode"
                    )
                }
            } finally {
                if (process.isAlive) {
                    process.destroy()
                }
            }

            ToolProgressBus.update(tool.name, 1f, "Search completed, found ${files.size}")

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FindFilesResultData(path = path, pattern = pattern, files = files),
                    error = ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error searching for files", e)
            ToolProgressBus.update(tool.name, 1f, "Search failed")
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = emptyList()
                            ),
                    error = "Error searching for files: ${e.message}"
            )
        }
    }

    /** Get file information */
    override suspend fun fileInfo(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.fileInfo(tool)
        }

        if (isSafEnvironment(environment)) {
            return super.fileInfo(tool)
        }

        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        
        // 如果是Operit内部存储路径，使用super的高权限方法
        if (isOperitInternalPath(path)) {
            return super.fileInfo(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileInfoData(
                                    path = "",
                                    exists = false,
                                    fileType = "",
                                    size = 0,
                                    permissions = "",
                                    owner = "",
                                    group = "",
                                    lastModified = "",
                                    rawStatOutput = ""
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            // Check if file exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = false,
                                        fileType = "",
                                        size = 0,
                                        permissions = "",
                                        owner = "",
                                        group = "",
                                        lastModified = "",
                                        rawStatOutput = ""
                                ),
                        error = "File or directory does not exist: $path"
                )
            }

            // Get file details using stat
            val statResult = AndroidShellExecutor.executeShellCommand("stat '$path'")

            if (statResult.success) {
                // Get file type
                val fileTypeResult =
                        AndroidShellExecutor.executeShellCommand(
                                "test -d '$path' && echo 'directory' || (test -f '$path' && echo 'file' || echo 'other')"
                        )
                val fileType = fileTypeResult.stdout.trim()

                // Get file size
                val sizeResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %s '$path' 2>/dev/null || echo '0'"
                        )
                val size = sizeResult.stdout.trim().toLongOrNull() ?: 0

                // Get file permissions
                val permissionsResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %A '$path' 2>/dev/null || echo ''"
                        )
                val permissions = permissionsResult.stdout.trim()

                // Get owner and group
                val ownerResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %U '$path' 2>/dev/null || echo ''"
                        )
                val owner = ownerResult.stdout.trim()

                val groupResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %G '$path' 2>/dev/null || echo ''"
                        )
                val group = groupResult.stdout.trim()

                // Get last modified time
                val modifiedResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %Y '$path' 2>/dev/null || echo ''"
                        )
                val lastModifiedEpochSec = modifiedResult.stdout.trim().toLongOrNull()
                val lastModified =
                        if (lastModifiedEpochSec != null && lastModifiedEpochSec > 0) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                                .format(Date(lastModifiedEpochSec * 1000L))
                        } else {
                            ""
                        }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = true,
                                        fileType = fileType,
                                        size = size,
                                        permissions = permissions,
                                        owner = owner,
                                        group = group,
                                        lastModified = lastModified,
                                        rawStatOutput = statResult.stdout
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = true,
                                        fileType = "",
                                        size = 0,
                                        permissions = "",
                                        owner = "",
                                        group = "",
                                        lastModified = "",
                                        rawStatOutput = ""
                                ),
                        error = "Failed to get file information: ${statResult.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file information", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileInfoData(
                                    path = path,
                                    exists = false,
                                    fileType = "",
                                    size = 0,
                                    permissions = "",
                                    owner = "",
                                    group = "",
                                    lastModified = "",
                                    rawStatOutput = ""
                            ),
                    error = "Error getting file information: ${e.message}"
            )
        }
    }

    /** Zip files or directories */
    override suspend fun zipFiles(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.zipFiles(tool)
        }
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val includeRootDirectoryParameter = tool.parameters.find { it.name == "include_root_directory" }
        val includeRootDirectory =
                if (includeRootDirectoryParameter == null) {
                    true
                } else {
                    includeRootDirectoryParameter.value.toBooleanStrictOrNull()
                            ?: return ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "include_root_directory must be true or false"
                            )
                }
        PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(zipPath, tool.name, "destination")?.let { return it }

        if (isOperitInternalPath(sourcePath) || isOperitInternalPath(zipPath)) {
            return super.zipFiles(tool)
        }

        if (sourcePath.isBlank() || zipPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            val archiveSourcePath =
                    sourcePath.trimEnd('/').let {
                        if (it.isEmpty() && sourcePath.startsWith("/")) "/" else it
                    }
            // First, check if the source path exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e ${shQuote(archiveSourcePath)} && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source file or directory does not exist: $sourcePath"
                )
            }

            // Check if source is a directory
            val isDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d ${shQuote(archiveSourcePath)} && echo 'true' || echo 'false'"
                    )
            val isDirectory = isDirResult.stdout.trim() == "true"

            // Create parent directory for zip file if needed
            val zipDir = File(zipPath).parent
            if (zipDir != null) {
                AndroidShellExecutor.executeShellCommand("mkdir -p ${shQuote(zipDir)}")
            }

            // Use Java's ZipOutputStream to create the zip file
            // We'll use ADB to copy files to/from the device and process locally
            val sourceFile = File(archiveSourcePath)

            // Initialize buffer for file copy
            val buffer = ByteArray(1024)

            // Create temporary file for processing - using external files directory for
            // better
            // permissions
            val tempDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val tempSourceFile = File(tempDir, "temp_source_${System.currentTimeMillis()}")
            val tempZipFile = File(tempDir, "temp_zip_${System.currentTimeMillis()}.zip")

            try {
                // Make sure the temp directory exists
                tempDir.mkdirs()

                if (isDirectory) {
                    // For directories, we need to list all files and add them
                    // to the zip
                    val listResult =
                            AndroidShellExecutor.executeShellCommand("find ${shQuote(archiveSourcePath)} -type f")
                    val fileList = listResult.stdout.trim().split("\n").filter { it.isNotEmpty() }

                    // Create ZIP output stream
                    val fos = FileOutputStream(tempZipFile)
                    val zos = ZipOutputStream(BufferedOutputStream(fos))

                    try {
                        for (filePath in fileList) {
                            // Get the file path relative to the source
                            // directory
                            val pathInsideSource =
                                    if (archiveSourcePath == "/") {
                                        filePath.removePrefix("/")
                                    } else {
                                        filePath.removePrefix("$archiveSourcePath/")
                                    }
                            val relativePath =
                                    if (includeRootDirectory && sourceFile.name.isNotEmpty()) {
                                        "${sourceFile.name}/$pathInsideSource"
                                    } else {
                                        pathInsideSource
                                    }

                            // Copy the file from device to temp file
                            val pullResult =
                                    AndroidShellExecutor.executeShellCommand(
                                            "cat ${shQuote(filePath)} > ${shQuote(tempSourceFile.absolutePath)}"
                                    )
                            if (!pullResult.success) {
                                continue // Skip this file if we
                                // can't pull it
                            }

                            // Add the file to the ZIP
                            val fis = FileInputStream(tempSourceFile)
                            val bis = BufferedInputStream(fis)

                            try {
                                // Add ZIP entry
                                val entry = ZipEntry(relativePath)
                                zos.putNextEntry(entry)

                                // Write file content to ZIP
                                var len: Int
                                while (bis.read(buffer).also { len = it } > 0) {
                                    zos.write(buffer, 0, len)
                                }

                                zos.closeEntry()
                            } finally {
                                bis.close()
                                fis.close()
                                tempSourceFile.delete()
                            }
                        }
                    } finally {
                        zos.close()
                        fos.close()
                    }
                } else {
                    // For a single file, simpler process
                    // Copy the file from device to temp file
                    val pullResult =
                            AndroidShellExecutor.executeShellCommand(
                                    "cat ${shQuote(archiveSourcePath)} > ${shQuote(tempSourceFile.absolutePath)}"
                            )
                    if (!pullResult.success) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Failed to read source file: ${pullResult.stderr}"
                        )
                    }

                    // Create zip file with single entry
                    val fos = FileOutputStream(tempZipFile)
                    val zos = ZipOutputStream(BufferedOutputStream(fos))

                    try {
                        val fis = FileInputStream(tempSourceFile)
                        val bis = BufferedInputStream(fis)

                        try {
                            // Add ZIP entry
                            val entry = ZipEntry(sourceFile.name)
                            zos.putNextEntry(entry)

                            // Write file content to ZIP
                            var len: Int
                            while (bis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }

                            zos.closeEntry()
                        } finally {
                            bis.close()
                            fis.close()
                        }
                    } finally {
                        zos.close()
                        fos.close()
                    }
                }

                // Log information about the temp ZIP file
                AppLogger.d(
                        TAG,
                        "Temp ZIP file created at: ${tempZipFile.absolutePath}, size: ${tempZipFile.length()} bytes"
                )

                // Push the ZIP file to the destination
                val pushResult =
                        AndroidShellExecutor.executeShellCommand(
                                "cat ${shQuote(tempZipFile.absolutePath)} > ${shQuote(zipPath)}"
                        )
                if (!pushResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to write ZIP file: ${pushResult.stderr}"
                    )
                }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "zip",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully compressed $sourcePath to $zipPath"
                                ),
                        error = ""
                )
            } finally {
                // Clean up temporary files
                tempSourceFile.delete()
                tempZipFile.delete()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error compressing files", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error compressing files: ${e.message}"
            )
        }
    }

    /** Unzip a zip file */
    override suspend fun unzipFiles(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.unzipFiles(tool)
        }
        val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        PathValidator.validateAndroidPath(zipPath, tool.name, "source")?.let { return it }
        PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

        if (isOperitInternalPath(zipPath) || isOperitInternalPath(destPath)) {
            return super.unzipFiles(tool)
        }

        val actualZipPath = zipPath
        val actualDestPath = destPath

        if (zipPath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            ToolProgressBus.update(tool.name, -1f, "Unzipping...")
            // Check if the zip file exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f ${shQuote(zipPath)} && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Zip file does not exist: $zipPath"
                )
            }

            // Create destination directory if it doesn't exist
            AndroidShellExecutor.executeShellCommand("mkdir -p ${shQuote(destPath)};")

            val deviceUnzipCommands =
                listOf(
                    "unzip -o ${shQuote(zipPath)} -d ${shQuote(destPath)}",
                    "toybox unzip -o ${shQuote(zipPath)} -d ${shQuote(destPath)}",
                    "busybox unzip -o ${shQuote(zipPath)} -d ${shQuote(destPath)}"
                )

            for (cmd in deviceUnzipCommands) {
                try {
                    ToolProgressBus.update(tool.name, 0.02f, "Unzipping (device)...")
                    val extractedCount = AtomicInteger(0)
                    val progressFloor = AtomicInteger(2)
                    val stderrBuilder = StringBuilder()

                    val process = AndroidShellExecutor.startShellProcess("$cmd;")
                    try {
                        val exitCode = coroutineScope {
                            val stdoutJob = launch {
                                process.stdout.collect { line ->
                                    val t = line.trimStart()
                                    val isProgressLine =
                                        t.startsWith("inflating:") ||
                                        t.startsWith("extracting:") ||
                                        t.startsWith("creating:") ||
                                        t.startsWith("  inflating:") ||
                                        t.startsWith("  extracting:")

                                    if (isProgressLine) {
                                        val extracted = extractedCount.incrementAndGet()
                                        val p = (smoothProgress(extracted, 120f) * 100).toInt().coerceIn(0, 99)
                                        updateProgressFloor(progressFloor, p)
                                        if (extracted % 10 == 0) {
                                            ToolProgressBus.update(
                                                tool.name,
                                                progressFloor.get() / 100f,
                                                "Unzipping... extracted $extracted"
                                            )
                                        }
                                    }
                                }
                            }
                            val stderrJob = launch {
                                process.stderr.collect { line ->
                                    if (line.isNotBlank()) {
                                        stderrBuilder.appendLine(line)
                                    }

                                    val t = line.trimStart()
                                    val isProgressLine =
                                        t.startsWith("inflating:") ||
                                        t.startsWith("extracting:") ||
                                        t.startsWith("creating:") ||
                                        t.startsWith("  inflating:") ||
                                        t.startsWith("  extracting:")

                                    if (isProgressLine) {
                                        val extracted = extractedCount.incrementAndGet()
                                        val p = (smoothProgress(extracted, 120f) * 100).toInt().coerceIn(0, 99)
                                        updateProgressFloor(progressFloor, p)
                                        if (extracted % 10 == 0) {
                                            ToolProgressBus.update(
                                                tool.name,
                                                progressFloor.get() / 100f,
                                                "Unzipping... extracted $extracted"
                                            )
                                        }
                                    }
                                }
                            }
                            val tickerJob = launch {
                                while (isActive && process.isAlive) {
                                    delay(500)
                                    val current = progressFloor.get()
                                    if (current < 95) {
                                        progressFloor.compareAndSet(current, current + 1)
                                        ToolProgressBus.update(
                                            tool.name,
                                            progressFloor.get() / 100f,
                                            "Unzipping..."
                                        )
                                    }
                                }
                            }

                            val code = process.waitFor()
                            stdoutJob.cancelAndJoin()
                            stderrJob.cancelAndJoin()
                            tickerJob.cancelAndJoin()
                            code
                        }

                        if (exitCode == 0) {
                            ToolProgressBus.update(tool.name, 1f, "Unzip completed")
                            return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                    FileOperationData(
                                        operation = "unzip",
                                        path = zipPath,
                                        successful = true,
                                        details = "Successfully extracted $zipPath to $destPath"
                                    ),
                                error = ""
                            )
                        }
                    } finally {
                        if (process.isAlive) {
                            process.destroy()
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Device-side unzip failed for cmd: $cmd", e)
                }
            }

            // Create temporary files for processing - using external files directory
            // for better
            // permissions
            val tempDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val tempZipFile = File(tempDir, "temp_zip_${System.currentTimeMillis()}.zip")

            try {
                // Make sure the temp directory exists
                tempDir.mkdirs()

                // Copy the zip file from device to temp file
                val pullResult =
                        AndroidShellExecutor.executeShellCommand(
                                "cat ${shQuote(zipPath)} > ${shQuote(tempZipFile.absolutePath)}"
                        )
                if (!pullResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to read zip file: ${pullResult.stderr}"
                    )
                }

                // Log information about the temp ZIP file
                AppLogger.d(
                        TAG,
                        "Temp ZIP file loaded at: ${tempZipFile.absolutePath}, size: ${tempZipFile.length()} bytes"
                )

                val totalEntries = try {
                    ZipFile(tempZipFile).use { it.size() }
                } catch (_: Exception) {
                    null
                }
                var processedEntries = 0

                // Extract files using ZipInputStream
                val buffer = ByteArray(64 * 1024)
                val zipInputStream =
                        ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile)))

                try {
                    var zipEntry: ZipEntry? = zipInputStream.nextEntry
                    while (zipEntry != null) {
                        val fileName = zipEntry.name
                        if (fileName.startsWith("/") || fileName.contains("..") || fileName.contains("\\")) {
                            zipInputStream.closeEntry()
                            zipEntry = zipInputStream.nextEntry
                            continue
                        }

                        val newFile = File(tempDir, fileName)

                        val newFileCanonical = newFile.canonicalPath
                        val tempDirCanonical = tempDir.canonicalPath + File.separator
                        if (!newFileCanonical.startsWith(tempDirCanonical)) {
                            zipInputStream.closeEntry()
                            zipEntry = zipInputStream.nextEntry
                            continue
                        }

                        // Skip directories, but make sure they exist
                        if (zipEntry.isDirectory) {
                            newFile.mkdirs()
                            val dirPath = "$destPath/$fileName"
                            AndroidShellExecutor.executeShellCommand("mkdir -p ${shQuote(dirPath)};")
                            zipInputStream.closeEntry()
                            zipEntry = zipInputStream.nextEntry
                            continue
                        }

                        // Create parent directories if needed
                        val filePath = "$destPath/$fileName"
                        val parentDirPath = File(filePath).parent
                        if (parentDirPath != null) {
                            AndroidShellExecutor.executeShellCommand("mkdir -p ${shQuote(parentDirPath)};")
                        }

                        newFile.parentFile?.mkdirs()

                        // Extract file
                        val fileOutputStream = FileOutputStream(newFile)

                        try {
                            var len: Int
                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                fileOutputStream.write(buffer, 0, len)
                            }
                        } finally {
                            fileOutputStream.close()
                        }

                        // Copy the extracted file to device
                        val pushResult =
                                AndroidShellExecutor.executeShellCommand(
                                        "cat ${shQuote(newFile.absolutePath)} > ${shQuote(filePath)}"
                                )
                        if (!pushResult.success) {
                            AppLogger.w(TAG, "Failed to copy extracted file: $fileName to $filePath")
                            // Continue with next file
                        }

                        // Clean up temp file
                        newFile.delete()

                        zipInputStream.closeEntry()
                        processedEntries++
                        val progress =
                            if (totalEntries != null && totalEntries > 0) {
                                (processedEntries.toFloat() / totalEntries.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                        val msg =
                            if (totalEntries != null && totalEntries > 0) {
                                "Unzipping... ($processedEntries/$totalEntries)"
                            } else {
                                "Unzipping..."
                            }
                        ToolProgressBus.update(tool.name, progress, msg)
                        zipEntry = zipInputStream.nextEntry
                    }
                } finally {
                    zipInputStream.close()
                }

                ToolProgressBus.update(tool.name, 1f, "Unzip completed")
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "unzip",
                                        path = zipPath,
                                        successful = true,
                                        details = "Successfully extracted $zipPath to $destPath"
                                ),
                        error = ""
                )
            } finally {
                tempZipFile.delete()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting zip file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error extracting zip file: ${e.message}"
            )
        } finally {
            ToolProgressBus.clear()
        }
    }

    /** 打开文件 使用系统默认应用打开文件 */
    override suspend fun openFile(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.openFile(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

        if (isOperitInternalPath(path)) {
            return super.openFile(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "open",
                                    path = "",
                                    successful = false,
                                    details = "Must provide path parameter"
                            ),
                    error = "Must provide path parameter"
            )
        }

        return try {
            // 首先检查文件是否存在
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "open",
                                        path = path,
                                        successful = false,
                                        details = "File does not exist: $path"
                                ),
                        error = "File does not exist: $path"
                )
            }

            // 获取文件MIME类型
            val mimeTypeResult =
                    AndroidShellExecutor.executeShellCommand("file --mime-type -b '$path'")
            val mimeType =
                    if (mimeTypeResult.success) mimeTypeResult.stdout.trim()
                    else "application/octet-stream"

            // 使用Android intent打开文件
            val command = "am start -a android.intent.action.VIEW -d 'file://$path' -t '$mimeType'"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "open",
                                        path = path,
                                        successful = true,
                                        details = "Opened file with system app: $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "open",
                                        path = path,
                                        successful = false,
                                        details = "Failed to open file: ${result.stderr}"
                                ),
                        error = "Failed to open file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error opening file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "open",
                                    path = path,
                                    successful = false,
                                    details = "Error opening file: ${e.message}"
                            ),
                    error = "Error opening file: ${e.message}"
            )
        }
    }

    /** 分享文件 调用系统分享功能 */
    override suspend fun shareFile(tool: AITool): ToolResult {
        val environment = tool.parameters.find { it.name == "environment" }?.value
        if (environment == "linux") {
            return super.shareFile(tool)
        }
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
        val title = tool.parameters.find { it.name == "title" }?.value ?: "Share File"

        if (isOperitInternalPath(path)) {
            return super.shareFile(tool)
        }

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = "",
                                    successful = false,
                                    details = "Must provide path parameter"
                            ),
                    error = "Must provide path parameter"
            )
        }

        return try {
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f ${shQuote(path)} && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "share",
                                        path = path,
                                        successful = false,
                                        details = "File does not exist: $path"
                                ),
                        error = "File does not exist: $path"
                )
            }

            val stagingRoot = (context.getExternalFilesDir(null) ?: context.cacheDir)
            val stagingDir = File(stagingRoot, "share_tmp")
            if (!stagingDir.exists()) {
                stagingDir.mkdirs()
            }

            val sourceName = File(path).name.ifBlank { "shared_file" }
            val stagedFile = File(stagingDir, "${System.currentTimeMillis()}_$sourceName")
            val copyResult =
                    AndroidShellExecutor.executeShellCommand(
                            "cat ${shQuote(path)} > ${shQuote(stagedFile.absolutePath)}"
                    )

            if (!copyResult.success || !stagedFile.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "share",
                                        path = path,
                                        successful = false,
                                        details = "Failed to prepare file for sharing: ${copyResult.stderr}"
                                ),
                        error = "Failed to prepare file for sharing: ${copyResult.stderr}"
                )
            }

            val extension = stagedFile.extension.lowercase(Locale.US)
            val mimeType =
                    if (extension.isNotEmpty()) {
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                                ?: "application/octet-stream"
                    } else {
                        "application/octet-stream"
                    }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, stagedFile)
            val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        clipData =
                                ClipData.newUri(
                                        context.contentResolver,
                                        stagedFile.name,
                                        contentUri
                                )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
            val chooserIntent =
                    Intent.createChooser(shareIntent, title).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            context.startActivity(chooserIntent)

            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = path,
                                    successful = true,
                                    details = "Opened share interface, sharing file: $path"
                            ),
                    error = ""
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(TAG, "No activity found to handle sharing file: $path", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = path,
                                    successful = false,
                                    details = "No application found to share this file type."
                            ),
                    error = "No application found to share this file type."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sharing file", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = path,
                                    successful = false,
                                    details = "Error sharing file: ${e.message}"
                            ),
                    error = "Error sharing file: ${e.message}"
            )
        }
    }

    /** 下载文件 从网络URL下载文件到指定路径 */
    override suspend fun downloadFile(tool: AITool): ToolResult {
        return super.downloadFile(tool)
    }

    /** Write base64 encoded content to a binary file */

//         if (path.isBlank()) {
//             return ToolResult(
//                     toolName = tool.name,
//                     success = false,
//                     result =
//                     FileOperationData(
//                             operation = "write_binary",
//                             path = "",
//                             successful = false,
//                             details = "Path parameter is required"
//                     ),
//                     error = "Path parameter is required"
//             )
//         }

//         return try {
//             // Ensure parent directory exists
//             val directory = File(path).parent
//             if (directory != null) {
//                 AndroidShellExecutor.executeShellCommand("mkdir -p '$directory'")
//             }

//             // Write content using echo and base64 decode
//             val writeResult =
//                     AndroidShellExecutor.executeShellCommand(
//                             "echo '$base64Content' | base64 -d > '$path'"
//                     )

//             if (!writeResult.success) {
//                 return ToolResult(
//                         toolName = tool.name,
//                         success = false,
//                         result =
//                         FileOperationData(
//                                 operation = "write_binary",
//                                 path = path,
//                                 successful = false,
//                                 details = "Failed to write binary file: ${writeResult.stderr}"
//                         ),
//                         error = "Failed to write binary file: ${writeResult.stderr}"
//                 )
//             }

//             // Verify write was successful
//             val sizeResult =
//                     AndroidShellExecutor.executeShellCommand("stat -c %s '$path' 2>/dev/null || echo '0'")
//             val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
//             val originalSize =
//                     android.util.Base64.decode(base64Content, android.util.Base64.NO_WRAP).size

//             if (size.toLong() != originalSize.toLong()) {
//                  return ToolResult(
//                     toolName = tool.name,
//                     success = false,
//                     result =
//                     FileOperationData(
//                             operation = "write_binary",
//                             path = path,
//                             successful = false,
//                             details = "Write completed but file size mismatch. Expected: $originalSize, Got: $size. Possible write failure."
//                     ),
//                     error = "Write completed but file size mismatch. Expected: $originalSize, Got: $size. Possible write failure."
//                 )
//             }

//             return ToolResult(
//                     toolName = tool.name,
//                     success = true,
//                     result =
//                     FileOperationData(
//                             operation = "write_binary",
//                             path = path,
//                             successful = true,
//                             details = "Binary content written to $path"
//                     ),
//                     error = ""
//             )
//         } catch (e: Exception) {
//             AppLogger.e(TAG, "Error writing binary file", e)
//             return ToolResult(
//                     toolName = tool.name,
//                     success = false,
//                     result =
//                     FileOperationData(
//                             operation = "write_binary",
//                             path = path,
//                             successful = false,
//                             details = "Error writing binary file: ${e.message}"
//                     ),
//                     error = "Error writing binary file: ${e.message}"
//             )
//         }
//     }
}
