package com.ai.assistance.operit.data.db

import android.content.Context
import com.ai.assistance.operit.data.model.MyObjectBox
import com.ai.assistance.operit.data.persistence.ObjectBoxRecoveryStorage
import com.ai.assistance.operit.data.persistence.StorageProfileIdPolicy
import com.ai.assistance.operit.data.persistence.StorageRecoveryException
import com.ai.assistance.operit.data.persistence.StorageReplacementGate
import com.ai.assistance.operit.util.AppLogger
import io.objectbox.BoxStore
import io.objectbox.exception.FileCorruptException
import io.objectbox.reactive.DataSubscription
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ObjectBoxManager {
    private const val TAG = "ObjectBoxManager"
    private const val CHECKPOINT_DELAY_SECONDS = 15L
    private const val CHECKPOINT_MAX_RETRY_DELAY_SECONDS = 15L * 60L

    private class StoreEntry(
        val context: Context,
        val profileId: String,
        val directory: File,
        val store: BoxStore
    ) {
        val lifecycleLock = ReentrantLock(true)
        var changeSubscription: DataSubscription? = null
        var checkpointTask: ScheduledFuture<*>? = null
        var changeSequence: Long = 0L
        var checkpointSequence: Long = 0L
        var checkpointFailureCount: Int = 0
    }

    internal data class SnapshotFile(
        val relativePath: String,
        val file: File
    )

    internal class SnapshotExport internal constructor(
        val files: List<SnapshotFile>,
        private val stagingDirectory: File
    ) : Closeable {
        override fun close() {
            if (stagingDirectory.exists() && !stagingDirectory.deleteRecursively()) {
                AppLogger.w(TAG, "Failed to remove ObjectBox raw snapshot staging directory")
            }
        }
    }

    private val stores = mutableMapOf<String, StoreEntry>()
    private val preparedProfiles = mutableSetOf<String>()
    private val profilesBeingDeleted = mutableSetOf<String>()
    private val storeLock = Any()
    private val validationLock = Any()
    private val checkpointExecutor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "objectbox-recovery-checkpoint").apply { isDaemon = true }
        }

    fun get(context: Context, profileId: String): BoxStore {
        return StorageReplacementGate.withStorageAccess {
            getOrBuildStore(context.applicationContext, profileId)
        }
    }

    private fun getOrBuildStore(context: Context, profileId: String): BoxStore {
        while (true) {
            var detachedEntry: StoreEntry? = null
            val resolvedStore =
                synchronized(storeLock) {
                    check(profileId !in profilesBeingDeleted) {
                        "ObjectBox profile is being deleted: $profileId"
                    }
                    val current = stores[profileId]
                    when {
                        current == null -> {
                            try {
                                buildStore(context, profileId).also { entry ->
                                    stores[profileId] = entry
                                }.store
                            } catch (e: Exception) {
                                preparedProfiles.remove(profileId)
                                throw e
                            }
                        }
                        !current.store.isClosed -> current.store
                        else -> {
                            stores.remove(profileId)
                            preparedProfiles.remove(profileId)
                            detachedEntry = current
                            null
                        }
                    }
                }
            if (resolvedStore != null) return resolvedStore

            val detached = detachedEntry
            if (detached != null) {
                detached.lifecycleLock.withLock {
                    cancelRecoveryTracking(detached)
                }
            }
        }
    }

    fun preflightAll(context: Context) {
        StorageReplacementGate.withStorageAccess {
            synchronized(storeLock) {
                val appContext = context.applicationContext
                val candidates = linkedMapOf<String, File>()
                val liveCandidates =
                    appContext.filesDir.listFiles { file ->
                        file.name == "objectbox" || file.name.startsWith("objectbox_")
                    } ?: throw IllegalStateException("Failed to enumerate ObjectBox live profiles")
                liveCandidates.forEach { directory ->
                    val profileId =
                        if (directory.name == "objectbox") "default"
                        else directory.name.removePrefix("objectbox_")
                    if (!StorageProfileIdPolicy.isSafeMemorySpaceId(profileId)) {
                        ObjectBoxRecoveryStorage.quarantineInvalidProfileDirectory(
                            appContext,
                            profileId,
                            directory
                        )
                    } else {
                        candidates[profileId] = directory
                    }
                }
                ObjectBoxRecoveryStorage.profileIdsWithRecoveryArtifacts(appContext)
                    .forEach { profileId ->
                        check(StorageProfileIdPolicy.isSafeMemorySpaceId(profileId)) {
                            "ObjectBox recovery metadata contains an unsafe profile ID"
                        }
                        if (profileId !in candidates) {
                            val directory = databaseDirectory(appContext, profileId)
                            check(
                                directory.canonicalFile.parentFile ==
                                    appContext.filesDir.canonicalFile
                            ) {
                                "ObjectBox recovery profile resolves outside filesDir"
                            }
                            candidates[profileId] = directory
                        }
                    }
                candidates.toSortedMap().forEach { (profileId, directory) ->
                    if (profileId in preparedProfiles) return@forEach
                    ObjectBoxRecoveryStorage.prepareForOpen(
                        appContext,
                        profileId,
                        directory
                    ) { candidate -> validateDirectory(appContext, candidate) }
                    preparedProfiles += profileId
                }
            }
        }
    }

    internal fun validateRecoveryDirectory(context: Context, directory: File): Boolean {
        synchronized(validationLock) {
            val lockFile = File(directory, "lock.mdb")
            if (lockFile.exists() && !lockFile.delete()) {
                throw IllegalStateException("Failed to remove ObjectBox recovery lock file")
            }
            return try {
                validateDirectory(context.applicationContext, directory)
            } finally {
                // Opening the extracted payload creates lock.mdb. It is process state, not
                // database content, and must never be copied into the restored live directory.
                if (lockFile.exists() && !lockFile.delete()) {
                    throw IllegalStateException("Failed to clean ObjectBox recovery lock file")
                }
            }
        }
    }

    private fun buildStore(context: Context, profileId: String): StoreEntry {
        // 如果profileId是"default"，我们使用旧的数据库位置以实现向后兼容
        val dbDir = databaseDirectory(context, profileId)
        val wasNew = !File(dbDir, "data.mdb").isFile

        if (profileId !in preparedProfiles) {
            ObjectBoxRecoveryStorage.prepareForOpen(
                context,
                profileId,
                dbDir
            ) { candidate -> validateDirectory(context, candidate) }
            preparedProfiles += profileId
        }

        var store = openValidatedStore(context, dbDir)

        if (wasNew) {
            store.close()
            ObjectBoxRecoveryStorage.checkpointClosed(
                context,
                profileId,
                dbDir
            ) { candidate -> validateDirectory(context, candidate) }
            store = openValidatedStore(context, dbDir)
        }

        val entry = StoreEntry(context, profileId, dbDir, store)
        try {
            installRecoveryTracking(entry)
            return entry
        } catch (e: Exception) {
            try {
                store.close()
            } catch (closeError: Exception) {
                AppLogger.e(TAG, "Failed to close ObjectBox store after tracking setup failed", closeError)
            }
            throw e
        }
    }

    private fun databaseDirectory(context: Context, profileId: String): File {
        require(StorageProfileIdPolicy.isSafeMemorySpaceId(profileId)) {
            "Invalid ObjectBox profile ID"
        }
        val dbName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
        val filesDirectory = context.filesDir.canonicalFile
        val databaseDirectory = File(filesDirectory, dbName).canonicalFile
        require(databaseDirectory.parentFile == filesDirectory) {
            "ObjectBox profile must resolve to a direct filesDir child"
        }
        return databaseDirectory
    }

    private fun openStore(context: Context, directory: File): BoxStore =
        MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .directory(directory)
            .build()

    private fun openValidatedStore(context: Context, directory: File): BoxStore {
        val store = openStore(context, directory)
        try {
            store.validate(0L, true)
            return store
        } catch (e: Exception) {
            try {
                store.close()
            } catch (closeError: Exception) {
                AppLogger.e(TAG, "Failed to close rejected ObjectBox store", closeError)
            }
            AppLogger.e(TAG, "ObjectBox validation failed while opening profile", e)
            throw e
        }
    }

    private fun validateDirectory(context: Context, directory: File): Boolean {
        synchronized(validationLock) {
            var store: BoxStore? = null
            return try {
                store = openStore(context, directory)
                store.validate(0L, true)
                true
            } catch (e: FileCorruptException) {
                AppLogger.e(TAG, "ObjectBox page validation found corrupt data", e)
                false
            } catch (e: Exception) {
                if (hasCause<FileCorruptException>(e)) {
                    AppLogger.e(TAG, "ObjectBox validation found wrapped page corruption", e)
                    return false
                }
                AppLogger.e(TAG, "ObjectBox validation could not complete", e)
                throw e
            } finally {
                try {
                    store?.close()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to close ObjectBox validation store", e)
                }
            }
        }
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

    private fun installRecoveryTracking(entry: StoreEntry) {
        if (entry.changeSubscription != null || entry.store.isClosed) return
        entry.changeSubscription =
            entry.store.subscribe()
                .onlyChanges()
                .observer { _ ->
                    synchronized(storeLock) {
                        if (stores[entry.profileId] === entry && !entry.store.isClosed) {
                            entry.changeSequence++
                            scheduleCheckpointLocked(entry, CHECKPOINT_DELAY_SECONDS)
                        }
                    }
                }
    }

    private fun scheduleCheckpointLocked(entry: StoreEntry, delaySeconds: Long) {
        if (entry.checkpointTask != null) return
        entry.checkpointTask =
            checkpointExecutor.schedule(
                { checkpointOpenStore(entry.profileId, entry.store) },
                delaySeconds,
                TimeUnit.SECONDS
            )
    }

    private fun checkpointOpenStore(profileId: String, expectedStore: BoxStore) {
        val entry =
            try {
                StorageReplacementGate.withStorageAccess {
                    synchronized(storeLock) {
                        stores[profileId]
                            ?.takeIf { current ->
                                current.store === expectedStore && !current.store.isClosed
                            }
                    }
                }
            } catch (e: Exception) {
                synchronized(storeLock) {
                    stores[profileId]
                        ?.takeIf { current -> current.store === expectedStore }
                        ?.let { current ->
                            current.checkpointTask = null
                            if (current.checkpointSequence < current.changeSequence) {
                                scheduleCheckpointLocked(current, CHECKPOINT_DELAY_SECONDS)
                            }
                        }
                }
                AppLogger.i(TAG, "Active ObjectBox checkpoint skipped during exclusive recovery")
                return
            }

        if (entry == null) return

        entry.lifecycleLock.withLock {
            val observedSequence =
                synchronized(storeLock) {
                    if (stores[profileId] !== entry || entry.store.isClosed) {
                        entry.checkpointTask = null
                        null
                    } else {
                        entry.changeSequence
                    }
                }
            if (observedSequence == null) return@withLock

            var checkpointSucceeded = false
            try {
                ObjectBoxRecoveryStorage.checkpointOpen(
                    entry.context,
                    entry.profileId,
                    entry.directory,
                    stableCopy = { target ->
                        entry.store.runInTx(Runnable {
                            File(entry.directory, "data.mdb")
                                .copyTo(target, overwrite = false)
                            Unit
                        })
                    }
                ) { candidate -> validateDirectory(entry.context, candidate) }
                checkpointSucceeded = true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to checkpoint active ObjectBox profile", e)
            } finally {
                synchronized(storeLock) {
                    if (stores[profileId] === entry) {
                        entry.checkpointTask = null
                        if (checkpointSucceeded) {
                            entry.checkpointFailureCount = 0
                            entry.checkpointSequence = observedSequence
                        } else {
                            entry.checkpointFailureCount =
                                minOf(entry.checkpointFailureCount + 1, 30)
                        }
                        if (entry.checkpointSequence < entry.changeSequence) {
                            val delaySeconds =
                                if (checkpointSucceeded) {
                                    CHECKPOINT_DELAY_SECONDS
                                } else {
                                    checkpointRetryDelaySeconds(entry.checkpointFailureCount)
                                }
                            scheduleCheckpointLocked(entry, delaySeconds)
                        }
                    }
                }
            }
        }
    }

    private fun checkpointRetryDelaySeconds(failureCount: Int): Long {
        val shift = minOf(failureCount, 6)
        return minOf(
            CHECKPOINT_MAX_RETRY_DELAY_SECONDS,
            CHECKPOINT_DELAY_SECONDS * (1L shl shift)
        )
    }

    private fun cancelPendingCheckpointLocked(entry: StoreEntry) {
        val task = entry.checkpointTask ?: return
        val cancelledBeforeRun = task.cancel(false)
        if (cancelledBeforeRun || task.isDone) {
            entry.checkpointTask = null
        }
    }

    private fun cancelRecoveryTracking(entry: StoreEntry) {
        entry.checkpointTask?.cancel(false)
        entry.checkpointTask = null
        entry.changeSubscription?.cancel()
        entry.changeSubscription = null
    }

    private fun closeAndCheckpoint(entry: StoreEntry) {
        val ownsEntry =
            synchronized(storeLock) {
                if (stores[entry.profileId] !== entry) {
                    false
                } else {
                    cancelPendingCheckpointLocked(entry)
                    true
                }
            }
        if (!ownsEntry) return

        var storeClosed = false
        try {
            entry.store.close()
            storeClosed = true
            entry.changeSubscription?.cancel()
            entry.changeSubscription = null
            ObjectBoxRecoveryStorage.checkpointClosed(
                entry.context,
                entry.profileId,
                entry.directory
            ) { candidate -> validateDirectory(entry.context, candidate) }
        } finally {
            if (storeClosed || entry.store.isClosed) {
                cancelRecoveryTracking(entry)
                synchronized(storeLock) {
                    if (stores[entry.profileId] === entry) {
                        stores.remove(entry.profileId)
                        preparedProfiles.remove(entry.profileId)
                    }
                }
            } else {
                synchronized(storeLock) {
                    if (stores[entry.profileId] === entry &&
                        entry.checkpointSequence < entry.changeSequence
                    ) {
                        scheduleCheckpointLocked(
                            entry,
                            checkpointRetryDelaySeconds(entry.checkpointFailureCount)
                        )
                    }
                }
            }
        }
    }

    private fun recordCloseFailure(
        currentFailure: Throwable?,
        nextFailure: Throwable
    ): Throwable {
        if (currentFailure == null) return nextFailure
        currentFailure.addSuppressed(nextFailure)
        return currentFailure
    }

    private fun closeAllInternal(strictFailureMessage: String?) {
        val entries = synchronized(storeLock) { stores.values.toList() }
        var failure: Throwable? = null
        entries.forEach { entry ->
            entry.lifecycleLock.withLock {
                try {
                    closeAndCheckpoint(entry)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to close and checkpoint ObjectBox profile", e)
                    failure = recordCloseFailure(failure, e)
                }
            }
        }
        synchronized(storeLock) {
            preparedProfiles.retainAll(stores.keys)
        }
        if (strictFailureMessage != null) {
            failure?.let { error ->
                throw StorageRecoveryException(strictFailureMessage, error)
            }
        }
    }

    fun close(profileId: String) {
        val entry = synchronized(storeLock) { stores[profileId] } ?: return
        entry.lifecycleLock.withLock {
            closeAndCheckpoint(entry)
        }
    }

    /**
     * 物理删除指定profileId的数据库（包括关闭store和删除文件夹）。
     */
    fun delete(context: Context, profileId: String) {
        StorageReplacementGate.withStorageAccess {
            synchronized(storeLock) {
                check(profilesBeingDeleted.add(profileId)) {
                    "ObjectBox profile deletion is already active: $profileId"
                }
            }
            try {
                while (true) {
                    val entry = synchronized(storeLock) { stores[profileId] } ?: break
                    entry.lifecycleLock.withLock {
                        closeAndCheckpoint(entry)
                    }
                }
                synchronized(storeLock) {
                    preparedProfiles.remove(profileId)
                }

                val dbDir = databaseDirectory(context.applicationContext, profileId)
                // Detach verified slots first. If live deletion later fails, the slots remain in
                // quarantine for rescue but cannot resurrect a profile the user chose to delete.
                ObjectBoxRecoveryStorage.archiveRecoveryStateForDeletion(context, profileId)
                if (dbDir.exists() && !dbDir.deleteRecursively()) {
                    throw IllegalStateException(
                        "Failed to delete ObjectBox profile directory: ${dbDir.name}"
                    )
                }
            } finally {
                synchronized(storeLock) {
                    profilesBeingDeleted.remove(profileId)
                }
            }
        }
    }

    internal fun stageAllForSnapshotExport(context: Context): SnapshotExport {
        val appContext = context.applicationContext
        val stagingDirectory =
            File(appContext.cacheDir, "objectbox_raw_export_${UUID.randomUUID()}")
        check(stagingDirectory.mkdirs()) {
            "Failed to create ObjectBox raw snapshot staging directory"
        }

        try {
            val profileDirectories =
                synchronized(storeLock) {
                    check(profilesBeingDeleted.isEmpty()) {
                        "ObjectBox profile deletion is active during raw snapshot export"
                    }
                    appContext.filesDir.listFiles { file ->
                        file.name == "objectbox" || file.name.startsWith("objectbox_")
                    }?.toList()
                        ?: throw IllegalStateException(
                            "Failed to enumerate ObjectBox profiles for raw snapshot export"
                        )
                }

            val files = mutableListOf<SnapshotFile>()
            profileDirectories.sortedBy { it.name }.forEachIndexed { index, directory ->
                check(directory.isDirectory) {
                    "ObjectBox raw snapshot source is not a directory: ${directory.name}"
                }
                val canonicalDirectory = directory.canonicalFile
                check(canonicalDirectory.parentFile == appContext.filesDir.canonicalFile) {
                    "ObjectBox raw snapshot source resolves outside filesDir"
                }
                val profileId =
                    if (directory.name == "objectbox") "default"
                    else directory.name.removePrefix("objectbox_")
                check(profileId.isNotBlank()) {
                    "ObjectBox raw snapshot profile ID is blank"
                }
                check(StorageProfileIdPolicy.isSafeMemorySpaceId(profileId)) {
                    "ObjectBox raw snapshot profile ID is unsafe"
                }

                val stagedProfileDirectory = File(stagingDirectory, "profile_$index")
                check(stagedProfileDirectory.mkdirs()) {
                    "Failed to create ObjectBox raw snapshot profile directory"
                }
                val stagedDataFile = File(stagedProfileDirectory, "data.mdb")
                stageProfileData(profileId, canonicalDirectory, stagedDataFile)
                check(validateDirectory(appContext, stagedProfileDirectory)) {
                    "ObjectBox raw snapshot copy failed full-page validation: ${directory.name}"
                }
                val stagedLockFile = File(stagedProfileDirectory, "lock.mdb")
                if (stagedLockFile.exists() && !stagedLockFile.delete()) {
                    throw IllegalStateException(
                        "Failed to remove ObjectBox raw snapshot validation lock file"
                    )
                }
                files +=
                    SnapshotFile(
                        relativePath = "${directory.name}/data.mdb",
                        file = stagedDataFile
                    )
            }
            return SnapshotExport(files, stagingDirectory)
        } catch (e: Exception) {
            if (!stagingDirectory.deleteRecursively()) {
                AppLogger.w(TAG, "Failed to clean rejected ObjectBox raw snapshot staging directory")
            }
            throw e
        }
    }

    private fun stageProfileData(
        profileId: String,
        directory: File,
        target: File
    ) {
        while (true) {
            val entry =
                synchronized(storeLock) {
                    check(profileId !in profilesBeingDeleted) {
                        "ObjectBox profile deletion began during raw snapshot export"
                    }
                    stores[profileId]
                }
            if (entry == null) {
                val copiedClosedProfile =
                    synchronized(storeLock) {
                        check(profileId !in profilesBeingDeleted) {
                            "ObjectBox profile deletion began during raw snapshot export"
                        }
                        if (stores[profileId] != null) {
                            false
                        } else {
                            val source = File(directory, "data.mdb")
                            check(source.isFile) {
                                "ObjectBox raw snapshot source data.mdb is missing"
                            }
                            source.copyTo(target, overwrite = false)
                            true
                        }
                    }
                if (copiedClosedProfile) return
                continue
            }

            entry.lifecycleLock.withLock {
                var detachedClosedEntry = false
                val canCopyOpenStore =
                    synchronized(storeLock) {
                        when {
                            stores[profileId] !== entry -> false
                            entry.store.isClosed -> {
                                stores.remove(profileId)
                                preparedProfiles.remove(profileId)
                                detachedClosedEntry = true
                                false
                            }
                            else -> true
                        }
                    }
                if (detachedClosedEntry) cancelRecoveryTracking(entry)
                if (!canCopyOpenStore) return@withLock

                entry.store.runInTx(Runnable {
                    val source = File(directory, "data.mdb")
                    check(source.isFile) {
                        "ObjectBox raw snapshot source data.mdb is missing"
                    }
                    source.copyTo(target, overwrite = false)
                    Unit
                })
                return
            }
        }
    }

    fun closeAll() {
        closeAllInternal(strictFailureMessage = null)
    }

    fun closeAllForSnapshotExport() {
        closeAllInternal(
            strictFailureMessage =
                "ObjectBox owners could not be closed before raw snapshot export."
        )
    }

    fun closeAllForReplacement() {
        closeAllInternal(
            strictFailureMessage =
                "ObjectBox owners could not be closed before raw snapshot replacement."
        )
    }

    fun invalidatePreparedState() {
        synchronized(storeLock) {
            check(stores.isEmpty()) { "ObjectBox stores must be closed before replacing profile files" }
            preparedProfiles.clear()
        }
    }
}
