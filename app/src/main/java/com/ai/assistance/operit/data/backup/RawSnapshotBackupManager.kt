package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.persistence.PreferenceRecoveryStorage
import com.ai.assistance.operit.data.persistence.RecoverablePreferenceDataStores
import com.ai.assistance.operit.data.persistence.RoomRecoveryStorage
import com.ai.assistance.operit.data.persistence.StorageProcessLock
import com.ai.assistance.operit.data.persistence.StorageProfileIdPolicy
import com.ai.assistance.operit.data.persistence.StorageQuarantineFiles
import com.ai.assistance.operit.data.persistence.StorageRecoveryCoordinator
import com.ai.assistance.operit.data.persistence.StorageRecoveryException
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
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
private const val RAW_SNAPSHOT_PAYLOAD_PREFIX = "payload/"
private const val RAW_SNAPSHOT_QUARANTINE_FORMAT_VERSION = 1
internal const val RAW_SNAPSHOT_TRANSACTION_MARKER_FORMAT_VERSION = 1
private const val RAW_SNAPSHOT_TRANSACTION_MARKER_FILE_NAME = "raw_restore_transaction.json"
private const val RAW_SNAPSHOT_TRANSACTION_MARKER_MAX_BYTES = 64L * 1024L

// Snapshots may include terminal images and external assets, so byte caps are multi-gigabyte while
// still bounding cache growth, expansion ratio, individual databases, and manifest allocation.
internal const val RAW_SNAPSHOT_MAX_ARCHIVE_BYTES = 8L * 1024L * 1024L * 1024L
internal const val RAW_SNAPSHOT_MAX_ENTRY_UNCOMPRESSED_BYTES = 8L * 1024L * 1024L * 1024L
internal const val RAW_SNAPSHOT_MAX_TOTAL_UNCOMPRESSED_BYTES = 16L * 1024L * 1024L * 1024L
internal const val RAW_SNAPSHOT_MAX_MANIFEST_BYTES = 64L * 1024L * 1024L
internal const val RAW_SNAPSHOT_MAX_PAYLOAD_ENTRY_COUNT = 200_000
internal const val RAW_SNAPSHOT_MAX_ENTRY_NAME_CHARS = 4_096
internal const val RAW_SNAPSHOT_STORAGE_RESERVE_BYTES = 64L * 1024L * 1024L

internal val RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES =
    listOf(
        "payload/files/",
        "payload/external_files/",
        "payload/shared_prefs/",
        "payload/datastore/",
        "payload/databases/"
    )

internal fun hasExactReleasedFormat1Includes(includes: List<String>): Boolean =
    includes == RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES

private val RAW_SNAPSHOT_SHA256_PATTERN = Regex("[0-9a-f]{64}")
private val RAW_SNAPSHOT_TRANSACTION_ID_PATTERN = Regex("[0-9a-f]{32}")

@Serializable
internal enum class RawSnapshotRestoreCategory(val quarantineDirectoryName: String) {
    FILES("files"),
    EXTERNAL_FILES("external_files"),
    SHARED_PREFS("shared_prefs"),
    DATASTORE("datastore"),
    DATABASES("databases")
}

internal val RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER =
    listOf(
        RawSnapshotRestoreCategory.FILES,
        RawSnapshotRestoreCategory.EXTERNAL_FILES,
        RawSnapshotRestoreCategory.SHARED_PREFS,
        RawSnapshotRestoreCategory.DATASTORE,
        RawSnapshotRestoreCategory.DATABASES
    )

@Serializable
internal data class RawSnapshotQuarantineFileState(
    val relativePath: String,
    val byteLength: Long,
    val sha256: String
)

@Serializable
internal data class RawSnapshotQuarantineCategoryState(
    val category: RawSnapshotRestoreCategory,
    val originallyExisted: Boolean,
    val directories: List<String>,
    val files: List<RawSnapshotQuarantineFileState>
)

@Serializable
internal data class RawSnapshotQuarantineManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val categories: List<RawSnapshotQuarantineCategoryState>
)

@Serializable
internal enum class RawSnapshotTransactionMarkerState {
    PREPARED,
    MUTATION_STARTED,
    COMMITTED
}

@Serializable
internal data class RawSnapshotTransactionMarker(
    val formatVersion: Int,
    val transactionId: String,
    val quarantineDirectoryBasename: String,
    val recoveryEpochArchiveBasename: String,
    val state: RawSnapshotTransactionMarkerState
)

internal enum class RawSnapshotTransactionRecoveryDecision {
    RESTORE_PREVIOUS_EPOCH,
    REMOVE_COMMITTED_MARKER
}

internal fun rawSnapshotQuarantineBasename(transactionId: String): String =
    "raw_restore_pre_state_$transactionId"

internal fun rawSnapshotRecoveryEpochArchiveBasename(transactionId: String): String =
    "recovery_epoch_$transactionId"

internal fun rawSnapshotFailedImportArchiveBasename(transactionId: String): String =
    "failed_import_recovery_epoch_$transactionId"

internal fun validateRawSnapshotTransactionMarker(marker: RawSnapshotTransactionMarker) {
    require(marker.formatVersion == RAW_SNAPSHOT_TRANSACTION_MARKER_FORMAT_VERSION) {
        "Raw snapshot transaction marker version is invalid"
    }
    require(RAW_SNAPSHOT_TRANSACTION_ID_PATTERN.matches(marker.transactionId)) {
        "Raw snapshot transaction ID is invalid"
    }
    require(
        marker.quarantineDirectoryBasename ==
            rawSnapshotQuarantineBasename(marker.transactionId)
    ) {
        "Raw snapshot transaction quarantine basename is invalid"
    }
    require(
        marker.recoveryEpochArchiveBasename ==
            rawSnapshotRecoveryEpochArchiveBasename(marker.transactionId)
    ) {
        "Raw snapshot transaction recovery archive basename is invalid"
    }
}

internal fun rawSnapshotTransactionRecoveryDecision(
    state: RawSnapshotTransactionMarkerState
): RawSnapshotTransactionRecoveryDecision =
    when (state) {
        RawSnapshotTransactionMarkerState.PREPARED,
        RawSnapshotTransactionMarkerState.MUTATION_STARTED ->
            RawSnapshotTransactionRecoveryDecision.RESTORE_PREVIOUS_EPOCH
        RawSnapshotTransactionMarkerState.COMMITTED ->
            RawSnapshotTransactionRecoveryDecision.REMOVE_COMMITTED_MARKER
    }

internal fun isValidRawSnapshotMarkerTransition(
    from: RawSnapshotTransactionMarkerState,
    to: RawSnapshotTransactionMarkerState
): Boolean =
    (from == RawSnapshotTransactionMarkerState.PREPARED &&
        to == RawSnapshotTransactionMarkerState.MUTATION_STARTED) ||
        (from == RawSnapshotTransactionMarkerState.MUTATION_STARTED &&
            to == RawSnapshotTransactionMarkerState.COMMITTED)

internal fun rawSnapshotRollbackCategoryOrder(): List<RawSnapshotRestoreCategory> =
    RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER.asReversed()

internal fun createRawSnapshotRestoreRollbackException(
    originalFailure: Throwable,
    rollbackFailures: List<Throwable>
): StorageRecoveryException {
    val message =
        if (rollbackFailures.isEmpty()) {
            "Raw snapshot restore failed; the pre-restore storage state was restored."
        } else {
            "Raw snapshot restore failed and rollback was incomplete " +
                "(${rollbackFailures.size} rollback failures)."
        }
    return StorageRecoveryException(message, originalFailure).also { recoveryFailure ->
        rollbackFailures.forEach { rollbackFailure ->
            recoveryFailure.addSuppressed(rollbackFailure)
        }
    }
}

internal fun validateRawSnapshotQuarantineManifest(manifest: RawSnapshotQuarantineManifest) {
    require(manifest.formatVersion == RAW_SNAPSHOT_QUARANTINE_FORMAT_VERSION) {
        "Raw snapshot quarantine manifest version is invalid"
    }
    require(manifest.createdAt > 0L) {
        "Raw snapshot quarantine creation time is invalid"
    }
    require(manifest.categories.map { state -> state.category } ==
        RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER
    ) {
        "Raw snapshot quarantine category order is invalid"
    }

    manifest.categories.forEach { state ->
        require(state.directories == state.directories.sorted()) {
            "Raw snapshot quarantine directories are not deterministically ordered"
        }
        require(state.files == state.files.sortedBy { file -> file.relativePath }) {
            "Raw snapshot quarantine files are not deterministically ordered"
        }
        require(state.directories.distinct().size == state.directories.size) {
            "Raw snapshot quarantine contains duplicate directories"
        }
        require(state.files.map { file -> file.relativePath }.distinct().size == state.files.size) {
            "Raw snapshot quarantine contains duplicate files"
        }
        if (!state.originallyExisted) {
            require(state.directories.isEmpty() && state.files.isEmpty()) {
                "An absent raw snapshot category cannot contain quarantine entries"
            }
            return@forEach
        }

        val directoryPaths = state.directories.toSet()
        val filePaths = state.files.mapTo(mutableSetOf()) { file -> file.relativePath }
        require(directoryPaths.intersect(filePaths).isEmpty()) {
            "Raw snapshot quarantine path is both a file and directory"
        }
        state.directories.forEach { relativePath ->
            requireValidRawSnapshotQuarantineRelativePath(relativePath)
            relativePath.parentRelativePath()?.let { parent ->
                require(parent in directoryPaths) {
                    "Raw snapshot quarantine directory parent is missing: $relativePath"
                }
            }
        }
        state.files.forEach { file ->
            requireValidRawSnapshotQuarantineRelativePath(file.relativePath)
            require(file.byteLength >= 0L) {
                "Raw snapshot quarantine file length is negative"
            }
            require(RAW_SNAPSHOT_SHA256_PATTERN.matches(file.sha256)) {
                "Raw snapshot quarantine file SHA-256 is invalid"
            }
            file.relativePath.parentRelativePath()?.let { parent ->
                require(parent in directoryPaths) {
                    "Raw snapshot quarantine file parent is missing: ${file.relativePath}"
                }
            }
        }
    }
}

private fun requireValidRawSnapshotQuarantineRelativePath(relativePath: String) {
    require(relativePath.isNotEmpty() && !relativePath.startsWith('/') &&
        !relativePath.endsWith('/')
    ) {
        "Raw snapshot quarantine relative path is invalid: $relativePath"
    }
    require('\\' !in relativePath && '\u0000' !in relativePath) {
        "Raw snapshot quarantine relative path contains an invalid character: $relativePath"
    }
    require(relativePath.split('/').none { segment ->
        segment.isEmpty() || segment == "." || segment == ".."
    }) {
        "Raw snapshot quarantine relative path is not normalized: $relativePath"
    }
}

private fun String.parentRelativePath(): String? =
    substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }

internal fun isSupportedSnapshotPackageName(packageName: String): Boolean =
    packageName == SNAPSHOT_PACKAGE_NAME_PREFIX ||
        packageName.startsWith("$SNAPSHOT_PACKAGE_NAME_PREFIX.")

@Serializable
data class RawSnapshotPayloadFile(
    val zipPath: String,
    val byteLength: Long,
    val sha256: String
)

internal fun addRawSnapshotBytesWithinLimit(
    currentBytes: Long,
    additionalBytes: Long,
    limitBytes: Long,
    description: String
): Long {
    require(currentBytes >= 0L && additionalBytes >= 0L && limitBytes >= 0L) {
        "$description byte accounting is invalid"
    }
    val updated =
        try {
            Math.addExact(currentBytes, additionalBytes)
        } catch (overflow: ArithmeticException) {
            throw IllegalArgumentException("$description byte count overflow", overflow)
        }
    require(updated <= limitBytes) {
        "$description exceeds the ${limitBytes}-byte limit"
    }
    return updated
}

