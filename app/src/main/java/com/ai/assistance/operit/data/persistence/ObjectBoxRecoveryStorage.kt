package com.ai.assistance.operit.data.persistence

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ObjectBoxRecoveryStorage {
    private const val TAG = "ObjectBoxRecovery"
    private const val DATA_FILE_NAME = "data.mdb"
    private const val LOCK_FILE_NAME = "lock.mdb"
    private const val FORMAT_VERSION = 1
    private val lock = Any()
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class SnapshotMetadata(
        val formatVersion: Int,
        val profileId: String,
        val sequence: Long,
        val createdAt: Long,
        val size: Long,
        val sha256: String
    )

    private data class VerifiedSnapshot(
        val dataFile: File,
        val metadata: SnapshotMetadata
    )

    fun prepareForOpen(
        context: Context,
        profileId: String,
        databaseDirectory: File,
        validator: (File) -> Boolean
    ) {
        synchronized(lock) {
            val appContext = context.applicationContext
            val dataFile = File(databaseDirectory, DATA_FILE_NAME)
            if (!dataFile.isFile) {
                val invalidDirectoryPath =
                    databaseDirectory.exists() && !databaseDirectory.isDirectory
                val hasRemnants =
                    invalidDirectoryPath ||
                        databaseDirectory.listFiles()?.isNotEmpty() == true
                val snapshot = readLatestSnapshot(appContext, profileId, validator)
                if (snapshot == null) {
                    if (dataFile.exists() ||
                        hasRemnants ||
                        hasSnapshotArtifacts(appContext, profileId)
                    ) {
                        val quarantine =
                            if (databaseDirectory.exists()) {
                                quarantine(appContext, profileId, databaseDirectory)
                            } else {
                                null
                            }
                        PreferenceRecoveryStorage.recordStorageEvent(
                            appContext,
                            "objectbox",
                            "objectbox_invalid_data_path",
                            "preserved_without_snapshot"
                        )
                        throw StorageRecoveryException(
                            "ObjectBox data.mdb is missing or invalid and no verified snapshot " +
                                "can be opened." +
                                (quarantine?.let {
                                    " Original files were preserved at ${it.absolutePath}"
                                } ?: "")
                        )
                    }
                    return
                }

                val quarantine =
                    if (hasRemnants) {
                        quarantine(appContext, profileId, databaseDirectory)
                    } else {
                        null
                    }
                if (databaseDirectory.exists() && !databaseDirectory.deleteRecursively()) {
                    throw IllegalStateException(
                        "Failed to remove quarantined ObjectBox remnants"
                    )
                }
                check(databaseDirectory.mkdirs() || databaseDirectory.isDirectory) {
                    "Failed to create ObjectBox database directory"
                }
                val lockFile = File(databaseDirectory, LOCK_FILE_NAME)
                if (lockFile.exists() && !lockFile.deleteRecursively()) {
                    throw IllegalStateException("Failed to remove stale ObjectBox lock file")
                }
                writeAtomic(dataFile) { output ->
                    snapshot.dataFile.inputStream().use { it.copyTo(output) }
                }
                if (!validateCopy(appContext, dataFile, validator)) {
                    throw StorageRecoveryException(
                        "Verified ObjectBox snapshot failed after restoring a missing data file."
                    )
                }
                writeSnapshot(appContext, profileId, dataFile, validator)
                PreferenceRecoveryStorage.recordStorageEvent(
                    appContext,
                    "objectbox",
                    "objectbox_missing",
                    "restored_snapshot"
                )
                if (quarantine != null) {
                    AppLogger.i(
                        TAG,
                        "ObjectBox remnants preserved at ${quarantine.absolutePath}"
                    )
                }
                AppLogger.w(TAG, "Restored missing ObjectBox profile from verified snapshot")
                return
            }

            val invalidLockPath =
                File(databaseDirectory, LOCK_FILE_NAME)
                    .takeIf { lockFile -> lockFile.exists() && !lockFile.isFile }
            if (invalidLockPath != null) {
                val quarantine = quarantine(appContext, profileId, databaseDirectory)
                check(invalidLockPath.deleteRecursively()) {
                    "Failed to remove invalid ObjectBox lock path"
                }
                PreferenceRecoveryStorage.recordStorageEvent(
                    appContext,
                    "objectbox",
                    "objectbox_invalid_lock_path",
                    "quarantined_and_removed"
                )
                AppLogger.w(
                    TAG,
                    "Invalid ObjectBox lock path preserved at ${quarantine.absolutePath}"
                )
            }

            if (validateCopy(appContext, dataFile, validator)) {
                writeSnapshot(appContext, profileId, dataFile, validator)
                return
            }

            val quarantine = quarantine(appContext, profileId, databaseDirectory)
            val snapshot = readLatestSnapshot(appContext, profileId, validator)
            if (snapshot == null) {
                PreferenceRecoveryStorage.recordStorageEvent(
                    appContext,
                    "objectbox",
                    "objectbox_corruption",
                    "preserved_without_snapshot"
                )
                throw StorageRecoveryException(
                    "ObjectBox profile is corrupt and has no verified snapshot. " +
                    "Original files were preserved at ${quarantine.absolutePath}"
                )
            }

            check(databaseDirectory.deleteRecursively()) {
                "Failed to remove quarantined ObjectBox profile"
            }
            check(databaseDirectory.mkdirs() || databaseDirectory.isDirectory) {
                "Failed to create ObjectBox database directory"
            }
            val lockFile = File(databaseDirectory, LOCK_FILE_NAME)
            if (lockFile.exists() && !lockFile.deleteRecursively()) {
                throw IllegalStateException("Failed to remove stale ObjectBox lock file")
            }
            writeAtomic(dataFile) { output -> snapshot.dataFile.inputStream().use { it.copyTo(output) } }
            if (!validateCopy(appContext, dataFile, validator)) {
                throw StorageRecoveryException(
                    "Verified ObjectBox snapshot failed after replacement. " +
                        "Original files remain at ${quarantine.absolutePath}"
                )
            }
            writeSnapshot(appContext, profileId, dataFile, validator)
            PreferenceRecoveryStorage.recordStorageEvent(
                appContext,
                "objectbox",
                "objectbox_corruption",
                "restored_snapshot"
            )
            AppLogger.w(TAG, "Restored corrupt ObjectBox profile from verified snapshot")
        }
    }

    fun checkpointClosed(
        context: Context,
        profileId: String,
        databaseDirectory: File,
        validator: (File) -> Boolean
    ) {
        synchronized(lock) {
            val dataFile = File(databaseDirectory, DATA_FILE_NAME)
            if (!dataFile.isFile) return
            if (!validateCopy(context.applicationContext, dataFile, validator)) {
                AppLogger.e(TAG, "Closed ObjectBox profile failed validation; snapshot not updated")
                return
            }
            writeSnapshot(context.applicationContext, profileId, dataFile, validator)
        }
    }

    fun checkpointOpen(
        context: Context,
        profileId: String,
        databaseDirectory: File,
        stableCopy: (File) -> Unit,
        validator: (File) -> Boolean
    ) {
        synchronized(lock) {
            val appContext = context.applicationContext
            val sourceDataFile = File(databaseDirectory, DATA_FILE_NAME)
            if (!sourceDataFile.isFile) return
            val stagingDirectory =
                File(appContext.cacheDir, "objectbox_checkpoint_${UUID.randomUUID()}")
            check(stagingDirectory.mkdirs()) {
                "Failed to create ObjectBox checkpoint staging directory"
            }
            try {
                val stagedDataFile = File(stagingDirectory, DATA_FILE_NAME)
                stableCopy(stagedDataFile)
                check(stagedDataFile.isFile) {
                    "Active ObjectBox checkpoint did not produce data.mdb"
                }
                check(validator(stagingDirectory)) {
                    "Active ObjectBox checkpoint failed full-page validation"
                }
                writeSnapshot(appContext, profileId, stagedDataFile, validator)
            } finally {
                if (!stagingDirectory.deleteRecursively()) {
                    AppLogger.w(TAG, "Failed to remove ObjectBox checkpoint staging directory")
                }
            }
        }
    }

    fun archiveRecoveryStateForDeletion(context: Context, profileId: String) {
        synchronized(lock) {
            val appContext = context.applicationContext
            val directory = File(recoveryRoot(appContext), safeProfileName(profileId))
            if (!directory.exists()) return
            check(directory.isDirectory) {
                "ObjectBox recovery state is not a directory for an intentionally deleted profile"
            }
            val quarantine =
                File(
                    File(appContext.noBackupFilesDir, "storage-recovery/quarantine"),
                    "objectbox_deleted_${safeProfileName(profileId)}_" +
                        "${System.currentTimeMillis()}_${UUID.randomUUID()}"
                )
            check(
                quarantine.parentFile?.mkdirs() == true ||
                    quarantine.parentFile?.isDirectory == true
            ) {
                "Failed to create ObjectBox deletion quarantine"
            }
            check(directory.renameTo(quarantine)) {
                "Failed to detach recovery state for an intentionally deleted profile"
            }
        }
    }

    fun quarantineInvalidProfileDirectory(
        context: Context,
        profileId: String,
        databaseDirectory: File
    ) {
        synchronized(lock) {
            val appContext = context.applicationContext
            val canonicalDirectory = databaseDirectory.canonicalFile
            check(canonicalDirectory.parentFile == appContext.filesDir.canonicalFile) {
                "Invalid ObjectBox profile resolves outside filesDir"
            }
            val quarantine = quarantine(appContext, profileId, canonicalDirectory)
            archiveRecoveryStateForDeletion(appContext, profileId)
            check(canonicalDirectory.deleteRecursively()) {
                "Failed to remove quarantined invalid ObjectBox profile directory"
            }
            PreferenceRecoveryStorage.recordStorageEvent(
                appContext,
                "objectbox",
                "objectbox_invalid_profile_id",
                "quarantined_and_removed"
            )
            AppLogger.w(
                TAG,
                "Invalid ObjectBox profile ID preserved at ${quarantine.absolutePath}"
            )
        }
    }

    fun profileIdsWithRecoveryArtifacts(context: Context): Set<String> =
        synchronized(lock) {
            val root = recoveryRoot(context.applicationContext)
            if (!root.exists()) return@synchronized emptySet<String>()
            check(root.isDirectory) { "ObjectBox recovery root is not a directory" }
            val profileDirectories = root.listFiles()
                ?: throw IllegalStateException("Failed to enumerate ObjectBox recovery profiles")
            profileDirectories
                .asSequence()
                .filter { it.isDirectory }
                .flatMap { directory ->
                    (0..1).asSequence().mapNotNull { slot ->
                        val metadata =
                            decodeMetadata(
                                File(directory, "data.$slot.json"),
                                "${directory.name} slot $slot"
                            ) ?: return@mapNotNull null
                        if (safeProfileName(metadata.profileId) != directory.name) {
                            AppLogger.e(
                                TAG,
                                "Ignoring ObjectBox recovery metadata stored under the wrong profile"
                            )
                            return@mapNotNull null
                        }
                        metadata.profileId
                    }
                }
                .toSortedSet()
        }

    private fun validateCopy(
        context: Context,
        sourceDataFile: File,
        validator: (File) -> Boolean
    ): Boolean {
        val validationDirectory =
            File(context.cacheDir, "objectbox_validation_${UUID.randomUUID()}")
        check(validationDirectory.mkdirs()) {
            "Failed to create ObjectBox validation directory"
        }
        return try {
            sourceDataFile.copyTo(File(validationDirectory, DATA_FILE_NAME), overwrite = false)
            validator(validationDirectory)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to validate ObjectBox data copy", e)
            throw e
        } finally {
            if (!validationDirectory.deleteRecursively()) {
                AppLogger.w(TAG, "Failed to remove ObjectBox validation directory")
            }
        }
    }

    private fun writeSnapshot(
        context: Context,
        profileId: String,
        dataFile: File,
        validator: (File) -> Boolean
    ) {
        val existing = listOfNotNull(readMetadata(context, profileId, 0), readMetadata(context, profileId, 1))
        val sequence = Math.addExact(existing.maxOfOrNull { it.sequence } ?: 0L, 1L)
        val slot = (sequence % 2L).toInt()
        val target = snapshotDataFile(context, profileId, slot)
        writeAtomic(target) { output -> dataFile.inputStream().use { it.copyTo(output) } }
        val metadata =
            SnapshotMetadata(
                formatVersion = FORMAT_VERSION,
                profileId = profileId,
                sequence = sequence,
                createdAt = System.currentTimeMillis(),
                size = target.length(),
                sha256 = sha256(target)
            )
        writeAtomic(snapshotMetadataFile(context, profileId, slot)) { output ->
            output.write(json.encodeToString(metadata).toByteArray(Charsets.UTF_8))
        }
        check(readSlot(context, profileId, slot, validator) != null) {
            "ObjectBox recovery snapshot verification failed"
        }
    }

    private fun readLatestSnapshot(
        context: Context,
        profileId: String,
        validator: (File) -> Boolean
    ): VerifiedSnapshot? =
        listOfNotNull(
            readSlot(context, profileId, 0, validator),
            readSlot(context, profileId, 1, validator)
        ).maxByOrNull { it.metadata.sequence }

    private fun readSlot(
        context: Context,
        profileId: String,
        slot: Int,
        validator: (File) -> Boolean
    ): VerifiedSnapshot? {
        val dataFile = snapshotDataFile(context, profileId, slot)
        val metadata = readMetadata(context, profileId, slot) ?: return null
        if (!dataFile.isFile || dataFile.length() != metadata.size) return null
        val actualHash =
            try {
                sha256(dataFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unable to read ObjectBox recovery snapshot in slot $slot", e)
                throw e
            }
        if (actualHash != metadata.sha256) return null
        if (!validateCopy(context, dataFile, validator)) {
            AppLogger.e(TAG, "Corrupt ObjectBox recovery snapshot in slot $slot")
            return null
        }
        return VerifiedSnapshot(dataFile, metadata)
    }

    private fun readMetadata(context: Context, profileId: String, slot: Int): SnapshotMetadata? {
        val file = snapshotMetadataFile(context, profileId, slot)
        val metadata = decodeMetadata(file, "slot $slot") ?: return null
        if (metadata.profileId != profileId) {
            AppLogger.e(TAG, "ObjectBox snapshot profile mismatch in slot $slot")
            return null
        }
        return metadata
    }

    private fun decodeMetadata(file: File, label: String): SnapshotMetadata? {
        if (!file.isFile) return null
        return try {
            val metadata =
                AtomicFile(file).openRead().use {
                    json.decodeFromString<SnapshotMetadata>(
                        it.readBytes().toString(Charsets.UTF_8)
                    )
                }
            check(metadata.formatVersion == FORMAT_VERSION)
            check(StorageProfileIdPolicy.isSafeMemorySpaceId(metadata.profileId))
            check(metadata.sequence > 0L)
            check(metadata.createdAt > 0L)
            check(metadata.size > 0L)
            check(metadata.sha256.matches(Regex("[0-9a-f]{64}")))
            metadata
        } catch (e: Exception) {
            AppLogger.e(TAG, "Invalid ObjectBox snapshot metadata in $label", e)
            null
        }
    }

    private fun quarantine(context: Context, profileId: String, sourceDirectory: File): File {
        val directory =
            File(
                File(context.noBackupFilesDir, "storage-recovery/quarantine"),
                "objectbox_${safeProfileName(profileId)}_${System.currentTimeMillis()}_${UUID.randomUUID()}"
            )
        check(directory.mkdirs() || directory.isDirectory) {
            "Failed to create ObjectBox quarantine directory"
        }
        if (sourceDirectory.isDirectory) {
            copyDirectoryStrict(sourceDirectory, directory)
        } else if (sourceDirectory.isFile) {
            StorageQuarantineFiles.copyVerified(
                sourceDirectory,
                File(directory, sourceDirectory.name)
            )
        } else {
            error("Unsupported ObjectBox corruption source: ${sourceDirectory.absolutePath}")
        }
        return directory
    }

    private fun copyDirectoryStrict(source: File, target: File) {
        check(target.mkdirs() || target.isDirectory) {
            "Failed to create ObjectBox quarantine subdirectory"
        }
        val children = source.listFiles()
            ?: throw IllegalStateException(
                "Failed to enumerate ObjectBox corruption source: ${source.absolutePath}"
            )
        children.forEach { child ->
            val destination = File(target, child.name)
            when {
                child.isDirectory -> copyDirectoryStrict(child, destination)
                child.isFile -> StorageQuarantineFiles.copyVerified(child, destination)
                else -> error("Unsupported ObjectBox corruption source: ${child.absolutePath}")
            }
        }
    }

    private fun recoveryRoot(context: Context): File =
        File(context.noBackupFilesDir, "storage-recovery/objectbox")

    private fun profileRoot(context: Context, profileId: String): File =
        File(
            recoveryRoot(context),
            safeProfileName(profileId)
        ).also { directory ->
            check(directory.mkdirs() || directory.isDirectory) {
                "Failed to create ObjectBox recovery profile directory"
            }
        }

    private fun snapshotDataFile(context: Context, profileId: String, slot: Int): File =
        File(profileRoot(context, profileId), "data.$slot.mdb")

    private fun snapshotMetadataFile(context: Context, profileId: String, slot: Int): File =
        File(profileRoot(context, profileId), "data.$slot.json")

    private fun hasSnapshotArtifacts(context: Context, profileId: String): Boolean =
        (0..1).any { slot ->
            snapshotDataFile(context, profileId, slot).exists() ||
                snapshotMetadataFile(context, profileId, slot).exists()
        }

    private fun safeProfileName(profileId: String): String {
        val readable = profileId.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(64)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(profileId.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }
        return "${readable}_$hash"
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
