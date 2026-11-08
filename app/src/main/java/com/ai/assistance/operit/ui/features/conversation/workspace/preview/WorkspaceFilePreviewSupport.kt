package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo

private const val LOCAL_WORKSPACE_HOST = "127.0.0.1"

internal fun workspaceMimeTypeForPath(path: String): String {
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension.isBlank()) return "application/octet-stream"

    return when (extension) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs", "cjs" -> "application/javascript"
        "json" -> "application/json"
        "txt", "md", "log" -> "text/plain"
        "xml" -> "application/xml"
        "csv" -> "text/csv"
        "yaml", "yml" -> "application/yaml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "video/webm"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "3gp" -> "video/3gpp"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pdf" -> "application/pdf"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}

internal val OpenFileInfo.isHtml: Boolean
    get() = name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

internal val OpenFileInfo.isMarkdown: Boolean
    get() = name.endsWith(".md", ignoreCase = true) ||
        name.endsWith(".markdown", ignoreCase = true)

internal val OpenFileInfo.isImage: Boolean
    get() = resolvedMimeType.startsWith("image/", ignoreCase = true)

internal val OpenFileInfo.isAudio: Boolean
    get() = resolvedMimeType.startsWith("audio/", ignoreCase = true)

internal val OpenFileInfo.isVideo: Boolean
    get() = resolvedMimeType.startsWith("video/", ignoreCase = true)

internal val OpenFileInfo.isMediaPreviewable: Boolean
    get() = isImage || isAudio || isVideo

internal val OpenFileInfo.isPdf: Boolean
    get() = resolvedMimeType.equals("application/pdf", ignoreCase = true)

internal val OpenFileInfo.isWordDocument: Boolean
    get() = resolvedMimeType.equals("application/msword", ignoreCase = true) ||
        resolvedMimeType.equals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ignoreCase = true
        )

internal val OpenFileInfo.isSpreadsheetDocument: Boolean
    get() = resolvedMimeType.equals("application/vnd.ms-excel", ignoreCase = true) ||
        resolvedMimeType.equals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ignoreCase = true
        )

internal val OpenFileInfo.isReadOnlyDocumentPreviewable: Boolean
    get() = isPdf || isWordDocument || isSpreadsheetDocument

internal val OpenFileInfo.isReadOnlyPreview: Boolean
    get() = isMediaPreviewable || isReadOnlyDocumentPreviewable

internal val OpenFileInfo.resolvedMimeType: String
    get() = mimeType.ifBlank { workspaceMimeTypeForPath(path) }

internal fun workspaceShouldOpenAsDirectPreview(path: String): Boolean {
    val mimeType = workspaceMimeTypeForPath(path)
    return mimeType.startsWith("image/", ignoreCase = true) ||
        mimeType.startsWith("audio/", ignoreCase = true) ||
        mimeType.startsWith("video/", ignoreCase = true) ||
        mimeType.equals("application/pdf", ignoreCase = true) ||
        mimeType.equals("application/msword", ignoreCase = true) ||
        mimeType.equals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ignoreCase = true
        ) ||
        mimeType.equals("application/vnd.ms-excel", ignoreCase = true) ||
        mimeType.equals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ignoreCase = true
        )
}

internal fun buildWorkspacePreviewUri(
    filePath: String,
    workspaceRootPath: String,
    workspaceEnv: String?,
    cacheBustToken: String? = null
): Uri? {
    val relativePath = resolveWorkspacePreviewRelativePath(filePath, workspaceRootPath, workspaceEnv)
    if (relativePath != null) {
        val builder = Uri.Builder()
            .scheme("http")
            .encodedAuthority("$LOCAL_WORKSPACE_HOST:${LocalWebServer.WORKSPACE_PORT}")
            .path(relativePath)
        if (!cacheBustToken.isNullOrBlank()) {
            builder.appendQueryParameter("v", cacheBustToken)
        }
        return builder.build()
    }

    if (workspaceEnv.isNullOrBlank()) {
        return Uri.fromFile(File(filePath))
    }

    return null
}

private fun resolveWorkspacePreviewRelativePath(
    filePath: String,
    workspaceRootPath: String,
    workspaceEnv: String?
): String? {
    return if (workspaceEnv.isNullOrBlank()) {
        resolveLocalWorkspaceRelativePath(filePath, workspaceRootPath)
    } else {
        resolveVirtualWorkspaceRelativePath(filePath, workspaceRootPath)
    }
}

private fun resolveLocalWorkspaceRelativePath(filePath: String, workspaceRootPath: String): String? {
    val canonicalFile = runCatching { File(filePath).canonicalPath.normalizeWorkspacePath() }
        .getOrElse { filePath.normalizeWorkspacePath() }
    val canonicalRoot = runCatching { File(workspaceRootPath).canonicalPath.normalizeWorkspacePath() }
        .getOrElse { workspaceRootPath.normalizeWorkspacePath() }
        .trimEnd('/')
        .ifBlank { "/" }

    if (canonicalFile == canonicalRoot) {
        return "/"
    }

    return if (canonicalFile.startsWith("$canonicalRoot/")) {
        "/" + canonicalFile.removePrefix("$canonicalRoot/").trimStart('/')
    } else {
        null
    }
}

private fun resolveVirtualWorkspaceRelativePath(filePath: String, workspaceRootPath: String): String? {
    val normalizedFile = filePath.normalizeWorkspacePath()
    val normalizedRoot = workspaceRootPath.normalizeWorkspacePath().trimEnd('/').ifBlank { "/" }

    if (normalizedFile == normalizedRoot) {
        return "/"
    }

    return if (normalizedRoot == "/") {
        normalizedFile.ensureLeadingSlash()
    } else if (normalizedFile.startsWith("$normalizedRoot/")) {
        "/" + normalizedFile.removePrefix("$normalizedRoot/").trimStart('/')
    } else {
        null
    }
}

private fun String.normalizeWorkspacePath(): String {
    val withForwardSlash = replace('\\', '/')
    val collapsed = withForwardSlash.replace(Regex("/+"), "/")
    return collapsed.ensureLeadingSlash()
}

private fun String.ensureLeadingSlash(): String {
    return if (startsWith("/")) this else "/$this"
}