internal enum class RawSnapshotPayloadEntryKind {
    DIRECTORY,
    REGULAR_FILE,
    NON_REGULAR
}

internal data class RawSnapshotObservedPayloadEntry(
    val zipPath: String,
    val kind: RawSnapshotPayloadEntryKind,
    val byteLength: Long,
    val sha256: String
)

internal fun validateRawSnapshotPayloadInventory(
    manifestDirectories: List<String>,
    manifestFiles: List<RawSnapshotPayloadFile>,
    observedEntries: List<RawSnapshotObservedPayloadEntry>
) {
    require(manifestDirectories == RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES) {
        "Raw snapshot manifest has an invalid payload directory list"
    }
    require(manifestFiles == manifestFiles.sortedBy { file -> file.zipPath }) {
        "Raw snapshot manifest file inventory is not deterministically ordered"
    }

    val manifestFilesByPath = LinkedHashMap<String, RawSnapshotPayloadFile>()
    manifestFiles.forEach { file ->
        requireValidRawSnapshotPayloadZipPath(file.zipPath, isDirectory = false)
        requireValidRawSnapshotPayloadMetadata(file.byteLength, file.sha256)
        require(manifestFilesByPath.put(file.zipPath, file) == null) {
            "Raw snapshot manifest contains duplicate file: ${file.zipPath}"
        }
    }

    val observedFilesByPath = LinkedHashMap<String, RawSnapshotObservedPayloadEntry>()
    val observedDirectories = mutableSetOf<String>()
    val observedPaths = mutableSetOf<String>()
    observedEntries.forEach { entry ->
        require(observedPaths.add(entry.zipPath)) {
            "Raw snapshot archive contains duplicate payload path: ${entry.zipPath}"
        }
        when (entry.kind) {
            RawSnapshotPayloadEntryKind.DIRECTORY -> {
                requireValidRawSnapshotPayloadZipPath(entry.zipPath, isDirectory = true)
                require(entry.zipPath in RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES) {
                    "Raw snapshot archive contains an unlisted payload directory: ${entry.zipPath}"
                }
                observedDirectories += entry.zipPath
            }
            RawSnapshotPayloadEntryKind.REGULAR_FILE -> {
                requireValidRawSnapshotPayloadZipPath(entry.zipPath, isDirectory = false)
                requireValidRawSnapshotPayloadMetadata(entry.byteLength, entry.sha256)
                observedFilesByPath[entry.zipPath] = entry
            }
            RawSnapshotPayloadEntryKind.NON_REGULAR -> {
                throw IllegalArgumentException(
                    "Raw snapshot archive contains a non-regular payload path: ${entry.zipPath}"
                )
            }
        }
    }

    RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES.forEach { directory ->
        require(directory in observedDirectories) {
            "Raw snapshot archive is missing required payload directory entry: $directory"
        }
    }

    val missingFiles = manifestFilesByPath.keys - observedFilesByPath.keys
    require(missingFiles.isEmpty()) {
        "Raw snapshot archive is missing listed payload file: ${missingFiles.first()}"
    }
    val unlistedFiles = observedFilesByPath.keys - manifestFilesByPath.keys
    require(unlistedFiles.isEmpty()) {
        "Raw snapshot archive contains unlisted payload file: ${unlistedFiles.first()}"
    }

    manifestFilesByPath.forEach { (zipPath, expected) ->
        val observed = checkNotNull(observedFilesByPath[zipPath])
        require(observed.byteLength == expected.byteLength) {
            "Raw snapshot payload size mismatch: $zipPath"
        }
        require(observed.sha256 == expected.sha256) {
            "Raw snapshot payload SHA-256 mismatch: $zipPath"
        }
    }
}

internal fun requireRawSnapshotReplacementSourceDirectory(source: File) {
    require(Files.isDirectory(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        "Raw snapshot replacement source is not a directory: ${source.absolutePath}"
    }
    requireNotNull(source.listFiles()) {
        "Raw snapshot replacement source cannot be enumerated: ${source.absolutePath}"
    }
}

private fun requireValidRawSnapshotPayloadMetadata(byteLength: Long, sha256: String) {
    require(byteLength >= 0L) { "Raw snapshot payload length is negative" }
    require(RAW_SNAPSHOT_SHA256_PATTERN.matches(sha256)) {
        "Raw snapshot payload SHA-256 is invalid"
    }
}

private fun requireValidRawSnapshotPayloadZipPath(zipPath: String, isDirectory: Boolean) {
    require(zipPath.startsWith(RAW_SNAPSHOT_PAYLOAD_PREFIX)) {
        "Raw snapshot payload path is outside payload/: $zipPath"
    }
    require('\\' !in zipPath && '\u0000' !in zipPath) {
        "Raw snapshot payload path contains an invalid character: $zipPath"
    }
    require(zipPath.endsWith('/') == isDirectory) {
        "Raw snapshot payload path has an invalid file type suffix: $zipPath"
    }
    val pathWithoutDirectorySuffix = if (isDirectory) zipPath.dropLast(1) else zipPath
    val segments = pathWithoutDirectorySuffix.split('/')
    val minimumSegmentCount = if (isDirectory) 2 else 3
    require(segments.size >= minimumSegmentCount && segments.none { segment ->
        segment.isEmpty() || segment == "." || segment == ".."
    }) {
        "Raw snapshot payload path is not normalized: $zipPath"
    }
    require(RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES.any { category ->
        zipPath.startsWith(category)
    }) {
        "Raw snapshot payload path is outside a required category: $zipPath"
    }
}

private fun ByteArray.toLowerHexString(): String {
    val hexDigits = "0123456789abcdef"
    val result = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        result[index * 2] = hexDigits[value ushr 4]
        result[index * 2 + 1] = hexDigits[value and 0x0f]
    }
    return String(result)
}

object RawSnapshotBackupManager {

    private const val TAG = "RawSnapshotBackup"
    private const val FORMAT_VERSION = 2

    private const val ZIP_PREFIX = "operit_raw_snapshot_"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_PAYLOAD_PREFIX = RAW_SNAPSHOT_PAYLOAD_PREFIX

    private const val ENTRY_FILES = "payload/files/"
    private const val ENTRY_EXTERNAL_FILES = "payload/external_files/"
    private const val ENTRY_SHARED_PREFS = "payload/shared_prefs/"
    private const val ENTRY_DATASTORE = "payload/datastore/"
    private const val ENTRY_DATABASES = "payload/databases/"

    private val RELEASED_FORMAT_1_INCLUDES = RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES

    private val terminalTopLevelDirNames = setOf("usr", "tmp", "bin")
    private val roomDatabaseFileNames =
        setOf(
            RoomRecoveryStorage.DATABASE_NAME,
            "${RoomRecoveryStorage.DATABASE_NAME}-wal",
            "${RoomRecoveryStorage.DATABASE_NAME}-shm",
            "${RoomRecoveryStorage.DATABASE_NAME}-journal"
        )

    private val mutex = Mutex()
    private val transactionRecoveryMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var activeRestoreTransactionId: String? = null

    // This type was public in the released manager; keep source visibility while format 2 extends
    // its schema with a public payload inventory element type.
    @Serializable
    data class Manifest(
        val formatVersion: Int,
        val packageName: String,
        val createdAt: Long,
        val payloadDirectories: List<String>,
        val payloadFiles: List<RawSnapshotPayloadFile>,
        val includeTerminalData: Boolean
    )

    @Serializable
    private data class ManifestVersionEnvelope(
        val formatVersion: Int
    )

    @Serializable
    private data class ReleasedFormat1Manifest(
        val formatVersion: Int,
        val packageName: String,
        val createdAt: Long,
        val includes: List<String>,
        val includeTerminalData: Boolean
    )

    private sealed interface ParsedSnapshotManifest {
        val formatVersion: Int
        val packageName: String
        val createdAt: Long
        val includeTerminalData: Boolean

        data class Version1(val manifest: ReleasedFormat1Manifest) : ParsedSnapshotManifest {
            override val formatVersion: Int = manifest.formatVersion
            override val packageName: String = manifest.packageName
            override val createdAt: Long = manifest.createdAt
            override val includeTerminalData: Boolean = manifest.includeTerminalData
        }

        data class Version2(val manifest: Manifest) : ParsedSnapshotManifest {
            override val formatVersion: Int = manifest.formatVersion
            override val packageName: String = manifest.packageName
            override val createdAt: Long = manifest.createdAt
            override val includeTerminalData: Boolean = manifest.includeTerminalData
        }
    }

