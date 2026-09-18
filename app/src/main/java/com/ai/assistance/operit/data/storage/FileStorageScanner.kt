package com.ai.assistance.operit.data.storage

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class StorageScanRoot(
    val directory: File,
    val defaultScope: StorageScope,
)

internal data class StorageScanRule(
    val path: File,
    val category: StorageCategory,
    val detail: StorageDetail,
    val scope: StorageScope? = null,
    val cleanupTarget: CleanupTarget? = null,
)

internal data class RawStorageUsage(
    val bytes: Long = 0L,
    val fileCount: Long = 0L,
    val inaccessibleEntryCount: Int = 0,
)

internal data class RawStorageScan(
    val scopeBytes: Map<StorageScope, Long>,
    val categoryUsage: Map<StorageCategory, RawStorageUsage>,
    val detailUsage: Map<StorageDetail, RawStorageUsage>,
    val cleanupUsage: Map<CleanupTarget, RawStorageUsage>,
    val inaccessibleEntryCount: Int,
    val skippedSymbolicLinkCount: Int,
)

internal class FileStorageScanner {
    suspend fun scan(
        roots: List<StorageScanRoot>,
        rules: List<StorageScanRule>,
    ): RawStorageScan = withContext(Dispatchers.IO) {
        val symbolicLinkRootCount =
            roots.count { root ->
                runCatching { Files.isSymbolicLink(root.directory.toPath()) }.getOrDefault(false)
            }
        val preparedRoots = prepareRoots(roots)
        val preparedRules = prepareRules(rules)
        val scopeStats = mutableMapOf<StorageScope, Long>()
        val categoryStats = mutableMapOf<StorageCategory, MutableUsage>()
        val detailStats = mutableMapOf<StorageDetail, MutableUsage>()
        val cleanupStats = mutableMapOf<CleanupTarget, MutableUsage>()
        val visitedDirectories = mutableSetOf<String>()
        var inaccessibleEntryCount = 0
        var skippedSymbolicLinkCount = symbolicLinkRootCount
        val coroutineContext = currentCoroutineContext()

        preparedRoots.forEach { root ->
            if (!root.file.exists()) return@forEach
            val pending = ArrayDeque<File>()
            pending.add(root.file)

            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                val current = pending.removeLast()

                if (runCatching { Files.isSymbolicLink(current.toPath()) }.getOrDefault(false)) {
                    skippedSymbolicLinkCount++
                    continue
                }

                val canonicalPath =
                    try {
                        current.canonicalPath
                    } catch (_: Exception) {
                        inaccessibleEntryCount++
                        markInaccessible(
                            path = current.absolutePath,
                            root = root,
                            rules = preparedRules,
                            categoryStats = categoryStats,
                            detailStats = detailStats,
                            cleanupStats = cleanupStats,
                        )
                        continue
                    }

                if (!isAtOrBelow(canonicalPath, root.canonicalPath)) {
                    skippedSymbolicLinkCount++
                    continue
                }

                when {
                    current.isDirectory -> {
                        if (!visitedDirectories.add(canonicalPath)) continue
                        val children =
                            try {
                                current.listFiles()
                            } catch (_: SecurityException) {
                                null
                            }
                        if (children == null) {
                            inaccessibleEntryCount++
                            markInaccessible(
                                path = canonicalPath,
                                root = root,
                                rules = preparedRules,
                                categoryStats = categoryStats,
                                detailStats = detailStats,
                                cleanupStats = cleanupStats,
                            )
                        } else {
                            children.forEach(pending::addLast)
                        }
                    }

                    current.isFile -> {
                        val bytes = runCatching { current.length().coerceAtLeast(0L) }.getOrDefault(0L)
                        val ownership = resolveOwnership(canonicalPath, root, preparedRules)
                        scopeStats[ownership.scope] = (scopeStats[ownership.scope] ?: 0L) + bytes
                        categoryStats.getOrPut(ownership.category, ::MutableUsage).addFile(bytes)
                        detailStats.getOrPut(ownership.detail, ::MutableUsage).addFile(bytes)
                        ownership.cleanupTarget?.let { target ->
                            cleanupStats.getOrPut(target, ::MutableUsage).addFile(bytes)
                        }
                    }
                }
            }
        }

