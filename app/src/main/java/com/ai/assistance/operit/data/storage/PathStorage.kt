package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PathStorageStats(
    val bytes: Long = 0L,
    val fileCount: Long = 0L,
    val exists: Boolean = false,
    val lastModifiedMillis: Long = 0L,
)

internal fun File.computeStorageStats(): PathStorageStats {
    if (!exists()) return PathStorageStats()
    if (isSymbolic()) return PathStorageStats(exists = true, lastModifiedMillis = lastModified())
    if (isFile) {
        return PathStorageStats(
            bytes = length().coerceAtLeast(0L),
            fileCount = 1L,
            exists = true,
            lastModifiedMillis = lastModified(),
        )
    }

    var bytes = 0L
    var files = 0L
    var lastModifiedMillis = lastModified()
    val pending = ArrayDeque<File>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val current = pending.removeLast()
        if (current.isSymbolic()) continue
        if (current.isFile) {
            bytes += current.length().coerceAtLeast(0L)
            files += 1L
            lastModifiedMillis = maxOf(lastModifiedMillis, current.lastModified())
            continue
        }
        if (current.isDirectory) {
            lastModifiedMillis = maxOf(lastModifiedMillis, current.lastModified())
            current.listFiles()?.forEach(pending::addLast)
        }
    }
    return PathStorageStats(
        bytes = bytes,
        fileCount = files,
        exists = true,
        lastModifiedMillis = lastModifiedMillis,
    )
}

internal fun File.isSymbolic(): Boolean =
    runCatching { Files.isSymbolicLink(toPath()) }.getOrDefault(false)

internal fun File.canonicalOrAbsolute(): String =
    runCatching { canonicalPath }.getOrDefault(absolutePath)

internal fun File.canonicalOrSelf(): File =
    runCatching { canonicalFile }.getOrDefault(this)

internal fun formatStorageTimestamp(millis: Long, locale: Locale = Locale.getDefault()): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("yyyy/MM/dd HH:mm", locale).format(Date(millis))
}

internal suspend fun SafeDirectoryCleaner.cleanDirectory(
    directory: File,
    preservedNames: Set<String> = emptySet(),
): RawDirectoryCleanupResult = withContext(Dispatchers.IO) {
    clean(listOf(DirectoryCleanupRequest(directory, preservedNames)))
}

internal fun estimateTextBytes(characterCount: Long): Long = characterCount.coerceAtLeast(0L)

internal fun File.deleteTreeWithProgress(
    onProgress: (deletedBytes: Long, totalBytes: Long) -> Unit,
): Boolean {
    if (!exists()) {
        onProgress(0L, 0L)
        return true
    }
    val totalBytes = computeStorageStats().bytes
    if (isFile) {
        val size = length().coerceAtLeast(0L)
        val deleted = delete()
        onProgress(if (deleted) size else 0L, size)
        return deleted
    }
    var deletedBytes = 0L
    fun report() {
        onProgress(deletedBytes.coerceAtMost(totalBytes.coerceAtLeast(deletedBytes)), totalBytes)
    }
    fun walk(file: File): Boolean {
        if (!file.exists()) return true
        // 符号链接只删链接本身，不跟随到目标。
        if (file.isSymbolic()) {
            return file.delete() || !file.exists()
        }
        if (file.isDirectory) {
            var ok = true
            file.listFiles()?.forEach { child ->
                if (!walk(child)) ok = false
            }
            return file.delete() && ok
        }
        val size = file.length().coerceAtLeast(0L)
        val deleted = file.delete()
        if (deleted) {
            deletedBytes += size
            report()
        }
        return deleted
    }
    val result = walk(this)
    onProgress(if (result) totalBytes.coerceAtLeast(deletedBytes) else deletedBytes, totalBytes)
    return result
}
