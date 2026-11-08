package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermission
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class DirectoryCleanupRequest(
    val directory: File,
    val preservedNames: Set<String> = emptySet(),
    val repairDirectoryPermissions: Boolean = false,
)

internal data class RawDirectoryCleanupResult(
    val deletedBytes: Long,
    val deletedFileCount: Long,
    val failedEntryCount: Int,
    val firstFailedPath: String? = null,
)

internal class SafeDirectoryCleaner {
    suspend fun clean(requests: List<DirectoryCleanupRequest>): RawDirectoryCleanupResult =
        withContext(Dispatchers.IO) {
            val result = MutableCleanupResult()
            val visitedRoots = mutableSetOf<String>()

            requests.forEach requestLoop@{ request ->
                currentCoroutineContext().ensureActive()
                if (runCatching { Files.isSymbolicLink(request.directory.toPath()) }.getOrDefault(false)) {
                    result.recordFailure(request.directory)
                    return@requestLoop
                }
                val root = runCatching { request.directory.canonicalFile }.getOrNull()
                if (root == null) {
                    result.recordFailure(request.directory)
                    return@requestLoop
                }
                if (!visitedRoots.add(root.path) || !root.exists()) return@requestLoop
                if (!root.isDirectory) {
                    result.recordFailure(root)
                    return@requestLoop
                }

                prepareDirectory(root, request.repairDirectoryPermissions)
                val children = runCatching { root.listFiles() }.getOrNull()
                if (children == null) {
                    result.recordFailure(root)
                    return@requestLoop
                }
                children.forEach { child ->
                    if (child.name !in request.preservedNames) {
                        deleteEntry(root, child, result, request.repairDirectoryPermissions)
                    }
                }
            }

            RawDirectoryCleanupResult(
                deletedBytes = result.deletedBytes,
                deletedFileCount = result.deletedFileCount,
                failedEntryCount = result.failedEntryCount,
                firstFailedPath = result.firstFailedPath,
            )
        }

    private suspend fun deleteEntry(
        root: File,
        entry: File,
        result: MutableCleanupResult,
        repairDirectoryPermissions: Boolean,
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
                } else if (Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    result.recordFailure(file)
                }
                continue
            }

            val canonicalPath = runCatching { file.canonicalPath }.getOrNull()
            if (canonicalPath == null || !isAtOrBelow(canonicalPath, rootPath)) {
                result.recordFailure(file)
                continue
            }

            if (file.isDirectory && !frame.expanded) {
                // 空目录可直接移除，无需读取权限为 000 的挂载占位目录。
                if (file.delete()) continue
                prepareDirectory(file, repairDirectoryPermissions)
                val children = runCatching { file.listFiles() }.getOrNull()
                if (children == null) {
                    result.recordFailure(file)
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
                    result.recordFailure(file)
                }
            } else if (file.exists() && !file.delete()) {
                result.recordFailure(file)
            }
        }
    }

    private fun prepareDirectory(directory: File, repairPermissions: Boolean) {
        if (!repairPermissions) return
        // 仅由 Ubuntu 删除显式启用；不递归 chmod，也不修改符号链接目标。
        runCatching {
            if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return
            val permissions = Files.getPosixFilePermissions(directory.toPath(), LinkOption.NOFOLLOW_LINKS).toMutableSet()
            val changed = permissions.addAll(
                listOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
            if (changed) {
                // 直接按路径修改权限，避免先打开 000 目录的属性视图。
                Files.setPosixFilePermissions(directory.toPath(), permissions)
            }
        }
        // 无法修复时由正常扫描报告失败，保留删除记录和具体路径供重试。
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
        var firstFailedPath: String? = null

        fun recordFailure(file: File) {
            failedEntryCount++
            if (firstFailedPath == null) firstFailedPath = file.absolutePath
        }
    }
}