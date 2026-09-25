package com.ai.assistance.operit.data.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.Box
import io.objectbox.kotlin.boxFor

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DataStorageRepository internal constructor(
    context: Context,
    private val scanner: FileStorageScanner,
    private val cleaner: SafeDirectoryCleaner,
) {
    constructor(context: Context) : this(context, FileStorageScanner(), SafeDirectoryCleaner())

    private val appContext = context.applicationContext

    suspend fun scan(): DataStorageSnapshot = loadSnapshot(forceRefresh = false)

    suspend fun refresh(): DataStorageSnapshot = loadSnapshot(forceRefresh = true)

    fun cachedSnapshot(): DataStorageSnapshot? = snapshotCache

    fun invalidateCache() {
        snapshotCache = null
    }

    private suspend fun loadSnapshot(forceRefresh: Boolean): DataStorageSnapshot =
        snapshotMutex.withLock {
            if (!forceRefresh) {
                snapshotCache?.let { return@withLock it }
            }

            withContext(Dispatchers.IO) {
                val layout = buildLayout()
                val raw = scanner.scan(layout.roots, layout.rules)
                val counts = loadCategoryCounts(layout, raw)

                val categories =
                    StorageCategory.values().map { category ->
                        val usage = raw.categoryUsage[category] ?: RawStorageUsage()
                        val categoryCounts = counts[category] ?: CategoryCounts()
                        StorageCategoryUsage(
                            category = category,
                            bytes = usage.bytes,
                            fileCount = usage.fileCount,
                            inaccessibleEntryCount = usage.inaccessibleEntryCount,
                            itemCount = categoryCounts.primary,
                            secondaryItemCount = categoryCounts.secondary,
                            details =
                                StorageDetail.values().mapNotNull { detail ->
                                    if (detail.category != category) return@mapNotNull null
                                    val detailUsage = raw.detailUsage[detail] ?: return@mapNotNull null
                                    if (
                                        detailUsage.bytes == 0L &&
                                            detailUsage.fileCount == 0L &&
                                            detailUsage.inaccessibleEntryCount == 0
                                    ) {
                                        return@mapNotNull null
                                    }
                                    StorageDetailUsage(
                                        detail = detail,
                                        bytes = detailUsage.bytes,
                                        fileCount = detailUsage.fileCount,
                                        inaccessibleEntryCount = detailUsage.inaccessibleEntryCount,
                                    )
                                },
                        )
                    }

                DataStorageSnapshot(
                    trackedBytes = raw.scopeBytes.values.sum(),
                    deviceStorage = readDeviceStorageUsage(),
                    scopes =
                        StorageScope.values().map { scope ->
                            StorageScopeUsage(scope = scope, bytes = raw.scopeBytes[scope] ?: 0L)
                        },
                    categories = categories,
                    cleanupTargets =
                        CleanupTarget.values().map { target ->
                            val usage = raw.cleanupUsage[target] ?: RawStorageUsage()
                            CleanupTargetUsage(
                                target = target,
                                bytes = usage.bytes,
                                fileCount = usage.fileCount,
                                inaccessibleEntryCount = usage.inaccessibleEntryCount,
                            )
                        },
                    inaccessibleEntryCount = raw.inaccessibleEntryCount,
                    skippedSymbolicLinkCount = raw.skippedSymbolicLinkCount,
                    scannedAtMillis = System.currentTimeMillis(),
                ).also { snapshotCache = it }
            }
        }

    suspend fun cleanup(targets: Set<CleanupTarget>): DataStorageCleanupResult {
        if (targets.isEmpty()) {
            return DataStorageCleanupResult(emptySet(), 0L, 0L, 0)
        }

        return snapshotMutex.withLock {
            snapshotCache = null
            val cleanupLocations = buildLayout().cleanupLocations
            val requests =
                targets.flatMap { target ->
                    cleanupLocations[target].orEmpty().map { location ->
                        DirectoryCleanupRequest(
                            directory = location.directory,
                            preservedNames = location.preservedNames,
                        )
                    }
                }
            val result = cleaner.clean(requests)
            DataStorageCleanupResult(
                targets = targets,
                deletedBytes = result.deletedBytes,
                deletedFileCount = result.deletedFileCount,
                failedEntryCount = result.failedEntryCount,
            )
        }
    }

    private fun buildLayout(): StorageLayout {
        val filesDir = appContext.filesDir
        val dataDir = appContext.dataDir
        val publicRoot =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit",
            )
        val externalFiles = appContext.getExternalFilesDirs(null).filterNotNull()
        val externalCaches = appContext.externalCacheDirs.filterNotNull()

        val roots = buildList {
            add(StorageScanRoot(dataDir, StorageScope.APP_DATA))
            add(StorageScanRoot(publicRoot, StorageScope.USER_FILES))
            externalFiles.forEach { add(StorageScanRoot(it, StorageScope.USER_FILES)) }
            externalCaches.forEach { add(StorageScanRoot(it, StorageScope.CACHE)) }
        }

        val rules = mutableListOf<StorageScanRule>()
        fun rule(
            path: File,
            category: StorageCategory,
            detail: StorageDetail,
            scope: StorageScope? = null,
            cleanupTarget: CleanupTarget? = null,
        ) {
            rules += StorageScanRule(path, category, detail, scope, cleanupTarget)
        }

        val linuxRoot = File(filesDir, "usr")
        val ubuntuRoot = File(filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")
        val linuxPackageCache = File(ubuntuRoot, "var/cache/apt/archives")
        rule(linuxRoot, StorageCategory.LINUX_ENVIRONMENT, StorageDetail.LINUX_SYSTEM)
        rule(
            File(filesDir, ".local"),
            StorageCategory.LINUX_ENVIRONMENT,
            StorageDetail.LINUX_SYSTEM,
        )
        listOf(

            File(linuxRoot, "bin"),
            File(filesDir, "bin"),
            File(filesDir, "common.sh"),
            File(filesDir, "setup_fake_sysdata.sh"),
            File(filesDir, "proot-distro.zip"),
            File(filesDir, "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"),
        ).forEach { path ->
            rule(path, StorageCategory.LINUX_ENVIRONMENT, StorageDetail.TERMINAL_RUNTIME)
        }
        rule(
            File(filesDir, "tmp"),
            StorageCategory.LINUX_ENVIRONMENT,
            StorageDetail.TERMINAL_RUNTIME,
            StorageScope.CACHE,
            CleanupTarget.TEMPORARY_FILES,
        )
        rule(
            linuxPackageCache,
            StorageCategory.CACHE_AND_TEMPORARY,
            StorageDetail.LINUX_PACKAGE_CACHE,
            StorageScope.CACHE,
            CleanupTarget.LINUX_PACKAGE_CACHE,
        )

        val modelsRoot = File(publicRoot, "models")
        rule(modelsRoot, StorageCategory.LOCAL_MODELS, StorageDetail.OTHER_MODELS)
        rule(File(modelsRoot, "mnn"), StorageCategory.LOCAL_MODELS, StorageDetail.MNN_MODELS)
        rule(File(modelsRoot, "llama"), StorageCategory.LOCAL_MODELS, StorageDetail.LLAMA_MODELS)
        rule(
            File(filesDir, ".sherpa_ncnn_models"),
            StorageCategory.LOCAL_MODELS,
            StorageDetail.SPEECH_MODELS,
        )

        rule(
            File(filesDir, "workspace"),
            StorageCategory.WORKSPACES_AND_MEDIA,
            StorageDetail.INTERNAL_WORKSPACES,
        )
        rule(
            File(publicRoot, "workspace"),
            StorageCategory.WORKSPACES_AND_MEDIA,
            StorageDetail.SHARED_WORKSPACES,
        )
        rule(
            File(filesDir, "image_pool"),
            StorageCategory.WORKSPACES_AND_MEDIA,
            StorageDetail.MEDIA_POOLS,
        )
        rule(
            File(filesDir, "media_pool"),
            StorageCategory.WORKSPACES_AND_MEDIA,
            StorageDetail.MEDIA_POOLS,
        )

        val databaseFile = appContext.getDatabasePath("app_database")
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            rule(
                File(databaseFile.absolutePath + suffix),
                StorageCategory.CHAT_HISTORY,
                StorageDetail.CHAT_DATABASE,
            )
        }

        val memoryDirectories =
            filesDir.listFiles { file ->
                file.isDirectory && (file.name == "objectbox" || file.name.startsWith("objectbox_"))
            }.orEmpty().ifEmpty { arrayOf(File(filesDir, "objectbox")) }
        memoryDirectories.forEach { directory ->
            rule(directory, StorageCategory.MEMORY_LIBRARY, StorageDetail.MEMORY_DATABASES)
        }
        rule(
            File(filesDir, ".vector_index"),
            StorageCategory.MEMORY_LIBRARY,
            StorageDetail.VECTOR_INDEXES,
        )
        rule(
            File(filesDir, "memory-space-profiles"),
            StorageCategory.MEMORY_LIBRARY,
            StorageDetail.MEMORY_DATABASES,
        )

        rule(
            File(publicRoot, "backup"),
            StorageCategory.BACKUPS_AND_EXPORTS,
            StorageDetail.BACKUPS,
        )
        rule(
            File(publicRoot, "exports"),
            StorageCategory.BACKUPS_AND_EXPORTS,
            StorageDetail.EXPORTS,
        )

        rule(
            File(filesDir, "datastore"),
            StorageCategory.CONFIGURATION,
            StorageDetail.PREFERENCES,
        )
        rule(
            File(dataDir, "datastore"),
            StorageCategory.CONFIGURATION,
            StorageDetail.PREFERENCES,
        )
        rule(
            File(dataDir, "shared_prefs"),
            StorageCategory.CONFIGURATION,
            StorageDetail.PREFERENCES,
        )
        rule(
            File(filesDir, "custom_emoji"),
            StorageCategory.CONFIGURATION,
            StorageDetail.CHARACTER_ASSETS,
        )

        listOf("plugins", "mcp_plugins", "bridge", "dev_package").forEach { child ->
            rule(
                File(publicRoot, child),
                StorageCategory.PACKAGES_AND_PLUGINS,
                StorageDetail.PLUGIN_FILES,
            )
        }
        rule(
            File(publicRoot, "skills"),
            StorageCategory.PACKAGES_AND_PLUGINS,
            StorageDetail.SKILL_FILES,
        )
        externalFiles.forEach { directory ->
            rule(
                File(directory, "packages"),
                StorageCategory.PACKAGES_AND_PLUGINS,
                StorageDetail.PLUGIN_FILES,
            )
        }

        val cleanupLocations = mutableMapOf<CleanupTarget, MutableList<CleanupLocation>>()
        fun cleanupRule(
            path: File,
            detail: StorageDetail,
            target: CleanupTarget,
            preservedNames: Set<String> = emptySet(),
        ) {
            rule(
                path,
                StorageCategory.CACHE_AND_TEMPORARY,
                detail,
                StorageScope.CACHE,
                target,
            )
            cleanupLocations.getOrPut(target, ::mutableListOf) +=
                CleanupLocation(path, preservedNames)
        }

        cleanupRule(appContext.cacheDir, StorageDetail.APP_CACHE, CleanupTarget.APP_CACHE)
        cleanupRule(appContext.codeCacheDir, StorageDetail.CODE_CACHE, CleanupTarget.APP_CACHE)
        externalCaches.forEach { directory ->
            cleanupRule(directory, StorageDetail.EXTERNAL_CACHE, CleanupTarget.APP_CACHE)
        }
        cleanupRule(
            File(publicRoot, "cleanOnExit"),
            StorageDetail.TEMPORARY_FILES,
            CleanupTarget.TEMPORARY_FILES,
            preservedNames = setOf(".nomedia"),
        )
        cleanupRule(
            File(publicRoot, "cache"),
            StorageDetail.TEMPORARY_FILES,
            CleanupTarget.TEMPORARY_FILES,
        )
        cleanupLocations.getOrPut(CleanupTarget.TEMPORARY_FILES, ::mutableListOf) +=
            CleanupLocation(File(filesDir, "tmp"))
        cleanupRule(
            File(filesDir, "image_cache"),
            StorageDetail.TEMPORARY_FILES,
            CleanupTarget.TEMPORARY_FILES,
        )
        cleanupRule(File(filesDir, "logs"), StorageDetail.LOG_FILES, CleanupTarget.LOG_FILES)
        cleanupRule(
            File(filesDir, "toolpkg_cache"),
            StorageDetail.PACKAGE_CACHE,
            CleanupTarget.PACKAGE_CACHE,
        )
        cleanupRule(
            File(filesDir, "skill_repo_zip_pool"),
            StorageDetail.PACKAGE_CACHE,
            CleanupTarget.PACKAGE_CACHE,
        )
        cleanupLocations.getOrPut(CleanupTarget.LINUX_PACKAGE_CACHE, ::mutableListOf) +=
            CleanupLocation(linuxPackageCache, setOf("lock"))

        rule(
            File(dataDir, "app_webview"),
            StorageCategory.OTHER,
            StorageDetail.BROWSER_DATA,
        )
        rule(
            File(dataDir, "no_backup/.webview"),
            StorageCategory.OTHER,
            StorageDetail.BROWSER_DATA,
        )

        return StorageLayout(roots, rules, cleanupLocations.mapValues { it.value.toList() })
    }

    private suspend fun loadCategoryCounts(
        layout: StorageLayout,
        raw: RawStorageScan,
    ): Map<StorageCategory, CategoryCounts> {
        val counts = mutableMapOf<StorageCategory, CategoryCounts>()
        runCatching {
            val database = AppDatabase.getDatabase(appContext)
            counts[StorageCategory.CHAT_HISTORY] =
                CategoryCounts(
                    primary = database.chatDao().getTotalChatCount().toLong(),
                    secondary = database.messageDao().getTotalMessageCount().toLong(),
                )
        }.onFailure { AppLogger.w(TAG, "Unable to read chat counts: ${it.message}") }

        runCatching {
            val profileIds =
                UserPreferencesManager.getInstance(appContext).memorySpaceListFlow.first()
                    .ifEmpty { listOf("default") }
                    .distinct()
            var memoryCount = 0L
            profileIds.forEach { profileId ->
                val directoryName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
                if (File(appContext.filesDir, directoryName).isDirectory) {
                    val box: Box<Memory> = ObjectBoxManager.get(appContext, profileId).boxFor()
                    memoryCount += box.count()
                }
            }
            counts[StorageCategory.MEMORY_LIBRARY] =
                CategoryCounts(memoryCount, profileIds.size.toLong())
        }.onFailure { AppLogger.w(TAG, "Unable to read memory counts: ${it.message}") }

        runCatching {
            val cardCount =
                CharacterCardManager.getInstance(appContext).characterCardListFlow.first().size.toLong()
            val configCount = ModelConfigManager(appContext).configListFlow.first().size.toLong()
            counts[StorageCategory.CONFIGURATION] = CategoryCounts(cardCount, configCount)
        }.onFailure { AppLogger.w(TAG, "Unable to read configuration counts: ${it.message}") }

        val rulesByDetail = layout.rules.groupBy { it.detail }
        counts[StorageCategory.LOCAL_MODELS] =
            CategoryCounts(
                primary =
                    countImmediateEntries(
                        rulesByDetail
                            .filterKeys {
                                it == StorageDetail.MNN_MODELS ||
                                    it == StorageDetail.LLAMA_MODELS ||
                                    it == StorageDetail.SPEECH_MODELS
                            }.values.flatten().map { it.path },
                    ),
            )
        counts[StorageCategory.WORKSPACES_AND_MEDIA] =
            CategoryCounts(
                primary =
                    countImmediateEntries(
                        rulesByDetail
                            .filterKeys {
                                it == StorageDetail.INTERNAL_WORKSPACES ||
                                    it == StorageDetail.SHARED_WORKSPACES
                            }.values.flatten().map { it.path },
                    ),
            )
        counts[StorageCategory.BACKUPS_AND_EXPORTS] =
            CategoryCounts(primary = raw.categoryUsage[StorageCategory.BACKUPS_AND_EXPORTS]?.fileCount)
        counts[StorageCategory.PACKAGES_AND_PLUGINS] =
            CategoryCounts(
                primary =
                    countImmediateEntries(
                        rulesByDetail
                            .filterKeys {
                                it == StorageDetail.PLUGIN_FILES || it == StorageDetail.SKILL_FILES
                            }.values.flatten().map { it.path },
                    ),
            )
        counts[StorageCategory.CACHE_AND_TEMPORARY] =
            CategoryCounts(primary = raw.categoryUsage[StorageCategory.CACHE_AND_TEMPORARY]?.fileCount)
        return counts
    }

    private fun countImmediateEntries(paths: List<File>): Long =
        paths.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sumOf { directory ->
                runCatching {
                    directory.listFiles()?.count { it.isDirectory || it.isFile }?.toLong() ?: 0L
                }.getOrDefault(0L)
            }

    private fun readDeviceStorageUsage(): DeviceStorageUsage? =
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            DeviceStorageUsage(
                totalBytes = stat.blockCountLong * stat.blockSizeLong,
                availableBytes = stat.availableBlocksLong * stat.blockSizeLong,
            )
        }.onFailure { AppLogger.w(TAG, "Unable to read device storage: ${it.message}") }
            .getOrNull()
    private data class StorageLayout(
        val roots: List<StorageScanRoot>,
        val rules: List<StorageScanRule>,
        val cleanupLocations: Map<CleanupTarget, List<CleanupLocation>>,
    )

    private data class CleanupLocation(
        val directory: File,
        val preservedNames: Set<String> = emptySet(),
    )

    private data class CategoryCounts(
        val primary: Long? = null,
        val secondary: Long? = null,
    )
    private val StorageDetail.category: StorageCategory
        get() =
            when (this) {
                StorageDetail.LINUX_SYSTEM,
                StorageDetail.TERMINAL_RUNTIME -> StorageCategory.LINUX_ENVIRONMENT
                StorageDetail.MNN_MODELS,
                StorageDetail.LLAMA_MODELS,
                StorageDetail.SPEECH_MODELS,
                StorageDetail.OTHER_MODELS -> StorageCategory.LOCAL_MODELS
                StorageDetail.INTERNAL_WORKSPACES,
                StorageDetail.SHARED_WORKSPACES,
                StorageDetail.MEDIA_POOLS -> StorageCategory.WORKSPACES_AND_MEDIA
                StorageDetail.CHAT_DATABASE -> StorageCategory.CHAT_HISTORY
                StorageDetail.MEMORY_DATABASES,
                StorageDetail.VECTOR_INDEXES -> StorageCategory.MEMORY_LIBRARY
                StorageDetail.BACKUPS,
                StorageDetail.EXPORTS -> StorageCategory.BACKUPS_AND_EXPORTS
                StorageDetail.PREFERENCES,
                StorageDetail.CHARACTER_ASSETS -> StorageCategory.CONFIGURATION
                StorageDetail.PLUGIN_FILES,
                StorageDetail.SKILL_FILES -> StorageCategory.PACKAGES_AND_PLUGINS
                StorageDetail.APP_CACHE,
                StorageDetail.CODE_CACHE,
                StorageDetail.EXTERNAL_CACHE,
                StorageDetail.TEMPORARY_FILES,
                StorageDetail.LOG_FILES,
                StorageDetail.PACKAGE_CACHE,
                StorageDetail.LINUX_PACKAGE_CACHE -> StorageCategory.CACHE_AND_TEMPORARY
                StorageDetail.BROWSER_DATA,
                StorageDetail.OTHER_APP_DATA,
                StorageDetail.OTHER_USER_FILES -> StorageCategory.OTHER
            }

    private companion object {
        const val TAG = "DataStorageRepository"
        private val snapshotMutex = Mutex()

        @Volatile private var snapshotCache: DataStorageSnapshot? = null
    }
}