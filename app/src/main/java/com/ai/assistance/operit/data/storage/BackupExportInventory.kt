package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.data.backup.OperitBackupDirs
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BackupExportKind {
    CHAT_BACKUP,
    DATABASE_BACKUP,
    RAW_SNAPSHOT,
    CHARACTER_CARDS,
    MEMORY,
    MODEL_CONFIG,
    EXPORT_FILE,
    UNKNOWN,
}

data class BackupExportEntry(
    val id: String,
    val name: String,
    val path: File,
    val kind: BackupExportKind,
    val bytes: Long,
    val lastModifiedMillis: Long,
    val valid: Boolean,
)

data class BackupExportSnapshot(
    val entries: List<BackupExportEntry>,
    val totalBytes: Long,
    val scannedAtMillis: Long,
)

class BackupExportInventory(context: Context) {
    private val storageRepository = DataStorageRepository(context)
    private val cleaner = SafeDirectoryCleaner()

    suspend fun load(): BackupExportSnapshot = withContext(Dispatchers.IO) {
        val roots = listOf(
            OperitBackupDirs.backupRootDir(),
            OperitPaths.exportsDir(),
        )
        val files = roots.flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown().filter { it.isFile }.toList()
        }
        val entries = files.map { file ->
            BackupExportEntry(
                id = file.canonicalOrAbsolute(),
                name = file.name,
                path = file,
                kind = classify(file),
                bytes = file.length().coerceAtLeast(0L),
                lastModifiedMillis = file.lastModified(),
                valid = file.length() > 0L,
            )
        }.sortedByDescending { it.lastModifiedMillis }

        BackupExportSnapshot(
            entries = entries,
            totalBytes = entries.sumOf { it.bytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(entries: List<BackupExportEntry>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            onProgress(entry.name, index, entries.size, released)
            val ok = if (entry.path.isFile) entry.path.delete() else cleaner.cleanDirectory(entry.path).failedEntryCount == 0
            if (ok || !entry.path.exists()) {
                deleted++
                released += entry.bytes
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }

    private fun classify(file: File): BackupExportKind {
        val name = file.name.lowercase()
        val parent = file.parentFile?.name.orEmpty().lowercase()
        return when {
            name.startsWith("chat_backup_") || parent == "chat" -> BackupExportKind.CHAT_BACKUP
            name.startsWith("room_db_") || parent == "room_db" -> BackupExportKind.DATABASE_BACKUP
            name.contains("raw_snapshot") || parent == "raw_snapshot" -> BackupExportKind.RAW_SNAPSHOT
            name.startsWith("character_cards_backup_") || parent == "character_cards" -> BackupExportKind.CHARACTER_CARDS
            name.startsWith("memory_backup_") || parent == "memory" -> BackupExportKind.MEMORY
            name.startsWith("model_config_backup_") || parent == "model_config" -> BackupExportKind.MODEL_CONFIG
            file.absolutePath.contains("${File.separator}exports${File.separator}") -> BackupExportKind.EXPORT_FILE
            else -> BackupExportKind.UNKNOWN
        }
    }
}
