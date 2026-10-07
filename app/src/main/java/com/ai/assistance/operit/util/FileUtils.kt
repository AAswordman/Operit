package com.ai.assistance.operit.util

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

object FileUtils {

    private const val TAG = "FileUtils"
    private const val BACKGROUND_IMAGES_DIR = "background_images"
    private const val BACKGROUND_VIDEOS_DIR = "background_videos"

    // List of common video file extensions
    private val VIDEO_EXTENSIONS = listOf("mp4", "3gp", "webm", "mkv", "avi", "mov", "flv", "wmv")

    private val TEXT_BASED_EXTENSIONS = setOf(
        // Common text files
        "txt", "md", "log", "ini", "env", "csv", "tsv", "text", "me",

        // Web files
        "html", "htm", "css", "js", "json", "xml", "yaml", "yml", "svg", "url",
        "sass", "scss", "less", "ejs", "hbs", "pug", "rss", "atom", "vtt", "webmanifest", "jsp", "asp", "aspx",

        // Programming language source files
        "java", "kt", "kts", "gradle",
        "c", "cpp", "h", "hpp", "cs", "m",
        "py", "rb", "php", "go", "swift",
        "ts", "tsx", "jsx",
        "sh", "bat", "ps1", "zsh",
        "sql", "groovy", "lua", "perl", "pl", "r", "dart", "rust", "rs", "scala",
        "asm", "pas", "f", "f90", "for", "lisp", "hs", "erl", "vb", "vbs", "tcl", "d", "nim", "sol", "zig", "vala", "cob", "cbl",

        // Config files
        "properties", "toml", "dockerfile", "gitignore", "gitattributes", "editorconfig", "conf", "cfg",
        "jsonc", "json5", "reg", "iml", "inf",

        // Document formats & Data Serialization
        "rtf", "tex", "srt", "sub", "asciidoc", "adoc", "rst", "org", "wiki", "mediawiki",
        "vcf", "ics", "gpx", "kml", "opml"
    )

    // Common text files without extensions
    private val TEXT_BASED_FILENAMES = setOf(
        "readme", "makefile", "dockerfile", "license", "changelog", "authors", 
        "contributors", "copying", "install", "news", "todo", "version",
        "gemfile", "rakefile", "vagrantfile", "buildfile"
    )

    /**
     * Checks if a file extension corresponds to a text-based file format.
     * @param extension The file extension without the dot (e.g., "txt", "java").
     * @return True if the extension is for a known text-based file, false otherwise.
     */
    fun isTextBasedExtension(extension: String): Boolean {
        return extension.lowercase() in TEXT_BASED_EXTENSIONS
    }

    fun isTextBasedFileName(fileName: String): Boolean {
        val name = fileName.trim()
        if (name.isBlank()) return false

        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return if (ext.isBlank() || ext == name) {
            name.lowercase() in TEXT_BASED_FILENAMES
        } else {
            isTextBasedExtension(ext)
        }
    }

