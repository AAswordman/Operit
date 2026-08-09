package com.ai.assistance.operit.data.persistence

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.util.AtomicFile
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RoomRecoveryStorage {
    const val DATABASE_NAME = "app_database"

    private const val TAG = "RoomRecovery"
    private const val FORMAT_VERSION = 1
    private val lock = Any()
    private var preparedForOpen = false
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class SnapshotMetadata(
        val formatVersion: Int,
        val databaseName: String,
        val sequence: Long,
        val createdAt: Long,
        val size: Long,
        val sha256: String
    )

    private data class VerifiedSnapshot(
        val databaseFile: File,
        val metadata: SnapshotMetadata
    )

    private class UnsupportedDatabaseVersionException(val version: Int) :
        IllegalStateException("Room database version $version is newer than this application")

    private class PreservingCorruptionHandler : DatabaseErrorHandler {
        var corruptionReported: Boolean = false
            private set

        override fun onCorruption(database: SQLiteDatabase) {
            corruptionReported = true
            // Android's default handler deletes the source before openDatabase returns. Recovery
            // must retain it until quarantine succeeds, so validation only records this signal.
            AppLogger.e(TAG, "SQLite reported Room corruption; source retained: ${database.path}")
        }
    }

    fun prepareForOpen(context: Context) {
        synchronized(lock) {
            if (preparedForOpen) return
            val appContext = context.applicationContext
            val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
            if (!databaseFile.isFile) {
                val invalidDataPath = databaseFile.exists()
                val eventKind = if (invalidDataPath) "room_invalid_data_path" else "room_missing"
                val remnants = databaseFiles(databaseFile).filter { it.exists() }
                val snapshot = readLatestSnapshotForRecovery(appContext)
                if (snapshot == null) {
                    if (remnants.isNotEmpty() || hasSnapshotArtifacts(appContext)) {
                        val quarantine =
                            remnants.takeIf { it.isNotEmpty() }?.let {
                                quarantine(appContext, it)
                            }
                        PreferenceRecoveryStorage.recordStorageEvent(
                            appContext,
                            DATABASE_NAME,
                            eventKind,
                            "preserved_without_verified_snapshot"
                        )
                        throw StorageRecoveryException(
                            "Room database is missing and recovery artifacts exist, but no " +
                                "verified snapshot can be opened." +
                                (quarantine?.let {
                                    " Remnants were preserved at ${it.absolutePath}"
                                } ?: "")
                        )
                    }
                    preparedForOpen = true
                    return
                }
                val quarantine =
                    remnants.takeIf { it.isNotEmpty() }?.let { quarantine(appContext, it) }
                replaceDatabase(databaseFile, snapshot.databaseFile)
                if (!checkpointAndValidate(appContext, databaseFile)) {
                    throw StorageRecoveryException(
                        "Verified Room snapshot could not be opened after restoring a missing " +
                            "database file."
                    )
                }
                writeSnapshot(appContext, databaseFile)
                PreferenceRecoveryStorage.recordStorageEvent(
                    appContext,
                    DATABASE_NAME,
                    eventKind,
                    "restored_snapshot"
                )
                if (quarantine != null) {
                    AppLogger.i(TAG, "Room sidecars preserved at ${quarantine.absolutePath}")
                }
                AppLogger.w(TAG, "Restored missing or invalid Room database from verified snapshot")
                preparedForOpen = true
                return
            }

            quarantineAndRemoveInvalidSidecars(appContext, databaseFile)

            try {
                if (checkpointAndValidate(appContext, databaseFile)) {
                    writeSnapshot(appContext, databaseFile)
                    preparedForOpen = true
                    return
                }
            } catch (e: UnsupportedDatabaseVersionException) {
                PreferenceRecoveryStorage.recordStorageEvent(
                    appContext,
                    DATABASE_NAME,
                    "room_version",
                    "newer_version_preserved"
                )
                throw StorageRecoveryException(
                    "Room database version ${e.version} requires a newer Operit build. " +
                        "The live database was preserved without replacement."
                )
            }

            val sourceFiles = databaseFiles(databaseFile)
            val quarantine = quarantine(appContext, sourceFiles)
            val snapshot = readLatestSnapshotForRecovery(appContext)
            if (snapshot == null) {
                PreferenceRecoveryStorage.recordStorageEvent(
                    appContext,
                    DATABASE_NAME,
                    "room_corruption",
                    "preserved_without_snapshot"
                )
                throw StorageRecoveryException(
                    "Room database is corrupt and no verified recovery snapshot exists. " +
                        "Original files were preserved at ${quarantine.absolutePath}"
                )
            }

            replaceDatabase(databaseFile, snapshot.databaseFile)
            if (!checkpointAndValidate(appContext, databaseFile)) {
                throw StorageRecoveryException(
                    "Verified Room snapshot could not be opened after replacement; " +
                        "original files remain at ${quarantine.absolutePath}"
                )
            }
            writeSnapshot(appContext, databaseFile)
            PreferenceRecoveryStorage.recordStorageEvent(
                appContext,
                DATABASE_NAME,
                "room_corruption",
                "restored_snapshot"
            )
            AppLogger.w(TAG, "Restored corrupt Room database from verified snapshot")
            preparedForOpen = true
        }
    }

    fun checkpointClosed(context: Context) {
        synchronized(lock) {
            try {
                val appContext = context.applicationContext
                val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
                if (!databaseFile.exists()) return
                if (!checkpointAndValidate(appContext, databaseFile)) {
                    AppLogger.e(TAG, "Closed Room database failed quick_check; snapshot not updated")
                    return
                }
                writeSnapshot(appContext, databaseFile)
            } finally {
                preparedForOpen = false
            }
        }
    }

    internal fun invalidatePreparedState() {
        synchronized(lock) {
            preparedForOpen = false
        }
    }

    fun validateDatabaseSet(context: Context, databaseFile: File): Boolean =
        synchronized(lock) {
            try {
                checkpointAndValidate(context.applicationContext, databaseFile)
            } catch (e: UnsupportedDatabaseVersionException) {
                AppLogger.e(TAG, "Room database requires a newer schema version", e)
                false
            }
        }

    fun replaceWithVerifiedDatabase(
        context: Context,
        replacementDatabaseFile: File,
        reason: String
    ) {
        synchronized(lock) {
            preparedForOpen = false
            val appContext = context.applicationContext
            check(checkpointAndValidate(appContext, replacementDatabaseFile)) {
                "Replacement Room database failed quick_check"
            }
            val target = appContext.getDatabasePath(DATABASE_NAME)
            val quarantine =
                if (databaseFiles(target).any { it.exists() }) {
                    quarantine(appContext, databaseFiles(target))
                } else {
                    null
                }
            replaceDatabase(target, replacementDatabaseFile)
            check(checkpointAndValidate(appContext, target)) {
                "Room database failed quick_check after replacement"
            }
            writeSnapshot(appContext, target)
            PreferenceRecoveryStorage.recordStorageEvent(
                appContext,
                DATABASE_NAME,
                "room_replacement",
                reason
            )
            if (quarantine != null) {
                AppLogger.i(TAG, "Previous Room database preserved at ${quarantine.absolutePath}")
            }
        }
    }

    private fun quarantineAndRemoveInvalidSidecars(context: Context, databaseFile: File) {
        val invalidSidecars =
            databaseFiles(databaseFile)
                .drop(1)
                .filter { sidecar -> sidecar.exists() && !sidecar.isFile }
        if (invalidSidecars.isEmpty()) return

        val quarantine = quarantine(context, invalidSidecars)
        invalidSidecars.forEach { sidecar ->
            check(sidecar.deleteRecursively()) {
                "Failed to remove invalid Room sidecar path: ${sidecar.absolutePath}"
            }
        }
        PreferenceRecoveryStorage.recordStorageEvent(
            context,
            DATABASE_NAME,
            "room_invalid_sidecar_path",
            "quarantined_and_removed"
        )
        AppLogger.w(TAG, "Invalid Room sidecars preserved at ${quarantine.absolutePath}")
    }

    private fun checkpointAndValidate(context: Context, databaseFile: File): Boolean {
        val corruptionHandler = PreservingCorruptionHandler()
        val version =
            try {
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    corruptionHandler
                ).use { database ->
                    database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                        if (cursor.moveToFirst() &&
                            cursor.columnCount > 0 &&
                            cursor.getInt(0) != 0
                        ) {
                            throw IllegalStateException("Room WAL checkpoint remained busy")
                        }
                    }
                    if (!checkQuickCheck(database)) return false
                    readUserVersion(database)
                }
            } catch (e: SQLiteDatabaseCorruptException) {
                AppLogger.e(TAG, "Room database validation failed: ${databaseFile.name}", e)
                return false
            } catch (e: Exception) {
                if (corruptionHandler.corruptionReported) {
                    AppLogger.e(TAG, "Room database open reported corruption", e)
                    return false
                }
                if (hasCause<SQLiteDatabaseCorruptException>(e)) {
                    AppLogger.e(TAG, "Room database validation found wrapped corruption", e)
                    return false
                }
                AppLogger.e(TAG, "Room database validation could not complete", e)
                throw StorageRecoveryException(
                    "Room validation could not complete; the live database was preserved.",
                    e
                )
            }
        if (corruptionHandler.corruptionReported) {
            AppLogger.e(TAG, "Room database validation received a corruption signal")
            return false
        }
        return validateRoomVersionAndSchema(context, databaseFile, version)
    }

    private fun validateSnapshot(context: Context, databaseFile: File): Boolean {
        val corruptionHandler = PreservingCorruptionHandler()
        val version =
            try {
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    corruptionHandler
                ).use { database ->
                    if (!checkQuickCheck(database)) return false
                    readUserVersion(database)
                }
            } catch (e: SQLiteDatabaseCorruptException) {
                AppLogger.e(TAG, "Room snapshot is corrupt: ${databaseFile.name}", e)
                return false
            } catch (e: Exception) {
                if (corruptionHandler.corruptionReported) {
                    AppLogger.e(TAG, "Room snapshot open reported corruption", e)
                    return false
                }
                if (hasCause<SQLiteDatabaseCorruptException>(e)) {
                    AppLogger.e(TAG, "Room snapshot validation found wrapped corruption", e)
                    return false
                }
                AppLogger.e(TAG, "Room snapshot validation could not complete", e)
                throw StorageRecoveryException(
                    "Room recovery snapshot validation could not complete.",
                    e
                )
            }
        if (corruptionHandler.corruptionReported) {
            AppLogger.e(TAG, "Room snapshot validation received a corruption signal")
            return false
        }
        return validateRoomVersionAndSchema(context, databaseFile, version)
    }

    private fun validateRoomVersionAndSchema(
        context: Context,
        databaseFile: File,
        version: Int
    ): Boolean {
        if (version > AppDatabase.DATABASE_VERSION) {
            throw UnsupportedDatabaseVersionException(version)
        }
        if (version <= 0) {
            AppLogger.e(TAG, "Existing Room database has no released schema version")
            return false
        }
        if (!AppDatabase.validateRecoveryCandidate(context, databaseFile)) {
            AppLogger.e(TAG, "Room schema validation failed: ${databaseFile.name}")
            return false
        }
        return true
    }

    private fun checkQuickCheck(database: SQLiteDatabase): Boolean {
        database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            var sawResult = false
            while (cursor.moveToNext()) {
                sawResult = true
                if (!cursor.getString(0).equals("ok", ignoreCase = true)) return false
            }
            return sawResult
        }
    }

    private fun readUserVersion(database: SQLiteDatabase): Int =
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "Room user_version returned no row" }
            cursor.getInt(0)
        }

    private inline fun <reified T : Throwable> hasCause(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return true
            val cause = current.cause
            if (cause === current) break
            current = cause
        }
        return false
    }

    private fun writeSnapshot(context: Context, databaseFile: File) {
        val existing = listOfNotNull(readMetadata(context, 0), readMetadata(context, 1))
        val sequence = Math.addExact(existing.maxOfOrNull { it.sequence } ?: 0L, 1L)
        val slot = (sequence % 2L).toInt()
        val target = snapshotDatabaseFile(context, slot)
        writeAtomic(target) { output -> databaseFile.inputStream().use { it.copyTo(output) } }
        val metadata =
            SnapshotMetadata(
                formatVersion = FORMAT_VERSION,
                databaseName = DATABASE_NAME,
                sequence = sequence,
                createdAt = System.currentTimeMillis(),
                size = target.length(),
                sha256 = sha256(target)
            )
        writeAtomic(snapshotMetadataFile(context, slot)) { output ->
            output.write(json.encodeToString(metadata).toByteArray(Charsets.UTF_8))
        }
        check(readSlot(context, slot) != null) { "Room recovery snapshot verification failed" }
    }

    private fun readLatestSnapshot(context: Context): VerifiedSnapshot? =
        listOfNotNull(readSlot(context, 0), readSlot(context, 1))
            .maxByOrNull { it.metadata.sequence }

    private fun readLatestSnapshotForRecovery(context: Context): VerifiedSnapshot? =
        try {
            readLatestSnapshot(context)
        } catch (e: UnsupportedDatabaseVersionException) {
            PreferenceRecoveryStorage.recordStorageEvent(
                context,
                DATABASE_NAME,
                "room_version",
                "newer_snapshot_preserved"
            )
            throw StorageRecoveryException(
                "A Room recovery snapshot uses schema version ${e.version}, which requires a " +
                    "newer Operit build. Recovery artifacts were preserved without replacement.",
                e
            )
        }

    private fun readSlot(context: Context, slot: Int): VerifiedSnapshot? {
        val databaseFile = snapshotDatabaseFile(context, slot)
        val metadata = readMetadata(context, slot) ?: return null
        if (!databaseFile.isFile || databaseFile.length() != metadata.size) return null
        val actualHash =
            try {
                sha256(databaseFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unable to read Room recovery snapshot in slot $slot", e)
                throw StorageRecoveryException(
                    "Room recovery snapshot could not be read.",
                    e
                )
            }
        if (actualHash != metadata.sha256) return null
        if (!validateSnapshot(context, databaseFile)) {
            AppLogger.e(TAG, "Invalid Room recovery snapshot in slot $slot")
            return null
        }
        return VerifiedSnapshot(databaseFile, metadata)
    }

    private fun readMetadata(context: Context, slot: Int): SnapshotMetadata? {
        val file = snapshotMetadataFile(context, slot)
        if (!file.isFile) return null
        return try {
            val metadata =
                AtomicFile(file).openRead().use {
                    json.decodeFromString<SnapshotMetadata>(
                        it.readBytes().toString(Charsets.UTF_8)
                    )
                }
            check(metadata.formatVersion == FORMAT_VERSION)
            check(metadata.databaseName == DATABASE_NAME)
            check(metadata.sequence > 0L)
            check(metadata.createdAt > 0L)
            check(metadata.size > 0L)
            check(metadata.sha256.matches(Regex("[0-9a-f]{64}")))
            metadata
        } catch (e: Exception) {
            AppLogger.e(TAG, "Invalid Room snapshot metadata in slot $slot", e)
            null
        }
    }

    private fun quarantine(context: Context, files: List<File>): File {
        val directory =
            File(
                File(context.noBackupFilesDir, "storage-recovery/quarantine"),
                "room_${System.currentTimeMillis()}_${UUID.randomUUID()}"
            )
        check(directory.mkdirs() || directory.isDirectory) {
            "Failed to create Room quarantine directory"
        }
        files.filter { it.exists() }.forEach { source ->
            val target = File(directory, source.name)
            if (source.isDirectory) {
                copyDirectoryStrict(source, target)
            } else if (source.isFile) {
                StorageQuarantineFiles.copyVerified(source, target)
            } else {
                error("Unsupported Room corruption source: ${source.absolutePath}")
            }
        }
        return directory
    }

    private fun copyDirectoryStrict(source: File, target: File) {
        check(target.mkdirs() || target.isDirectory) {
            "Failed to create Room quarantine subdirectory"
        }
        val children = source.listFiles()
            ?: throw IllegalStateException(
                "Failed to enumerate Room corruption source: ${source.absolutePath}"
            )
        children.forEach { child ->
            val destination = File(target, child.name)
            when {
                child.isDirectory -> copyDirectoryStrict(child, destination)
                child.isFile -> StorageQuarantineFiles.copyVerified(child, destination)
                else -> error("Unsupported Room corruption source: ${child.absolutePath}")
            }
        }
    }

    private fun replaceDatabase(databaseFile: File, snapshot: File) {
        val relatedFiles = databaseFiles(databaseFile).filter { it != databaseFile }
        relatedFiles.forEach { file ->
            if (file.exists() && !file.deleteRecursively()) {
                throw IllegalStateException("Failed to remove stale Room sidecar: ${file.name}")
            }
        }
        if (databaseFile.exists() && !databaseFile.isFile && !databaseFile.deleteRecursively()) {
            throw IllegalStateException("Failed to remove invalid Room database path")
        }
        writeAtomic(databaseFile) { output -> snapshot.inputStream().use { it.copyTo(output) } }
    }

    private fun databaseFiles(databaseFile: File): List<File> =
        listOf(
            databaseFile,
            File(databaseFile.absolutePath + "-wal"),
            File(databaseFile.absolutePath + "-shm"),
            File(databaseFile.absolutePath + "-journal")
        )

    private fun snapshotRoot(context: Context): File =
        File(context.noBackupFilesDir, "storage-recovery/room").apply { mkdirs() }

    private fun snapshotDatabaseFile(context: Context, slot: Int): File =
        File(snapshotRoot(context), "$DATABASE_NAME.$slot.db")

    private fun snapshotMetadataFile(context: Context, slot: Int): File =
        File(snapshotRoot(context), "$DATABASE_NAME.$slot.json")

    private fun hasSnapshotArtifacts(context: Context): Boolean =
        (0..1).any { slot ->
            snapshotDatabaseFile(context, slot).exists() ||
                snapshotMetadataFile(context, slot).exists()
        }

    private fun writeAtomic(file: File, writer: (FileOutputStream) -> Unit) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            val stream = atomicFile.startWrite()
            output = stream
            writer(stream)
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            output?.let(atomicFile::failWrite)
            throw e
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