    private data class CentralDirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val uncompressedSize: Long,
        val compressedSize: Long,
        val crc32: Long,
        val method: Int
    )

    private data class ArchiveInspection(
        val manifest: ParsedSnapshotManifest,
        val entries: List<CentralDirectoryEntry>,
        val manifestBytes: Long,
        val declaredUncompressedBytes: Long
    ) {
        val payloadFilePaths: Set<String>
            get() =
                entries
                    .filter { entry ->
                        !entry.isDirectory && entry.name.startsWith(ENTRY_PAYLOAD_PREFIX)
                    }
                    .mapTo(mutableSetOf()) { entry -> entry.name }
    }

    data class SnapshotOptions(
        val includeTerminalData: Boolean = false
    )

    private data class StagedZipFile(
        val relativePath: String,
        val file: File
    )

    private data class ValidatedPayloadDirectories(
        val filesDir: File,
        val externalFilesDir: File,
        val sharedPrefsDir: File,
        val datastoreDir: File,
        val databasesDir: File
    ) {
        val allDirectories: List<File>
            get() =
                listOf(
                    filesDir,
                    externalFilesDir,
                    sharedPrefsDir,
                    datastoreDir,
                    databasesDir
                )
    }

    private data class ReplacementCategory(
        val category: RawSnapshotRestoreCategory,
        val sourceDirectory: File,
        val destinationDirectory: File,
        val preservedTopLevelDirNames: Set<String> = emptySet()
    )

    private data class PreRestoreQuarantine(
        val rootDirectory: File,
        val manifest: RawSnapshotQuarantineManifest
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
        ignoreUnknownKeys = false
        isLenient = false
    }
    private val manifestEnvelopeJson = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    internal suspend fun recoverInterruptedRawRestore(context: Context) =
        withContext(Dispatchers.IO) {
            requireSnapshotProcess(context)
            val initialMarker = readRawSnapshotTransactionMarker(context) ?: return@withContext
            if (activeRestoreTransactionId == initialMarker.transactionId) return@withContext

            transactionRecoveryMutex.withLock {
                val marker = readRawSnapshotTransactionMarker(context) ?: return@withLock
                if (activeRestoreTransactionId == marker.transactionId) return@withLock
                when (rawSnapshotTransactionRecoveryDecision(marker.state)) {
                    RawSnapshotTransactionRecoveryDecision.REMOVE_COMMITTED_MARKER -> {
                        requireCommittedQuarantineReference(context, marker)
                        StorageRecoveryCoordinator.validateRecoveryEpochArchive(
                            context.applicationContext,
                            marker.transactionId,
                            marker.recoveryEpochArchiveBasename
                        )
                        deleteRawSnapshotTransactionMarker(context, marker)
                        AppLogger.i(TAG, "Cleared committed raw snapshot transaction marker")
                    }
                    RawSnapshotTransactionRecoveryDecision.RESTORE_PREVIOUS_EPOCH -> {
                        restoreInterruptedRawSnapshotTransaction(context, marker)
                    }
                }
            }
        }

    private fun rawSnapshotTransactionMarkerFile(context: Context): File =
        File(
            File(context.applicationContext.noBackupFilesDir, "storage-recovery"),
            RAW_SNAPSHOT_TRANSACTION_MARKER_FILE_NAME
        )

    private fun readRawSnapshotTransactionMarker(context: Context): RawSnapshotTransactionMarker? {
        val markerFile = rawSnapshotTransactionMarkerFile(context)
        val atomicFile = AtomicFile(markerFile)
        if (!markerFile.exists()) return null
        val bytes =
            atomicFile.openRead().use { input ->
                input.readBytesSafely(
                    maxBytes = RAW_SNAPSHOT_TRANSACTION_MARKER_MAX_BYTES,
                    description = "Raw snapshot transaction marker"
                )
            }
        return try {
            val marker =
                json.decodeFromString<RawSnapshotTransactionMarker>(
                    bytes.toString(Charsets.UTF_8)
                )
            validateRawSnapshotTransactionMarker(marker)
            marker
        } catch (failure: Exception) {
            throw StorageRecoveryException(
                "Raw snapshot transaction marker is invalid.",
                failure
            )
        }
    }

    private fun writeInitialRawSnapshotTransactionMarker(
        context: Context,
        marker: RawSnapshotTransactionMarker
    ) {
        validateRawSnapshotTransactionMarker(marker)
        require(marker.state == RawSnapshotTransactionMarkerState.PREPARED) {
            "Initial raw snapshot transaction marker must be PREPARED"
        }
        check(readRawSnapshotTransactionMarker(context) == null) {
            "A raw snapshot transaction marker already exists"
        }
        writeRawSnapshotTransactionMarker(context, marker)
        check(readRawSnapshotTransactionMarker(context) == marker) {
            "Raw snapshot PREPARED marker durability verification failed"
        }
    }

    private fun transitionRawSnapshotTransactionMarker(
        context: Context,
        expected: RawSnapshotTransactionMarker,
        nextState: RawSnapshotTransactionMarkerState
    ): RawSnapshotTransactionMarker {
        val current = checkNotNull(readRawSnapshotTransactionMarker(context)) {
            "Raw snapshot transaction marker disappeared"
        }
        check(current == expected) { "Raw snapshot transaction marker changed unexpectedly" }
        require(isValidRawSnapshotMarkerTransition(current.state, nextState)) {
            "Invalid raw snapshot marker transition: ${current.state} -> $nextState"
        }
        val next = current.copy(state = nextState)
        writeRawSnapshotTransactionMarker(context, next)
        check(readRawSnapshotTransactionMarker(context) == next) {
            "Raw snapshot $nextState marker durability verification failed"
        }
        return next
    }

    private fun writeRawSnapshotTransactionMarker(
        context: Context,
        marker: RawSnapshotTransactionMarker
    ) {
        validateRawSnapshotTransactionMarker(marker)
        val markerFile = rawSnapshotTransactionMarkerFile(context)
        val root = requireNotNull(markerFile.parentFile)
        check(
            root.mkdirs() || Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            "Failed to create raw snapshot transaction marker directory"
        }
        check(markerFile.canonicalFile.parentFile == root.canonicalFile) {
            "Raw snapshot transaction marker resolves outside storage-recovery"
        }

        val atomicFile = AtomicFile(markerFile)
        var output: FileOutputStream? = null
        try {
            val stream = atomicFile.startWrite()
            output = stream
            val bytes = json.encodeToString(marker).toByteArray(Charsets.UTF_8)
            check(bytes.size.toLong() <= RAW_SNAPSHOT_TRANSACTION_MARKER_MAX_BYTES) {
                "Raw snapshot transaction marker exceeds its byte limit"
            }
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (failure: Throwable) {
            output?.let(atomicFile::failWrite)
            throw failure
        }
    }

    private fun deleteRawSnapshotTransactionMarker(
        context: Context,
        expected: RawSnapshotTransactionMarker
    ) {
        check(readRawSnapshotTransactionMarker(context) == expected) {
            "Raw snapshot transaction marker changed before deletion"
        }
        val atomicFile = AtomicFile(rawSnapshotTransactionMarkerFile(context))
        atomicFile.delete()
        check(!rawSnapshotTransactionMarkerFile(context).exists()) {
            "Failed to delete raw snapshot transaction marker"
        }
    }

    private fun requireCommittedQuarantineReference(
        context: Context,
        marker: RawSnapshotTransactionMarker
    ) {
        validateRawSnapshotTransactionMarker(marker)
        val quarantineParent =
            File(context.applicationContext.noBackupFilesDir, "storage-recovery/quarantine")
                .canonicalFile
        val quarantine = File(quarantineParent, marker.quarantineDirectoryBasename).canonicalFile
        check(
            quarantine.parentFile == quarantineParent &&
                quarantine.name == marker.quarantineDirectoryBasename &&
                Files.isDirectory(quarantine.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            "Committed raw snapshot quarantine reference is invalid"
        }
    }

    private suspend fun restoreInterruptedRawSnapshotTransaction(
        context: Context,
        marker: RawSnapshotTransactionMarker
    ) {
        val closeFailures = closeOwnersForRawSnapshotTransactionRecovery()
        if (closeFailures.isNotEmpty()) {
            throw storageRecoveryFailure(
                "Raw snapshot transaction recovery could not close all storage owners.",
                closeFailures
            )
        }

        val quarantine = loadPreRestoreQuarantine(context, marker)
        val replacements = replacementCategoriesForRecovery(context, quarantine)
        val restoreFailures = mutableListOf<Throwable>()
        val replacementsByCategory = replacements.associateBy { replacement -> replacement.category }
        val statesByCategory = quarantine.manifest.categories.associateBy { state -> state.category }
        rawSnapshotRollbackCategoryOrder().forEach { category ->
            try {
                restoreCategoryFromQuarantine(
                    replacement = checkNotNull(replacementsByCategory[category]),
                    quarantineRoot = quarantine.rootDirectory,
                    expectedState = checkNotNull(statesByCategory[category])
                )
            } catch (failure: Throwable) {
                val evidence =
                    IllegalStateException(
                        "Interrupted raw snapshot restore failed for category $category",
                        failure
                    )
                restoreFailures += evidence
                AppLogger.e(
                    TAG,
                    "Interrupted raw snapshot restore failed for category $category",
                    failure
                )
            }
        }
        if (restoreFailures.isNotEmpty()) {
            throw storageRecoveryFailure(
                "Raw snapshot transaction recovery could not restore all live categories.",
                restoreFailures
            )
        }

        StorageRecoveryCoordinator.restoreRecoveryEpochAfterRawRestoreFailure(
            context = context.applicationContext,
            transactionId = marker.transactionId,
            archiveBasename = marker.recoveryEpochArchiveBasename,
            mutationStarted = marker.state == RawSnapshotTransactionMarkerState.MUTATION_STARTED
        )
        RoomRecoveryStorage.invalidatePreparedState()
        ObjectBoxManager.invalidatePreparedState()
        verifyLiveCategoriesMatchQuarantine(replacements, quarantine.manifest)
        deleteRawSnapshotTransactionMarker(context, marker)
        try {
            StorageRecoveryCoordinator.completeRestoredRawRestoreRecoveryEpoch(
                context.applicationContext,
                marker.transactionId,
                marker.recoveryEpochArchiveBasename
            )
        } catch (cleanupFailure: Throwable) {
            AppLogger.e(TAG, "Failed to remove restored recovery epoch metadata", cleanupFailure)
        }
        AppLogger.i(TAG, "Recovered interrupted raw snapshot transaction ${marker.transactionId}")
    }

    private suspend fun closeOwnersForRawSnapshotTransactionRecovery(): List<Throwable> {
        val failures = mutableListOf<Throwable>()

        fun record(step: String, failure: Throwable) {
            val evidence = IllegalStateException("Failed to $step", failure)
            failures += evidence
            AppLogger.e(TAG, "Failed to $step", failure)
        }

        try {
            RecoverablePreferenceDataStores.closeAllAndAwait()
        } catch (failure: Throwable) {
            record("close DataStore owners for raw restore recovery", failure)
        }
        try {
            AppDatabase.closeDatabase()
        } catch (failure: Throwable) {
            record("close Room owner for raw restore recovery", failure)
        }
        try {
            ObjectBoxManager.closeAllForReplacement()
        } catch (failure: Throwable) {
            record("close ObjectBox owners for raw restore recovery", failure)
        }
        return failures
    }

    private fun storageRecoveryFailure(
        message: String,
        failures: List<Throwable>
    ): StorageRecoveryException {
        require(failures.isNotEmpty()) { "Storage recovery failure evidence is empty" }
        return StorageRecoveryException(message, failures.first()).also { error ->
            failures.drop(1).forEach { failure -> error.addSuppressed(failure) }
        }
    }

    private fun loadPreRestoreQuarantine(
        context: Context,
        marker: RawSnapshotTransactionMarker
    ): PreRestoreQuarantine {
        validateRawSnapshotTransactionMarker(marker)
        val parent =
            File(context.applicationContext.noBackupFilesDir, "storage-recovery/quarantine")
                .canonicalFile
        val root = File(parent, marker.quarantineDirectoryBasename).canonicalFile
        check(
            root.parentFile == parent &&
                root.name == marker.quarantineDirectoryBasename &&
                Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            "Raw snapshot transaction quarantine is missing or invalid"
        }
        val quarantine = PreRestoreQuarantine(root, readQuarantineManifest(root))
        verifyPreRestoreQuarantine(quarantine)
        return quarantine
    }

    private fun replacementCategoriesForRecovery(
        context: Context,
        quarantine: PreRestoreQuarantine
    ): List<ReplacementCategory> {
        val appContext = context.applicationContext
        val externalFilesDir =
            requireNotNull(appContext.getExternalFilesDir(null)) {
                "External files dir is unavailable during raw restore recovery"
            }
        val destinations =
            mapOf(
                RawSnapshotRestoreCategory.FILES to appContext.filesDir,
                RawSnapshotRestoreCategory.EXTERNAL_FILES to externalFilesDir,
                RawSnapshotRestoreCategory.SHARED_PREFS to File(appContext.dataDir, "shared_prefs"),
                RawSnapshotRestoreCategory.DATASTORE to File(appContext.dataDir, "datastore"),
                RawSnapshotRestoreCategory.DATABASES to File(appContext.dataDir, "databases")
            )
        return RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER.map { category ->
            ReplacementCategory(
                category = category,
                sourceDirectory =
                    File(quarantine.rootDirectory, category.quarantineDirectoryName),
                destinationDirectory = checkNotNull(destinations[category])
            )
        }
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
                val manifestCreatedAt = System.currentTimeMillis()

                ZipOutputStream(BufferedOutputStream(FileOutputStream(tmpFile))).use { zos ->
                    val payloadFiles = mutableListOf<RawSnapshotPayloadFile>()
                    val writtenEntryNames = mutableSetOf<String>()

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
                                    writtenEntryNames = writtenEntryNames,
                                    payloadFiles = payloadFiles,
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
                                writtenEntryNames = writtenEntryNames,
                                payloadFiles = payloadFiles,
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
                                writtenEntryNames = writtenEntryNames,
                                payloadFiles = payloadFiles,
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
                            addDirToZip(
                                zos = zos,
                                dir = sharedPrefsDir,
                                entryPrefix = ENTRY_SHARED_PREFS,
                                writtenEntryNames = writtenEntryNames,
                                payloadFiles = payloadFiles
                            )
                        }
                    AppLogger.i(TAG, "export add shared_prefs done in ${sharedPrefsMs}ms")

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATASTORE))
                    }
                    val datastoreMs =
                        measureTimeMillis {
                            addDirToZip(
                                zos = zos,
                                dir = datastoreDir,
                                entryPrefix = ENTRY_DATASTORE,
                                writtenEntryNames = writtenEntryNames,
                                payloadFiles = payloadFiles
                            )
                        }
                    AppLogger.i(TAG, "export add datastore done in ${datastoreMs}ms")

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATABASES))
                    }
                    val databasesMs =
                        measureTimeMillis {
                            addDirToZip(
                                zos = zos,
                                dir = databasesDir,
                                entryPrefix = ENTRY_DATABASES,
                                excludedTopLevelDirNames = roomDatabaseFileNames,
                                writtenEntryNames = writtenEntryNames,
                                payloadFiles = payloadFiles
                            )
                            addStagedSnapshotToZip(
                                zos = zos,
                                entryPrefix = ENTRY_DATABASES,
                                files = stagedRoomFiles,
                                processedBefore = 0,
                                totalFiles = 0,
                                writtenEntryNames = writtenEntryNames,
                                payloadFiles = payloadFiles,
                                onPercentChanged = null
                            )
                        }
                    AppLogger.i(TAG, "export add databases done in ${databasesMs}ms")

                    val manifest =
                        Manifest(
                            formatVersion = FORMAT_VERSION,
                            packageName = context.packageName,
                            createdAt = manifestCreatedAt,
                            payloadDirectories = RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES,
                            payloadFiles = payloadFiles.sortedBy { file -> file.zipPath },
                            includeTerminalData = options.includeTerminalData
                        )
                    check(writtenEntryNames.add(ENTRY_MANIFEST)) {
                        "Duplicate raw snapshot manifest entry"
                    }
                    zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
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
            val lease = StorageProcessLock.acquireOperationLease(context, "raw-snapshot-restore")
            val restoreId = UUID.randomUUID().toString()
            val cacheZip = File(context.cacheDir, "raw_snapshot_restore_$restoreId.zip")
            val workDir = File(context.cacheDir, "raw_snapshot_restore_work_$restoreId")
            var storageOwnersClosed = false
            var replacementLease: StorageReplacementGate.ReplacementLease? = null
            var restoreFailure: Throwable? = null
            var cacheCreated = false
            var workDirectoryCreated = false

            try {
                val pendingRecoveryLease = StorageReplacementGate.acquire()
                replacementLease = pendingRecoveryLease
                pendingRecoveryLease.withAccess {
                    recoverInterruptedRawRestore(context.applicationContext)
                }
                pendingRecoveryLease.close()
                replacementLease = null

                cacheCreated = cacheZip.createNewFile()
                check(cacheCreated) {
                    "Failed to create unique raw snapshot restore cache file"
                }
                workDirectoryCreated = workDir.mkdir()
                check(workDirectoryCreated) {
                    "Failed to create unique raw snapshot restore work directory"
                }
                AppLogger.i(TAG, "restore start uri=$uri")
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.PREPARING) }
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.READING_ZIP) }
                val archiveBytes =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        copyArchiveToCache(input, cacheZip)
                    } ?: throw IllegalStateException("Failed to open uri")

                AppLogger.i(TAG, "restore cached zip: ${cacheZip.absolutePath} ($archiveBytes bytes)")
                val archiveInspection = inspectBackupZip(cacheZip)
                val snapshotManifest = archiveInspection.manifest
                val requiredExtractionSpace =
                    addRawSnapshotBytesWithinLimit(
                        currentBytes = archiveInspection.declaredUncompressedBytes,
                        additionalBytes = RAW_SNAPSHOT_STORAGE_RESERVE_BYTES,
                        limitBytes = Long.MAX_VALUE,
                        description = "Raw snapshot extraction space"
                    )
                require(workDir.usableSpace >= requiredExtractionSpace) {
                    "Raw snapshot requires $requiredExtractionSpace bytes of extraction space, " +
                        "but only ${workDir.usableSpace} bytes are available"
                }

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.EXTRACTING) }
                extractZipToWorkDir(cacheZip, workDir, archiveInspection)

                val payloadDir = File(workDir, "payload")
                val expectedPayloadFiles = archiveInspection.payloadFilePaths
                val validatedPayload = validatePayloadShape(expectedPayloadFiles, payloadDir)
                validateRoomPayload(context, validatedPayload.databasesDir, workDir)
                validateObjectBoxPayloads(context, validatedPayload.filesDir, workDir)

                // Database validators run against isolated copies. Rechecking the source tree here
                // guarantees that validation introduced no file before live owners are closed.
                validateExtractedPayloadTree(expectedPayloadFiles, payloadDir)
                validatedPayload.allDirectories.forEach { source ->
                    requireRawSnapshotReplacementSourceDirectory(source)
                }

                val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()

                val preserveTerminal = !snapshotManifest.includeTerminalData
                val preservedTerminalNames = if (preserveTerminal) terminalTopLevelDirNames else emptySet()
                val preservedAlwaysExcludedNames = alwaysExcluded.filterNot { dirName ->
                    File(validatedPayload.filesDir, dirName).exists()
                }.toSet()
                val preservedNames = preservedTerminalNames + preservedAlwaysExcludedNames
                val externalFilesDir =
                    requireNotNull(context.getExternalFilesDir(null)) {
                        "External files dir is unavailable"
                    }
                val replacementCategories =
                    listOf(
                        ReplacementCategory(
                            category = RawSnapshotRestoreCategory.FILES,
                            sourceDirectory = validatedPayload.filesDir,
                            destinationDirectory = context.filesDir,
                            preservedTopLevelDirNames = preservedNames
                        ),
                        ReplacementCategory(
                            category = RawSnapshotRestoreCategory.EXTERNAL_FILES,
                            sourceDirectory = validatedPayload.externalFilesDir,
                            destinationDirectory = externalFilesDir
                        ),
                        ReplacementCategory(
                            category = RawSnapshotRestoreCategory.SHARED_PREFS,
                            sourceDirectory = validatedPayload.sharedPrefsDir,
                            destinationDirectory = File(context.dataDir, "shared_prefs")
                        ),
                        ReplacementCategory(
                            category = RawSnapshotRestoreCategory.DATASTORE,
                            sourceDirectory = validatedPayload.datastoreDir,
                            destinationDirectory = File(context.dataDir, "datastore")
                        ),
                        ReplacementCategory(
                            category = RawSnapshotRestoreCategory.DATABASES,
                            sourceDirectory = validatedPayload.databasesDir,
                            destinationDirectory = File(context.dataDir, "databases")
                        )
                    )
                check(replacementCategories.map { category -> category.category } ==
                    RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER
                ) {
                    "Raw snapshot replacement category order is invalid"
                }

                AppLogger.i(
                    TAG,
                    "restore manifest ok (formatVersion=${snapshotManifest.formatVersion}, " +
                        "includeTerminalData=${snapshotManifest.includeTerminalData})"
                )

                AppLogger.i(TAG, "restore replace dirs (preserveTerminalTopLevel=${preservedNames.isNotEmpty()})")

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
                    val transactionId = UUID.randomUUID().toString().replace("-", "")
                    val quarantineBasename = rawSnapshotQuarantineBasename(transactionId)
                    val recoveryEpochBasename =
                        rawSnapshotRecoveryEpochArchiveBasename(transactionId)
                    var marker: RawSnapshotTransactionMarker? = null
                    var transactionCompleted = false
                    try {
                        val preRestoreQuarantine =
                            try {
                                createPreRestoreQuarantine(
                                    context,
                                    replacementCategories,
                                    transactionId
                                )
                            } catch (quarantineFailure: Throwable) {
                                throw StorageRecoveryException(
                                    "Failed to preserve live storage before raw snapshot replacement.",
                                    quarantineFailure
                                )
                            }
                        check(preRestoreQuarantine.rootDirectory.name == quarantineBasename) {
                            "Raw snapshot quarantine does not match its transaction ID"
                        }
                        verifyLiveCategoriesMatchQuarantine(
                            replacementCategories,
                            preRestoreQuarantine.manifest
                        )

                        marker =
                            RawSnapshotTransactionMarker(
                                formatVersion = RAW_SNAPSHOT_TRANSACTION_MARKER_FORMAT_VERSION,
                                transactionId = transactionId,
                                quarantineDirectoryBasename = quarantineBasename,
                                recoveryEpochArchiveBasename = recoveryEpochBasename,
                                state = RawSnapshotTransactionMarkerState.PREPARED
                            )
                        activeRestoreTransactionId = transactionId
                        writeInitialRawSnapshotTransactionMarker(context, checkNotNull(marker))

                        StorageRecoveryCoordinator.archiveSnapshotsBeforeRawRestoreRecovery(
                            context = context.applicationContext,
                            transactionId = transactionId,
                            archiveBasename = recoveryEpochBasename
                        )
                        marker =
                            transitionRawSnapshotTransactionMarker(
                                context = context,
                                expected = checkNotNull(marker),
                                nextState = RawSnapshotTransactionMarkerState.MUTATION_STARTED
                            )

                        replacementCategories.forEach { replacement ->
                            withContext(Dispatchers.Main) {
                                onProgress?.invoke(replacement.category.restoreProgress())
                            }
                            replaceDirContents(
                                replacement.sourceDirectory,
                                replacement.destinationDirectory,
                                replacement.preservedTopLevelDirNames
                            )
                        }

                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(RestoreProgress.FINALIZING)
                        }
                        RoomRecoveryStorage.invalidatePreparedState()
                        ObjectBoxManager.invalidatePreparedState()
                        StorageRecoveryCoordinator.recoverPreferences(context.applicationContext)
                        RecoverablePreferenceDataStores.closeAllAndAwait()
                        AppDatabase.closeDatabase()
                        ObjectBoxManager.closeAllForReplacement()
                        marker =
                            transitionRawSnapshotTransactionMarker(
                                context = context,
                                expected = checkNotNull(marker),
                                nextState = RawSnapshotTransactionMarkerState.COMMITTED
                            )
                        PreferenceRecoveryStorage.recordStorageEvent(
                            context.applicationContext,
                            "raw_snapshot",
                            "pre_restore_quarantine",
                            "preserved"
                        )
                        deleteRawSnapshotTransactionMarker(context, checkNotNull(marker))
                        transactionCompleted = true
                    } catch (originalFailure: Throwable) {
                        activeRestoreTransactionId = null
                        val persistedMarker =
                            try {
                                readRawSnapshotTransactionMarker(context)
                            } catch (markerFailure: Throwable) {
                                throw createRawSnapshotRestoreRollbackException(
                                    originalFailure,
                                    listOf(markerFailure)
                                )
                            }
                        if (persistedMarker == null) {
                            throw originalFailure
                        }
                        try {
                            withContext(NonCancellable + Dispatchers.IO) {
                                recoverInterruptedRawRestore(context.applicationContext)
                            }
                        } catch (recoveryFailure: Throwable) {
                            throw createRawSnapshotRestoreRollbackException(
                                originalFailure,
                                listOf(recoveryFailure)
                            )
                        }
                        if (persistedMarker.transactionId == transactionId &&
                            persistedMarker.state == RawSnapshotTransactionMarkerState.COMMITTED
                        ) {
                            transactionCompleted = true
                        } else {
                            throw createRawSnapshotRestoreRollbackException(
                                originalFailure,
                                emptyList()
                            )
                        }
                    } finally {
                        if (activeRestoreTransactionId == transactionId) {
                            activeRestoreTransactionId = null
                        }
                    }
                    check(transactionCompleted) { "Raw snapshot transaction did not complete" }
                    AppLogger.i(
                        TAG,
                        "restore done: ${snapshotManifest.packageName}; pre-restore quarantine=" +
                            quarantineBasename
                    )
                }
            } catch (failure: Throwable) {
                restoreFailure = failure
                AppLogger.e(TAG, "restore failed", failure)
                throw failure
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    var cleanupFailure: Throwable? = null

                    fun recordCleanupFailure(step: String, failure: Throwable) {
                        val evidence =
                            IllegalStateException("Raw snapshot restore cleanup failed: $step", failure)
                        AppLogger.e(TAG, "Raw snapshot restore cleanup failed: $step", failure)
                        val firstFailure = cleanupFailure
                        if (firstFailure == null) {
                            cleanupFailure = evidence
                        } else {
                            firstFailure.addSuppressed(evidence)
                        }
                    }

                    if (storageOwnersClosed) {
                        try {
                            RecoverablePreferenceDataStores.closeAllAndAwait()
                        } catch (failure: Throwable) {
                            recordCleanupFailure("close preference owners", failure)
                        }
                        try {
                            AppDatabase.closeDatabase()
                        } catch (failure: Throwable) {
                            recordCleanupFailure("close Room owner", failure)
                        }
                        try {
                            ObjectBoxManager.closeAll()
                        } catch (failure: Throwable) {
                            recordCleanupFailure("close ObjectBox owners", failure)
                        }
                    }
                    if (workDirectoryCreated) {
                        try {
                            deletePathStrict(workDir)
                        } catch (failure: Throwable) {
                            recordCleanupFailure("delete unique restore work directory", failure)
                        }
                    }
                    if (cacheCreated) {
                        try {
                            deletePathStrict(cacheZip)
                        } catch (failure: Throwable) {
                            recordCleanupFailure("delete restore cache archive", failure)
                        }
                    }
                    val transactionResolved =
                        try {
                            readRawSnapshotTransactionMarker(context) == null
                        } catch (failure: Throwable) {
                            recordCleanupFailure("read raw restore transaction marker", failure)
                            false
                        }
                    if (transactionResolved) {
                        try {
                            replacementLease?.close()
                        } catch (failure: Throwable) {
                            recordCleanupFailure("release replacement gate", failure)
                        }
                    } else if (replacementLease != null) {
                        AppLogger.e(
                            TAG,
                            "Raw snapshot transaction remains unresolved; replacement gate retained"
                        )
                    }
                    try {
                        lease.close()
                    } catch (failure: Throwable) {
                        recordCleanupFailure("release process lease", failure)
                    }

                    cleanupFailure?.let { evidence ->
                        val primaryFailure = restoreFailure
                        if (primaryFailure != null) {
                            primaryFailure.addSuppressed(evidence)
                        } else {
                            throw StorageRecoveryException(
                                "Raw snapshot restore completed but cleanup failed.",
                                evidence
                            )
                        }
                    }
                }
            }
        }
    }
    }

    private fun inspectBackupZip(zipFile: File): ArchiveInspection {
        require(Files.isRegularFile(zipFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Raw snapshot archive cache is not a regular file"
        }
        require(zipFile.length() in 1L..RAW_SNAPSHOT_MAX_ARCHIVE_BYTES) {
            "Raw snapshot archive exceeds the ${RAW_SNAPSHOT_MAX_ARCHIVE_BYTES}-byte limit"
        }

        try {
            ZipFile(zipFile).use { archive ->
                val entries = mutableListOf<CentralDirectoryEntry>()
                val entryNames = mutableSetOf<String>()
                val payloadTargetPaths = mutableSetOf<String>()
                var manifestEntry: ZipEntry? = null
                var payloadEntryCount = 0
                var declaredUncompressedBytes = 0L
                var declaredCompressedBytes = 0L

                val enumeration = archive.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    val metadata = entry.toCentralDirectoryEntry()
                    require(metadata.name.length in 1..RAW_SNAPSHOT_MAX_ENTRY_NAME_CHARS) {
                        "Invalid backup zip entry name length"
                    }
                    require(entryNames.add(metadata.name)) {
                        "Invalid backup zip central directory: duplicate entry ${metadata.name}"
                    }
                    require(metadata.method == ZipEntry.STORED || metadata.method == ZipEntry.DEFLATED) {
                        "Invalid backup zip compression method: ${metadata.name}"
                    }
                    require(metadata.uncompressedSize >= 0L && metadata.compressedSize >= 0L) {
                        "Invalid backup zip central directory size: ${metadata.name}"
                    }
                    require(metadata.crc32 >= 0L) {
                        "Invalid backup zip central directory CRC: ${metadata.name}"
                    }

                    if (metadata.name == ENTRY_MANIFEST) {
                        require(!metadata.isDirectory) {
                            "Invalid backup zip: $ENTRY_MANIFEST is not a regular file"
                        }
                        require(manifestEntry == null) {
                            "Invalid backup zip central directory: duplicate $ENTRY_MANIFEST"
                        }
                        require(metadata.uncompressedSize <= RAW_SNAPSHOT_MAX_MANIFEST_BYTES) {
                            "Raw snapshot manifest exceeds the " +
                                "${RAW_SNAPSHOT_MAX_MANIFEST_BYTES}-byte limit"
                        }
                        manifestEntry = entry
                    } else {
                        require(metadata.name.startsWith(ENTRY_PAYLOAD_PREFIX)) {
                            "Invalid backup zip: unexpected entry ${metadata.name}"
                        }
                        requireValidRawSnapshotPayloadZipPath(
                            metadata.name,
                            isDirectory = metadata.isDirectory
                        )
                        payloadEntryCount++
                        require(payloadEntryCount <= RAW_SNAPSHOT_MAX_PAYLOAD_ENTRY_COUNT) {
                            "Raw snapshot payload exceeds the " +
                                "$RAW_SNAPSHOT_MAX_PAYLOAD_ENTRY_COUNT-entry limit"
                        }
                        require(metadata.uncompressedSize <=
                            RAW_SNAPSHOT_MAX_ENTRY_UNCOMPRESSED_BYTES
                        ) {
                            "Raw snapshot payload entry exceeds the " +
                                "${RAW_SNAPSHOT_MAX_ENTRY_UNCOMPRESSED_BYTES}-byte limit: " +
                                metadata.name
                        }
                        if (metadata.isDirectory) {
                            require(metadata.uncompressedSize == 0L && metadata.crc32 == 0L) {
                                "Raw snapshot payload directory contains data: ${metadata.name}"
                            }
                        }
                        val logicalTarget = metadata.name.removeSuffix("/")
                        require(payloadTargetPaths.add(logicalTarget)) {
                            "Invalid backup zip central directory target collision: ${metadata.name}"
                        }
                    }

                    declaredUncompressedBytes =
                        addRawSnapshotBytesWithinLimit(
                            currentBytes = declaredUncompressedBytes,
                            additionalBytes = metadata.uncompressedSize,
                            limitBytes = RAW_SNAPSHOT_MAX_TOTAL_UNCOMPRESSED_BYTES,
                            description = "Raw snapshot total uncompressed data"
                        )
                    declaredCompressedBytes =
                        addRawSnapshotBytesWithinLimit(
                            currentBytes = declaredCompressedBytes,
                            additionalBytes = metadata.compressedSize,
                            limitBytes = zipFile.length(),
                            description = "Raw snapshot central-directory compressed data"
                        )
                    entries += metadata
                }

                val requiredManifestEntry =
                    manifestEntry
                        ?: throw IllegalArgumentException(
                            "Invalid backup zip: missing $ENTRY_MANIFEST"
                        )
                validateCentralDirectoryPathHierarchy(entries)
                val manifestBytes =
                    archive.getInputStream(requiredManifestEntry).use { input ->
                        input.readBytesSafely(
                            maxBytes = RAW_SNAPSHOT_MAX_MANIFEST_BYTES,
                            description = "Raw snapshot manifest"
                        )
                    }
                require(manifestBytes.size.toLong() == requiredManifestEntry.size) {
                    "Raw snapshot manifest length does not match its central directory"
                }
                require(crc32(manifestBytes) == requiredManifestEntry.crc) {
                    "Raw snapshot manifest CRC does not match its central directory"
                }

                val parsedManifest = parseSnapshotManifest(manifestBytes)
                when (parsedManifest) {
                    is ParsedSnapshotManifest.Version1 -> {
                        require(hasExactReleasedFormat1Includes(parsedManifest.manifest.includes)) {
                            "Format-1 raw snapshot manifest has an invalid includes list"
                        }
                        val roomPath = ENTRY_DATABASES + RoomRecoveryStorage.DATABASE_NAME
                        require(entries.any { entry ->
                            entry.name == roomPath && !entry.isDirectory
                        }) {
                            "Format-1 raw snapshot is missing its required Room database"
                        }
                        // Released format 1 has no file inventory. Its compatibility boundary is
                        // strict central-directory/path/size/CRC validation plus the verified
                        // pre-restore quarantine that can restore every live category.
                    }
                    is ParsedSnapshotManifest.Version2 -> {
                        validateVersion2CentralDirectory(parsedManifest.manifest, entries)
                    }
                }
                return ArchiveInspection(
                    manifest = parsedManifest,
                    entries = entries,
                    manifestBytes = manifestBytes.size.toLong(),
                    declaredUncompressedBytes = declaredUncompressedBytes
                )
            }
        } catch (failure: IllegalArgumentException) {
            throw failure
        } catch (failure: Exception) {
            throw IllegalArgumentException(
                "Invalid raw snapshot ZIP central directory",
                failure
            )
        }
    }

    private fun ZipEntry.toCentralDirectoryEntry(): CentralDirectoryEntry =
        CentralDirectoryEntry(
            name = name,
            isDirectory = isDirectory,
            uncompressedSize = size,
            compressedSize = compressedSize,
            crc32 = crc,
            method = method
        )

    private fun validateCentralDirectoryPathHierarchy(entries: List<CentralDirectoryEntry>) {
        val payloadEntries =
            entries
                .filter { entry -> entry.name.startsWith(ENTRY_PAYLOAD_PREFIX) }
        val logicalPaths = payloadEntries.map { entry -> entry.name.removeSuffix("/") }.sorted()
        payloadEntries.filterNot { entry -> entry.isDirectory }.forEach { entry ->
            val descendantPrefix = "${entry.name}/"
            val searchResult = logicalPaths.binarySearch(descendantPrefix)
            val candidateIndex = if (searchResult >= 0) searchResult else -searchResult - 1
            val candidate = logicalPaths.getOrNull(candidateIndex)
            require(candidate == null || !candidate.startsWith(descendantPrefix)) {
                val logicalPath = entry.name
                "Invalid backup zip: payload file is an ancestor of another entry: $logicalPath"
            }
        }
    }

    private fun parseSnapshotManifest(bytes: ByteArray): ParsedSnapshotManifest {
        val text = bytes.toString(Charsets.UTF_8)
        val envelope =
            try {
                manifestEnvelopeJson.decodeFromString<ManifestVersionEnvelope>(text)
            } catch (failure: Exception) {
                throw IllegalArgumentException("Invalid raw snapshot manifest version envelope", failure)
            }
        val parsed =
            try {
                when (envelope.formatVersion) {
                    1 ->
                        ParsedSnapshotManifest.Version1(
                            json.decodeFromString<ReleasedFormat1Manifest>(text)
                        )
                    FORMAT_VERSION ->
                        ParsedSnapshotManifest.Version2(
                            json.decodeFromString<Manifest>(text)
                        )
                    else ->
                        throw IllegalArgumentException(
                            "Unsupported backup version: ${envelope.formatVersion}"
                        )
                }
            } catch (failure: IllegalArgumentException) {
                throw failure
            } catch (failure: Exception) {
                throw IllegalArgumentException(
                    "Raw snapshot manifest does not match format ${envelope.formatVersion}",
                    failure
                )
            }
        require(parsed.formatVersion == envelope.formatVersion) {
            "Raw snapshot manifest version changed during schema decoding"
        }
        require(parsed.createdAt > 0L) {
            "Raw snapshot manifest creation time is invalid"
        }
        require(isSupportedSnapshotPackageName(parsed.packageName)) {
            "Backup package mismatch: ${parsed.packageName}"
        }
        return parsed
    }

    private fun validateVersion2CentralDirectory(
        manifest: Manifest,
        entries: List<CentralDirectoryEntry>
    ) {
        require(manifest.payloadFiles.size <= RAW_SNAPSHOT_MAX_PAYLOAD_ENTRY_COUNT) {
            "Raw snapshot manifest exceeds the " +
                "$RAW_SNAPSHOT_MAX_PAYLOAD_ENTRY_COUNT-file inventory limit"
        }
        var declaredPayloadBytes = 0L
        manifest.payloadFiles.forEach { file ->
            require(file.byteLength <= RAW_SNAPSHOT_MAX_ENTRY_UNCOMPRESSED_BYTES) {
                "Raw snapshot manifest declares an oversized payload file: ${file.zipPath}"
            }
            declaredPayloadBytes =
                addRawSnapshotBytesWithinLimit(
                    currentBytes = declaredPayloadBytes,
                    additionalBytes = file.byteLength,
                    limitBytes = RAW_SNAPSHOT_MAX_TOTAL_UNCOMPRESSED_BYTES,
                    description = "Raw snapshot manifest payload inventory"
                )
        }
        val manifestFilesByPath = manifest.payloadFiles.associateBy { file -> file.zipPath }
        val observations =
            entries
                .filter { entry -> entry.name.startsWith(ENTRY_PAYLOAD_PREFIX) }
                .map { entry ->
                    if (entry.isDirectory) {
                        RawSnapshotObservedPayloadEntry(
                            zipPath = entry.name,
                            kind = RawSnapshotPayloadEntryKind.DIRECTORY,
                            byteLength = 0L,
                            sha256 = ""
                        )
                    } else {
                        RawSnapshotObservedPayloadEntry(
                            zipPath = entry.name,
                            kind = RawSnapshotPayloadEntryKind.REGULAR_FILE,
                            byteLength = entry.uncompressedSize,
                            sha256 =
                                requireNotNull(manifestFilesByPath[entry.name]) {
                                    "Format-2 archive contains an unlisted payload file: " +
                                        entry.name
                                }.sha256
                        )
                    }
                }
        validateRawSnapshotPayloadInventory(
            manifestDirectories = manifest.payloadDirectories,
            manifestFiles = manifest.payloadFiles,
            observedEntries = observations
        )
    }

    private fun extractZipToWorkDir(
        zipFile: File,
        workDir: File,
        inspection: ArchiveInspection
    ) {
        val payloadRoot = File(workDir, "payload")
        check(
            payloadRoot.mkdirs() ||
                Files.isDirectory(payloadRoot.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            "Failed to create raw snapshot payload root"
        }
        val payloadCanonical = payloadRoot.canonicalFile
        var extractedPayloadFiles = 0
        val extractedTargetPaths = mutableSetOf<String>()
        val observedPayloadEntries = mutableListOf<RawSnapshotObservedPayloadEntry>()
        var totalUncompressedBytes = inspection.manifestBytes
        val version2FilesByPath: Map<String, RawSnapshotPayloadFile> =
            when (val parsed = inspection.manifest) {
                is ParsedSnapshotManifest.Version1 -> emptyMap()
                is ParsedSnapshotManifest.Version2 ->
                    parsed.manifest.payloadFiles.associateBy { file -> file.zipPath }
            }
        val buffer = ByteArray(64 * 1024)
        val extractMs = measureTimeMillis {
            ZipFile(zipFile).use { archive ->
                val currentEntries = mutableListOf<CentralDirectoryEntry>()
                val enumeration = archive.entries()
                while (enumeration.hasMoreElements()) {
                    currentEntries += enumeration.nextElement().toCentralDirectoryEntry()
                }
                require(currentEntries == inspection.entries) {
                    "Raw snapshot ZIP central directory changed before extraction"
                }

                inspection.entries.forEach { metadata ->
                    if (metadata.name == ENTRY_MANIFEST) return@forEach
                    val entry =
                        requireNotNull(archive.getEntry(metadata.name)) {
                            "Raw snapshot ZIP entry disappeared before extraction: ${metadata.name}"
                        }
                    val name = metadata.name

                    val target = File(workDir, name)
                    val targetCanonical = target.canonicalFile
                    val isPayloadRoot = targetCanonical == payloadCanonical
                    val isPayloadChild =
                        targetCanonical.path.startsWith(payloadCanonical.path + File.separator)
                    if (!isPayloadRoot && !isPayloadChild) {
                        throw IllegalArgumentException("Invalid zip entry path: $name")
                    }
                    if (!extractedTargetPaths.add(targetCanonical.path)) {
                        throw IllegalArgumentException(
                            "Invalid backup zip: duplicate target path $name"
                        )
                    }

                    if (metadata.isDirectory) {
                        check(
                            target.mkdirs() ||
                                Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)
                        ) {
                            "Failed to create raw snapshot payload directory: $name"
                        }
                        archive.getInputStream(entry).use { input ->
                            if (input.read() != -1) {
                                throw IllegalArgumentException(
                                    "Invalid backup zip: payload directory contains file data: $name"
                                )
                            }
                        }
                        if (metadata.uncompressedSize != 0L) {
                            throw IllegalArgumentException(
                                "Invalid backup zip: payload directory contains file data: $name"
                            )
                        }
                        observedPayloadEntries +=
                            RawSnapshotObservedPayloadEntry(
                                zipPath = name,
                                kind = RawSnapshotPayloadEntryKind.DIRECTORY,
                                byteLength = 0L,
                                sha256 = ""
                            )
                        return@forEach
                    }

                    val parent = checkNotNull(target.parentFile)
                    check(
                        parent.mkdirs() ||
                            Files.isDirectory(parent.toPath(), LinkOption.NOFOLLOW_LINKS)
                    ) {
                        "Failed to create raw snapshot payload parent: $name"
                    }
                    if (pathExistsStrict(target)) {
                        throw IllegalArgumentException(
                            "Invalid backup zip: payload path conflicts with a directory: $name"
                        )
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val crc = CRC32()
                    var byteLength = 0L
                    val version2ExpectedFile =
                        when (inspection.manifest) {
                            is ParsedSnapshotManifest.Version1 -> null
                            is ParsedSnapshotManifest.Version2 ->
                                checkNotNull(version2FilesByPath[name]) {
                                    "Format-2 payload file is absent from its manifest: $name"
                                }
                        }
                    archive.getInputStream(entry).use { input ->
                        BufferedOutputStream(FileOutputStream(target)).use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                val nextEntryBytes =
                                    addRawSnapshotBytesWithinLimit(
                                        currentBytes = byteLength,
                                        additionalBytes = read.toLong(),
                                        limitBytes = RAW_SNAPSHOT_MAX_ENTRY_UNCOMPRESSED_BYTES,
                                        description = "Raw snapshot payload entry $name"
                                    )
                                val expectedLength = version2ExpectedFile?.byteLength
                                require(expectedLength == null || nextEntryBytes <= expectedLength) {
                                    "Format-2 payload entry exceeds its declared length: $name"
                                }
                                totalUncompressedBytes =
                                    addRawSnapshotBytesWithinLimit(
                                        currentBytes = totalUncompressedBytes,
                                        additionalBytes = read.toLong(),
                                        limitBytes = RAW_SNAPSHOT_MAX_TOTAL_UNCOMPRESSED_BYTES,
                                        description = "Raw snapshot total uncompressed data"
                                    )
                                require(workDir.usableSpace >=
                                    RAW_SNAPSHOT_STORAGE_RESERVE_BYTES + read.toLong()
                                ) {
                                    "Raw snapshot extraction would consume reserved storage while " +
                                        "writing $name"
                                }
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                crc.update(buffer, 0, read)
                                byteLength = nextEntryBytes
                            }
                        }
                    }
                    require(byteLength == metadata.uncompressedSize) {
                        "Raw snapshot payload length differs from its central directory: $name"
                    }
                    require(version2ExpectedFile == null ||
                        byteLength == version2ExpectedFile.byteLength
                    ) {
                        "Format-2 payload entry length differs from its manifest: $name"
                    }
                    require(crc.value == metadata.crc32) {
                        "Raw snapshot payload CRC differs from its central directory: $name"
                    }
                    if (!Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        throw IllegalArgumentException(
                            "Invalid backup zip: payload path is not a regular file: $name"
                        )
                    }

                    extractedPayloadFiles++
                    observedPayloadEntries +=
                        RawSnapshotObservedPayloadEntry(
                            zipPath = name,
                            kind = RawSnapshotPayloadEntryKind.REGULAR_FILE,
                            byteLength = byteLength,
                            sha256 = digest.digest().toLowerHexString()
                        )
                }
            }
        }

        AppLogger.i(TAG, "restore extract done in ${extractMs}ms (payloadFiles=$extractedPayloadFiles)")
        require(totalUncompressedBytes == inspection.declaredUncompressedBytes) {
            "Raw snapshot total extracted bytes differ from the central directory"
        }

        when (val parsed = inspection.manifest) {
            is ParsedSnapshotManifest.Version1 -> {
                // The released writer omitted directory entries for empty categories. The exact
                // includes list validated during inspection is the authority to materialize them.
                RELEASED_FORMAT_1_INCLUDES.forEach { entryPrefix ->
                    val directory = File(workDir, entryPrefix.removeSuffix("/"))
                    check(
                        directory.mkdirs() ||
                            Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)
                    ) {
                        "Failed to materialize format-1 payload category: $entryPrefix"
                    }
                }
            }
            is ParsedSnapshotManifest.Version2 -> {
                validateRawSnapshotPayloadInventory(
                    manifestDirectories = parsed.manifest.payloadDirectories,
                    manifestFiles = parsed.manifest.payloadFiles,
                    observedEntries = observedPayloadEntries
                )
            }
        }
    }

    private fun validatePayloadShape(
        expectedFiles: Set<String>,
        payloadDir: File
    ): ValidatedPayloadDirectories {
        require(Files.isDirectory(payloadDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Raw snapshot payload root is not a directory"
        }
        val validated =
            ValidatedPayloadDirectories(
                filesDir = File(payloadDir, "files"),
                externalFilesDir = File(payloadDir, "external_files"),
                sharedPrefsDir = File(payloadDir, "shared_prefs"),
                datastoreDir = File(payloadDir, "datastore"),
                databasesDir = File(payloadDir, "databases")
            )
        validated.allDirectories.forEach { directory ->
            requireRawSnapshotReplacementSourceDirectory(directory)
        }
        validateExtractedPayloadTree(expectedFiles, payloadDir)
        return validated
    }

    private fun validateExtractedPayloadTree(expectedFiles: Set<String>, payloadDir: File) {
        val expectedDirectories =
            RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES
                .mapTo(mutableSetOf()) { directory ->
                    directory.removePrefix(ENTRY_PAYLOAD_PREFIX).removeSuffix("/")
                }
        expectedFiles.forEach { filePath ->
            var parentPath = filePath.substringBeforeLast('/')
            while (parentPath != ENTRY_PAYLOAD_PREFIX.removeSuffix("/")) {
                expectedDirectories += parentPath.removePrefix(ENTRY_PAYLOAD_PREFIX)
                parentPath = parentPath.substringBeforeLast('/')
            }
        }

        val actualFiles = mutableSetOf<String>()
        val actualDirectories = mutableSetOf<String>()

        fun visit(directory: File, relativeDirectory: String) {
            val children =
                directory.listFiles()
                    ?: throw IllegalArgumentException(
                        "Raw snapshot payload directory cannot be enumerated: ${directory.absolutePath}"
                    )
            children.forEach { child ->
                val relativePath =
                    if (relativeDirectory.isEmpty()) child.name
                    else "$relativeDirectory/${child.name}"
                when {
                    Files.isDirectory(child.toPath(), LinkOption.NOFOLLOW_LINKS) -> {
                        actualDirectories += relativePath
                        visit(child, relativePath)
                    }
                    Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS) -> {
                        actualFiles += ENTRY_PAYLOAD_PREFIX + relativePath
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "Raw snapshot payload contains a non-regular path: $relativePath"
                        )
                    }
                }
            }
        }

        visit(payloadDir, "")
        require(actualDirectories == expectedDirectories) {
            "Raw snapshot payload directory tree does not match its manifest"
        }
        require(actualFiles == expectedFiles) {
            "Raw snapshot payload file tree does not match its manifest"
        }
    }

    private fun validateRoomPayload(context: Context, databasesDir: File, workDir: File) {
        val roomPayload = File(databasesDir, RoomRecoveryStorage.DATABASE_NAME)
        if (!Files.isRegularFile(roomPayload.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("Raw snapshot is missing its Room database")
        }
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            if (File(roomPayload.absolutePath + suffix).exists()) {
                throw IllegalArgumentException(
                    "Raw snapshot must contain a checkpointed Room database without sidecars"
                )
            }
        }

        val validationDir = File(workDir, "room_validation")
        check(validationDir.mkdirs()) {
            "Failed to create Room raw snapshot validation directory"
        }
        val validationDatabase = File(validationDir, RoomRecoveryStorage.DATABASE_NAME)
        roomPayload.copyTo(validationDatabase, overwrite = false)
        if (!RoomRecoveryStorage.validateDatabaseSet(context, validationDatabase)) {
            throw IllegalArgumentException("Raw snapshot contains an invalid Room database")
        }
    }

    private fun validateObjectBoxPayloads(context: Context, filesDir: File, workDir: File) {
        val filesPayloadEntries =
            filesDir.listFiles()
                ?: throw IllegalArgumentException(
                    "Raw snapshot files payload cannot be enumerated"
                )
        val objectBoxPayloads =
            filesPayloadEntries
                .filter { file -> isObjectBoxDirectoryName(file.name) }
                .sortedBy { file -> file.name }
        if (objectBoxPayloads.isEmpty()) return

        val validationRoot = File(workDir, "objectbox_validation")
        check(validationRoot.mkdirs()) {
            "Failed to create ObjectBox raw snapshot validation directory"
        }
        objectBoxPayloads.forEachIndexed { index, objectBoxPayload ->
            val profileId =
                if (objectBoxPayload.name == "objectbox") {
                    "default"
                } else {
                    objectBoxPayload.name.removePrefix("objectbox_")
                }
            if (!StorageProfileIdPolicy.isSafeMemorySpaceId(profileId)) {
                throw IllegalArgumentException("Raw snapshot ObjectBox profile ID is invalid")
            }
            if (!Files.isDirectory(objectBoxPayload.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw IllegalArgumentException(
                    "Raw snapshot ObjectBox payload is not a directory: ${objectBoxPayload.name}"
                )
            }
            val objectBoxFiles =
                objectBoxPayload.listFiles()
                    ?: throw IllegalArgumentException(
                        "Raw snapshot ObjectBox payload cannot be enumerated: ${objectBoxPayload.name}"
                    )
            val dataFile = File(objectBoxPayload, "data.mdb")
            if (objectBoxFiles.size != 1 ||
                !Files.isRegularFile(dataFile.toPath(), LinkOption.NOFOLLOW_LINKS)
            ) {
                throw IllegalArgumentException(
                    "Raw snapshot ObjectBox payload must contain only data.mdb: ${objectBoxPayload.name}"
                )
            }

            val validationDirectory = File(validationRoot, "profile_$index")
            check(validationDirectory.mkdirs()) {
                "Failed to create ObjectBox profile validation directory"
            }
            dataFile.copyTo(File(validationDirectory, "data.mdb"), overwrite = false)
            if (!ObjectBoxManager.validateRecoveryDirectory(context, validationDirectory)) {
                throw IllegalArgumentException(
                    "Raw snapshot contains an invalid ObjectBox database: ${objectBoxPayload.name}"
                )
            }
        }
    }

    private fun RawSnapshotRestoreCategory.restoreProgress(): RestoreProgress =
        when (this) {
            RawSnapshotRestoreCategory.FILES -> RestoreProgress.REPLACING_FILES
            RawSnapshotRestoreCategory.EXTERNAL_FILES -> RestoreProgress.REPLACING_EXTERNAL_FILES
            RawSnapshotRestoreCategory.SHARED_PREFS -> RestoreProgress.REPLACING_SHARED_PREFS
            RawSnapshotRestoreCategory.DATASTORE -> RestoreProgress.REPLACING_DATASTORE
            RawSnapshotRestoreCategory.DATABASES -> RestoreProgress.REPLACING_DATABASES
        }

    private fun createPreRestoreQuarantine(
        context: Context,
        replacements: List<ReplacementCategory>,
        transactionId: String
    ): PreRestoreQuarantine {
        require(RAW_SNAPSHOT_TRANSACTION_ID_PATTERN.matches(transactionId)) {
            "Raw snapshot quarantine transaction ID is invalid"
        }
        val appContext = context.applicationContext
        val quarantineParent =
            File(appContext.noBackupFilesDir, "storage-recovery/quarantine")
        check(
            quarantineParent.mkdirs() ||
                Files.isDirectory(quarantineParent.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            "Failed to create raw snapshot quarantine parent"
        }

        val uniqueName = rawSnapshotQuarantineBasename(transactionId)
        val stagingRoot = File(quarantineParent, ".$uniqueName.tmp")
        val finalRoot = File(quarantineParent, uniqueName)
        requireQuarantineOutsideReplacementTargets(stagingRoot, replacements)
        requireQuarantineOutsideReplacementTargets(finalRoot, replacements)
        check(!pathExistsStrict(stagingRoot) && !pathExistsStrict(finalRoot)) {
            "Raw snapshot quarantine path already exists"
        }
        check(stagingRoot.mkdirs()) {
            "Failed to create raw snapshot quarantine staging directory"
        }

        var cleanupRoot = stagingRoot
        try {
            val manifest =
                RawSnapshotQuarantineManifest(
                    formatVersion = RAW_SNAPSHOT_QUARANTINE_FORMAT_VERSION,
                    createdAt = System.currentTimeMillis(),
                    categories =
                        replacements.map { replacement ->
                            captureQuarantineCategoryState(
                                replacement.category,
                                replacement.destinationDirectory
                            )
                        }
                )
            validateRawSnapshotQuarantineManifest(manifest)

            replacements.zip(manifest.categories).forEach { (replacement, state) ->
                check(replacement.category == state.category)
                copyCategoryStateStrict(
                    sourceRoot = replacement.destinationDirectory,
                    targetRoot = File(stagingRoot, state.category.quarantineDirectoryName),
                    expectedState = state
                )
            }
            // Re-hash every live category after all copies so an out-of-band write cannot publish
            // a quarantine assembled from different storage epochs.
            verifyLiveCategoriesMatchQuarantine(replacements, manifest)
            writeQuarantineManifest(stagingRoot, manifest)

            check(stagingRoot.renameTo(finalRoot)) {
                "Failed to publish verified raw snapshot quarantine"
            }
            cleanupRoot = finalRoot
            val quarantine = PreRestoreQuarantine(finalRoot, manifest)
            verifyPreRestoreQuarantine(quarantine)
            return quarantine
        } catch (failure: Throwable) {
            try {
                deletePathStrict(cleanupRoot)
            } catch (cleanupFailure: Throwable) {
                AppLogger.e(TAG, "Failed to remove incomplete raw snapshot quarantine", cleanupFailure)
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    private fun requireQuarantineOutsideReplacementTargets(
        quarantineRoot: File,
        replacements: List<ReplacementCategory>
    ) {
        val quarantineCanonical = quarantineRoot.canonicalFile
        replacements.forEach { replacement ->
            val targetCanonical = replacement.destinationDirectory.canonicalFile
            check(!isSameOrDescendant(quarantineCanonical, targetCanonical) &&
                !isSameOrDescendant(targetCanonical, quarantineCanonical)
            ) {
                "Raw snapshot quarantine overlaps replacement target: ${replacement.category}"
            }
        }
    }

    private fun isSameOrDescendant(candidate: File, parent: File): Boolean =
        candidate == parent || candidate.path.startsWith(parent.path + File.separator)

    private fun writeQuarantineManifest(
        quarantineRoot: File,
        manifest: RawSnapshotQuarantineManifest
    ) {
        validateRawSnapshotQuarantineManifest(manifest)
        val manifestFile = File(quarantineRoot, "quarantine_manifest.json")
        check(!pathExistsStrict(manifestFile)) {
            "Raw snapshot quarantine manifest already exists"
        }
        FileOutputStream(manifestFile, false).use { output ->
            output.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        check(readQuarantineManifest(quarantineRoot) == manifest) {
            "Raw snapshot quarantine manifest verification failed"
        }
    }

    private fun readQuarantineManifest(quarantineRoot: File): RawSnapshotQuarantineManifest {
        val manifestFile = File(quarantineRoot, "quarantine_manifest.json")
        check(Files.isRegularFile(manifestFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Raw snapshot quarantine manifest is not a regular file"
        }
        val manifest =
            FileInputStream(manifestFile).buffered().use { input ->
                json.decodeFromString<RawSnapshotQuarantineManifest>(
                    input.readBytes().toString(Charsets.UTF_8)
                )
            }
        validateRawSnapshotQuarantineManifest(manifest)
        return manifest
    }

    private fun verifyPreRestoreQuarantine(quarantine: PreRestoreQuarantine) {
        check(readQuarantineManifest(quarantine.rootDirectory) == quarantine.manifest) {
            "Raw snapshot quarantine manifest changed after publication"
        }
        val expectedRootEntries =
            quarantine.manifest.categories
                .filter { state -> state.originallyExisted }
                .mapTo(mutableSetOf()) { state -> state.category.quarantineDirectoryName }
                .apply { add("quarantine_manifest.json") }
        val actualRootEntries =
            quarantine.rootDirectory.listFiles()
                ?.mapTo(mutableSetOf()) { entry -> entry.name }
                ?: throw IllegalStateException(
                    "Failed to enumerate raw snapshot quarantine root"
                )
        check(actualRootEntries == expectedRootEntries) {
            "Raw snapshot quarantine root contains unexpected entries"
        }
        quarantine.manifest.categories.forEach { state ->
            val observed =
                captureQuarantineCategoryState(
                    state.category,
                    File(quarantine.rootDirectory, state.category.quarantineDirectoryName)
                )
            check(observed == state) {
                "Raw snapshot quarantine category verification failed: ${state.category}"
            }
        }
    }

    private fun verifyLiveCategoriesMatchQuarantine(
        replacements: List<ReplacementCategory>,
        manifest: RawSnapshotQuarantineManifest
    ) {
        validateRawSnapshotQuarantineManifest(manifest)
        check(replacements.map { replacement -> replacement.category } ==
            RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER
        ) {
            "Raw snapshot replacement order changed during quarantine verification"
        }
        replacements.zip(manifest.categories).forEach { (replacement, expected) ->
            check(replacement.category == expected.category)
            val observed =
                captureQuarantineCategoryState(
                    replacement.category,
                    replacement.destinationDirectory
                )
            check(observed == expected) {
                "Live raw snapshot category changed during quarantine: ${replacement.category}"
            }
        }
    }

    private fun captureQuarantineCategoryState(
        category: RawSnapshotRestoreCategory,
        root: File
    ): RawSnapshotQuarantineCategoryState {
        if (!pathExistsStrict(root)) {
            return RawSnapshotQuarantineCategoryState(
                category = category,
                originallyExisted = false,
                directories = emptyList(),
                files = emptyList()
            )
        }
        check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Raw snapshot live category is not a directory: ${root.absolutePath}"
        }

        val baseCanonical = root.canonicalFile
        val directories = mutableListOf<String>()
        val files = mutableListOf<RawSnapshotQuarantineFileState>()

        fun visit(directory: File, relativeDirectory: String) {
            val children =
                directory.listFiles()
                    ?.sortedBy { child -> child.name }
                    ?: throw IllegalStateException(
                        "Failed to enumerate raw snapshot category: ${directory.absolutePath}"
                    )
            children.forEach { child ->
                val relativePath =
                    if (relativeDirectory.isEmpty()) child.name
                    else "$relativeDirectory/${child.name}"
                requireValidRawSnapshotQuarantineRelativePath(relativePath)
                when {
                    Files.isDirectory(child.toPath(), LinkOption.NOFOLLOW_LINKS) -> {
                        requireCanonicalChild(baseCanonical, child)
                        directories += relativePath
                        visit(child, relativePath)
                    }
                    Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS) -> {
                        requireCanonicalChild(baseCanonical, child)
                        files += inspectQuarantineRegularFile(child, relativePath)
                    }
                    else -> {
                        throw IllegalStateException(
                            "Raw snapshot category contains a non-regular entry: " +
                                child.absolutePath
                        )
                    }
                }
            }
        }

        visit(root, "")
        return RawSnapshotQuarantineCategoryState(
            category = category,
            originallyExisted = true,
            directories = directories.sorted(),
            files = files.sortedBy { file -> file.relativePath }
        )
    }

    private fun requireCanonicalChild(baseCanonical: File, child: File) {
        val childCanonical = child.canonicalFile
        check(childCanonical.path.startsWith(baseCanonical.path + File.separator)) {
            "Raw snapshot quarantine path resolves outside its category"
        }
    }

    private fun inspectQuarantineRegularFile(
        file: File,
        relativePath: String
    ): RawSnapshotQuarantineFileState {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Raw snapshot quarantine source is not a regular file: ${file.absolutePath}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var byteLength = 0L
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                byteLength = Math.addExact(byteLength, read.toLong())
            }
        }
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            file.length() == byteLength
        ) {
            "Raw snapshot quarantine source changed while being read: ${file.absolutePath}"
        }
        return RawSnapshotQuarantineFileState(
            relativePath = relativePath,
            byteLength = byteLength,
            sha256 = digest.digest().toLowerHexString()
        )
    }

    private fun copyCategoryStateStrict(
        sourceRoot: File,
        targetRoot: File,
        expectedState: RawSnapshotQuarantineCategoryState
    ) {
        check(captureQuarantineCategoryState(expectedState.category, sourceRoot) == expectedState) {
            "Raw snapshot quarantine source changed before copy: ${expectedState.category}"
        }
        check(!pathExistsStrict(targetRoot)) {
            "Raw snapshot quarantine target already exists: ${targetRoot.absolutePath}"
        }
        if (!expectedState.originallyExisted) return

        check(targetRoot.mkdirs()) {
            "Failed to create raw snapshot quarantine category: ${expectedState.category}"
        }
        expectedState.directories
            .sortedWith(compareBy<String> { path -> path.count { character -> character == '/' } }
                .thenBy { path -> path })
            .forEach { relativePath ->
                val target = resolveQuarantineRelativePath(targetRoot, relativePath)
                check(target.mkdir()) {
                    "Failed to create raw snapshot quarantine directory: $relativePath"
                }
            }
        expectedState.files.forEach { expectedFile ->
            val source = resolveQuarantineRelativePath(sourceRoot, expectedFile.relativePath)
            check(inspectQuarantineRegularFile(source, expectedFile.relativePath) == expectedFile) {
                "Raw snapshot quarantine source file changed: ${expectedFile.relativePath}"
            }
            val target = resolveQuarantineRelativePath(targetRoot, expectedFile.relativePath)
            StorageQuarantineFiles.copyVerified(source, target)
            check(inspectQuarantineRegularFile(target, expectedFile.relativePath) == expectedFile) {
                "Raw snapshot quarantine copied file verification failed: " +
                    expectedFile.relativePath
            }
        }
        check(captureQuarantineCategoryState(expectedState.category, sourceRoot) == expectedState) {
            "Raw snapshot quarantine source changed during copy: ${expectedState.category}"
        }
        check(captureQuarantineCategoryState(expectedState.category, targetRoot) == expectedState) {
            "Raw snapshot quarantine category copy verification failed: ${expectedState.category}"
        }
    }

    private fun resolveQuarantineRelativePath(root: File, relativePath: String): File {
        requireValidRawSnapshotQuarantineRelativePath(relativePath)
        val resolved = File(root, relativePath.replace('/', File.separatorChar))
        val rootCanonical = root.canonicalFile
        val resolvedCanonical = resolved.canonicalFile
        check(resolvedCanonical.path.startsWith(rootCanonical.path + File.separator)) {
            "Raw snapshot quarantine relative path resolves outside its root"
        }
        return resolved
    }

    private fun pathExistsStrict(path: File): Boolean {
        if (Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS)) return true
        check(Files.notExists(path.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Unable to determine raw snapshot path state: ${path.absolutePath}"
        }
        return false
    }

    private fun deletePathStrict(path: File) {
        if (!pathExistsStrict(path)) return
        if (Files.isDirectory(path.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val children =
                path.listFiles()
                    ?.sortedBy { child -> child.name }
                    ?: throw IllegalStateException(
                        "Failed to enumerate raw snapshot deletion path: ${path.absolutePath}"
                    )
            children.forEach { child -> deletePathStrict(child) }
        }
        Files.delete(path.toPath())
        check(!pathExistsStrict(path)) {
            "Failed to delete raw snapshot path: ${path.absolutePath}"
        }
    }

    private fun restoreCategoryFromQuarantine(
        replacement: ReplacementCategory,
        quarantineRoot: File,
        expectedState: RawSnapshotQuarantineCategoryState
    ) {
        check(replacement.category == expectedState.category)
        val quarantineCategory =
            File(quarantineRoot, expectedState.category.quarantineDirectoryName)
        check(captureQuarantineCategoryState(expectedState.category, quarantineCategory) ==
            expectedState
        ) {
            "Raw snapshot rollback source verification failed: ${expectedState.category}"
        }

        deletePathStrict(replacement.destinationDirectory)
        copyCategoryStateStrict(
            sourceRoot = quarantineCategory,
            targetRoot = replacement.destinationDirectory,
            expectedState = expectedState
        )
        check(captureQuarantineCategoryState(
            expectedState.category,
            replacement.destinationDirectory
        ) == expectedState
        ) {
            "Raw snapshot rollback destination verification failed: ${expectedState.category}"
        }
    }

    private fun addDirToZip(
        zos: ZipOutputStream,
        dir: File,
        entryPrefix: String,
        writtenEntryNames: MutableSet<String>,
        payloadFiles: MutableList<RawSnapshotPayloadFile>,
        excludedTopLevelDirNames: Set<String> = emptySet(),
        totalFiles: Int = 0,
        onPercentChanged: ((Int) -> Unit)? = null,
        excludeObjectBoxDirectories: Boolean = false
    ): Int {
        val sourceExists = Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)
        if (sourceExists) {
            check(Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "Raw snapshot source is not a directory: ${dir.absolutePath}"
            }
        }
        writePayloadDirectoryEntry(zos, entryPrefix, writtenEntryNames)
        if (!sourceExists) return 0

        val baseCanonical = dir.canonicalFile

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
            if (!Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) return@forEach

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
            payloadFiles +=
                writePayloadFileToZip(
                    zos = zos,
                    source = canonical,
                    entryName = entryName,
                    writtenEntryNames = writtenEntryNames
                )

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
        writtenEntryNames: MutableSet<String>,
        payloadFiles: MutableList<RawSnapshotPayloadFile>,
        onPercentChanged: ((Int) -> Unit)?
    ) {
        var processedFiles = processedBefore
        var lastPercent = -1
        files.sortedBy { snapshotFile -> snapshotFile.relativePath }.forEach { snapshotFile ->
            check(Files.isRegularFile(snapshotFile.file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "Raw snapshot staged data file is missing"
            }
            val relativePath = snapshotFile.relativePath.replace(File.separatorChar, '/')
            val entryName = entryPrefix + relativePath
            requireValidRawSnapshotPayloadZipPath(entryName, isDirectory = false)
            payloadFiles +=
                writePayloadFileToZip(
                    zos = zos,
                    source = snapshotFile.file,
                    entryName = entryName,
                    writtenEntryNames = writtenEntryNames
                )

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

    private fun writePayloadDirectoryEntry(
        zos: ZipOutputStream,
        entryName: String,
        writtenEntryNames: MutableSet<String>
    ) {
        check(entryName in RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES) {
            "Invalid raw snapshot payload directory entry: $entryName"
        }
        check(writtenEntryNames.add(entryName)) {
            "Duplicate raw snapshot zip entry: $entryName"
        }
        zos.putNextEntry(ZipEntry(entryName))
        zos.closeEntry()
    }

    private fun writePayloadFileToZip(
        zos: ZipOutputStream,
        source: File,
        entryName: String,
        writtenEntryNames: MutableSet<String>
    ): RawSnapshotPayloadFile {
        requireValidRawSnapshotPayloadZipPath(entryName, isDirectory = false)
        check(writtenEntryNames.add(entryName)) {
            "Duplicate raw snapshot zip entry: $entryName"
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var byteLength = 0L
        zos.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(source)).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                zos.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                byteLength = Math.addExact(byteLength, read.toLong())
            }
        }
        zos.closeEntry()
        return RawSnapshotPayloadFile(
            zipPath = entryName,
            byteLength = byteLength,
            sha256 = digest.digest().toLowerHexString()
        )
    }

    private fun shouldPruneDirForZip(
        currentDir: File,
        baseDir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>,
        excludeObjectBoxDirectories: Boolean
    ): Boolean {
        if (!Files.isDirectory(currentDir.toPath(), LinkOption.NOFOLLOW_LINKS)) return true
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
        if (!Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return 0
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
            if (!Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) return@forEach
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
        requireRawSnapshotReplacementSourceDirectory(fromDir)

        if (toDir.exists()) {
            check(Files.isDirectory(toDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
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

    private fun InputStream.readBytesSafely(maxBytes: Long, description: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var bytesRead = 0L
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            bytesRead =
                addRawSnapshotBytesWithinLimit(
                    currentBytes = bytesRead,
                    additionalBytes = read.toLong(),
                    limitBytes = maxBytes,
                    description = description
                )
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun crc32(bytes: ByteArray): Long =
        CRC32().apply { update(bytes) }.value

    private fun copyArchiveToCache(input: InputStream, cacheZip: File): Long {
        var copiedBytes = 0L
        val buffer = ByteArray(64 * 1024)
        val cacheParent = checkNotNull(cacheZip.parentFile)
        FileOutputStream(cacheZip, false).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                copiedBytes =
                    addRawSnapshotBytesWithinLimit(
                        currentBytes = copiedBytes,
                        additionalBytes = read.toLong(),
                        limitBytes = RAW_SNAPSHOT_MAX_ARCHIVE_BYTES,
                        description = "Raw snapshot archive"
                    )
                require(cacheParent.usableSpace >=
                    RAW_SNAPSHOT_STORAGE_RESERVE_BYTES + read.toLong()
                ) {
                    "Raw snapshot archive caching would consume reserved storage"
                }
                output.write(buffer, 0, read)
            }
            output.flush()
            output.fd.sync()
        }
        require(copiedBytes > 0L) { "Raw snapshot archive is empty" }
        check(cacheZip.length() == copiedBytes) {
            "Raw snapshot archive cache length verification failed"
        }
        return copiedBytes
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
