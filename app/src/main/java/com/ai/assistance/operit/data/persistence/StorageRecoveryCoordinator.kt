package com.ai.assistance.operit.data.persistence

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.data.backup.RawSnapshotBackupManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StorageRecoveryException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

object StorageRecoveryCoordinator {
    private const val TAG = "StorageRecovery"
    private const val RAW_RESTORE_EPOCH_FORMAT_VERSION = 1
    private const val RAW_RESTORE_EPOCH_MANIFEST = "epoch_manifest.json"
    private const val RAW_RESTORE_EPOCH_MANIFEST_MAX_BYTES = 64L * 1024L
    private val rawRestoreTransactionIdPattern = Regex("[0-9a-f]{32}")
    private val recoveryRootNames = listOf("preferences", "room", "objectbox")
    private val strictJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    @Serializable
    private data class RawRestoreEpochManifest(
        val formatVersion: Int,
        val transactionId: String,
        val originalRoots: List<String>
    )

    suspend fun recoverPreferences(context: Context) {
        val appContext = context.applicationContext
        RawSnapshotBackupManager.recoverInterruptedRawRestore(appContext)
        val unreadableStores = RecoverablePreferenceDataStores.preflightKnownStores(appContext)
        if (unreadableStores.isNotEmpty()) {
            throw StorageRecoveryException(
                "Preferences remain unreadable after corruption handling: ${unreadableStores.joinToString()}"
            )
        }

        val userPreferencesManager = UserPreferencesManager.getInstance(appContext)
        // A deletion marker is committed before any profile files are removed. Replay it before
        // ObjectBox artifact discovery so a verified recovery slot cannot resurrect that profile.
        userPreferencesManager.completePendingMemorySpaceDeletions()

        // Physical databases must be recovered before logical indexes are repaired. Otherwise a
        // temporarily unreadable ObjectBox profile can be mistaken for a deleted memory space.
        RoomRecoveryStorage.prepareForOpen(appContext)
        ObjectBoxManager.preflightAll(appContext)

        var repairedStoreCount = 0
        if (SpeechServiceProfilesPreferences(appContext).initializeAndRepair()) {
            repairedStoreCount++
        }
        if (userPreferencesManager.repairPersistedState()) {
            repairedStoreCount++
        }
        if (ApiPreferences.getInstance(appContext).repairPersistedState()) repairedStoreCount++
        if (GitHubAuthPreferences.getInstance(appContext).repairPersistedState()) {
            repairedStoreCount++
        }
        if (ExternalHttpApiPreferences.getInstance(appContext).repairPersistedState()) {
            repairedStoreCount++
        }
        if (AndroidPermissionPreferences(appContext).repairPersistedState()) repairedStoreCount++
        if (ToolPermissionSystem.getInstance(appContext).repairPersistedState()) {
            repairedStoreCount++
        }

        val modelConfigManager = ModelConfigManager(appContext)
        if (modelConfigManager.repairPersistedState()) repairedStoreCount++
        if (FunctionalConfigManager(appContext).repairPersistedState()) repairedStoreCount++

        val characterCardManager = CharacterCardManager.getInstance(appContext)
        if (characterCardManager.repairPersistedState()) repairedStoreCount++
        if (CharacterGroupCardManager.getInstance(appContext).repairPersistedState()) {
            repairedStoreCount++
        }

        RecoverablePreferenceDataStores.checkpointKnownStores(appContext)
        AppLogger.i(TAG, "Preferences recovery completed; repairedStores=$repairedStoreCount")
    }

