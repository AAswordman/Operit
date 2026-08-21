package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.persistence.StorageProcessLock
import com.ai.assistance.operit.data.stats.TokenUsageRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RoomDatabaseRestoreManager {
    private const val DB_NAME = "app_database"

    private const val AUTO_BACKUP_FILE_PREFIX = "room_db_backup_"
    private const val MANUAL_BACKUP_FILE_PREFIX = "room_db_manual_backup_"

    fun listRecentAutoBackups(context: Context, limit: Int = 3): List<File> {
        val newDir = OperitBackupDirs.roomDbDir()
        val legacyDir = OperitBackupDirs.operitRootDir()

        val backups = sequenceOf(newDir, legacyDir)
            .flatMap { dir ->
                (dir.listFiles { f ->
                    f.isFile && f.name.startsWith(AUTO_BACKUP_FILE_PREFIX) && f.name.endsWith(".zip")
                }?.asSequence() ?: emptySequence())
            }
            .distinctBy { it.name }
            .toList()

        return backups.sortedByDescending { it.name }.take(limit)
    }

    fun listRecentBackups(context: Context, limit: Int = 3): List<File> {
        val newDir = OperitBackupDirs.roomDbDir()
        val legacyDir = OperitBackupDirs.operitRootDir()

        val backups = sequenceOf(newDir, legacyDir)
            .flatMap { dir ->
                (dir.listFiles { f ->
                    f.isFile && isRoomDatabaseBackupFile(f.name)
                }?.asSequence() ?: emptySequence())
            }
            .distinctBy { it.name }
            .toList()

        return backups
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .take(limit)
    }

    fun isRoomDatabaseBackupFile(name: String): Boolean {
        return (name.startsWith(AUTO_BACKUP_FILE_PREFIX) || name.startsWith(MANUAL_BACKUP_FILE_PREFIX)) &&
            name.endsWith(".zip")
    }

    suspend fun restoreFromBackupUri(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val cacheFile = File.createTempFile("room_db_restore_", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Failed to open uri")
                TokenUsageRepository.withDatabaseRestore {
                    StorageProcessLock.withExclusiveAccess(context, "room-database-restore") {
                        restoreFromBackupFileInternal(context, cacheFile)
                    }
                }
            } finally {
                cacheFile.delete()
            }
        }
    }

    suspend fun restoreFromBackupFile(context: Context, zipFile: File) {
        withContext(Dispatchers.IO) {
            TokenUsageRepository.withDatabaseRestore {
                StorageProcessLock.withExclusiveAccess(context, "room-database-restore") {
                    restoreFromBackupFileInternal(context, zipFile)
                }
            }
        }
    }

    private fun restoreFromBackupFileInternal(context: Context, zipFile: File) {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw IllegalArgumentException("Backup file not found: ${zipFile.absolutePath}")
        }

        val workDir =
            File(context.cacheDir, "room_restore_${UUID.randomUUID()}")
        check(workDir.mkdirs()) { "Failed to create Room restore work directory" }
        val tmpDb = File(workDir, DB_NAME)
        val tmpWal = File(workDir, "${DB_NAME}-wal")
        val tmpShm = File(workDir, "${DB_NAME}-shm")

        var extractedDb = false
        var extractedWal = false
        var extractedShm = false
        val extractedNames = mutableSetOf<String>()

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    when (name) {
                        DB_NAME -> {
                            check(extractedNames.add(name)) {
                                "Invalid backup zip: duplicate $name"
                            }
                            writeStreamToFile(zis, tmpDb)
                            extractedDb = true
                        }
                        "${DB_NAME}-wal" -> {
                            check(extractedNames.add(name)) {
                                "Invalid backup zip: duplicate $name"
                            }
                            writeStreamToFile(zis, tmpWal)
                            extractedWal = true
                        }
                        "${DB_NAME}-shm" -> {
                            check(extractedNames.add(name)) {
                                "Invalid backup zip: duplicate $name"
                            }
                            writeStreamToFile(zis, tmpShm)
                            extractedShm = true
                        }
                    }

                    zis.closeEntry()
                }
            }

            if (!extractedDb) {
                throw IllegalArgumentException("Invalid backup zip: missing $DB_NAME")
            }

            if (!extractedWal) tmpWal.delete()
            if (!extractedShm) tmpShm.delete()
            AppDatabase.replaceWithVerifiedDatabase(
                context,
                tmpDb,
                reason = "user_restore"
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeStreamToFile(input: ZipInputStream, target: File) {
        val buffer = ByteArray(64 * 1024)
        BufferedOutputStream(FileOutputStream(target)).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
        }
    }

}
