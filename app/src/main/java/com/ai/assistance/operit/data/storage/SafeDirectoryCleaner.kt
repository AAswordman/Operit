package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class DirectoryCleanupRequest(
    val directory: File,
    val preservedNames: Set<String> = emptySet(),
)

internal data class RawDirectoryCleanupResult(
    val deletedBytes: Long,
    val deletedFileCount: Long,
    val failedEntryCount: Int,
)

internal class SafeDirectoryCleaner {
    suspend fun clean(requests: List<DirectoryCleanupRequest>): RawDirectoryCleanupResult =
        withContext(Dispatchers.IO) {
            val result = MutableCleanupResult()
            val visitedRoots = mutableSetOf<String>()

            requests.forEach requestLoop@{ request ->
                currentCoroutineContext().ensureActive()
                if (runCatching { Files.isSymbolicLink(request.directory.toPath()) }.getOrDefault(false)) {
                    result.failedEntryCount++
                    return@requestLoop
                }
                val root = runCatching { request.directory.canonicalFile }.getOrNull()
                if (root == null) {
                    result.failedEntryCount++
                    return@requestLoop
                }
                if (!visitedRoots.add(root.path) || !root.exists()) return@requestLoop
                if (!root.isDirectory) {
                    result.failedEntryCount++
                    return@requestLoop
                }

                val children = runCatching { root.listFiles() }.getOrNull()
                if (children == null) {
                    result.failedEntryCount++
                    return@requestLoop
                }
                children.forEach { child ->
                    if (child.name !in request.preservedNames) {
                        deleteEntry(root, child, result)
                    }
                }
            }

            RawDirectoryCleanupResult(
                deletedBytes = result.deletedBytes,
                deletedFileCount = result.deletedFileCount,
                failedEntryCount = result.failedEntryCount,
            )
        }

    private suspend fun deleteEntry(
        root: File,
        entry: File,
        result: MutableCleanupResult,
    ) {
        val pending = ArrayDeque<DeleteFrame>()
        pending.add(DeleteFrame(entry, expanded = false))
        val rootPath = root.canonicalPath

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val frame = pending.removeLast()
            val file = frame.file

            if (runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)) {
                if (file.delete()) {
                    result.deletedFileCount++
                } else if (file.exists()) {
                    result.failedEntryCount++
                }
                continue
            }

            val canonicalPath = runCatching { file.canonicalPath }.getOrNull()
            if (canonicalPath == null || !isAtOrBelow(canonicalPath, rootPath)) {
                result.failedEntryCount++
                continue
            }

            if (file.isDirectory && !frame.expanded) {
                val children = runCatching { file.listFiles() }.getOrNull()
                if (children == null) {
                    result.failedEntryCount++
                    continue
                }
                pending.add(DeleteFrame(file, expanded = true))
                children.forEach { pending.add(DeleteFrame(it, expanded = false)) }
                continue
            }

            if (file.isFile) {
                val bytes = runCatching { file.length() }.getOrDefault(0L)
                if (file.delete()) {
                    result.deletedBytes += bytes
                    result.deletedFileCount++
                } else if (file.exists()) {
                    result.failedEntryCount++
                }
            } else if (file.exists() && !file.delete()) {
                result.failedEntryCount++
            }
        }
    }

    private fun isAtOrBelow(path: String, root: String): Boolean =
        path == root || path.startsWith(root + File.separator)

    private data class DeleteFrame(
        val file: File,
        val expanded: Boolean,
    )

    private class MutableCleanupResult {
        var deletedBytes: Long = 0L
        var deletedFileCount: Long = 0L
        var failedEntryCount: Int = 0
    }
}