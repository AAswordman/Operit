package com.ai.assistance.operit.data.storage

enum class StorageCategory {
    LINUX_ENVIRONMENT,
    LOCAL_MODELS,
    WORKSPACES_AND_MEDIA,
    CHAT_HISTORY,
    MEMORY_LIBRARY,
    BACKUPS_AND_EXPORTS,
    CONFIGURATION,
    PACKAGES_AND_PLUGINS,
    CACHE_AND_TEMPORARY,
    OTHER,
}

enum class StorageScope {
    APP_DATA,
    USER_FILES,
    CACHE,
}

enum class StorageDetail {
    LINUX_SYSTEM,
    TERMINAL_RUNTIME,
    MNN_MODELS,
    LLAMA_MODELS,
    SPEECH_MODELS,
    OTHER_MODELS,
    INTERNAL_WORKSPACES,
    SHARED_WORKSPACES,
    MEDIA_POOLS,
    CHAT_DATABASE,
    MEMORY_DATABASES,
    VECTOR_INDEXES,
    BACKUPS,
    EXPORTS,
    PREFERENCES,
    CHARACTER_ASSETS,
    PLUGIN_FILES,
    SKILL_FILES,
    APP_CACHE,
    CODE_CACHE,
    EXTERNAL_CACHE,
    TEMPORARY_FILES,
    LOG_FILES,
    PACKAGE_CACHE,
    LINUX_PACKAGE_CACHE,
    BROWSER_DATA,
    OTHER_APP_DATA,
    OTHER_USER_FILES,
}

enum class CleanupTarget {
    APP_CACHE,
    TEMPORARY_FILES,
    LOG_FILES,
    PACKAGE_CACHE,
    LINUX_PACKAGE_CACHE,
}

data class StorageDetailUsage(
    val detail: StorageDetail,
    val bytes: Long,
    val fileCount: Long,
    val inaccessibleEntryCount: Int,
)

data class StorageCategoryUsage(
    val category: StorageCategory,
    val bytes: Long,
    val fileCount: Long,
    val inaccessibleEntryCount: Int,
    val itemCount: Long? = null,
    val secondaryItemCount: Long? = null,
    val details: List<StorageDetailUsage> = emptyList(),
)

data class StorageScopeUsage(
    val scope: StorageScope,
    val bytes: Long,
)

data class CleanupTargetUsage(
    val target: CleanupTarget,
    val bytes: Long,
    val fileCount: Long,
    val inaccessibleEntryCount: Int,
)

data class DeviceStorageUsage(
    val totalBytes: Long,
    val availableBytes: Long,
)

data class DataStorageSnapshot(
    val trackedBytes: Long,
    val deviceStorage: DeviceStorageUsage?,
    val scopes: List<StorageScopeUsage>,
    val categories: List<StorageCategoryUsage>,
    val cleanupTargets: List<CleanupTargetUsage>,
    val inaccessibleEntryCount: Int,
    val skippedSymbolicLinkCount: Int,
    val scannedAtMillis: Long,
)

data class DataStorageCleanupResult(
    val targets: Set<CleanupTarget>,
    val deletedBytes: Long,
    val deletedFileCount: Long,
    val failedEntryCount: Int,
)