    /**
     * Archives the currently published recovery roots to a transaction-derived directory. The
     * manifest is durably published before the first rename, so startup can finish or reverse an
     * archive interrupted by process death.
     */
    @Synchronized
    fun archiveSnapshotsBeforeRawRestoreRecovery(
        context: Context,
        transactionId: String,
        archiveBasename: String
    ): File {
        requireRawRestoreEpochArguments(transactionId, archiveBasename)
        val appContext = context.applicationContext
        val root = recoveryRoot(appContext)
        val quarantineRoot = quarantineRoot(appContext)
        val archive = resolveArchive(quarantineRoot, archiveBasename)
        val staging = resolveArchive(quarantineRoot, ".$archiveBasename.preparing")
        check(!archive.exists() && !staging.exists()) {
            "Recovery epoch archive already exists"
        }

        val originalRoots =
            recoveryRootNames.filter { name ->
                val source = File(root, name)
                if (!pathExistsStrict(source)) return@filter false
                check(Files.isDirectory(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "Recovery root is not a directory: $name"
                }
                true
            }
        val manifest =
            RawRestoreEpochManifest(
                formatVersion = RAW_RESTORE_EPOCH_FORMAT_VERSION,
                transactionId = transactionId,
                originalRoots = originalRoots
            )
        check(staging.mkdir()) { "Failed to create recovery epoch staging directory" }
        writeEpochManifest(staging, manifest)
        check(staging.renameTo(archive)) { "Failed to publish recovery epoch archive" }

        val moved = mutableListOf<Pair<File, File>>()
        try {
            originalRoots.forEach { name ->
                val source = File(root, name)
                val target = File(archive, name)
                check(source.renameTo(target)) {
                    "Failed to archive recovery snapshots: $name"
                }
                moved += source to target
            }
            validateRecoveryEpochArchive(appContext, transactionId, archiveBasename)
        } catch (failure: Throwable) {
            moved.asReversed().forEach { (source, target) ->
                if (!target.renameTo(source)) {
                    failure.addSuppressed(
                        IllegalStateException(
                            "Failed to restore partially archived recovery snapshots: ${source.name}"
                        )
                    )
                }
            }
            throw failure
        }
        PreferenceRecoveryStorage.recordStorageEvent(
            appContext,
            "raw_snapshot",
            "recovery_epoch",
            "archived_previous_snapshots"
        )
        return archive
    }

    @Synchronized
    fun validateRecoveryEpochArchive(
        context: Context,
        transactionId: String,
        archiveBasename: String
    ) {
        requireRawRestoreEpochArguments(transactionId, archiveBasename)
        val archive = resolveArchive(quarantineRoot(context.applicationContext), archiveBasename)
        check(Files.isDirectory(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Recovery epoch archive is missing"
        }
        val manifest = readEpochManifest(archive, transactionId)
        val allowed = manifest.originalRoots.toSet() + RAW_RESTORE_EPOCH_MANIFEST
        val entries =
            archive.listFiles()
                ?: throw IllegalStateException("Failed to enumerate recovery epoch archive")
        check(entries.all { entry -> entry.name in allowed }) {
            "Recovery epoch archive contains unexpected entries"
        }
        manifest.originalRoots.forEach { name ->
            check(Files.isDirectory(File(archive, name).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "Recovery epoch archive is missing original root: $name"
            }
        }
    }

    @Synchronized
    fun restoreRecoveryEpochAfterRawRestoreFailure(
        context: Context,
        transactionId: String,
        archiveBasename: String,
        mutationStarted: Boolean
    ) {
        requireRawRestoreEpochArguments(transactionId, archiveBasename)
        val appContext = context.applicationContext
        val root = recoveryRoot(appContext)
        val quarantineRoot = quarantineRoot(appContext)
        val archive = resolveArchive(quarantineRoot, archiveBasename)
        if (!pathExistsStrict(archive)) {
            check(!mutationStarted) {
                "Recovery epoch archive is missing after raw restore mutation started"
            }
            deleteIncompleteEpochStaging(quarantineRoot, archiveBasename)
            return
        }
        check(Files.isDirectory(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Recovery epoch archive is not a directory"
        }
        val manifest = readEpochManifest(archive, transactionId)
        val originalRoots = manifest.originalRoots.toSet()
        val failedImport =
            resolveArchive(
                quarantineRoot,
                "failed_import_recovery_epoch_$transactionId"
            )
        if (mutationStarted) {
            check(
                failedImport.mkdirs() ||
                    Files.isDirectory(failedImport.toPath(), LinkOption.NOFOLLOW_LINKS)
            ) {
                "Failed to create deterministic failed-import recovery archive"
            }
        } else {
            check(!pathExistsStrict(failedImport)) {
                "PREPARED raw restore unexpectedly has a failed-import recovery archive"
            }
        }

        recoveryRootNames.forEach { name ->
            val live = File(root, name)
            val archived = File(archive, name)
            val failed = File(failedImport, name)
            if (name in originalRoots) {
                if (pathExistsStrict(archived)) {
                    check(Files.isDirectory(archived.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        "Archived recovery root is not a directory: $name"
                    }
                    if (pathExistsStrict(live)) {
                        check(mutationStarted && !pathExistsStrict(failed)) {
                            "Recovery epoch restore has conflicting roots: $name"
                        }
                        check(live.renameTo(failed)) {
                            "Failed to preserve imported recovery root: $name"
                        }
                    }
                    check(!pathExistsStrict(live) && archived.renameTo(live)) {
                        "Failed to restore original recovery root: $name"
                    }
                } else {
                    check(Files.isDirectory(live.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        "Previously restored recovery root is missing: $name"
                    }
                }
            } else {
                check(!pathExistsStrict(archived)) {
                    "Recovery epoch contains an originally absent root: $name"
                }
                if (pathExistsStrict(live)) {
                    check(mutationStarted && !pathExistsStrict(failed)) {
                        "Originally absent recovery root cannot be restored: $name"
                    }
                    check(live.renameTo(failed)) {
                        "Failed to preserve imported recovery root: $name"
                    }
                }
            }
        }
        recoveryRootNames.forEach { name ->
            check(
                Files.isDirectory(File(root, name).toPath(), LinkOption.NOFOLLOW_LINKS) ==
                    (name in originalRoots)
            ) {
                "Recovery epoch verification failed: $name"
            }
            check(!pathExistsStrict(File(archive, name))) {
                "Archived recovery root remains after restoration: $name"
            }
        }
        PreferenceRecoveryStorage.recordStorageEvent(
            appContext,
            "raw_snapshot",
            "recovery_epoch",
            "restored_after_failed_import"
        )
    }

    @Synchronized
    fun completeRestoredRawRestoreRecoveryEpoch(
        context: Context,
        transactionId: String,
        archiveBasename: String
    ) {
        requireRawRestoreEpochArguments(transactionId, archiveBasename)
        val archive = resolveArchive(quarantineRoot(context.applicationContext), archiveBasename)
        if (!pathExistsStrict(archive)) {
            deleteIncompleteEpochStaging(quarantineRoot(context.applicationContext), archiveBasename)
            return
        }
        val manifest = readEpochManifest(archive, transactionId)
        check(manifest.originalRoots.none { name -> pathExistsStrict(File(archive, name)) }) {
            "Cannot complete recovery epoch while archived roots remain"
        }
        AtomicFile(File(archive, RAW_RESTORE_EPOCH_MANIFEST)).delete()
        check(archive.listFiles().isNullOrEmpty()) {
            "Restored recovery epoch archive contains unexpected files"
        }
        check(archive.delete()) { "Failed to remove restored recovery epoch archive" }
    }

    private fun requireRawRestoreEpochArguments(
        transactionId: String,
        archiveBasename: String
    ) {
        require(rawRestoreTransactionIdPattern.matches(transactionId)) {
            "Raw restore recovery transaction ID is invalid"
        }
        require(archiveBasename == "recovery_epoch_$transactionId") {
            "Raw restore recovery archive basename is invalid"
        }
    }

    private fun recoveryRoot(context: Context): File =
        File(context.noBackupFilesDir, "storage-recovery").also { root ->
            check(
                root.mkdirs() || Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)
            ) { "Failed to create storage recovery root" }
        }

    private fun quarantineRoot(context: Context): File =
        File(recoveryRoot(context), "quarantine").canonicalFile.also { root ->
            check(
                root.mkdirs() || Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)
            ) {
                "Failed to create recovery epoch quarantine"
            }
        }

    private fun resolveArchive(parent: File, basename: String): File {
        require('/' !in basename && '\\' !in basename && basename != "." && basename != "..") {
            "Recovery epoch basename is invalid"
        }
        val resolved = File(parent, basename).canonicalFile
        check(resolved.parentFile == parent.canonicalFile && resolved.name == basename) {
            "Recovery epoch path resolves outside quarantine"
        }
        return resolved
    }

    private fun pathExistsStrict(path: File): Boolean {
        if (Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS)) return true
        check(Files.notExists(path.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Unable to determine recovery epoch path state: ${path.absolutePath}"
        }
        return false
    }

    private fun deleteIncompleteEpochStaging(quarantineRoot: File, archiveBasename: String) {
        val staging = resolveArchive(quarantineRoot, ".$archiveBasename.preparing")
        deletePathStrict(staging)
    }

    private fun deletePathStrict(path: File) {
        if (!pathExistsStrict(path)) return
        if (Files.isDirectory(path.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val children =
                path.listFiles()?.sortedBy { it.name }
                    ?: throw IllegalStateException(
                        "Failed to enumerate recovery epoch path: ${path.absolutePath}"
                    )
            children.forEach(::deletePathStrict)
        }
        Files.delete(path.toPath())
    }

    private fun writeEpochManifest(directory: File, manifest: RawRestoreEpochManifest) {
        validateEpochManifest(manifest, manifest.transactionId)
        val atomicFile = AtomicFile(File(directory, RAW_RESTORE_EPOCH_MANIFEST))
        var output: FileOutputStream? = null
        try {
            val stream = atomicFile.startWrite()
            output = stream
            stream.write(strictJson.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (failure: Throwable) {
            output?.let(atomicFile::failWrite)
            throw failure
        }
        check(readEpochManifest(directory, manifest.transactionId) == manifest) {
            "Recovery epoch manifest durability verification failed"
        }
    }

    private fun readEpochManifest(directory: File, transactionId: String): RawRestoreEpochManifest {
        val file = File(directory, RAW_RESTORE_EPOCH_MANIFEST)
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Recovery epoch manifest is missing"
        }
        check(file.length() in 1L..RAW_RESTORE_EPOCH_MANIFEST_MAX_BYTES) {
            "Recovery epoch manifest exceeds its byte limit"
        }
        val manifest =
            AtomicFile(file).openRead().use { input ->
                strictJson.decodeFromString<RawRestoreEpochManifest>(
                    input.readBytes().toString(Charsets.UTF_8)
                )
            }
        validateEpochManifest(manifest, transactionId)
        return manifest
    }

    private fun validateEpochManifest(
        manifest: RawRestoreEpochManifest,
        transactionId: String
    ) {
        check(manifest.formatVersion == RAW_RESTORE_EPOCH_FORMAT_VERSION) {
            "Recovery epoch manifest version is invalid"
        }
        check(manifest.transactionId == transactionId) {
            "Recovery epoch manifest transaction ID is invalid"
        }
        check(manifest.originalRoots == recoveryRootNames.filter { name ->
            name in manifest.originalRoots
        }) {
            "Recovery epoch manifest root list is invalid"
        }
    }
}
