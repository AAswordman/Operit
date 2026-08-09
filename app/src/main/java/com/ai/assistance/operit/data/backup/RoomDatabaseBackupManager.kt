package com.ai.assistance.operit.data.backup

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.persistence.StorageProcessLock
import com.ai.assistance.operit.data.stats.TokenUsageRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

object RoomDatabaseBackupManager {

    private const val TAG = "RoomDbBackup"
    private const val DB_NAME = "app_database"
    private const val AUTO_BACKUP_FILE_PREFIX = "room_db_backup_"
    private const val MANUAL_BACKUP_FILE_PREFIX = "room_db_manual_backup_"

    data class BackupResult(
        val performed: Boolean,
        val backupFile: File? = null,
        val skippedReason: String? = null
    )

    suspend fun pruneExcessBackups(context: Context) {
        TokenUsageRepository.withDatabaseAccess {
            val preferences = RoomDatabaseBackupPreferences.getInstance(context)
            val maxBackupCount = preferences.getMaxBackupCount()
            enforceMaxBackupCount(context, keepLatest = maxBackupCount)
        }
    }

    suspend fun backupIfNeeded(context: Context, force: Boolean): BackupResult {
        return TokenUsageRepository.withDatabaseAccess {
            val preferences = RoomDatabaseBackupPreferences.getInstance(context)
            val enabled = preferences.isDailyBackupEnabled()
            val maxBackupCount = preferences.getMaxBackupCount()
            if (!enabled && !force) {
                return@withDatabaseAccess BackupResult(performed = false, skippedReason = "disabled")
            }

            if (force) {
                val backupFile = createManualBackup(context)
                enforceMaxBackupCount(context, keepLatest = maxBackupCount)
                return@withDatabaseAccess BackupResult(performed = true, backupFile = backupFile)
            }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val lastValue = preferences.lastBackupDayFlow.first()
            if (lastValue == today) {
                return@withDatabaseAccess BackupResult(performed = false, skippedReason = "already_backed_up_today")
            }

            val backupFile = createOrReplaceAutoBackup(context, today)
            preferences.markSuccess(today, System.currentTimeMillis())
            enforceMaxBackupCount(context, keepLatest = maxBackupCount)
            BackupResult(performed = true, backupFile = backupFile)
        }
    }

    private suspend fun createOrReplaceAutoBackup(context: Context, day: String): File {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found: ${dbFile.absolutePath}")
        }

        val operitDir = OperitBackupDirs.roomDbDir()

        val targetFile = File(operitDir, "${AUTO_BACKUP_FILE_PREFIX}${day}.zip")
        val tmpFile = File(operitDir, "${targetFile.name}.tmp")

        if (tmpFile.exists()) {
            tmpFile.delete()
        }

        writeValidatedZip(context, tmpFile)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        return targetFile
    }

    private suspend fun createManualBackup(context: Context): File {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found: ${dbFile.absolutePath}")
        }

        val operitDir = OperitBackupDirs.roomDbDir()

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val targetFile = File(operitDir, "${MANUAL_BACKUP_FILE_PREFIX}${timestamp}.zip")
        val tmpFile = File(operitDir, "${targetFile.name}.tmp")

        if (tmpFile.exists()) {
            tmpFile.delete()
        }

        writeValidatedZip(context, tmpFile)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }

        return targetFile
    }

    private fun enforceMaxBackupCount(context: Context, keepLatest: Int) {
        val safeKeepLatest = keepLatest.coerceIn(1, 100)

        val newDir = OperitBackupDirs.roomDbDir()
        val legacyDir = OperitBackupDirs.operitRootDir()
        val dirs = listOf(newDir, legacyDir)

        val candidates = dirs
            .flatMap { dir ->
                dir.listFiles { f ->
                    f.isFile && isRoomDatabaseBackupFileName(f.name)
                }?.toList() ?: emptyList()
            }

        if (candidates.isEmpty()) return

        val grouped = candidates.groupBy { it.name }

        val deduped = grouped.mapNotNull { (_, files) ->
            val sorted = files.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.absolutePath })
            sorted.drop(1).forEach { it.delete() }
            sorted.firstOrNull()
        }

        val sorted = deduped.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
        sorted.drop(safeKeepLatest).forEach { it.delete() }
    }

    private fun isRoomDatabaseBackupFileName(name: String): Boolean {
        return (name.startsWith(AUTO_BACKUP_FILE_PREFIX) || name.startsWith(MANUAL_BACKUP_FILE_PREFIX)) &&
            name.endsWith(".zip")
    }

    private fun writeZip(outputFile: File, entries: Map<String, File>) {
        val buffer = ByteArray(64 * 1024)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            entries.forEach { (entryName, file) ->
                if (!file.exists() || !file.isFile) return@forEach

                zos.putNextEntry(ZipEntry(entryName))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    private fun writeValidatedZip(context: Context, outputFile: File) {
        StorageProcessLock.withExclusiveAccess(context, "room-database-backup") {
            AppDatabase.getDatabase(context).openHelper.writableDatabase
            val snapshot = AppDatabase.stageForSnapshotExport(context)
            try {
                val copiedDb = snapshot.files.single().file
                check(copiedDb.isFile) {
                    "Room backup staging did not produce $DB_NAME"
                }
                writeZip(
                    outputFile,
                    mapOf(DB_NAME to copiedDb)
                )
            } finally {
                snapshot.close()
            }
        }
    }

    private fun parseBackupDay(fileName: String): LocalDate {
        val raw = fileName.removePrefix(AUTO_BACKUP_FILE_PREFIX).removeSuffix(".zip")
        return try {
            LocalDate.parse(raw, DateTimeFormatter.ISO_DATE)
        } catch (_: Exception) {
            LocalDate.MIN
        }
    }
}