        RawStorageScan(
            scopeBytes = scopeStats.toMap(),
            categoryUsage = categoryStats.mapValues { it.value.freeze() },
            detailUsage = detailStats.mapValues { it.value.freeze() },
            cleanupUsage = cleanupStats.mapValues { it.value.freeze() },
            inaccessibleEntryCount = inaccessibleEntryCount,
            skippedSymbolicLinkCount = skippedSymbolicLinkCount,
        )
    }

    private fun prepareRoots(roots: List<StorageScanRoot>): List<PreparedRoot> {
        val candidates =
            roots.mapNotNull { root ->
                if (runCatching { Files.isSymbolicLink(root.directory.toPath()) }.getOrDefault(false)) {
                    return@mapNotNull null
                }
                runCatching {
                    PreparedRoot(root.directory.canonicalFile, root.directory.canonicalPath, root.defaultScope)
                }.getOrNull()
            }.distinctBy { it.canonicalPath }
                .sortedBy { it.canonicalPath.length }

        val selected = mutableListOf<PreparedRoot>()
        candidates.forEach { candidate ->
            if (selected.none { parent -> isAtOrBelow(candidate.canonicalPath, parent.canonicalPath) }) {
                selected += candidate
            }
        }
        return selected
    }

    private fun prepareRules(rules: List<StorageScanRule>): List<PreparedRule> =
        rules.mapNotNull { rule ->
            runCatching {
                PreparedRule(
                    canonicalPath = rule.path.canonicalPath,
                    category = rule.category,
                    detail = rule.detail,
                    scope = rule.scope,
                    cleanupTarget = rule.cleanupTarget,
                )
            }.getOrNull()
        }.distinctBy { rule ->
            listOf(
                rule.canonicalPath,
                rule.category.name,
                rule.detail.name,
                rule.scope?.name,
                rule.cleanupTarget?.name,
            )
        }.sortedByDescending { it.canonicalPath.length }

    private fun markInaccessible(
        path: String,
        root: PreparedRoot,
        rules: List<PreparedRule>,
        categoryStats: MutableMap<StorageCategory, MutableUsage>,
        detailStats: MutableMap<StorageDetail, MutableUsage>,
        cleanupStats: MutableMap<CleanupTarget, MutableUsage>,
    ) {
        val ownership = resolveOwnership(path, root, rules)
        categoryStats.getOrPut(ownership.category, ::MutableUsage).inaccessibleEntryCount++
        detailStats.getOrPut(ownership.detail, ::MutableUsage).inaccessibleEntryCount++
        ownership.cleanupTarget?.let { target ->
            cleanupStats.getOrPut(target, ::MutableUsage).inaccessibleEntryCount++
        }
    }

    private fun resolveOwnership(
        path: String,
        root: PreparedRoot,
        rules: List<PreparedRule>,
    ): Ownership {
        val rule = rules.firstOrNull { candidate -> isAtOrBelow(path, candidate.canonicalPath) }
        if (rule != null) {
            return Ownership(
                category = rule.category,
                detail = rule.detail,
                scope = rule.scope ?: root.defaultScope,
                cleanupTarget = rule.cleanupTarget,
            )
        }

        return when (root.defaultScope) {
            StorageScope.CACHE ->
                Ownership(
                    category = StorageCategory.CACHE_AND_TEMPORARY,
                    detail = StorageDetail.APP_CACHE,
                    scope = StorageScope.CACHE,
                    cleanupTarget = CleanupTarget.APP_CACHE,
                )

            StorageScope.USER_FILES ->
                Ownership(
                    category = StorageCategory.OTHER,
                    detail = StorageDetail.OTHER_USER_FILES,
                    scope = StorageScope.USER_FILES,
                    cleanupTarget = null,
                )

            StorageScope.APP_DATA ->
                Ownership(
                    category = StorageCategory.OTHER,
                    detail = StorageDetail.OTHER_APP_DATA,
                    scope = StorageScope.APP_DATA,
                    cleanupTarget = null,
                )
        }
    }

    private fun isAtOrBelow(path: String, root: String): Boolean =
        path == root || path.startsWith(root + File.separator)

    private data class PreparedRoot(
        val file: File,
        val canonicalPath: String,
        val defaultScope: StorageScope,
    )

    private data class PreparedRule(
        val canonicalPath: String,
        val category: StorageCategory,
        val detail: StorageDetail,
        val scope: StorageScope?,
        val cleanupTarget: CleanupTarget?,
    )

    private data class Ownership(
        val category: StorageCategory,
        val detail: StorageDetail,
        val scope: StorageScope,
        val cleanupTarget: CleanupTarget?,
    )

    private class MutableUsage {
        var bytes: Long = 0L
        var fileCount: Long = 0L
        var inaccessibleEntryCount: Int = 0

        fun addFile(fileBytes: Long) {
            bytes += fileBytes
            fileCount++
        }

        fun freeze(): RawStorageUsage =
            RawStorageUsage(
                bytes = bytes,
                fileCount = fileCount,
                inaccessibleEntryCount = inaccessibleEntryCount,
            )
    }
}
