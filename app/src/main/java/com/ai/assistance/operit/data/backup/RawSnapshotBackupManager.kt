package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.persistence.RecoverablePreferenceDataStores
import com.ai.assistance.operit.data.persistence.RoomRecoveryStorage
import com.ai.assistance.operit.data.persistence.StorageProcessLock
import com.ai.assistance.operit.data.persistence.StorageProfileIdPolicy
import com.ai.assistance.operit.data.persistence.StorageRecoveryCoordinator
import com.ai.assistance.operit.data.persistence.StorageReplacementGate
import com.ai.assistance.operit.data.stats.TokenUsageRepository
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SNAPSHOT_PACKAGE_NAME_PREFIX = "com.ai.assistance.operit"

internal fun isSupportedSnapshotPackageName(packageName: String): Boolean =
    packageName.startsWith(SNAPSHOT_PACKAGE_NAME_PREFIX)

object RawSnapshotBackupManager {

    private const val TAG = "RawSnapshotBackup"
    private const val FORMAT_VERSION = 1

    private const val ZIP_PREFIX = "operit_raw_snapshot_"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_PAYLOAD_PREFIX = "payload/"

    private const val ENTRY_FILES = "payload/files/"
    private const val ENTRY_EXTERNAL_FILES = "payload/external_files/"
    private const val ENTRY_SHARED_PREFS = "payload/shared_prefs/"
    private const val ENTRY_DATASTORE = "payload/datastore/"
    private const val ENTRY_DATABASES = "payload/databases/"
    private val REQUIRED_MANIFEST_INCLUDES =
        listOf(
            ENTRY_FILES,
            ENTRY_EXTERNAL_FILES,
            ENTRY_SHARED_PREFS,
            ENTRY_DATASTORE,
            ENTRY_DATABASES
        )

    private val terminalTopLevelDirNames = setOf("usr", "tmp", "bin")
    private val roomDatabaseFileNames =
        setOf(
            RoomRecoveryStorage.DATABASE_NAME,
            "${RoomRecoveryStorage.DATABASE_NAME}-wal",
            "${RoomRecoveryStorage.DATABASE_NAME}-shm",
            "${RoomRecoveryStorage.DATABASE_NAME}-journal"
        )

    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Serializable
    data class Manifest(
        val formatVersion: Int,
        val packageName: String,
        val createdAt: Long,
        val includes: List<String>,
        val includeTerminalData: Boolean = true
    )

    data class SnapshotOptions(
        val includeTerminalData: Boolean = false
    )

    private data class StagedZipFile(
        val relativePath: String,
        val file: File
    )

    enum class ExportProgress {
        PREPARING,
        SCANNING_FILES,
        ZIPPING_FILES,
        ZIPPING_EXTERNAL_FILES,
        ZIPPING_SHARED_PREFS,
        ZIPPING_DATASTORE,
        ZIPPING_DATABASES,
        FINALIZING
    }

    data class ExportProgressInfo(
        val stage: ExportProgress,
        val percent: Int? = null,
        val scannedFiles: Int? = null
    )

