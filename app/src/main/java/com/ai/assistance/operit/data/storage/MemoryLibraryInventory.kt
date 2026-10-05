package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.normalizeFolderPath
import com.ai.assistance.operit.util.OperitPaths
import io.objectbox.kotlin.boxFor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class MemoryStorageEntry(
    val profileId: String,
    val profileName: String,
    val memoryId: Long,
    val uuid: String,
    val title: String,
    val folderPath: String?,
    val estimatedBytes: Long,
    val updatedAtMillis: Long,
    val isDocument: Boolean,
)

data class MemoryFolderGroup(
    val key: String,
    val profileId: String,
    val profileName: String,
    val folderPath: String?,
    val folderName: String,
    val memoryCount: Int,
    val estimatedBytes: Long,
    val updatedAtMillis: Long,
    val documentCount: Int,
    val entries: List<MemoryStorageEntry>,
)

data class MemoryLibrarySnapshot(
    val entries: List<MemoryStorageEntry>,
    val folders: List<MemoryFolderGroup>,
    val profileCount: Int,
    val memoryCount: Int,
    val folderCount: Int,
    val databaseBytes: Long,
    val estimatedBytes: Long,
    val scannedAtMillis: Long,
)

class MemoryLibraryInventory(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = UserPreferencesManager.getInstance(appContext)
    private val storageRepository = DataStorageRepository(appContext)

    suspend fun load(): MemoryLibrarySnapshot = withContext(Dispatchers.IO) {
        val profileIds = preferences.memorySpaceListFlow.first().ifEmpty { listOf("default") }.distinct()
        val entries = mutableListOf<MemoryStorageEntry>()
        var databaseBytes = 0L
        profileIds.forEach { profileId ->
            val spaceName = runCatching { preferences.getMemorySpaceFlow(profileId).first().name }
                .getOrDefault(profileId)
            val dbName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
            val dbDir = File(appContext.filesDir, dbName)
            val dbStats = dbDir.computeStorageStats()
            databaseBytes += dbStats.bytes
            if (!dbDir.isDirectory) return@forEach
            val memories = runCatching {
                ObjectBoxManager.get(appContext, profileId).boxFor<Memory>().all
            }.getOrDefault(emptyList())
            val searchable = memories.filterNot { memory ->
                val title = memory.title.trim()
                title == ".folder_placeholder"
            }
            val profileShare = if (searchable.isEmpty()) 0L else dbStats.bytes
            searchable.forEach { memory ->
                val contentBytes = estimateTextBytes(memory.content.length.toLong() + memory.title.length.toLong())
                val share = if (searchable.isEmpty()) 0L else profileShare / searchable.size
                entries += MemoryStorageEntry(
                    profileId = profileId,
                    profileName = spaceName,
                    memoryId = memory.id,
                    uuid = memory.uuid,
                    title = memory.title.ifBlank { memory.uuid },
                    folderPath = normalizeFolderPath(memory.folderPath),
                    estimatedBytes = contentBytes + share,
                    updatedAtMillis = memory.updatedAt.time,
                    isDocument = memory.isDocumentNode,
                )
            }
        }
        val indexBytes = OperitPaths.vectorIndexDir(appContext).computeStorageStats().bytes
        val folders = entries.groupBy { "${it.profileId}:${it.folderPath.orEmpty()}" }.map { (_, grouped) ->
            val first = grouped.first()
            val folderPath = first.folderPath
            MemoryFolderGroup(
                key = "${first.profileId}:${folderPath.orEmpty()}",
                profileId = first.profileId,
                profileName = first.profileName,
                folderPath = folderPath,
                folderName = folderPath?.substringAfterLast('/') ?: "",
                memoryCount = grouped.size,
                estimatedBytes = grouped.sumOf { it.estimatedBytes },
                updatedAtMillis = grouped.maxOf { it.updatedAtMillis },
                documentCount = grouped.count { it.isDocument },
                entries = grouped,
            )
        }.sortedByDescending { it.estimatedBytes }
        MemoryLibrarySnapshot(
            entries = entries.sortedByDescending { it.estimatedBytes },
            folders = folders,
            profileCount = profileIds.size,
            memoryCount = entries.size,
            folderCount = folders.size,
            databaseBytes = databaseBytes + indexBytes,
            estimatedBytes = entries.sumOf { it.estimatedBytes } + indexBytes,
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(entries: List<MemoryStorageEntry>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.groupBy { it.profileId }.forEach { (profileId, grouped) ->
            val repository = MemoryRepository(appContext, profileId)
            grouped.forEachIndexed { index, entry ->
                onProgress(entry.title, deleted + failed, entries.size, released)
                val ok = runCatching { repository.deleteMemory(entry.memoryId) }.getOrDefault(false)
                if (ok) {
                    deleted++
                    released += entry.estimatedBytes
                } else {
                    failed++
                }
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }
}
