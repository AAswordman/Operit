package com.ai.assistance.operit.data.storage

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LinuxStorageUnitKind {
    ROOT,
    HOME,
    APT_CACHE,
    LOGS_TEMP,
    TERMINAL_PACKAGES,
}

data class LinuxStorageUnit(
    val kind: LinuxStorageUnitKind,
    val path: File,
    val extraPaths: List<File> = emptyList(),
    val bytes: Long,
    val fileCount: Long,
    val exists: Boolean,
    val locked: Boolean,
    val lastModifiedMillis: Long,
) {
    fun deletionPaths(): List<File> =
        when (kind) {
            LinuxStorageUnitKind.TERMINAL_PACKAGES -> extraPaths
            else -> listOf(path) + extraPaths
        }.distinctBy { it.canonicalOrAbsolute() }
}

data class LinuxEnvironmentSnapshot(
    val units: List<LinuxStorageUnit>,
    val totalBytes: Long,
    val ubuntuInstalled: Boolean,
    val scannedAtMillis: Long,
)

class LinuxEnvironmentInventory(
    context: Context,
) {
    private val cleaner = SafeDirectoryCleaner()
    private val storageRepository = DataStorageRepository(context)
    private val filesDir = context.applicationContext.filesDir
    private val ubuntuRoot =
        File(filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")

    suspend fun load(): LinuxEnvironmentSnapshot = withContext(Dispatchers.IO) {
        val home = File(ubuntuRoot, "home")
        val aptCache = File(ubuntuRoot, "var/cache/apt/archives")
        val logs = File(ubuntuRoot, "var/log")
        val ubuntuTmp = File(ubuntuRoot, "tmp")
        val appTmp = File(filesDir, "tmp")
        val terminalPackages =
            listOf(
                File(filesDir, "proot-distro.zip"),
                File(filesDir, "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"),
                File(filesDir, "bin"),
                File(filesDir, "common.sh"),
                File(filesDir, "setup_fake_sysdata.sh"),
            ).filter { it.exists() }

        val homeStats = home.computeStorageStats()
        val aptStats = aptCache.computeStorageStats()
        val logStats = logs.computeStorageStats()
        val ubuntuTmpStats = ubuntuTmp.computeStorageStats()
        val appTmpStats = appTmp.computeStorageStats()
        val packageStats = terminalPackages.fold(PathStorageStats()) { acc, file ->
            val stats = file.computeStorageStats()
            PathStorageStats(
                bytes = acc.bytes + stats.bytes,
                fileCount = acc.fileCount + stats.fileCount,
                exists = acc.exists || stats.exists,
                lastModifiedMillis = maxOf(acc.lastModifiedMillis, stats.lastModifiedMillis),
            )
        }
        val rootStats = ubuntuRoot.computeStorageStats()
        val localStats = File(filesDir, ".local").computeStorageStats()
        val lockedRootBytes =
            (rootStats.bytes - homeStats.bytes - aptStats.bytes - logStats.bytes - ubuntuTmpStats.bytes)
                .coerceAtLeast(0L) + localStats.bytes
        val lockedRootFiles =
            (rootStats.fileCount - homeStats.fileCount - aptStats.fileCount - logStats.fileCount - ubuntuTmpStats.fileCount)
                .coerceAtLeast(0L) + localStats.fileCount

        val units = listOf(
            LinuxStorageUnit(
                kind = LinuxStorageUnitKind.ROOT,
                path = ubuntuRoot,
                bytes = lockedRootBytes,
                fileCount = lockedRootFiles,
                exists = ubuntuRoot.isDirectory,
                locked = true,
                lastModifiedMillis = rootStats.lastModifiedMillis,
            ),
            LinuxStorageUnit(
                kind = LinuxStorageUnitKind.HOME,
                path = home,
                bytes = homeStats.bytes,
                fileCount = homeStats.fileCount,
                exists = home.exists(),
                locked = false,
                lastModifiedMillis = homeStats.lastModifiedMillis,
            ),
            LinuxStorageUnit(
                kind = LinuxStorageUnitKind.APT_CACHE,
                path = aptCache,
                bytes = aptStats.bytes,
                fileCount = aptStats.fileCount,
                exists = aptCache.exists(),
                locked = false,
                lastModifiedMillis = aptStats.lastModifiedMillis,
            ),
            LinuxStorageUnit(
                kind = LinuxStorageUnitKind.LOGS_TEMP,
                path = logs,
                extraPaths = listOf(ubuntuTmp, appTmp),
                bytes = logStats.bytes + ubuntuTmpStats.bytes + appTmpStats.bytes,
                fileCount = logStats.fileCount + ubuntuTmpStats.fileCount + appTmpStats.fileCount,
                exists = logs.exists() || ubuntuTmp.exists() || appTmp.exists(),
                locked = false,
                lastModifiedMillis = maxOf(
                    logStats.lastModifiedMillis,
                    ubuntuTmpStats.lastModifiedMillis,
                    appTmpStats.lastModifiedMillis,
                ),
            ),
            LinuxStorageUnit(
                kind = LinuxStorageUnitKind.TERMINAL_PACKAGES,
                path = filesDir,
                extraPaths = terminalPackages,
                bytes = packageStats.bytes,
                fileCount = packageStats.fileCount,
                exists = packageStats.exists,
                locked = false,
                lastModifiedMillis = packageStats.lastModifiedMillis,
            ),
        )

        LinuxEnvironmentSnapshot(
            units = units,
            totalBytes = units.filter { it.kind != LinuxStorageUnitKind.APT_CACHE }.sumOf { it.bytes },
            ubuntuInstalled = ubuntuRoot.isDirectory,
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(unit: LinuxStorageUnit): StorageDeleteBatchResult = withContext(Dispatchers.IO) {
        if (unit.locked) {
            return@withContext StorageDeleteBatchResult(0, 1, 0L)
        }
        val paths = unit.deletionPaths()
        val preserved = if (unit.kind == LinuxStorageUnitKind.APT_CACHE) setOf("lock") else emptySet()
        var deletedBytes = 0L
        var deletedFiles = 0L
        var failed = 0
        paths.filter { it.exists() }.forEach { path ->
            if (path.isFile) {
                val bytes = path.length()
                if (path.delete()) {
                    deletedBytes += bytes
                    deletedFiles += 1L
                } else {
                    failed++
                }
            } else {
                val result = cleaner.clean(listOf(DirectoryCleanupRequest(path, preserved)))
                deletedBytes += result.deletedBytes
                deletedFiles += result.deletedFileCount
                failed += result.failedEntryCount
            }
        }
        storageRepository.invalidateCache()
        StorageDeleteBatchResult(
            deletedCount = if (failed == 0) 1 else 0,
            failedCount = failed,
            releasedBytes = deletedBytes,
        )
    }
}