    enum class RestoreProgress {
        PREPARING,
        READING_ZIP,
        EXTRACTING,
        REPLACING_FILES,
        REPLACING_EXTERNAL_FILES,
        REPLACING_SHARED_PREFS,
        REPLACING_DATASTORE,
        REPLACING_DATABASES,
        FINALIZING
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun exportToBackupDir(
        context: Context,
        options: SnapshotOptions = SnapshotOptions(),
        onProgress: ((ExportProgressInfo) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        TokenUsageRepository.withDatabaseAccess {
            mutex.withLock {
                requireSnapshotProcess(context)
            val lease = StorageProcessLock.acquireOperationLease(context, "raw-snapshot-export")
            val repairProcess = StorageProcessLock.isRepairProcess(context)
            var objectBoxSnapshot: ObjectBoxManager.SnapshotExport? = null
            var roomSnapshot: AppDatabase.SnapshotExport? = null
            try {
                AppLogger.i(TAG, "export start (includeTerminalData=${options.includeTerminalData})")
                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.PREPARING)) }
                if (repairProcess) {
                    RecoverablePreferenceDataStores.closeAllAndAwait()
                    AppDatabase.closeDatabase()
                    ObjectBoxManager.closeAllForSnapshotExport()
                    RoomRecoveryStorage.checkpointClosed(context)
                    AppDatabase.getDatabase(context).openHelper.writableDatabase
                    AppDatabase.closeDatabase()
                } else {
                    // Main-process repositories retain Room DAOs and ObjectBox stores. Closing
                    // them here would break the released settings-screen export contract.
                    RecoverablePreferenceDataStores.checkpointKnownStores(context)
                    AppDatabase.getDatabase(context).openHelper.writableDatabase
                }
                val stagedRoomSnapshot =
                    AppDatabase.stageForSnapshotExport(context).also { snapshot ->
                        roomSnapshot = snapshot
                    }
                val stagedRoomFiles =
                    stagedRoomSnapshot.files.map { snapshotFile ->
                        StagedZipFile(snapshotFile.relativePath, snapshotFile.file)
                    }
                val stagedObjectBoxSnapshot =
                    ObjectBoxManager.stageAllForSnapshotExport(context).also { snapshot ->
                        objectBoxSnapshot = snapshot
                    }
                val stagedObjectBoxFiles = stagedObjectBoxSnapshot.files
                    .map { snapshotFile ->
                        StagedZipFile(snapshotFile.relativePath, snapshotFile.file)
                    }
                val exportDir = OperitBackupDirs.rawSnapshotDir()
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val outFile = File(exportDir, "$ZIP_PREFIX$timestamp.zip")
                val tmpFile = File(exportDir, "${outFile.name}.tmp")

                if (tmpFile.exists()) {
                    tmpFile.delete()
                }

                val dataDir = context.dataDir
                val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
                    "External files dir is unavailable"
                }
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                val datastoreDir = File(dataDir, "datastore")
                val databasesDir = File(dataDir, "databases")

                val manifest =
                    Manifest(
                        formatVersion = FORMAT_VERSION,
                        packageName = context.packageName,
                        createdAt = System.currentTimeMillis(),
                        includes = REQUIRED_MANIFEST_INCLUDES,
                        includeTerminalData = options.includeTerminalData
                    )

                ZipOutputStream(BufferedOutputStream(FileOutputStream(tmpFile))).use { zos ->
                    zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()
                    val excludedNames =
                        if (options.includeTerminalData) {
                            alwaysExcluded
                        } else {
                            alwaysExcluded + terminalTopLevelDirNames
                        }
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(
                                stage = ExportProgress.SCANNING_FILES,
                                scannedFiles = 0
                            )
                        )
                    }
                    val filesTotalCount =
                        totalFilesForZip(
                            dir = context.filesDir,
                            entryPrefix = ENTRY_FILES,
                            excludedTopLevelDirNames = excludedNames,
                            excludeObjectBoxDirectories = true,
                            onScannedCountChanged = { scanned ->
                                if (onProgress != null) {
                                    mainHandler.post {
                                        onProgress.invoke(
                                            ExportProgressInfo(
                                                stage = ExportProgress.SCANNING_FILES,
                                                scannedFiles = scanned
                                            )
                                        )
                                    }
                                }
                            }
                        ) + stagedObjectBoxFiles.size
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(
                                stage = ExportProgress.SCANNING_FILES,
                                scannedFiles = filesTotalCount
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(ExportProgress.ZIPPING_FILES, 0)
                        )
                    }
                    val filesMs =
                        measureTimeMillis {
                            val regularFilesWritten =
                                addDirToZip(
                                    zos = zos,
                                    dir = context.filesDir,
                                    entryPrefix = ENTRY_FILES,
                                    excludedTopLevelDirNames = excludedNames,
                                    totalFiles = filesTotalCount,
                                    excludeObjectBoxDirectories = true,
                                    onPercentChanged = { percent ->
                                        if (onProgress != null) {
                                            mainHandler.post {
                                                onProgress.invoke(
                                                    ExportProgressInfo(
                                                        ExportProgress.ZIPPING_FILES,
                                                        percent
                                                    )
                                                )
                                            }
                                        }
                                    }
                                )
                            addStagedSnapshotToZip(
                                zos = zos,
                                entryPrefix = ENTRY_FILES,
                                files = stagedObjectBoxFiles,
                                processedBefore = regularFilesWritten,
                                totalFiles = filesTotalCount,
                                onPercentChanged = { percent ->
                                    if (onProgress != null) {
                                        mainHandler.post {
                                            onProgress.invoke(
                                                ExportProgressInfo(
                                                    ExportProgress.ZIPPING_FILES,
                                                    percent
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(ExportProgress.ZIPPING_FILES, 100)
                        )
                    }
                    AppLogger.i(
                        TAG,
                        "export add files done in ${filesMs}ms " +
                            "(excludedTopLevel=${excludedNames.size})"
                    )

                    val externalFilesTotalCount =
                        totalFilesForZip(
                            dir = externalFilesDir,
                            entryPrefix = ENTRY_EXTERNAL_FILES,
                            excludedTopLevelDirNames = emptySet()
                        )
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, 0)
                        )
                    }
                    val externalFilesMs =
                        measureTimeMillis {
                            addDirToZip(
                                zos = zos,
                                dir = externalFilesDir,
                                entryPrefix = ENTRY_EXTERNAL_FILES,
                                totalFiles = externalFilesTotalCount,
                                onPercentChanged = { percent ->
                                    if (onProgress != null) {
                                        mainHandler.post {
                                            onProgress.invoke(
                                                ExportProgressInfo(
                                                    ExportProgress.ZIPPING_EXTERNAL_FILES,
                                                    percent
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, 100)
                        )
                    }
                    AppLogger.i(
                        TAG,
                        "export add external_files done in ${externalFilesMs}ms"
                    )

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(
                            ExportProgressInfo(ExportProgress.ZIPPING_SHARED_PREFS)
                        )
                    }
                    val sharedPrefsMs =
                        measureTimeMillis {
                            addDirToZip(zos, sharedPrefsDir, ENTRY_SHARED_PREFS)
                        }
                    AppLogger.i(TAG, "export add shared_prefs done in ${sharedPrefsMs}ms")

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATASTORE))
                    }
                    val datastoreMs =
                        measureTimeMillis { addDirToZip(zos, datastoreDir, ENTRY_DATASTORE) }
                    AppLogger.i(TAG, "export add datastore done in ${datastoreMs}ms")

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATABASES))
                    }
                    val databasesMs =
                        measureTimeMillis {
                            addDirToZip(
                                zos,
                                databasesDir,
                                ENTRY_DATABASES,
                                excludedTopLevelDirNames = roomDatabaseFileNames
                            )
                            addStagedSnapshotToZip(
                                zos = zos,
                                entryPrefix = ENTRY_DATABASES,
                                files = stagedRoomFiles,
                                processedBefore = 0,
                                totalFiles = 0,
                                onPercentChanged = null
                            )
                        }
                    AppLogger.i(TAG, "export add databases done in ${databasesMs}ms")
                }

                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(ExportProgress.FINALIZING))
                }
                if (outFile.exists()) {
                    outFile.delete()
                }

                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
                }

                AppLogger.i(TAG, "export done: ${outFile.absolutePath} (${outFile.length()} bytes)")
                outFile
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        try {
                            try {
                                objectBoxSnapshot?.close()
                            } finally {
                                roomSnapshot?.close()
                            }
                        } finally {
                            if (repairProcess) {
                                RecoverablePreferenceDataStores.closeAllAndAwait()
                                AppDatabase.closeDatabase()
                                ObjectBoxManager.closeAll()
                            }
                        }
                    } finally {
                        lease.close()
                    }
                }
            }
        }
    }
    }

    suspend fun restoreFromBackupUri(
        context: Context,
        uri: Uri,
        onProgress: ((RestoreProgress) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        TokenUsageRepository.withDatabaseRestore {
            mutex.withLock {
                requireSnapshotProcess(context)
            val cacheZip = File.createTempFile("raw_snapshot_restore_", ".zip", context.cacheDir)
            val workDir = File(context.cacheDir, "raw_snapshot_restore_work")
            if (workDir.exists()) {
                check(workDir.deleteRecursively()) {
                    "Failed to remove previous raw snapshot restore work directory"
                }
            }
            check(workDir.mkdirs()) {
                "Failed to create raw snapshot restore work directory"
            }
            val lease =
                try {
                    StorageProcessLock.acquireOperationLease(context, "raw-snapshot-restore")
                } catch (e: Exception) {
                    cacheZip.delete()
                    workDir.deleteRecursively()
                    throw e
            }
            var storageOwnersClosed = false
            var replacementLease: StorageReplacementGate.ReplacementLease? = null

            try {
                AppLogger.i(TAG, "restore start uri=$uri")
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.PREPARING) }
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.READING_ZIP) }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheZip).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Failed to open uri")

                AppLogger.i(TAG, "restore cached zip: ${cacheZip.absolutePath} (${cacheZip.length()} bytes)")

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.EXTRACTING) }
                val manifest = extractZipToWorkDir(cacheZip, workDir)

                val payloadDir = File(workDir, "payload")
                validatePayloadShape(manifest, payloadDir)
                val externalFilesPayloadDir = File(payloadDir, "external_files")
                val roomPayload = File(payloadDir, "databases/${RoomRecoveryStorage.DATABASE_NAME}")
                val roomSidecarPayloads =
                    listOf("-wal", "-shm", "-journal").map { suffix ->
                        File(roomPayload.absolutePath + suffix)
                    }
                if (roomPayload.exists() && !roomPayload.isFile) {
                    throw IllegalArgumentException(
                        "Raw snapshot Room database payload is not a regular file"
                    )
                }
                roomSidecarPayloads.forEach { sidecar ->
                    if (sidecar.exists() && !sidecar.isFile) {
                        throw IllegalArgumentException(
                            "Raw snapshot Room sidecar is not a regular file: ${sidecar.name}"
                        )
                    }
                }
                if (!roomPayload.isFile && roomSidecarPayloads.any { it.exists() }) {
                    throw IllegalArgumentException(
                        "Raw snapshot contains Room sidecars without the main database"
                    )
                }
                if (roomPayload.isFile &&
                    !RoomRecoveryStorage.validateDatabaseSet(context, roomPayload)
                ) {
                    throw IllegalArgumentException("Raw snapshot contains an invalid Room database")
                }
                val filesPayload = File(payloadDir, "files")
                val filesPayloadEntries =
                    if (filesPayload.exists()) {
                        filesPayload.listFiles()
                            ?: throw IllegalArgumentException(
                                "Raw snapshot files payload cannot be enumerated"
                            )
                    } else {
                        emptyArray<File>()
                    }
                filesPayloadEntries
                    .filter { file ->
                        file.name == "objectbox" || file.name.startsWith("objectbox_")
                    }
                    .forEach { objectBoxPayload ->
                        val profileId =
                            if (objectBoxPayload.name == "objectbox") {
                                "default"
                            } else {
                                objectBoxPayload.name.removePrefix("objectbox_")
                            }
                        if (!StorageProfileIdPolicy.isSafeMemorySpaceId(profileId)) {
                            throw IllegalArgumentException(
                                "Raw snapshot ObjectBox profile ID is invalid"
                            )
                        }
                        if (!objectBoxPayload.isDirectory) {
                            throw IllegalArgumentException(
                                "Raw snapshot ObjectBox payload is not a directory: " +
                                    objectBoxPayload.name
                            )
                        }
                        if (!File(objectBoxPayload, "data.mdb").isFile) {
                            throw IllegalArgumentException(
                                "Raw snapshot contains an ObjectBox directory without data.mdb: " +
                                    objectBoxPayload.name
                            )
                        }
                        if (!ObjectBoxManager.validateRecoveryDirectory(context, objectBoxPayload)) {
                            throw IllegalArgumentException(
                                "Raw snapshot contains an invalid ObjectBox database: " +
                                    objectBoxPayload.name
                            )
                        }
                    }

                val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()

                val preserveTerminal = !manifest.includeTerminalData
                val preservedTerminalNames = if (preserveTerminal) terminalTopLevelDirNames else emptySet()
                val preservedAlwaysExcludedNames = alwaysExcluded.filterNot { dirName ->
                    File(payloadDir, "files/$dirName").exists()
                }.toSet()
                val preservedNames = preservedTerminalNames + preservedAlwaysExcludedNames
                val externalFilesDir =
                    if (externalFilesPayloadDir.exists()) {
                        requireNotNull(context.getExternalFilesDir(null)) {
                            "External files dir is unavailable"
                        }
                    } else {
                        null
                    }

                AppLogger.i(
                    TAG,
                    "restore manifest ok (formatVersion=${manifest.formatVersion}, includeTerminalData=${manifest.includeTerminalData})"
                )

                AppLogger.i(TAG, "restore replace dirs (preserveTerminalTopLevel=${preservedNames.isNotEmpty()})")

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_FILES) }

                // Keep the released settings-screen owners alive until the archive, all imported
                // databases, and replacement preconditions have passed validation. Once this flag
                // is set, final cleanup must finish closing even if a later replacement fails.
                val activeReplacement = StorageReplacementGate.acquire()
                replacementLease = activeReplacement
                activeReplacement.withAccess {
                    storageOwnersClosed = true
                    AppDatabase.closeDatabase()
                    ObjectBoxManager.closeAllForReplacement()
                    RecoverablePreferenceDataStores.closeAllAndAwait()

                    AppLogger.i(TAG, "restore closed databases after payload validation")

                    replaceDirContents(
                        File(payloadDir, "files"),
                        context.filesDir,
                        preservedTopLevelDirNames = preservedNames
                    )
                    if (externalFilesDir != null) {
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(RestoreProgress.REPLACING_EXTERNAL_FILES)
                        }
                        replaceDirContents(externalFilesPayloadDir, externalFilesDir)
                    }
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(RestoreProgress.REPLACING_SHARED_PREFS)
                    }
                    replaceDirContents(
                        File(payloadDir, "shared_prefs"),
                        File(context.dataDir, "shared_prefs")
                    )
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(RestoreProgress.REPLACING_DATASTORE)
                    }
                    replaceDirContents(
                        File(payloadDir, "datastore"),
                        File(context.dataDir, "datastore")
                    )
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(RestoreProgress.REPLACING_DATABASES)
                    }
                    replaceDirContents(
                        File(payloadDir, "databases"),
                        File(context.dataDir, "databases")
                    )

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(RestoreProgress.FINALIZING)
                    }
                    StorageRecoveryCoordinator.archiveSnapshotsBeforeRawRestoreRecovery(
                        context.applicationContext
                    )
                    RoomRecoveryStorage.invalidatePreparedState()
                    ObjectBoxManager.invalidatePreparedState()
                    StorageRecoveryCoordinator.recoverPreferences(context.applicationContext)
                    AppLogger.i(TAG, "restore done: ${manifest.packageName}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "restore failed", e)
                throw e
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        if (storageOwnersClosed) {
                            RecoverablePreferenceDataStores.closeAllAndAwait()
                            AppDatabase.closeDatabase()
                            ObjectBoxManager.closeAll()
                        }
                        try {
                            cacheZip.delete()
                        } catch (_: Exception) {
                        }
                        try {
                            workDir.deleteRecursively()
                        } catch (_: Exception) {
                        }
                    } finally {
                        try {
                            replacementLease?.close()
                        } finally {
                            lease.close()
                        }
                    }
                }
            }
        }
    }
    }

    private fun extractZipToWorkDir(zipFile: File, workDir: File): Manifest {
        val payloadRoot = File(workDir, "payload")
        check(payloadRoot.mkdirs() || payloadRoot.isDirectory) {
            "Failed to create raw snapshot payload root"
        }
        val payloadCanonical = payloadRoot.canonicalFile

        var manifestText: String? = null
        var extractedPayloadFiles = 0
        val extractedEntryNames = mutableSetOf<String>()
        val extractedTargetPaths = mutableSetOf<String>()

        val buffer = ByteArray(64 * 1024)
        val extractMs = measureTimeMillis {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    if (!extractedEntryNames.add(name)) {
                        zis.closeEntry()
                        throw IllegalArgumentException("Invalid backup zip: duplicate entry $name")
                    }

                    if (entry.isDirectory && !name.startsWith(ENTRY_PAYLOAD_PREFIX)) {
                        zis.closeEntry()
                        continue
                    }

                    if (name == ENTRY_MANIFEST) {
                        val bytes = zis.readBytesSafely(maxBytes = 512 * 1024)
                        manifestText = bytes.toString(Charsets.UTF_8)
                        zis.closeEntry()
                        continue
                    }

                    if (!name.startsWith(ENTRY_PAYLOAD_PREFIX)) {
                        zis.closeEntry()
                        continue
                    }

                    val target = File(workDir, name)
                    val targetCanonical = target.canonicalFile
                    val isPayloadRoot = targetCanonical == payloadCanonical
                    val isPayloadChild =
                        targetCanonical.path.startsWith(payloadCanonical.path + File.separator)
                    if (!isPayloadRoot && !isPayloadChild) {
                        zis.closeEntry()
                        throw IllegalArgumentException("Invalid zip entry path: $name")
                    }
                    if (!extractedTargetPaths.add(targetCanonical.path)) {
                        zis.closeEntry()
                        throw IllegalArgumentException(
                            "Invalid backup zip: duplicate target path $name"
                        )
                    }

                    if (entry.isDirectory) {
                        check(target.mkdirs() || target.isDirectory) {
                            "Failed to create raw snapshot payload directory: $name"
                        }
                        zis.closeEntry()
                        continue
                    }

                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                    }

                    extractedPayloadFiles++

                    zis.closeEntry()
                }
            }
        }

        AppLogger.i(TAG, "restore extract done in ${extractMs}ms (payloadFiles=$extractedPayloadFiles)")

        val manifest = manifestText?.let { json.decodeFromString(Manifest.serializer(), it) }
            ?: throw IllegalArgumentException("Invalid backup zip: missing $ENTRY_MANIFEST")

        if (manifest.formatVersion != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: ${manifest.formatVersion}")
        }

        if (!isSupportedSnapshotPackageName(manifest.packageName)) {
            throw IllegalArgumentException("Backup package mismatch: ${manifest.packageName}")
        }

        return manifest
    }

    private fun validatePayloadShape(manifest: Manifest, payloadDir: File) {
        if (manifest.includes != REQUIRED_MANIFEST_INCLUDES) {
            throw IllegalArgumentException("Raw snapshot manifest has an incomplete payload list")
        }
        check(payloadDir.isDirectory) { "Raw snapshot payload root is not a directory" }
        REQUIRED_MANIFEST_INCLUDES.forEach { entryPrefix ->
            val relative = entryPrefix.removePrefix(ENTRY_PAYLOAD_PREFIX).removeSuffix("/")
            val directory = File(payloadDir, relative)
            if (directory.exists() && !directory.isDirectory) {
                throw IllegalArgumentException(
                    "Raw snapshot payload category is not a directory: $relative"
                )
            }
        }
    }

    private fun addDirToZip(
        zos: ZipOutputStream,
        dir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String> = emptySet(),
        totalFiles: Int = 0,
        onPercentChanged: ((Int) -> Unit)? = null,
        excludeObjectBoxDirectories: Boolean = false
    ): Int {
        if (!dir.exists() || !dir.isDirectory) return 0

        val baseCanonical = dir.canonicalFile
        val buffer = ByteArray(64 * 1024)
        val writtenEntryNames = HashSet<String>()

        var processedFiles = 0
        var lastPercent = -1

        dir.walkTopDown().onEnter { currentDir ->
            !shouldPruneDirForZip(
                currentDir,
                dir,
                entryPrefix,
                excludedTopLevelDirNames,
                excludeObjectBoxDirectories
            )
        }.forEach { f ->
            if (!f.isFile) return@forEach

            val canonical = f.canonicalFile
            if (shouldSkipForZip(
                    canonical,
                    baseCanonical,
                    entryPrefix,
                    excludedTopLevelDirNames,
                    excludeObjectBoxDirectories
                )
            ) {
                if (canonical.name == "lock.mdb" && canonical.parentFile?.name?.startsWith("objectbox") == true) {
                    AppLogger.w(TAG, "export skip objectbox lock file: ${canonical.absolutePath}")
                }
                return@forEach
            }

            val rel = canonical.path.substring(baseCanonical.path.length + 1)
            val entryName = entryPrefix + rel.replace(File.separatorChar, '/')

            if (!writtenEntryNames.add(entryName)) {
                AppLogger.w(TAG, "export skip duplicate entry: $entryName")
                return@forEach
            }

            zos.putNextEntry(ZipEntry(entryName))
            BufferedInputStream(FileInputStream(canonical)).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()

            processedFiles++
            if (totalFiles > 0 && onPercentChanged != null) {
                val percent = ((processedFiles * 100) / totalFiles).coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onPercentChanged(percent)
                }
            }
        }
        return processedFiles
    }

    private fun addStagedSnapshotToZip(
        zos: ZipOutputStream,
        entryPrefix: String,
        files: List<StagedZipFile>,
        processedBefore: Int,
        totalFiles: Int,
        onPercentChanged: ((Int) -> Unit)?
    ) {
        val buffer = ByteArray(64 * 1024)
        val writtenEntryNames = mutableSetOf<String>()
        var processedFiles = processedBefore
        var lastPercent = -1
        files.forEach { snapshotFile ->
            check(snapshotFile.file.isFile) {
                "Raw snapshot staged data file is missing"
            }
            val relativePath = snapshotFile.relativePath.replace(File.separatorChar, '/')
            check(!relativePath.startsWith('/') &&
                relativePath.split('/').none { segment -> segment.isBlank() || segment == ".." }
            ) {
                "Invalid raw snapshot staged entry path"
            }
            val entryName = entryPrefix + relativePath
            check(writtenEntryNames.add(entryName)) {
                "Duplicate raw snapshot staged entry: $entryName"
            }

            zos.putNextEntry(ZipEntry(entryName))
            BufferedInputStream(FileInputStream(snapshotFile.file)).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()

            processedFiles++
            if (totalFiles > 0 && onPercentChanged != null) {
                val percent = ((processedFiles * 100) / totalFiles).coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onPercentChanged(percent)
                }
            }
        }
    }

    private fun shouldPruneDirForZip(
        currentDir: File,
        baseDir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>,
        excludeObjectBoxDirectories: Boolean
    ): Boolean {
        if (currentDir == baseDir) return false
        val parent = currentDir.parentFile ?: return false
        if (parent != baseDir) return false

        val name = currentDir.name
        if (excludedTopLevelDirNames.contains(name)) return true
        if (excludeObjectBoxDirectories && isObjectBoxDirectoryName(name)) return true

        if (entryPrefix == ENTRY_FILES) {
            if (name.startsWith("sherpa-ncnn-")) return true
        }

        return false
    }

    private fun shouldSkipForZip(
        canonical: File,
        baseCanonical: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>,
        excludeObjectBoxDirectories: Boolean
    ): Boolean {
        if (!canonical.path.startsWith(baseCanonical.path + File.separator)) return true

        if (canonical.name == "lock.mdb" && canonical.parentFile?.name?.startsWith("objectbox") == true) {
            return true
        }

        val rel = canonical.path.substring(baseCanonical.path.length + 1)
        val relNormalized = rel.replace(File.separatorChar, '/')
        val top = relNormalized.substringBefore('/', missingDelimiterValue = relNormalized)
        if (excludedTopLevelDirNames.isNotEmpty() && excludedTopLevelDirNames.contains(top)) {
            return true
        }
        if (excludeObjectBoxDirectories && isObjectBoxDirectoryName(top)) return true

        if (entryPrefix == ENTRY_FILES) {
            if (top.startsWith("sherpa-ncnn-")) {
                return true
            }

            // Exclude Ubuntu rootfs package (very large). Stored as a top-level file in filesDir.
            if (!relNormalized.contains('/')) {
                val name = relNormalized
                if (name.startsWith("ubuntu-", ignoreCase = true) && name.endsWith(".tar.xz", ignoreCase = true)) {
                    return true
                }
            }

            if (!relNormalized.contains('/')) {
                if (relNormalized.startsWith("memory_hnsw_") && relNormalized.endsWith(".idx")) {
                    return true
                }
                if (relNormalized.startsWith("doc_index_") && relNormalized.endsWith(".hnsw")) {
                    return true
                }
            }
        }

        return false
    }

    private fun isObjectBoxDirectoryName(name: String): Boolean =
        name == "objectbox" || name.startsWith("objectbox_")

    private fun totalFilesForZip(
        dir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>,
        excludeObjectBoxDirectories: Boolean = false,
        onScannedCountChanged: ((Int) -> Unit)? = null
    ): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        val baseCanonical = dir.canonicalFile
        var total = 0

        var lastReported = 0
        var lastReportAtMs = 0L
        dir.walkTopDown().onEnter { currentDir ->
            !shouldPruneDirForZip(
                currentDir,
                dir,
                entryPrefix,
                excludedTopLevelDirNames,
                excludeObjectBoxDirectories
            )
        }.forEach { f ->
            if (!f.isFile) return@forEach
            val canonical = f.canonicalFile
            if (shouldSkipForZip(
                    canonical,
                    baseCanonical,
                    entryPrefix,
                    excludedTopLevelDirNames,
                    excludeObjectBoxDirectories
                )
            ) return@forEach
            total++

            if (onScannedCountChanged != null) {
                val now = System.currentTimeMillis()
                if (total == 1 || total - lastReported >= 200 || now - lastReportAtMs >= 250L) {
                    lastReported = total
                    lastReportAtMs = now
                    onScannedCountChanged(total)
                }
            }
        }
        return total
    }

    private fun replaceDirContents(
        fromDir: File,
        toDir: File,
        preservedTopLevelDirNames: Set<String> = emptySet()
    ) {
        if (toDir.exists()) {
            check(toDir.isDirectory) {
                "Raw snapshot target is not a directory: ${toDir.absolutePath}"
            }
        } else {
            check(toDir.mkdirs()) {
                "Failed to create raw snapshot target: ${toDir.absolutePath}"
            }
        }

        // A raw snapshot is a complete restore point. Keeping entries that are absent from the
        // snapshot leaves newer migration markers behind and changes how restored data is read.
        val existingEntries =
            toDir.listFiles()
                ?: throw IllegalStateException(
                    "Failed to enumerate raw snapshot target: ${toDir.absolutePath}"
                )
        existingEntries.forEach { existing ->
            if (!preservedTopLevelDirNames.contains(existing.name)) {
                check(existing.deleteRecursively()) {
                    "Failed to remove stale snapshot entry: ${existing.absolutePath}"
                }
            }
        }

        if (!fromDir.exists() || !fromDir.isDirectory) return
        copyDir(fromDir, toDir, preservedTopLevelDirNames)
    }

    private fun copyDir(
        fromDir: File,
        toDir: File,
        preservedTopLevelDirNames: Set<String>
    ) {
        val baseCanonical = fromDir.canonicalFile
        fromDir.walkTopDown().forEach { f ->
            val canonical = f.canonicalFile
            if (!canonical.path.startsWith(baseCanonical.path + File.separator) && canonical != baseCanonical) {
                return@forEach
            }

            if (canonical == baseCanonical) return@forEach

            val rel = canonical.path.substring(baseCanonical.path.length + 1)
            if (preservedTopLevelDirNames.isNotEmpty()) {
                val relNormalized = rel.replace(File.separatorChar, '/')
                val top = relNormalized.substringBefore('/', missingDelimiterValue = relNormalized)
                if (preservedTopLevelDirNames.contains(top)) {
                    return@forEach
                }
            }
            val target = File(toDir, rel)

            if (canonical.isDirectory) {
                target.mkdirs()
            } else if (canonical.isFile) {
                target.parentFile?.mkdirs()
                // DataStore observes this directory. Replacing an active preferences file by
                // truncating it exposes a transient empty payload that can be persisted again.
                val atomicFile = AtomicFile(target)
                var output: FileOutputStream? = null
                try {
                    val stream = atomicFile.startWrite()
                    output = stream
                    canonical.inputStream().use { input -> input.copyTo(stream) }
                    atomicFile.finishWrite(stream)
                } catch (error: Throwable) {
                    output?.let(atomicFile::failWrite)
                    throw error
                }
            }
        }
    }

    private fun ZipInputStream.readBytesSafely(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            if (out.size() + read > maxBytes) {
                throw IllegalArgumentException("Zip entry too large")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun requireSnapshotProcess(context: Context) {
        // The released settings screen invokes this manager in the main process. It is safe only
        // while that process still owns the same cross-process lease used by :repair.
        check(
            StorageProcessLock.isRepairProcess(context) ||
                (StorageProcessLock.isMainProcess(context) &&
                    StorageProcessLock.mainProcessOwnsStorage())
        ) {
            "Raw snapshots require the storage-owning main process or the repair process"
        }
    }
}
