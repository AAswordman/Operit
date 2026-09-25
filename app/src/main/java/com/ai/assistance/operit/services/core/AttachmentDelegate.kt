package com.ai.assistance.operit.services.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.skill.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages attachment operations for the chat feature Handles adding, removing, and referencing
 * attachments
 */
class AttachmentDelegate(private val context: Context, private val toolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "AttachmentDelegate"
        private const val PACKAGE_ATTACHMENT_PREFIX = "package_attach:"
        private const val WORKSPACE_MENTION_ATTACHMENT_PREFIX = "workspace_mention:"
    }

    // State for attachments
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments
    private val attachmentListLock = Any()

    // Events
    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent

    /** Adds multiple attachments in one shot (dedup by filePath) */
    fun addAttachments(attachments: List<AttachmentInfo>): List<AttachmentInfo> {
        if (attachments.isEmpty()) return emptyList()
        synchronized(attachmentListLock) {
            val currentList = _attachments.value
            val existingPaths = currentList.mapTo(mutableSetOf()) { it.filePath }
            val usedFileNames = currentList.mapTo(mutableSetOf()) { it.fileName }
            val toAdd =
                    attachments.mapNotNull { incoming ->
                        if (!existingPaths.add(incoming.filePath)) {
                            null
                        } else {
                            val uniqueFileName = uniqueAttachmentFileName(incoming.fileName, usedFileNames)
                            usedFileNames.add(uniqueFileName)
                            if (uniqueFileName == incoming.fileName) {
                                incoming
                            } else {
                                incoming.copy(fileName = uniqueFileName)
                            }
                        }
                    }
            if (toAdd.isNotEmpty()) {
                _attachments.value = currentList + toAdd
            }
            return toAdd
        }
    }

    private fun appendAttachment(attachment: AttachmentInfo): AttachmentInfo {
        synchronized(attachmentListLock) {
            val currentList = _attachments.value
            val usedFileNames = currentList.mapTo(mutableSetOf()) { it.fileName }
            val uniqueFileName = uniqueAttachmentFileName(attachment.fileName, usedFileNames)
            val attachmentToAdd =
                    if (uniqueFileName == attachment.fileName) {
                        attachment
                    } else {
                        attachment.copy(fileName = uniqueFileName)
                    }
            _attachments.value = currentList + attachmentToAdd
            return attachmentToAdd
        }
    }

    private fun replaceAttachmentByPath(attachment: AttachmentInfo) {
        synchronized(attachmentListLock) {
            val currentList = _attachments.value.filterNot { it.filePath == attachment.filePath }
            val usedFileNames = currentList.mapTo(mutableSetOf()) { it.fileName }
            val uniqueFileName = uniqueAttachmentFileName(attachment.fileName, usedFileNames)
            val attachmentToAdd =
                    if (uniqueFileName == attachment.fileName) {
                        attachment
                    } else {
                        attachment.copy(fileName = uniqueFileName)
                    }
            _attachments.value = currentList + attachmentToAdd
        }
    }

    private fun uniqueAttachmentFileName(fileName: String, usedFileNames: Set<String>): String {
        if (!usedFileNames.contains(fileName)) return fileName

        val dotIndex = fileName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < fileName.lastIndex
        val baseName = if (hasExtension) fileName.substring(0, dotIndex) else fileName
        val extension = if (hasExtension) fileName.substring(dotIndex) else ""

        var index = 2
        var candidate = "${baseName}_$index$extension"
        while (usedFileNames.contains(candidate)) {
            index += 1
            candidate = "${baseName}_$index$extension"
        }
        return candidate
    }

    /**
     * Inserts a reference to an attachment at the current cursor position in the user's message
     * @return the formatted reference string
     */
    fun createAttachmentReference(attachment: AttachmentInfo): String {
        // Generate XML reference for the attachment
        val attachmentRef = StringBuilder("<attachment ")
        attachmentRef.append("id=\"${attachment.filePath}\" ")
        attachmentRef.append("filename=\"${attachment.fileName}\" ")
        attachmentRef.append("type=\"${attachment.mimeType}\" ")

        // Add size property
        if (attachment.fileSize > 0) {
            attachmentRef.append("size=\"${attachment.fileSize}\" ")
        }

        // Add content property (if exists)
        if (attachment.content.isNotEmpty()) {
            attachmentRef.append("content=\"${attachment.content}\" ")
        }

        attachmentRef.append("/>")

        return attachmentRef.toString()
    }

    /** Handles a photo taken by the camera */
    suspend fun handleTakenPhoto(uri: Uri) =
            withContext(Dispatchers.IO) {
                try {
                    val fileName = "camera_${System.currentTimeMillis()}.jpg"
                    val tempFile = createTempFileFromUri(uri, fileName)

                    if (tempFile != null) {
                        AppLogger.d(TAG, "Successfully created temp file from camera URI: ${tempFile.absolutePath}")

                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = tempFile.absolutePath,
                                        fileName = fileName,
                                        mimeType = "image/jpeg",
                                        fileSize = tempFile.length()
                                )

                        val addedAttachment = appendAttachment(attachmentInfo)

                        _toastEvent.emit(context.getString(R.string.attachment_photo_added, addedAttachment.fileName))
                    } else {
                        AppLogger.e(TAG, "Failed to create temp file from camera URI")
                        _toastEvent.emit(context.getString(R.string.attachment_photo_process_failed))
                    }
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_photo_add_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error adding photo attachment", e)
                }
            }

    suspend fun attachPackage(packageName: String) =
            withContext(Dispatchers.IO) { attachPackageInternal(packageName) }

    /** Handles a file or image attachment selected by the user 确保在IO线程执行所有文件操作 */
    suspend fun handleAttachment(filePath: String) =
            withContext(Dispatchers.IO) {
                try {
                    when {
                        filePath == "screen_capture" -> {
                            captureScreenContent()
                            return@withContext
                        }
                        filePath == "notifications_capture" -> {
                            captureNotifications()
                            return@withContext
                        }
                        filePath == "location_capture" -> {
                            captureLocation()
                            return@withContext
                        }
                        filePath.startsWith(PACKAGE_ATTACHMENT_PREFIX) -> {
                            attachPackageInternal(filePath.removePrefix(PACKAGE_ATTACHMENT_PREFIX).trim())
                            return@withContext
                        }
                    }

                    var sourceUri: Uri? = null
                    var fileName: String? = null
                    var mimeType: String? = null

                    if (filePath.startsWith("content://")) {
                        val uri = Uri.parse(filePath)
                        AppLogger.d(TAG, "Handling content URI: $uri")

                        sourceUri = uri
                        fileName = getFileNameFromUri(uri)
                        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    } else {
                        val localPath = if (filePath.startsWith("file://")) Uri.parse(filePath).path else filePath
                        if (localPath.isNullOrBlank()) {
                            _toastEvent.emit(context.getString(R.string.attachment_cannot_attach, filePath))
                            return@withContext
                        }

                        val file = java.io.File(localPath)
                        if (!file.exists()) {
                            _toastEvent.emit(context.getString(R.string.attachment_file_not_exist))
                            return@withContext
                        }

                        sourceUri = Uri.fromFile(file)
                        fileName = file.name
                        mimeType = getMimeTypeFromPath(localPath) ?: "application/octet-stream"
                    }

                    val resolvedUri = sourceUri
                    val resolvedFileName = fileName
                    val resolvedMimeType = mimeType
                    if (resolvedUri == null || resolvedFileName.isNullOrBlank() || resolvedMimeType.isNullOrBlank()) {
                        _toastEvent.emit(context.getString(R.string.attachment_cannot_attach, filePath))
                        return@withContext
                    }

                    AppLogger.d(TAG, "Copying attachment source to a local temporary file: $resolvedUri")
                    val tempFile = createTempFileFromUri(resolvedUri, resolvedFileName)
                    if (tempFile == null || !tempFile.exists()) {
                        AppLogger.e(TAG, "Failed to create temp file from source: $resolvedUri")
                        _toastEvent.emit(context.getString(R.string.attachment_cannot_attach, resolvedFileName))
                        return@withContext
                    }

                    val attachmentInfo =
                            AttachmentInfo(
                                    filePath = tempFile.absolutePath,
                                    fileName = resolvedFileName,
                                    mimeType = resolvedMimeType,
                                    fileSize = tempFile.length()
                            )
                    val addedAttachment = appendAttachment(attachmentInfo)

                    _toastEvent.emit(context.getString(R.string.attachment_added, addedAttachment.fileName))
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_add_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error adding attachment", e)
                }
            }

    suspend fun attachPastedText(text: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val externalDir = OperitPaths.cleanOnExitDir()
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }

                val noMediaFile = File(externalDir, ".nomedia")
                if (!noMediaFile.exists()) {
                    noMediaFile.createNewFile()
                }

                val textFile = File.createTempFile("pasted_text_", ".txt", externalDir)
                textFile.writeText(text, Charsets.UTF_8)

                val addedAttachment =
                    appendAttachment(
                        AttachmentInfo(
                            filePath = textFile.absolutePath,
                            fileName = textFile.name,
                            mimeType = "text/plain",
                            fileSize = textFile.length(),
                        )
                    )
                _toastEvent.emit(
                    context.getString(R.string.chat_pasted_text_attached, addedAttachment.fileName)
                )
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error creating pasted text attachment", e)
                _toastEvent.emit(context.getString(R.string.attachment_add_failed, e.message ?: ""))
                false
            }
        }

    /** 从URI创建临时文件 */
    private suspend fun createTempFileFromUri(uri: Uri, fileName: String): java.io.File? =
            withContext(Dispatchers.IO) {
                try {
                    val fileExtension = fileName.substringAfterLast('.', "jpg")

                    // 使用外部存储Download/Operit/cleanOnExit目录，而不是缓存目录
                    val externalDir = OperitPaths.cleanOnExitDir()

                    // 确保目录存在
                    if (!externalDir.exists()) {
                        externalDir.mkdirs()
                    }

                    // 确保.nomedia文件存在，防止媒体扫描
                    val noMediaFile = java.io.File(externalDir, ".nomedia")
                    if (!noMediaFile.exists()) {
                        noMediaFile.createNewFile()
                    }

                    val tempFile = java.io.File.createTempFile("attachment_", ".$fileExtension", externalDir)

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    if (tempFile.exists() && tempFile.length() > 0) {
                        AppLogger.d(TAG, "Successfully created temp image file: ${tempFile.absolutePath}")
                        return@withContext tempFile
                    }

                    return@withContext null
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to create temp file", e)
                    return@withContext null
                }
            }

    private suspend fun attachPackageInternal(packageName: String) {
        if (packageName.isBlank()) {
            _toastEvent.emit(context.getString(R.string.attachment_package_failed, packageName))
            return
        }

        val packageManager = PackageManager.getInstance(context, toolHandler)
        val isStandardPackage =
            packageManager.getAvailablePackages().containsKey(packageName) &&
                !packageManager.isToolPkgContainer(packageName)
        val isSkillPackage =
            SkillRepository.getInstance(context.applicationContext).getAiVisibleSkillPackages().containsKey(packageName)
        val isMcpPackage = packageManager.getAvailableServerPackages().containsKey(packageName)

        if (!isStandardPackage && !isSkillPackage && !isMcpPackage) {
            _toastEvent.emit(context.getString(R.string.attachment_package_failed, packageName))
            return
        }

        if (isStandardPackage) {
            packageManager.enablePackage(packageName)
        }

        val packageContent = packageManager.usePackage(packageName)
        if (isPackageAttachmentError(packageName, packageContent)) {
            AppLogger.w(TAG, "添加包附件失败: $packageName, reason=$packageContent")
            _toastEvent.emit(context.getString(R.string.attachment_package_failed, packageName))
            return
        }

        val attachmentInfo =
            AttachmentInfo(
                filePath = packageAttachmentPath(packageName),
                fileName = packageAttachmentDisplayName(packageName),
                mimeType = "text/plain",
                fileSize = packageContent.length.toLong(),
                content = packageContent
            )
        replaceAttachmentByPath(attachmentInfo)
        _toastEvent.emit(context.getString(R.string.attachment_package_added, packageName))
    }

    fun removePackageAttachment(packageName: String) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isEmpty()) return

        removeAttachment(packageAttachmentPath(normalizedPackageName))
    }

    suspend fun attachWorkspaceMention(
        workspacePath: String,
        relativePath: String,
        workspaceEnv: String? = null,
    ) = withContext(Dispatchers.IO) {
        val normalizedRelativePath = normalizeWorkspaceRelativePath(relativePath)
        if (workspacePath.isBlank() || normalizedRelativePath.isBlank()) {
            return@withContext
        }

        val fullPath = buildWorkspaceChildPath(workspacePath, normalizedRelativePath)
        val existsData = readWorkspaceEntryMetadata(fullPath, workspaceEnv)
        if (existsData == null || !existsData.exists) {
            _toastEvent.emit(context.getString(R.string.attachment_file_not_exist))
            return@withContext
        }

        val content =
            if (existsData.isDirectory) {
                buildWorkspaceDirectoryMentionContent(
                    fullPath = fullPath,
                    relativePath = normalizedRelativePath,
                    workspaceEnv = workspaceEnv,
                )
            } else {
                buildWorkspaceFileMentionContent(
                    fullPath = fullPath,
                    relativePath = normalizedRelativePath,
                    workspaceEnv = workspaceEnv,
                )
            }

        val attachmentInfo =
            AttachmentInfo(
                filePath = workspaceMentionAttachmentPath(normalizedRelativePath),
                fileName = normalizedRelativePath,
                mimeType =
                    if (existsData.isDirectory) {
                        "application/vnd.workspace-directory+plain"
                    } else {
                        "text/plain"
                    },
                fileSize = content.length.toLong(),
                content = content,
            )

        replaceAttachmentByPath(attachmentInfo)
    }

    fun removeWorkspaceMentionAttachment(relativePath: String) {
        val normalizedRelativePath = normalizeWorkspaceRelativePath(relativePath)
        if (normalizedRelativePath.isEmpty()) return
        removeAttachment(workspaceMentionAttachmentPath(normalizedRelativePath))
    }

    private fun packageAttachmentPath(packageName: String): String {
        return "$PACKAGE_ATTACHMENT_PREFIX$packageName"
    }

    private fun packageAttachmentDisplayName(packageName: String): String {
        return "包: $packageName"
    }

    private fun workspaceMentionAttachmentPath(relativePath: String): String {
        return "$WORKSPACE_MENTION_ATTACHMENT_PREFIX$relativePath"
    }

    private fun normalizeWorkspaceRelativePath(relativePath: String): String {
        return relativePath.trim().replace('\\', '/').trim('/')
    }

    private fun buildWorkspaceChildPath(workspacePath: String, relativePath: String): String {
        val normalizedRoot = workspacePath.trimEnd('/', '\\')
        val normalizedChild = relativePath.trimStart('/', '\\')
        return if (normalizedRoot.isEmpty()) {
            normalizedChild
        } else {
            "$normalizedRoot/$normalizedChild"
        }
    }

    private suspend fun readWorkspaceEntryMetadata(
        fullPath: String,
        workspaceEnv: String?,
    ): FileExistsData? {
        return if (workspaceEnv.isNullOrBlank()) {
            val file = File(fullPath)
            FileExistsData(
                path = fullPath,
                exists = file.exists(),
                isDirectory = file.isDirectory,
                size = if (file.exists() && !file.isDirectory) file.length() else 0L,
            )
        } else {
            val result =
                toolHandler.executeTool(
                    AITool(
                        name = "file_exists",
                        parameters = listOf(
                            ToolParameter("path", fullPath),
                            ToolParameter("environment", workspaceEnv),
                        ),
                    ),
                )
            result.result as? FileExistsData
        }
    }

    private suspend fun buildWorkspaceFileMentionContent(
        fullPath: String,
        relativePath: String,
        workspaceEnv: String?,
    ): String {
        val parameters =
            buildList {
                add(ToolParameter("path", fullPath))
                add(ToolParameter("text_only", "true"))
                if (!workspaceEnv.isNullOrBlank()) {
                    add(ToolParameter("environment", workspaceEnv))
                }
            }
        val result =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_full",
                    parameters = parameters,
                ),
            )
        val content = (result.result as? FileContentData)?.content.orEmpty()
        return buildString {
            appendLine("Selected workspace file: $relativePath")
            appendLine("This file was referenced via @ mention.")
            appendLine()
            appendLine("File content:")
            append(content)
        }
    }

    private suspend fun buildWorkspaceDirectoryMentionContent(
        fullPath: String,
        relativePath: String,
        workspaceEnv: String?,
    ): String {
        val parameters =
            buildList {
                add(ToolParameter("path", fullPath))
                if (!workspaceEnv.isNullOrBlank()) {
                    add(ToolParameter("environment", workspaceEnv))
                }
            }
        val result =
            toolHandler.executeTool(
                AITool(
                    name = "list_files",
                    parameters = parameters,
                ),
            )
        val listing = result.result as? DirectoryListingData
        return buildString {
            appendLine("Selected workspace directory: $relativePath")
            appendLine("This directory was referenced via @ mention.")
            appendLine()
            appendLine("Directory entries:")
            if (listing == null || listing.entries.isEmpty()) {
                appendLine("(empty)")
            } else {
                listing.entries
                    .sortedWith(
                        compareBy<DirectoryListingData.FileEntry> { !it.isDirectory }
                            .thenBy { it.name.lowercase() },
                    )
                    .forEach { entry ->
                        val kind = if (entry.isDirectory) "[DIR]" else "[FILE]"
                        appendLine("$kind ${entry.name}")
                    }
            }
        }
    }

    private fun isPackageAttachmentError(packageName: String, packageContent: String): Boolean {
        if (packageContent.isBlank()) {
            return true
        }
        return packageContent.startsWith("Package not found: ") ||
            packageContent.startsWith("Failed to load package data for: ") ||
            packageContent.startsWith("Missing required environment variables for package ") ||
            packageContent.startsWith("ToolPkg container '") ||
            packageContent.contains(" is inactive.") ||
            packageContent.startsWith("MCP server '") ||
            packageContent.startsWith("Cannot connect to MCP server") ||
            packageContent.startsWith("Cannot get MCP server configuration") ||
            packageContent == "Skill '$packageName' is set to not show to AI"
    }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        synchronized(attachmentListLock) {
            _attachments.value = _attachments.value.filter { it.filePath != filePath }
        }
    }

    /** Clear all attachments */
    fun clearAttachments() {
        synchronized(attachmentListLock) {
            _attachments.value = emptyList()
        }
    }

    /** Update attachments with a new list */
    fun updateAttachments(newAttachments: List<AttachmentInfo>) {
        synchronized(attachmentListLock) {
            _attachments.value = newAttachments
        }
    }

    /**
     * 截取当前屏幕并作为图片附件加入消息。
     * 发送时走与直接发图相同的多模态/识图链路，不再本地 OCR 成文本。
     */
    suspend fun captureScreenContent() =
            withContext(Dispatchers.IO) {
                try {
                    val screenshotTool = AITool(name = "capture_screenshot", parameters = emptyList())
                    val screenshotResult = toolHandler.executeTool(screenshotTool)
                    if (!screenshotResult.success) {
                        _toastEvent.emit(context.getString(R.string.attachment_screen_content_failed, screenshotResult.error ?: context.getString(R.string.attachment_screenshot_failed)))
                        return@withContext
                    }

                    val screenshotPath = screenshotResult.result.toString().trim()
                    val screenshotFile = File(screenshotPath)
                    if (screenshotPath.isBlank() || !screenshotFile.exists() || screenshotFile.length() <= 0L) {
                        _toastEvent.emit(context.getString(R.string.attachment_screen_content_failed, context.getString(R.string.attachment_screenshot_failed)))
                        return@withContext
                    }

                    val extension = screenshotFile.extension.ifBlank { "png" }
                    val fileName = "screen_content_${System.currentTimeMillis()}.$extension"
                    val mimeType = getMimeTypeFromPath(screenshotFile.absolutePath) ?: "image/png"
                    val attachmentInfo =
                        AttachmentInfo(
                            filePath = screenshotFile.absolutePath,
                            fileName = fileName,
                            mimeType = mimeType,
                            fileSize = screenshotFile.length()
                        )

                    appendAttachment(attachmentInfo)

                    _toastEvent.emit(context.getString(R.string.attachment_screen_content_added))
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_screen_content_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error capturing screen content", e)
                }
            }

    /** 获取设备当前通知并作为附件添加到消息 使用get_notifications AITool获取通知数据 确保在IO线程中执行 */
    suspend fun captureNotifications(limit: Int = 10) =
            withContext(Dispatchers.IO) {
                try {
                    // 创建工具参数
                    val toolParams =
                            listOf(
                                    ToolParameter("limit", limit.toString()),
                                    ToolParameter("include_ongoing", "true")
                            )

                    // 创建工具
                    val notificationsToolTask =
                            AITool(name = "get_notifications", parameters = toolParams)

                    // 执行工具
                    val result = toolHandler.executeTool(notificationsToolTask)

                    if (result.success) {
                        // 生成唯一ID
                        val captureId = "notifications_${System.currentTimeMillis()}"
                        val notificationsContent = result.result.toString()

                        // 创建附件信息
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId,
                                        fileName = "notifications.json",
                                        mimeType = "application/json",
                                        fileSize = notificationsContent.length.toLong(),
                                        content = notificationsContent
                                )

                        appendAttachment(attachmentInfo)

                        _toastEvent.emit(context.getString(R.string.attachment_notifications_added))
                    } else {
                        _toastEvent.emit(context.getString(R.string.attachment_notifications_failed, result.error ?: context.getString(R.string.attachment_unknown_error)))
                    }
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_notifications_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error capturing notifications", e)
                }
            }

    /** 获取设备当前位置并作为附件添加到消息 使用get_device_location AITool获取位置数据 确保在IO线程中执行 */
    suspend fun captureLocation(highAccuracy: Boolean = true) =
            withContext(Dispatchers.IO) {
                try {
                    // 创建工具参数
                    val toolParams =
                            listOf(
                                    ToolParameter("high_accuracy", highAccuracy.toString()),
                                    ToolParameter("timeout", "10") // 10秒超时
                            )

                    // 创建工具
                    val locationToolTask =
                            AITool(name = "get_device_location", parameters = toolParams)

                    // 执行工具
                    val result = toolHandler.executeTool(locationToolTask)

                    if (result.success) {
                        // 生成唯一ID
                        val captureId = "location_${System.currentTimeMillis()}"
                        val locationContent = result.result.toString()

                        // 创建附件信息
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId,
                                        fileName = "location.json",
                                        mimeType = "application/json",
                                        fileSize = locationContent.length.toLong(),
                                        content = locationContent
                                )

                        appendAttachment(attachmentInfo)

                        _toastEvent.emit(context.getString(R.string.attachment_location_added))
                    } else {
                        _toastEvent.emit(context.getString(R.string.attachment_location_failed, result.error ?: context.getString(R.string.attachment_unknown_error)))
                    }
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_location_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error capturing location", e)
                }
            }

    /** 获取当前时间并作为附件添加到消息 */
    suspend fun captureCurrentTime() =
            withContext(Dispatchers.IO) {
                try {
                    val captureId = "time_${System.currentTimeMillis()}"
                    val timeText =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val content = context.getString(R.string.attachment_current_time, timeText)
                    val attachmentInfo =
                        AttachmentInfo(
                            filePath = captureId,
                            fileName = "time.txt",
                            mimeType = "text/plain",
                            fileSize = content.length.toLong(),
                            content = content
                        )

                    appendAttachment(attachmentInfo)

                    _toastEvent.emit(context.getString(R.string.attachment_time_added))
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_time_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error capturing current time", e)
                }
            }

    /**
     * 捕获记忆文件夹并作为附件添加到消息
     * @param folderPaths 选中的记忆文件夹路径列表
     */
    suspend fun captureMemoryFolders(folderPaths: List<String>) =
            withContext(Dispatchers.IO) {
                try {
                    if (folderPaths.isEmpty()) {
                        _toastEvent.emit(context.getString(R.string.attachment_no_memory_folder))
                        return@withContext
                    }

                    // 生成唯一ID
                    val captureId = "memory_context_${System.currentTimeMillis()}"

                    // 构建XML格式的记忆上下文
                    val memoryContext = buildMemoryContextXml(folderPaths)

                    // 创建附件信息
                    val attachmentInfo =
                            AttachmentInfo(
                                    filePath = captureId,
                                    fileName = "memory_context.xml",
                                    mimeType = "application/xml",
                                    fileSize = memoryContext.length.toLong(),
                                    content = memoryContext
                            )

                    appendAttachment(attachmentInfo)

                    val folderCountText = if (folderPaths.size == 1) {
                        context.getString(R.string.attachment_memory_folder_added, folderPaths[0])
                    } else {
                        context.getString(R.string.attachment_memory_folders_added, folderPaths.size)
                    }
                    _toastEvent.emit(folderCountText)
                } catch (e: Exception) {
                    _toastEvent.emit(context.getString(R.string.attachment_memory_folder_failed, e.message ?: ""))
                    AppLogger.e(TAG, "Error capturing memory folders", e)
                }
            }

    /**
     * 构建记忆上下文XML字符串
     */
    private fun buildMemoryContextXml(folderPaths: List<String>): String {
        val foldersText = folderPaths.joinToString("\n") { "  - $it" }
        val examplePath = folderPaths.firstOrNull() ?: "some/folder/path"

        return """
<memory_context>
 <available_folders>
$foldersText
 </available_folders>
 <instruction>
- **CRITICAL**: To search within the folders listed above, you **MUST** use the `query_memory` tool and provide the `folder_path` parameter.
- The `folder_path` parameter's value **MUST** be one of the paths from the `<available_folders>` list.
- Autonomously decide whether a search is needed and what query to use based on the user's question.
- Example: `<tool name="query_memory"><param name="query">search query</param><param name="folder_path">$examplePath</param></tool>`
 </instruction>
</memory_context>""".trimIndent()
    }

    /** Get file name from content URI */
    private suspend fun getFileNameFromUri(uri: Uri): String =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileName = context.getString(R.string.attachment_unknown_file)

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex =
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                fileName = cursor.getString(displayNameIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting file name from URI", e)
                }

                return@withContext fileName
            }

    /** Get MIME type from file path */
    private fun getMimeTypeFromPath(filePath: String): String? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}