    /**
     * Checks if a file appears to be text-like by reading its first few bytes.
     * This is more reliable than extension checking as it analyzes actual content.
     * 
     * @param file The file to check
     * @param sampleSize Number of bytes to read for analysis (default: 512)
     * @return True if the file appears to contain text, false otherwise
     */
    fun isTextLike(file: File, sampleSize: Int = 512): Boolean {
        if (!file.exists() || !file.canRead()) {
            return false
        }

        // Empty files are considered text
        if (file.length() == 0L) {
            return true
        }

        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(minOf(sampleSize.toLong(), file.length()).toInt())
                val bytesRead = input.read(buffer)
                
                if (bytesRead <= 0) {
                    return true // Empty or unreadable, treat as text
                }

                return isTextLikeBytes(buffer, bytesRead)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking if file is text-like: ${file.path}", e)
            return false
        }
    }

    /**
     * Checks if a file appears to be text-like by reading its first few bytes from a path.
     * This version accepts a file path string.
     * 
     * @param path The file path to check
     * @param sampleSize Number of bytes to read for analysis (default: 512)
     * @return True if the file appears to contain text, false otherwise
     */
    fun isTextLike(path: String, sampleSize: Int = 512): Boolean {
        return isTextLike(File(path), sampleSize)
    }

    /**
     * Check if the given byte array appears to be text content.
     * This is useful when you already have a byte sample and want to check if it's text.
     * 
     * @param bytes The byte array to analyze
     * @return True if content appears to be text
     */
    fun isTextLike(bytes: ByteArray): Boolean {
        return isTextLikeBytes(bytes, bytes.size)
    }

    /**
     * Analyzes a byte array to determine if it contains text-like content.
     * 
     * Algorithm:
     * - Checks for null bytes (strong indicator of binary)
     * - Counts printable characters (ASCII 32-126)
     * - Counts common whitespace characters (tab, newline, carriage return)
     * - Counts UTF-8 continuation bytes and common UTF-8 patterns
     * 
     * @param bytes The byte array to analyze
     * @param length Number of bytes to analyze
     * @return True if content appears to be text
     */
    private fun isTextLikeBytes(bytes: ByteArray, length: Int): Boolean {
        if (length == 0) return true

        var textChars = 0
        var nonTextChars = 0

        var i = 0
        while (i < length) {
            val byte = bytes[i].toInt() and 0xFF

            when {
                // ASCII printable, tab, newline, carriage return
                byte >= 32 && byte <= 126 || byte == 9 || byte == 10 || byte == 13 -> {
                    textChars++
                    i++
                }
                // UTF-8 multi-byte sequences
                byte >= 0xC2 && byte <= 0xDF -> { // 2-byte sequence
                    if (i + 1 < length && (bytes[i + 1].toInt() and 0xC0 == 0x80)) {
                        textChars += 2
                        i += 2
                    } else {
                        nonTextChars++
                        i++
                    }
                }
                byte >= 0xE0 && byte <= 0xEF -> { // 3-byte sequence
                    if (i + 2 < length && (bytes[i + 1].toInt() and 0xC0 == 0x80) && (bytes[i + 2].toInt() and 0xC0 == 0x80)) {
                        textChars += 3
                        i += 3
                    } else {
                        nonTextChars++
                        i++
                    }
                }
                byte >= 0xF0 && byte <= 0xF4 -> { // 4-byte sequence
                    if (i + 3 < length && (bytes[i + 1].toInt() and 0xC0 == 0x80) && (bytes[i + 2].toInt() and 0xC0 == 0x80) && (bytes[i + 3].toInt() and 0xC0 == 0x80)) {
                        textChars += 4
                        i += 4
                    } else {
                        nonTextChars++
                        i++
                    }
                }
                else -> {
                    // Includes null bytes, control chars, invalid UTF-8 start bytes, etc.
                    nonTextChars++
                    i++
                }
            }
        }

        // If there are non-text characters, check the ratio.
        // A small number of weird characters might be acceptable in some text files.
        // If there are zero non-text characters, it's definitely text.
        // If there are any, and the ratio of non-text to text is high, it's binary.
        // We can define "binary" as having more than 10% non-text characters.
        if (nonTextChars == 0) return true
        
        // Avoid division by zero for empty or invalid files
        val totalChars = textChars + nonTextChars
        if (totalChars == 0) return true // Or false, depending on desired behavior for empty/unreadable

        return (nonTextChars.toDouble() / totalChars) < 0.1
    }

    /**
     * Checks if a file is a text-based file, considering both extension and filename.
     * @param file The file to check.
     * @return True if the file is likely a text-based file, false otherwise.
     */
    fun isTextBasedFile(file: File): Boolean {
        val extension = file.extension
        return if (extension.isEmpty()) {
            // Files without extension - check common text filenames
            file.name.lowercase() in TEXT_BASED_FILENAMES
        } else {
            isTextBasedExtension(extension)
        }
    }

    /**
     * Checks if a file is a valid workspace file to be shown or backed up.
     * This combines several rules:
     * - Must be a text-based file.
     * - Must not be a hidden file (name starts with '.').
     * - Must not be inside a '.backup' directory.
     *
     * @param file The file to check.
     * @param workspaceRoot The root of the workspace, for path checking.
     * @param gitignoreRules A list of .gitignore rules.
     * @return True if the file should be included in the workspace view/backup.
     */
    fun isWorkspaceFile(file: File, workspaceRoot: File, gitignoreRules: List<String>): Boolean {
        if (!file.isFile) return false

        // Use GitIgnoreFilter to check if the file should be ignored
        if (com.ai.assistance.operit.ui.features.chat.webview.workspace.process.GitIgnoreFilter.shouldIgnore(file, workspaceRoot, gitignoreRules)) {
            return false
        }

        // Also apply the basic text-based file check
        return isTextBasedFile(file)
    }

    /**
     * Check if a URI points to a video file
     * @param context The application context
     * @param uri The URI to check
     * @return true if the URI is a video file, false otherwise
     */
    fun isVideoFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false
        // Check if MIME type starts with "video/"
        if (mimeType.startsWith("video/")) return true

        // If MIME type doesn't tell us, check the file extension
        val extension = getFileExtension(context, uri)
        return extension != null && extension.lowercase() in VIDEO_EXTENSIONS
    }

    /**
     * Get the file extension from a URI
     * @param context The application context
     * @param uri The URI to get the extension from
     * @return The file extension or null if it couldn't be determined
     */
    fun getFileExtension(context: Context, uri: Uri): String? {
        // First try to get from content resolver
        val mimeType = context.contentResolver.getType(uri)
        return if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            // Fallback to path parsing
            val path = uri.path
            return path?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Copies a file from a given URI to the app's internal storage.
     * This makes the file private to the app and ensures persistent access.
     * @param context The context.
     * @param uri The URI of the file to copy.
     * @param uniqueName A unique name or prefix for the file to prevent overwriting.
     * @return The URI of the copied file in internal storage, or null on failure.
     */
    suspend fun copyFileToInternalStorage(context: Context, uri: Uri, uniqueName: String): Uri? = withContext(Dispatchers.IO) {
        var copiedFile: File? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                AppLogger.e("FileUtils", "Failed to open input stream for URI: $uri")
                return@withContext null
            }
            inputStream.use { input ->
                val originalExtension = getFileExtensionFromUri(context, uri) ?: "dat"
                val file = File(context.filesDir, "${uniqueName}_${UUID.randomUUID()}.${originalExtension}")
                copiedFile = file
                file.outputStream().use { output -> input.copyTo(output, 4 * 1024) }
            }
            val file = checkNotNull(copiedFile)
            AppLogger.d("FileUtils", "File copied successfully to internal storage: ${file.absolutePath}")
            Uri.fromFile(file)
        } catch (e: Exception) {
            // 复制失败只清理本次生成的半成品，已保存的旧资源继续由配置持有。
            copiedFile?.let { file ->
                if (file.exists() && !file.delete()) {
                    AppLogger.w("FileUtils", "清理复制失败的资源文件失败: ${file.absolutePath}")
                }
            }
            if (e is CancellationException) throw e
            AppLogger.e("FileUtils", "Error copying file to internal storage", e)
            null
        }
    }
    
    /**
     * Get the file extension from a URI
     * @param context The application context
     * @param uri The URI to get the extension from
     * @return The file extension or null if it couldn't be determined
     */
    private suspend fun getFileExtensionFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        // First try to get from the URI path itself
        val uriPath = uri.path
        if (uriPath != null) {
            val pathExtension = uriPath.substringAfterLast('.', "")
            if (pathExtension.isNotEmpty() && pathExtension.length <= 10 && !pathExtension.contains('/')) {
                return@withContext pathExtension.lowercase()
            }
        }
        
        // Try to get from content resolver
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (extension != null) {
                return@withContext extension.lowercase()
            }
        }
        
        // Try to get filename from content resolver
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val fileName = cursor.getString(nameIndex)
                    val fileExtension = fileName?.substringAfterLast('.', "")
                    if (!fileExtension.isNullOrEmpty() && fileExtension.length <= 10) {
                        return@withContext fileExtension.lowercase()
                    }
                }
            }
        }
        
        return@withContext null
    }

    /**
     * Clean up old background media files to prevent using too much storage Keeps only the most
     * recent file
     */
    private fun cleanOldBackgroundFiles(directory: File, currentFileName: String) {
        try {
            val files = directory.listFiles()
            if (files != null && files.size > 1) {
                // Delete all files except the current one
                files.forEach { file ->
                    if (file.name != currentFileName) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error cleaning old background files", e)
        }
    }

    /**
     * 检查视频文件大小是否超过限制
     * @param context 上下文
     * @param uri 视频文件URI
     * @param maxSizeMB 最大允许大小，单位MB
     * @return 如果视频大小在限制内返回true，否则返回false
     */
    fun checkVideoSize(context: Context, uri: Uri, maxSizeMB: Int = 30): Boolean {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fileSize = pfd.statSize
                val maxSizeBytes = maxSizeMB * 1024 * 1024L
                return fileSize <= maxSizeBytes
            }
        } catch (e: Exception) {
            AppLogger.e("FileUtils", "检查视频大小时出错", e)
        }
        // 如果无法检查大小，返回true以避免阻止用户选择
        return true
    }
}
