package com.ai.assistance.operit.data.persistence

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.toPreferences
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class PreferenceSourceIssue {
    MISSING_FILE,
    INVALID_PATH
}

internal object PreferenceRecoveryStorage {
    private const val TAG = "PreferenceRecovery"
    private const val FORMAT_VERSION = 1
    private const val MAX_EVENTS = 100
    private val lock = Any()

    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
            prettyPrint = false
        }

    @Serializable
    private enum class ValueType {
        BOOLEAN,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        STRING,
        STRING_SET
    }

    @Serializable
    private data class SnapshotEntry(
        val name: String,
        val type: ValueType,
        val scalar: String? = null,
        val strings: List<String> = emptyList()
    )

    @Serializable
    private data class SnapshotPayload(
        val formatVersion: Int,
        val storeName: String,
        val sequence: Long,
        val createdAt: Long,
        val entries: List<SnapshotEntry>
    )

    @Serializable
    private data class SnapshotEnvelope(
        val payload: SnapshotPayload,
        val sha256: String
    )

    @Serializable
    private data class RecoveryEvent(
        val id: String,
        val createdAt: Long,
        val storage: String,
        val kind: String,
        val action: String,
        val exceptionClass: String? = null
    )

    fun recoverFromCorruption(
        context: Context,
        storeName: String,
        sourceFile: File,
        exception: CorruptionException
    ): Preferences =
        synchronized(lock) {
            quarantineFile(context, storeName, sourceFile, "physical")
            val restored = readLatestSnapshot(context, storeName)
            val action = if (restored == null) "created_clean_state" else "restored_snapshot"
            recordEvent(context, storeName, "physical_corruption", action, exception)
            AppLogger.e(TAG, "$storeName is corrupt; $action", exception)
            restored ?: emptyPreferences()
        }

    fun readSnapshotForMissingFile(context: Context, storeName: String): Preferences? =
        synchronized(lock) {
            readLatestSnapshot(context.applicationContext, storeName)
        }

    fun prepareInvalidSourcePathRecovery(
        context: Context,
        storeName: String,
        source: File
    ): Preferences? =
        synchronized(lock) {
            check(source.exists() && !source.isFile) {
                "Preferences source path is not invalid: ${source.absolutePath}"
            }
            quarantineInvalidPath(context.applicationContext, storeName, source)
            val restored = readLatestSnapshot(context.applicationContext, storeName)
            check(source.deleteRecursively()) {
                "Failed to remove invalid Preferences source path: ${source.absolutePath}"
            }
            restored
        }

    fun recordSourceRecovery(
        context: Context,
        storeName: String,
        issue: PreferenceSourceIssue,
        restoredSnapshot: Boolean
    ) {
        synchronized(lock) {
            val kind =
                when (issue) {
                    PreferenceSourceIssue.MISSING_FILE -> "physical_missing"
                    PreferenceSourceIssue.INVALID_PATH -> "physical_invalid_path"
                }
            val action = if (restoredSnapshot) "restored_snapshot" else "created_clean_state"
            recordEvent(
                context.applicationContext,
                storeName,
                kind,
                action,
                null
            )
            AppLogger.w(TAG, "$storeName recovered from $kind; $action")
        }
    }

    fun checkpoint(context: Context, storeName: String, preferences: Preferences) {
        synchronized(lock) {
            val currentSequence =
                listOfNotNull(readSlot(context, storeName, 0), readSlot(context, storeName, 1))
                    .maxOfOrNull { it.payload.sequence }
                    ?: 0L
            val payload =
                SnapshotPayload(
                    formatVersion = FORMAT_VERSION,
                    storeName = storeName,
                    sequence = Math.addExact(currentSequence, 1L),
                    createdAt = System.currentTimeMillis(),
                    entries = encodeEntries(preferences)
                )
            val encodedPayload = json.encodeToString(payload)
            val envelope = SnapshotEnvelope(payload, sha256(encodedPayload.toByteArray(Charsets.UTF_8)))
            val slot = (payload.sequence % 2L).toInt()
            writeAtomic(snapshotFile(context, storeName, slot), json.encodeToString(envelope))
        }
    }

    fun quarantineLogicalState(
        context: Context,
        storeName: String,
        preferences: Preferences,
        issueKeys: Collection<String>
    ) {
        synchronized(lock) {
            val payload =
                SnapshotPayload(
                    formatVersion = FORMAT_VERSION,
                    storeName = storeName,
                    sequence = 0L,
                    createdAt = System.currentTimeMillis(),
                    entries = encodeEntries(preferences)
                )
            val encodedPayload = json.encodeToString(payload)
            val envelope = SnapshotEnvelope(payload, sha256(encodedPayload.toByteArray(Charsets.UTF_8)))
            val target = File(quarantineDir(context), logicalQuarantineName(storeName))
            writeAtomic(target, json.encodeToString(envelope))
            recordEvent(
                context,
                storeName,
                "logical_corruption",
                "repaired_key_count:${issueKeys.distinct().size}",
                null
            )
        }
    }

    fun recordStorageEvent(
        context: Context,
        storage: String,
        kind: String,
        action: String,
        exception: Throwable? = null
    ) {
        synchronized(lock) {
            recordEvent(context, storage, kind, action, exception)
        }
    }

    private fun readLatestSnapshot(context: Context, storeName: String): Preferences? {
        val snapshots =
            listOfNotNull(readSlot(context, storeName, 0), readSlot(context, storeName, 1))
                .sortedByDescending { it.payload.sequence }
        snapshots.forEach { envelope ->
            try {
                return decodePreferences(envelope)
            } catch (e: Exception) {
                // A valid envelope is not sufficient: every typed value must decode before this
                // snapshot is allowed to replace the live store.
                AppLogger.e(
                    TAG,
                    "Ignoring undecodable recovery snapshot for $storeName sequence " +
                        envelope.payload.sequence,
                    e
                )
            }
        }
        return null
    }

    private fun readSlot(context: Context, storeName: String, slot: Int): SnapshotEnvelope? {
        val file = snapshotFile(context, storeName, slot)
        if (!file.exists()) return null
        return try {
            val content = AtomicFile(file).openRead().use { it.readBytes().toString(Charsets.UTF_8) }
            val envelope = json.decodeFromString<SnapshotEnvelope>(content)
            val payloadText = json.encodeToString(envelope.payload)
            check(envelope.payload.formatVersion == FORMAT_VERSION)
            check(envelope.payload.storeName == storeName)
            check(envelope.payload.sequence > 0L)
            check(envelope.payload.createdAt > 0L)
            check(
                envelope.payload.entries.map { entry -> entry.name }.distinct().size ==
                    envelope.payload.entries.size
            )
            check(envelope.sha256 == sha256(payloadText.toByteArray(Charsets.UTF_8)))
            envelope
        } catch (e: Exception) {
            AppLogger.e(TAG, "Ignoring invalid recovery snapshot for $storeName slot $slot", e)
            null
        }
    }

    private fun encodeEntries(preferences: Preferences): List<SnapshotEntry> =
        preferences.asMap().entries
            .map { (key, value) ->
                when (value) {
                    is Boolean -> SnapshotEntry(key.name, ValueType.BOOLEAN, scalar = value.toString())
                    is Int -> SnapshotEntry(key.name, ValueType.INT, scalar = value.toString())
                    is Long -> SnapshotEntry(key.name, ValueType.LONG, scalar = value.toString())
                    is Float -> SnapshotEntry(key.name, ValueType.FLOAT, scalar = value.toString())
                    is Double -> SnapshotEntry(key.name, ValueType.DOUBLE, scalar = value.toString())
                    is String -> SnapshotEntry(key.name, ValueType.STRING, scalar = value)
                    is Set<*> -> {
                        val strings = value.map { element -> requireNotNull(element as? String) }.sorted()
                        SnapshotEntry(key.name, ValueType.STRING_SET, strings = strings)
                    }
                    else -> error("Unsupported Preferences value type for ${key.name}: ${value.javaClass.name}")
                }
            }
            .sortedBy { it.name }

    private fun decodePreferences(envelope: SnapshotEnvelope): Preferences {
        val mutable = mutablePreferencesOf()
        envelope.payload.entries.forEach { entry ->
            when (entry.type) {
                ValueType.BOOLEAN ->
                    mutable[booleanPreferencesKey(entry.name)] = requireNotNull(entry.scalar).toBooleanStrict()
                ValueType.INT -> mutable[intPreferencesKey(entry.name)] = requireNotNull(entry.scalar).toInt()
                ValueType.LONG -> mutable[longPreferencesKey(entry.name)] = requireNotNull(entry.scalar).toLong()
                ValueType.FLOAT -> mutable[floatPreferencesKey(entry.name)] = requireNotNull(entry.scalar).toFloat()
                ValueType.DOUBLE -> mutable[doublePreferencesKey(entry.name)] = requireNotNull(entry.scalar).toDouble()
                ValueType.STRING -> mutable[stringPreferencesKey(entry.name)] = requireNotNull(entry.scalar)
                ValueType.STRING_SET -> mutable[stringSetPreferencesKey(entry.name)] = entry.strings.toSet()
            }
        }
        return mutable.toPreferences()
    }

    private fun quarantineFile(context: Context, storeName: String, source: File, kind: String): File? {
        if (!source.exists()) return null
        check(source.isFile) {
            "Preferences corruption source is not a regular file: ${source.absolutePath}"
        }
        val target =
            File(
                quarantineDir(context),
                "${System.currentTimeMillis()}_${safeName(storeName)}_${kind}_${UUID.randomUUID()}.preferences_pb"
            )
        try {
            StorageQuarantineFiles.copyVerified(source, target)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to quarantine $storeName; automatic replacement aborted", e)
            throw StorageRecoveryException(
                "Failed to preserve corrupt Preferences store $storeName before replacement.",
                e
            )
        }
        return target
    }

    private fun quarantineInvalidPath(context: Context, storeName: String, source: File): File {
        val target =
            File(
                quarantineDir(context),
                "${System.currentTimeMillis()}_${safeName(storeName)}_physical_path_${UUID.randomUUID()}"
            )
        check(target.mkdirs() || target.isDirectory) {
            "Failed to create Preferences path quarantine"
        }
        if (source.isDirectory) {
            copyDirectoryStrict(source, target, "Preferences")
        } else {
            error("Unsupported Preferences corruption source: ${source.absolutePath}")
        }
        return target
    }

    private fun copyDirectoryStrict(source: File, target: File, storage: String) {
        check(target.mkdirs() || target.isDirectory) {
            "Failed to create $storage quarantine subdirectory"
        }
        val children = source.listFiles()
            ?: throw IllegalStateException(
                "Failed to enumerate $storage corruption source: ${source.absolutePath}"
            )
        children.forEach { child ->
            val destination = File(target, child.name)
            when {
                child.isDirectory -> copyDirectoryStrict(child, destination, storage)
                child.isFile -> StorageQuarantineFiles.copyVerified(child, destination)
                else -> error("Unsupported $storage corruption source: ${child.absolutePath}")
            }
        }
    }

    private fun recordEvent(
        context: Context,
        storage: String,
        kind: String,
        action: String,
        exception: Throwable?
    ) {
        try {
            val file = File(rootDir(context), "events.json")
            val current =
                if (file.exists()) {
                    runCatching {
                        AtomicFile(file).openRead().use {
                            json.decodeFromString<List<RecoveryEvent>>(it.readBytes().toString(Charsets.UTF_8))
                        }
                    }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
            val updated =
                (current +
                    RecoveryEvent(
                        id = UUID.randomUUID().toString(),
                        createdAt = System.currentTimeMillis(),
                        storage = storage,
                        kind = kind,
                        action = action,
                        exceptionClass = exception?.javaClass?.name
                    )).takeLast(MAX_EVENTS)
            writeAtomic(file, json.encodeToString(updated))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to record recovery event for $storage", e)
        }
    }

    private fun writeAtomic(file: File, content: String) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            output?.let(atomicFile::failWrite)
            throw e
        }
    }

    private fun rootDir(context: Context): File =
        File(context.noBackupFilesDir, "storage-recovery").apply { mkdirs() }

    private fun snapshotFile(context: Context, storeName: String, slot: Int): File =
        File(File(rootDir(context), "preferences"), "${safeName(storeName)}.$slot.json")

    private fun quarantineDir(context: Context): File =
        File(rootDir(context), "quarantine").apply { mkdirs() }

    private fun logicalQuarantineName(storeName: String): String =
        "${System.currentTimeMillis()}_${safeName(storeName)}_logical_${UUID.randomUUID()}.json"

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
