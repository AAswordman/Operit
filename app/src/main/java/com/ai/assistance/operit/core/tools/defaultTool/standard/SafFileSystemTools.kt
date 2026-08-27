package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.GrepResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import java.io.BufferedReader
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SafFileSystemTools(
    private val context: Context,
    private val apiPreferences: ApiPreferences
) {
    private val contentResolver = context.contentResolver

    private fun resolveEnvLabel(environment: String?): String {
        val env = environment?.trim().orEmpty()
        return if (env.isBlank()) "repo" else env
    }

    private fun extractSafBookmarkNameOrNull(environment: String?): String? {
        val env = environment ?: return null
        val trimmed = env.trim()
        if (!trimmed.startsWith("repo:", ignoreCase = true)) return null
        return trimmed.removePrefix("repo:").trim().takeIf { it.isNotEmpty() }
    }

    private fun normalizeAbsolutePath(path: String): String {
        var p = path.trim()
        if (p.isEmpty()) return "/"
        if (!p.startsWith("/")) p = "/$p"
        // Remove trailing slash (except root)
        if (p.length > 1 && p.endsWith('/')) p = p.dropLast(1)
        // Collapse multiple slashes
        while (p.contains("//")) p = p.replace("//", "/")
        return p
    }

    private suspend fun resolveTreeUriFromEnvironment(environment: String?): Uri? {
        val name = extractSafBookmarkNameOrNull(environment) ?: return null
        val bookmarks = apiPreferences.safBookmarksFlow.first()
        val matched = bookmarks.firstOrNull { it.name == name }
            ?: bookmarks.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return null
        return runCatching { Uri.parse(matched.uri) }.getOrNull()
    }

    private suspend fun resolveSafPathToDocumentUriOrNull(path: String, environment: String?): Uri? {
        // Already a content uri
        if (isContentUri(path)) return runCatching { Uri.parse(path) }.getOrNull()

        val treeUri = resolveTreeUriFromEnvironment(environment) ?: return null
        val authority = treeUri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val baseTreeUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)

        val abs = normalizeAbsolutePath(path)
        if (abs == "/") {
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, treeId)
            return rootDocUri
        }

        var currentDocId = treeId
        val segments = abs.trim('/').split('/').filter { it.isNotEmpty() }
        for (seg in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(baseTreeUri, currentDocId)
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            ) ?: return null

            var nextDocId: String? = null
            cursor.use {
                val idIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (it.moveToNext()) {
                    val n = if (nameIdx >= 0 && !it.isNull(nameIdx)) it.getString(nameIdx) else null
                    if (n == seg) {
                        nextDocId = if (idIdx >= 0 && !it.isNull(idIdx)) it.getString(idIdx) else null
                        break
                    }
                }
            }

            val found = nextDocId ?: return null
            currentDocId = found
        }

        return DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, currentDocId)
    }

    private fun splitParentAndNameForAbsolutePath(path: String): Pair<String, String>? {
        val abs = normalizeAbsolutePath(path)
        if (abs == "/") return null
        val parent = abs.substringBeforeLast('/', missingDelimiterValue = "")
        val name = abs.substringAfterLast('/')
        if (name.isBlank()) return null
        val parentPath = if (parent.isBlank()) "/" else parent
        return parentPath to name
    }

    private fun splitSyntheticChildPath(path: String): Pair<String, String>? {
        val idx = path.lastIndexOf('/')
        if (idx <= 0 || idx >= path.length - 1) return null
        val parent = path.substring(0, idx)
        val name = path.substring(idx + 1)
        if (parent.startsWith("content://", ignoreCase = true) && name.isNotBlank()) {
            return parent to name
        }
        return null
    }

    private fun requireSafCreateMimeType(fileName: String): String {
        val normalizedName = fileName.lowercase(Locale.ROOT)
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            normalizedName in setOf("license", "notice", "readme", "changelog", "dockerfile", "makefile") -> "text/plain"
            normalizedName in setOf(".gitignore", ".gitattributes", ".editorconfig", ".env", ".npmrc", ".yarnrc") -> "text/plain"
            extension == "md" || extension == "markdown" -> "text/markdown"
            extension == "txt" || extension == "log" -> "text/plain"
            extension == "json" -> "application/json"
            extension == "xml" -> "application/xml"
            extension == "html" || extension == "htm" -> "text/html"
            extension == "css" -> "text/css"
            extension == "csv" -> "text/csv"
            extension == "js" || extension == "mjs" || extension == "cjs" -> "text/javascript"
            extension == "ts" || extension == "tsx" -> "text/typescript"
            extension == "kt" || extension == "kts" -> "text/x-kotlin"
            extension == "java" -> "text/x-java-source"
            extension == "py" -> "text/x-python"
            extension == "sh" || extension == "bash" -> "application/x-sh"
            extension == "yaml" || extension == "yml" -> "application/x-yaml"
            extension == "toml" -> "application/toml"
            extension == "ini" || extension == "conf" || extension == "properties" -> "text/plain"
            extension == "svg" -> "image/svg+xml"
            extension == "png" -> "image/png"
            extension == "jpg" || extension == "jpeg" -> "image/jpeg"
            extension == "webp" -> "image/webp"
            extension == "gif" -> "image/gif"
            extension == "pdf" -> "application/pdf"
            extension == "zip" -> "application/zip"
            extension == "gz" -> "application/gzip"
            extension == "apk" -> "application/vnd.android.package-archive"
            else -> throw IllegalArgumentException("Unsupported file extension for SAF create: $fileName")
        }
    }

    suspend fun writeFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "write_binary", env = envLabel, path = "", successful = false, details = "Path parameter is required"),
                error = "Path parameter is required"
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                val decoded = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)

                val synthetic = splitSyntheticChildPath(path)
                val targetUri: Uri = if (synthetic != null) {
                    val (parentStr, name) = synthetic
                    val parentUri = resolveSafPathToDocumentUriOrNull(parentStr, environment)
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                            error = "Invalid repository path: $parentStr"
                        )
                    val created = createChildDocumentOrNull(parentUri, name, requireSafCreateMimeType(name))
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Failed to create file"),
                            error = "Failed to create file"
                        )
                    created
                } else {
                    resolveSafPathToDocumentUriOrNull(path, environment)
                        ?: run {
                            val parts = splitParentAndNameForAbsolutePath(path)
                            if (parts != null && extractSafBookmarkNameOrNull(environment) != null) {
                                val (parentPath, name) = parts
                                val parentUri = resolveSafPathToDocumentUriOrNull(parentPath, environment)
                                    ?: return@withContext ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                                        error = "Invalid repository path: $parentPath"
                                    )
                                createChildDocumentOrNull(parentUri, name, requireSafCreateMimeType(name))
                                    ?: return@withContext ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Failed to create file"),
                                        error = "Failed to create file"
                                    )
                            } else {
                                null
                            }
                        }
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                            error = "Invalid repository path: $path"
                        )
                }

                val output = openOutputStreamOrNull(targetUri, "w")
                    ?: return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Failed to open uri"),
                        error = "Failed to open uri: $path"
                    )

                output.use { it.write(decoded) }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = true, details = "Successfully wrote binary to $path"),
                    error = ""
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(operation = "write_binary", env = envLabel, path = path, successful = false, details = "Error writing binary file: ${e.message}"),
                    error = "Error writing binary file: ${e.message}"
                )
            }
        }
    }

    suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Source and destination parameters are required"),
                error = "Source and destination parameters are required"
            )
        }
        val sourceIsContent = isContentUri(sourcePath)
        val destIsContent = isContentUri(destPath)
        if (!sourceIsContent && !destIsContent) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Repository copy requires at least one content:// path"),
                error = "Repository copy requires at least one content:// path"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                if (sourceIsContent && !destIsContent) {
                    val sourceUri = Uri.parse(sourcePath)
                    val sourceDocUri = resolveSourceDocumentUriOrNull(sourcePath)
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Invalid source uri"),
                            error = "Invalid source uri: $sourcePath"
                        )
                    if (isDirectoryUri(sourceUri)) {
                        return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Copy directory from repository to local is not supported"),
                            error = "Copy directory from repository to local is not supported"
                        )
                    }

                    val destFile = java.io.File(destPath)
                    destFile.parentFile?.mkdirs()
                    openInputStreamOrNull(sourceDocUri).use { input ->
                        if (input == null) throw IllegalStateException("Failed to open input stream")
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }

                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = true, details = "Successfully copied $sourcePath to $destPath"),
                        error = ""
                    )
                }

                if (!sourceIsContent && destIsContent) {
                    val sourceFile = java.io.File(sourcePath)
                    if (!sourceFile.exists()) {
                        return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Source path does not exist"),
                            error = "Source path does not exist: $sourcePath"
                        )
                    }
                    if (sourceFile.isDirectory) {
                        return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Copy directory from local to repository is not supported"),
                            error = "Copy directory from local to repository is not supported"
                        )
                    }

                    val destUri = Uri.parse(destPath)
                    val destDocUri = toTreeDocumentUri(destUri) ?: destUri
                    val destMime = queryMimeType(destDocUri)
                    val targetUri: Uri = when {
                        splitSyntheticChildPath(destPath) != null -> {
                            val (parentStr, name) = splitSyntheticChildPath(destPath)!!
                            createChildDocumentOrNull(Uri.parse(parentStr), name, "application/octet-stream")
                                ?: throw IllegalStateException("Failed to create destination file")
                        }
                        destMime == DocumentsContract.Document.MIME_TYPE_DIR -> {
                            DocumentsContract.createDocument(
                                contentResolver,
                                destDocUri,
                                "application/octet-stream",
                                sourceFile.name
                            ) ?: throw IllegalStateException("Failed to create destination file")
                        }
                        destMime != null -> destDocUri
                        else -> throw IllegalStateException("Destination does not exist; use parentUri/name")
                    }

                    sourceFile.inputStream().use { input ->
                        openOutputStreamOrNull(targetUri, "w").use { output ->
                            if (output == null) throw IllegalStateException("Failed to open output stream")
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }

                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = true, details = "Successfully copied $sourcePath to $destPath"),
                        error = ""
                    )
                }

                val sourceUri = Uri.parse(sourcePath)
                val sourceDocUri = resolveSourceDocumentUriOrNull(sourcePath)
                    ?: return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Invalid source uri"),
                        error = "Invalid source uri: $sourcePath"
                    )

                val sourceName = queryDisplayName(sourceDocUri) ?: ""
                val sourceIsDir = isDirectoryUri(sourceUri)
                val sourceMime = queryMimeType(sourceDocUri) ?: if (sourceIsDir) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"

                fun resolveDestTargetForFile(): Uri {
                    val synthetic = splitSyntheticChildPath(destPath)
                    if (synthetic != null) {
                        val (parentStr, name) = synthetic
                        val created = createChildDocumentOrNull(Uri.parse(parentStr), name, sourceMime)
                        return created ?: throw IllegalStateException("Failed to create destination file")
                    }
                    val destUri = Uri.parse(destPath)
                    val destDocUri = toTreeDocumentUri(destUri) ?: destUri
                    val destMime = queryMimeType(destDocUri)
                    if (destMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val name = if (sourceName.isNotBlank()) sourceName else "copied_file"
                        val created = DocumentsContract.createDocument(contentResolver, destDocUri, sourceMime, name)
                        return created ?: throw IllegalStateException("Failed to create destination file")
                    }
                    if (destMime != null) {
                        return destDocUri
                    }
                    throw IllegalStateException("Destination does not exist; use parentUri/name")
                }

                fun resolveDestTargetForDir(): Uri {
                    if (!recursive) throw IllegalStateException("Cannot copy directory without recursive flag")
                    val synthetic = splitSyntheticChildPath(destPath)
                    if (synthetic != null) {
                        val (parentStr, name) = synthetic
                        val created = createChildDocumentOrNull(Uri.parse(parentStr), name, DocumentsContract.Document.MIME_TYPE_DIR)
                        return created ?: throw IllegalStateException("Failed to create destination directory")
                    }
                    val destUri = Uri.parse(destPath)
                    val destDocUri = toTreeDocumentUri(destUri) ?: destUri
                    val destMime = queryMimeType(destDocUri)
                    if (destMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return destUri
                    }
                    throw IllegalStateException("Destination must be a directory uri or parentUri/name")
                }

                fun copyDirRecursive(srcDirUri: Uri, dstDirUri: Uri) {
                    val childrenQuery = resolveChildrenQueryUri(srcDirUri)
                        ?: throw IllegalStateException("Repository copy_dir requires tree-backed document uri")
                    val treeUri = toTreeUriOrNull(srcDirUri)
                        ?: throw IllegalStateException("Repository copy_dir requires tree-backed document uri")
                    val dstDirDocUri = ensureDirectoryDocumentUriOrNull(dstDirUri)
                        ?: throw IllegalStateException("Destination is not a directory")

                    contentResolver.query(
                        childrenQuery.first,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                        while (cursor.moveToNext()) {
                            if (idIdx < 0 || cursor.isNull(idIdx)) continue
                            val childId = cursor.getString(idIdx)
                            val childName = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else null
                            if (childName.isNullOrBlank()) continue
                            val childMime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else null
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)

                            if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                val createdDir = DocumentsContract.createDocument(
                                    contentResolver,
                                    dstDirDocUri,
                                    DocumentsContract.Document.MIME_TYPE_DIR,
                                    childName
                                ) ?: throw IllegalStateException("Failed to create directory: $childName")
                                copyDirRecursive(childUri, createdDir)
                            } else {
                                val createdFile = DocumentsContract.createDocument(
                                    contentResolver,
                                    dstDirDocUri,
                                    childMime ?: "application/octet-stream",
                                    childName
                                ) ?: throw IllegalStateException("Failed to create file: $childName")
                                copyStreams(childUri, createdFile)
                            }
                        }
                    } ?: throw IllegalStateException("Failed to query child documents")
                }

                if (!sourceIsDir) {
                    val destTarget = resolveDestTargetForFile()
                    copyStreams(sourceDocUri, destTarget)
                } else {
                    val destDir = resolveDestTargetForDir()
                    copyDirRecursive(sourceUri, destDir)
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = true, details = "Successfully copied $sourcePath to $destPath"),
                    error = ""
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(operation = "copy", env = envLabel, path = sourcePath, successful = false, details = "Error copying file/directory: ${e.message}"),
                    error = "Error copying file/directory: ${e.message}"
                )
            }
        }
    }

    suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "move", env = envLabel, path = sourcePath, successful = false, details = "Source and destination parameters are required"),
                error = "Source and destination parameters are required"
            )
        }
        val sourceIsContent = isContentUri(sourcePath)
        val destIsContent = isContentUri(destPath)
        if (!sourceIsContent && !destIsContent) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "move", env = envLabel, path = sourcePath, successful = false, details = "Repository move requires at least one content:// path"),
                error = "Repository move requires at least one content:// path"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val copyResult = copyFile(tool.copy(name = "copy_file"))
                if (!copyResult.success) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(operation = "move", env = envLabel, path = sourcePath, successful = false, details = "Failed to copy before move: ${copyResult.error}"),
                        error = "Failed to copy before move: ${copyResult.error}"
                    )
                }

                when {
                    sourceIsContent -> {
                        val sourceUri = Uri.parse(sourcePath)
                        deleteRecursive(sourceUri)
                    }
                    else -> {
                        val f = java.io.File(sourcePath)
                        if (f.isDirectory) {
                            f.deleteRecursively()
                        } else {
                            f.delete()
                        }
                    }
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(operation = "move", env = envLabel, path = sourcePath, successful = true, details = "Successfully moved $sourcePath to $destPath"),
                    error = ""
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(operation = "move", env = envLabel, path = sourcePath, successful = false, details = "Error moving file/directory: ${e.message}"),
                    error = "Error moving file/directory: ${e.message}"
                )
            }
        }
    }


    private fun toTreeDocumentUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)
        val docId = if (DocumentsContract.isTreeUri(uri)) treeId else runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: treeId
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

    private fun queryMimeType(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idx < 0 || cursor.isNull(idx)) return@use null
                cursor.getString(idx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isDirectoryUri(uri: Uri): Boolean {
        val docUri = toTreeDocumentUri(uri) ?: uri
        val mime = queryMimeType(docUri)
        return mime == DocumentsContract.Document.MIME_TYPE_DIR || DocumentsContract.isTreeUri(uri)
    }

    private fun openInputStreamOrNull(uri: Uri) = runCatching { contentResolver.openInputStream(uri) }.getOrNull()

    private fun openOutputStreamOrNull(uri: Uri, mode: String) = runCatching { contentResolver.openOutputStream(uri, mode) }.getOrNull()

    private fun copyStreams(src: Uri, dst: Uri) {
        openInputStreamOrNull(src).use { input ->
            if (input == null) throw IllegalStateException("Failed to open input stream")
            openOutputStreamOrNull(dst, "w").use { output ->
                if (output == null) throw IllegalStateException("Failed to open output stream")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }

    private fun ensureDirectoryDocumentUriOrNull(uri: Uri): Uri? {
        val docUri = toTreeDocumentUri(uri) ?: return null
        val mime = queryMimeType(docUri)
        return if (mime == DocumentsContract.Document.MIME_TYPE_DIR || DocumentsContract.isTreeUri(uri)) docUri else null
    }

    private fun createChildDocumentOrNull(parentDirUri: Uri, name: String, mimeType: String): Uri? {
        val parentDocUri = ensureDirectoryDocumentUriOrNull(parentDirUri) ?: return null
        return DocumentsContract.createDocument(contentResolver, parentDocUri, mimeType, name)
    }

    private fun resolveSourceDocumentUriOrNull(sourceStr: String): Uri? {
        val sourceUri = runCatching { Uri.parse(sourceStr) }.getOrNull() ?: return null
        return toTreeDocumentUri(sourceUri) ?: sourceUri
    }

    private fun deleteRecursive(uri: Uri) {
        val docUri = toTreeDocumentUri(uri) ?: uri
        val mime = queryMimeType(docUri)
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR || DocumentsContract.isTreeUri(uri)) {
            val childrenQuery = resolveChildrenQueryUri(uri) ?: resolveChildrenQueryUri(docUri)
            if (childrenQuery != null) {
                val treeUri = toTreeUriOrNull(uri) ?: toTreeUriOrNull(docUri)
                if (treeUri != null) {
                    contentResolver.query(
                        childrenQuery.first,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        while (cursor.moveToNext()) {
                            if (idIdx < 0 || cursor.isNull(idIdx)) continue
                            val childId = cursor.getString(idIdx)
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val childMime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else null
                            if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                deleteRecursive(childUri)
                            } else {
                                DocumentsContract.deleteDocument(contentResolver, childUri)
                            }
                        }
                    }
                }
            }
        }
        DocumentsContract.deleteDocument(contentResolver, docUri)
    }

    private fun isContentUri(path: String): Boolean {
        return path.startsWith("content://", ignoreCase = true)
    }

    suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "mkdir", env = envLabel, path = "", successful = false, details = "Path parameter is required"),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val synthetic = splitSyntheticChildPath(path)
                if (synthetic != null) {
                    val (parentStr, name) = synthetic
                    val parentUri = resolveSafPathToDocumentUriOrNull(parentStr, environment)
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                            error = "Invalid repository path: $parentStr"
                        )

                    val created = createChildDocumentOrNull(parentUri, name, DocumentsContract.Document.MIME_TYPE_DIR)
                    return@withContext if (created != null) {
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = true, details = "Successfully created directory $path"),
                            error = ""
                        )
                    } else {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Failed to create directory"),
                            error = "Failed to create directory"
                        )
                    }
                }

                val docUri = resolveSafPathToDocumentUriOrNull(path, environment)
                    ?: run {
                        val parts = splitParentAndNameForAbsolutePath(path)
                        if (parts != null && extractSafBookmarkNameOrNull(environment) != null) {
                            val (parentPath, name) = parts
                            val parentUri = resolveSafPathToDocumentUriOrNull(parentPath, environment)
                                ?: return@withContext ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                                    error = "Invalid repository path: $parentPath"
                                )
                            val created = createChildDocumentOrNull(parentUri, name, DocumentsContract.Document.MIME_TYPE_DIR)
                                ?: return@withContext ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Failed to create directory"),
                                    error = "Failed to create directory"
                                )
                            created
                        } else {
                            null
                        }
                    }
                    ?: return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                        error = "Invalid repository path: $path"
                    )

                val cursor = contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    val mimeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    if (it.moveToFirst() && mimeIdx >= 0 && !it.isNull(mimeIdx)) {
                        val mime = it.getString(mimeIdx)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            return@withContext ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = true, details = "Directory already exists: $path"),
                                error = ""
                            )
                        }
                    }
                }

                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Unsupported repository mkdir target (use parentUri/name)"),
                    error = "Unsupported repository mkdir target (use parentUri/name)"
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(operation = "mkdir", env = envLabel, path = path, successful = false, details = "Error creating directory: ${e.message}"),
                    error = "Error creating directory: ${e.message}"
                )
            }
        }
    }

    suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false
        val environment = tool.parameters.find { it.name == "environment" }?.value

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = if (append) "append" else "write",
                    env = resolveEnvLabel(environment),
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val synthetic = splitSyntheticChildPath(path)
                val targetUri: Uri = if (synthetic != null) {
                    val (parentStr, name) = synthetic
                    val parentUri = resolveSafPathToDocumentUriOrNull(parentStr, environment)
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(
                                operation = if (append) "append" else "write",
                                env = resolveEnvLabel(environment),
                                path = path,
                                successful = false,
                                details = "Invalid repository path"
                            ),
                            error = "Invalid repository path: $parentStr"
                        )

                    val created = createChildDocumentOrNull(parentUri, name, requireSafCreateMimeType(name))
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(
                                operation = if (append) "append" else "write",
                                env = resolveEnvLabel(environment),
                                path = path,
                                successful = false,
                                details = "Failed to create file"
                            ),
                            error = "Failed to create file"
                        )
                    created
                } else {
                    resolveSafPathToDocumentUriOrNull(path, environment)
                        ?: run {
                            val parts = splitParentAndNameForAbsolutePath(path)
                            if (parts != null && extractSafBookmarkNameOrNull(environment) != null) {
                                val (parentPath, name) = parts
                                val parentUri = resolveSafPathToDocumentUriOrNull(parentPath, environment)
                                    ?: return@withContext ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = FileOperationData(
                                            operation = if (append) "append" else "write",
                                            env = resolveEnvLabel(environment),
                                            path = path,
                                            successful = false,
                                            details = "Invalid repository path"
                                        ),
                                        error = "Invalid repository path: $parentPath"
                                    )
                                createChildDocumentOrNull(parentUri, name, requireSafCreateMimeType(name))
                                    ?: return@withContext ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = FileOperationData(
                                            operation = if (append) "append" else "write",
                                            env = resolveEnvLabel(environment),
                                            path = path,
                                            successful = false,
                                            details = "Failed to create file"
                                        ),
                                        error = "Failed to create file"
                                    )
                            } else {
                                null
                            }
                        }
                        ?: return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = FileOperationData(
                                operation = if (append) "append" else "write",
                                env = resolveEnvLabel(environment),
                                path = path,
                                successful = false,
                                details = "Invalid repository path"
                            ),
                            error = "Invalid repository path: $path"
                        )
                }

                val mime = queryMimeType(targetUri)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(
                            operation = if (append) "append" else "write",
                            env = resolveEnvLabel(environment),
                            path = path,
                            successful = false,
                            details = "Target is a directory"
                        ),
                        error = "Target is a directory: $path"
                    )
                }

                val mode = if (append) "wa" else "w"
                val output = openOutputStreamOrNull(targetUri, mode)
                    ?: return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(
                            operation = if (append) "append" else "write",
                            env = resolveEnvLabel(environment),
                            path = path,
                            successful = false,
                            details = "Failed to open uri"
                        ),
                        error = "Failed to open uri: $path"
                    )

                output.use { it.write(content.toByteArray(Charsets.UTF_8)) }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = if (append) "append" else "write",
                        env = resolveEnvLabel(environment),
                        path = path,
                        successful = true,
                        details = "Successfully wrote file: $path"
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = if (append) "append" else "write",
                        env = resolveEnvLabel(environment),
                        path = path,
                        successful = false,
                        details = "Error writing file: ${e.message}"
                    ),
                    error = "Error writing file: ${e.message}"
                )
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx < 0 || cursor.isNull(idx)) return@use null
                cursor.getString(idx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun querySize(uri: Uri): Long? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx < 0 || cursor.isNull(idx)) return@use null
                cursor.getLong(idx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryLastModifiedMillis(uri: Uri): Long? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idx < 0 || cursor.isNull(idx)) return@use null
                cursor.getLong(idx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openBufferedReader(uri: Uri): BufferedReader? {
        val input = contentResolver.openInputStream(uri) ?: return null
        return InputStreamReader(input, Charsets.UTF_8).buffered()
    }

    private fun guessPermissions(): String {
        return "r--r--r--"
    }

    private fun formatLastModified(millis: Long?): String {
        if (millis == null || millis <= 0) return ""
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))
        } catch (_: Exception) {
            ""
        }
    }

    private fun globToRegex(glob: String, caseInsensitive: Boolean): Regex {
        val regex = StringBuilder("^")
        for (i in glob.indices) {
            val c = glob[i]
            when (c) {
                '*' -> regex.append(".*")
                '?' -> regex.append(".")
                '.' -> regex.append("\\.")
                '\\' -> regex.append("\\\\")
                '[' -> regex.append("[")
                ']' -> regex.append("]")
                '(' -> regex.append("\\(")
                ')' -> regex.append("\\)")
                '{' -> regex.append("(")
                '}' -> regex.append(")")
                ',' -> regex.append("|")
                else -> regex.append(c)
            }
        }
        regex.append("$")
        return if (caseInsensitive) {
            Regex(regex.toString(), RegexOption.IGNORE_CASE)
        } else {
            Regex(regex.toString())
        }
    }

    private fun requireTreeUriOrError(tool: AITool, uri: Uri, actionName: String): ToolResult? {
        if (!android.provider.DocumentsContract.isTreeUri(uri)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "$actionName requires a tree URI (content://.../tree/...)"
            )
        }
        return null
    }

    private fun toTreeUriOrNull(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        return android.provider.DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun resolveChildrenQueryUri(pathUri: Uri): Pair<Uri, String>? {
        val treeUri = toTreeUriOrNull(pathUri) ?: return null
        val docId = when {
            android.provider.DocumentsContract.isDocumentUri(context, pathUri) -> {
                runCatching { android.provider.DocumentsContract.getDocumentId(pathUri) }.getOrNull()
            }
            android.provider.DocumentsContract.isTreeUri(pathUri) -> {
                runCatching { android.provider.DocumentsContract.getTreeDocumentId(pathUri) }.getOrNull()
            }
            else -> {
                runCatching { android.provider.DocumentsContract.getDocumentId(pathUri) }.getOrNull()
            }
        } ?: return null
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        return childrenUri to docId
    }

    suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val docUri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid repository path: $path"
            )

        val childrenQuery = resolveChildrenQueryUri(docUri)
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "list_files requires a tree-backed document uri"
            )

        return withContext(Dispatchers.IO) {
            try {
                val childrenUri = childrenQuery.first
                val cursor = contentResolver.query(
                    childrenUri,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                        android.provider.DocumentsContract.Document.COLUMN_SIZE,
                        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )
                    ?: return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to query child documents"
                    )

                val entries = mutableListOf<DirectoryListingData.FileEntry>()
                cursor.use {
                    val nameIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (it.moveToNext()) {
                        val name = if (nameIdx >= 0 && !it.isNull(nameIdx)) it.getString(nameIdx) else null
                        if (name.isNullOrBlank()) continue
                        val mime = if (mimeIdx >= 0 && !it.isNull(mimeIdx)) it.getString(mimeIdx) else null
                        val isDir = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                        val size = if (!isDir && sizeIdx >= 0 && !it.isNull(sizeIdx)) it.getLong(sizeIdx) else 0L
                        val lastModified = if (modIdx >= 0 && !it.isNull(modIdx)) it.getLong(modIdx) else 0L
                        entries.add(
                            DirectoryListingData.FileEntry(
                                name = name,
                                isDirectory = isDir,
                                size = size,
                                permissions = guessPermissions(),
                                lastModified = formatLastModified(lastModified)
                            )
                        )
                    }
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = DirectoryListingData(path = path, entries = entries, env = envLabel),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e("SafFileSystemTools", "Error listing files: $path", e)
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error listing directory: ${e.message}")
            }
        }
    }

    suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Invalid repository path: $path")

        return withContext(Dispatchers.IO) {
            try {
                val docUri = toTreeDocumentUri(uri) ?: uri
                val mime = queryMimeType(docUri)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR || DocumentsContract.isTreeUri(uri)
                if (isDirectory) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileExistsData(path = path, exists = true, isDirectory = true, size = 0L, env = envLabel),
                        error = ""
                    )
                }

                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val size = pfd.statSize
                    pfd.close()
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileExistsData(path = path, exists = true, isDirectory = false, size = if (size >= 0) size else 0L, env = envLabel),
                        error = ""
                    )
                }

                val name = queryDisplayName(uri)
                if (!name.isNullOrBlank()) {
                    val size = querySize(uri) ?: 0L
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileExistsData(path = path, exists = true, isDirectory = false, size = size, env = envLabel),
                        error = ""
                    )
                }

                ToolResult(toolName = tool.name, success = true, result = FileExistsData(path = path, exists = false, env = envLabel), error = "")
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileExistsData(path = path, exists = false, env = envLabel),
                    error = "Error checking file existence: ${e.message}"
                )
            }
        }
    }

    suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileInfoData(path, false, "", 0L, "", "", "", "", "", envLabel),
                error = "Invalid repository path: $path"
            )

        return withContext(Dispatchers.IO) {
            try {
                val displayName = queryDisplayName(uri) ?: ""
                val size = querySize(uri) ?: 0L
                val fileType = if (android.provider.DocumentsContract.isTreeUri(uri)) "directory" else "file"
                val lastModified = formatLastModified(queryLastModifiedMillis(uri))
                val rawInfo = buildString {
                    append("Uri: $path\n")
                    if (displayName.isNotBlank()) append("DisplayName: $displayName\n")
                    append("Type: $fileType\n")
                    append("Size: $size bytes\n")
                    if (lastModified.isNotBlank()) append("Last Modified: $lastModified\n")
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileInfoData(
                        path = path,
                        exists = true,
                        fileType = fileType,
                        size = size,
                        permissions = guessPermissions(),
                        owner = "",
                        group = "",
                        lastModified = lastModified,
                        rawStatOutput = rawInfo,
                        env = envLabel
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileInfoData(path, false, "", 0L, "", "", "", "", "", envLabel),
                    error = "Error getting file information: ${e.message}"
                )
            }
        }
    }

    suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(operation = "delete", env = envLabel, path = path, successful = false, details = "Invalid repository path"),
                error = "Invalid repository path: $path"
            )

        return withContext(Dispatchers.IO) {
            try {
                val deletedCount = contentResolver.delete(uri, null, null)
                if (deletedCount > 0) {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileOperationData(operation = "delete", env = envLabel, path = path, successful = true, details = "Successfully deleted $path"),
                        error = ""
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = FileOperationData(operation = "delete", env = envLabel, path = path, successful = false, details = "Failed to delete: provider returned 0"),
                        error = "Failed to delete: provider returned 0"
                    )
                }
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(operation = "delete", env = envLabel, path = path, successful = false, details = "Error deleting: ${e.message}"),
                    error = "Error deleting file/directory: ${e.message}"
                )
            }
        }
    }

    suspend fun findFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(path = path, pattern = pattern, files = emptyList(), env = envLabel),
                error = "Invalid repository path: $path"
            )

        val treeUri = toTreeUriOrNull(uri)
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(path = path, pattern = pattern, files = emptyList(), env = envLabel),
                error = "find_files requires a tree-backed document uri"
            )

        val startDocId =
            runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrNull()
                ?: runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?: return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FindFilesResultData(path = path, pattern = pattern, files = emptyList(), env = envLabel),
                    error = "Invalid document uri: $path"
                )
        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(path = path, pattern = pattern, files = emptyList(), env = envLabel),
                error = "Path and pattern parameters are required"
            )
        }

        val usePathPattern = tool.parameters.find { it.name == "use_path_pattern" }?.value?.toBoolean() ?: false
        val caseInsensitive = tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
        val maxDepth = tool.parameters.find { it.name == "max_depth" }?.value?.toIntOrNull() ?: -1
        val regex = globToRegex(pattern, caseInsensitive)

        val baseRepoPath = if (isContentUri(path)) null else normalizeAbsolutePath(path)

        var scanned = 0
        var matched = 0
        var lastUpdateTimeMs = 0L
        var lastRel = ""

        fun updateProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && lastUpdateTimeMs != 0L && now - lastUpdateTimeMs < 500L && scanned % 200 != 0) return
            ToolProgressBus.update(
                tool.name,
                -1f,
                "Searching... scanned $scanned, found $matched" + (if (lastRel.isNotBlank()) " (at $lastRel)" else "")
            )
            lastUpdateTimeMs = now
        }

        fun walk(parentDocId: String, relPrefix: String, depth: Int, out: MutableList<String>) {
            if (maxDepth >= 0 && depth > maxDepth) return
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            ) ?: return
            cursor.use {
                val idIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    val docId = if (idIdx >= 0 && !it.isNull(idIdx)) it.getString(idIdx) else null
                    val name = if (nameIdx >= 0 && !it.isNull(nameIdx)) it.getString(nameIdx) else null
                    val mime = if (mimeIdx >= 0 && !it.isNull(mimeIdx)) it.getString(mimeIdx) else null
                    if (docId.isNullOrBlank() || name.isNullOrBlank()) continue

                    scanned++
                    val isDir = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                    val rel = if (relPrefix.isBlank()) name else relPrefix + "/" + name
                    lastRel = rel
                    val testString = if (usePathPattern) rel else name
                    if (regex.matches(testString) && !isDir) {
                        val displayPath =
                            if (baseRepoPath != null) {
                                if (baseRepoPath == "/") "/$rel" else baseRepoPath.trimEnd('/') + "/" + rel
                            } else {
                                android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                            }
                        out.add(displayPath)
                        matched++
                    }
                    updateProgress()
                    if (isDir) {
                        walk(docId, rel, depth + 1, out)
                    }
                }
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                ToolProgressBus.update(tool.name, -1f, "Searching...")
                val out = mutableListOf<String>()
                walk(startDocId, "", 0, out)
                ToolProgressBus.update(tool.name, 1f, "Search completed, found ${out.size}")
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FindFilesResultData(path = path, pattern = pattern, files = out, env = envLabel),
                    error = ""
                )
            } catch (e: Exception) {
                ToolProgressBus.update(tool.name, 1f, "Search failed")
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FindFilesResultData(path = path, pattern = pattern, files = emptyList(), env = envLabel),
                    error = "Error searching for files: ${e.message}"
                )
            }
        }
    }

    suspend fun grepCode(tool: AITool): ToolResult {
        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = "grep_code is handled by StandardFileSystemTools (find_files + read_file_full)."
        )
    }

    private fun addLineNumbers(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits = lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    private fun addLineNumbers(content: String, startLineZeroBased: Int, totalLines: Int): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits = if (totalLines > 0) totalLines.toString().length else lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(startLineZeroBased + index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    private suspend fun readUpToBytes(uri: Uri, maxBytes: Int): Pair<ByteArray, Boolean> {
        return withContext(Dispatchers.IO) {
            val buf = ByteArray(maxBytes + 1)
            var total = 0
            val input = contentResolver.openInputStream(uri) ?: return@withContext Pair(ByteArray(0), false)
            BufferedInputStream(input).use { bis ->
                while (total < buf.size) {
                    val read = bis.read(buf, total, buf.size - total)
                    if (read <= 0) break
                    total += read
                }
            }
            val truncated = total > maxBytes
            val out = if (total <= maxBytes) buf.copyOf(total) else buf.copyOf(maxBytes)
            Pair(out, truncated)
        }
    }

    suspend fun readFileFull(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val textOnly = tool.parameters.find { it.name == "text_only" }?.value?.toBoolean() ?: false
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Invalid repository path: $path")

        return withContext(Dispatchers.IO) {
            try {
                if (textOnly) {
                    val sample = readUpToBytes(uri, 512).first
                    if (!FileUtils.isTextLike(sample)) {
                        return@withContext ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Skipped non-text file: $path")
                    }
                }
                val input = contentResolver.openInputStream(uri)
                    ?: return@withContext ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to open uri: $path")
                val bytes = input.use { it.readBytes() }
                if (bytes.isNotEmpty() && !FileUtils.isTextLike(bytes.take(1024).toByteArray())) {
                    return@withContext ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "File does not appear to be a text file. Use specialized tools for binary files.")
                }
                val content = bytes.toString(Charsets.UTF_8)
                ToolResult(toolName = tool.name, success = true, result = FileContentData(path = path, content = content, size = querySize(uri) ?: bytes.size.toLong(), env = envLabel), error = "")
            } catch (e: Exception) {
                AppLogger.e("SafFileSystemTools", "Error reading repository file full: $path", e)
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading file: ${e.message}")
            }
        }
    }

    suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Invalid repository path: $path")
        val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES

        return withContext(Dispatchers.IO) {
            try {
                val (bytes, truncated) = readUpToBytes(uri, maxFileSizeBytes)
                if (bytes.isNotEmpty() && !FileUtils.isTextLike(bytes.take(512).toByteArray())) {
                    return@withContext ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "File does not appear to be a text file. Use readFileFull tool for special file types.")
                }
                val content = bytes.toString(Charsets.UTF_8)
                var contentWithLineNumbers = addLineNumbers(content)
                if (truncated) {
                    contentWithLineNumbers += "\n\n... (file content truncated) ..."
                }
                ToolResult(toolName = tool.name, success = true, result = FileContentData(path = path, content = contentWithLineNumbers, size = contentWithLineNumbers.length.toLong(), env = envLabel), error = "")
            } catch (e: Exception) {
                AppLogger.e("SafFileSystemTools", "Error reading repository file: $path", e)
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading file: ${e.message}")
            }
        }
    }

    suspend fun readFilePart(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val startLineParam = tool.parameters.find { it.name == "start_line" }?.value?.toIntOrNull() ?: 1
        val endLineParam = tool.parameters.find { it.name == "end_line" }?.value?.toIntOrNull()
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Invalid repository path: $path")

        val maxFileSizeBytes = ToolExecutionLimits.MAX_FILE_READ_BYTES

        return withContext(Dispatchers.IO) {
            try {
                val reader = openBufferedReader(uri)
                    ?: return@withContext ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to open uri: $path")

                val allLines = reader.use { it.readLines() }
                val totalLines = allLines.size

                val startLine = maxOf(1, startLineParam).coerceIn(1, maxOf(1, totalLines))
                val endLine =
                    (endLineParam
                            ?: (startLine + ToolExecutionLimits.DEFAULT_FILE_READ_PART_LINES - 1))
                        .coerceIn(startLine, maxOf(1, totalLines))
                val startIndex = startLine - 1
                val endExclusive = endLine

                var partContent = if (totalLines > 0 && startIndex < totalLines) {
                    allLines.subList(startIndex, minOf(endExclusive, totalLines)).joinToString("\n")
                } else {
                    ""
                }

                val isTruncated = partContent.length > maxFileSizeBytes
                if (isTruncated) {
                    partContent = partContent.substring(0, maxFileSizeBytes)
                }

                var contentWithLineNumbers = addLineNumbers(partContent, startIndex, totalLines)
                if (isTruncated) {
                    contentWithLineNumbers += "\n\n... (file content truncated) ..."
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FilePartContentData(
                        path = path,
                        content = contentWithLineNumbers,
                        partIndex = 0,
                        totalParts = 1,
                        startLine = startIndex,
                        endLine = minOf(endExclusive, totalLines),
                        totalLines = totalLines,
                        env = envLabel
                    ),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e("SafFileSystemTools", "Error reading repository file part: $path", e)
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading file part: ${e.message}")
            }
        }
    }

    suspend fun readFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val envLabel = resolveEnvLabel(environment)
        val uri = resolveSafPathToDocumentUriOrNull(path, environment)
            ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Invalid repository path: $path")

        return withContext(Dispatchers.IO) {
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: return@withContext ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to open uri: $path")
                val bytes = input.use { it.readBytes() }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = BinaryFileContentData(path = path, contentBase64 = base64, size = querySize(uri) ?: bytes.size.toLong(), env = envLabel),
                    error = ""
                )
            } catch (e: Exception) {
                AppLogger.e("SafFileSystemTools", "Error reading repository binary file: $path", e)
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error reading binary file: ${e.message}")
            }
        }
    }
}
