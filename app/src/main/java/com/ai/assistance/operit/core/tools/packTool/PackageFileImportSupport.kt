package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.util.AppLogger
import java.io.File

/** 从分享或文件选择器导入外部包时的文件名判断与拷贝。 */
object PackageFileImportSupport {
    private const val TAG = "PackageFileImport"
    private val SHARE_IMPORTABLE_SUFFIXES = listOf(".toolpkg", ".js")

    fun isShareImportableFileName(fileName: String): Boolean {
        val lower = fileName.substringAfterLast('/').lowercase()
        return SHARE_IMPORTABLE_SUFFIXES.any { suffix -> lower.endsWith(suffix) }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query display name for $uri", e)
        }
        val lastSegment = uri.lastPathSegment?.substringAfterLast('/')
        return lastSegment?.takeIf { it.isNotBlank() }
    }

    fun areAllShareImportable(context: Context, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) {
            return false
        }
        return uris.all { uri ->
            val name = queryDisplayName(context, uri).orEmpty()
            isShareImportableFileName(name)
        }
    }

    private fun importFromUri(
        context: Context,
        packageManager: PackageManager,
        uri: Uri
    ): PackageManager.ExternalPackageImportResult {
        val rawFileName = queryDisplayName(context, uri)
            ?: return PackageManager.ExternalPackageImportResult("Cannot determine file name")
        val fileName = rawFileName.substringAfterLast('/').substringAfterLast('\\')
        if (fileName.isBlank() || fileName == "." || fileName == "..") {
            return PackageManager.ExternalPackageImportResult("Invalid shared file name")
        }
        val cacheDir = context.cacheDir.canonicalFile
        val tempFile = File(cacheDir, fileName).canonicalFile
        if (tempFile.parentFile != cacheDir) {
            return PackageManager.ExternalPackageImportResult("Invalid shared file name")
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return PackageManager.ExternalPackageImportResult("Cannot read shared file")
            packageManager.addPackageFileFromExternalStorage(tempFile.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import package from uri=$uri", e)
            PackageManager.ExternalPackageImportResult("Error importing package: ${e.message}")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun importSharedUris(
        context: Context,
        packageManager: PackageManager,
        uris: List<Uri>
    ): PackageShareImportSummary {
        var successCount = 0
        val errors = mutableListOf<String>()
        uris.forEach { uri ->
            val result = importFromUri(context, packageManager, uri)
            if (result.message.startsWith(prefix = "Successfully imported", ignoreCase = true)) {
                successCount += 1
            } else {
                val name = queryDisplayName(context, uri) ?: uri.toString()
                errors += "$name: ${result.message}"
            }
        }
        return PackageShareImportSummary(
            successCount = successCount,
            failureCount = errors.size,
            errors = errors
        )
    }
}

data class PackageShareImportSummary(
    val successCount: Int,
    val failureCount: Int,
    val errors: List<String>
) {
    val firstError: String?
        get() = errors.firstOrNull()
}
