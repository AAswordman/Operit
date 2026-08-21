package com.ai.assistance.operit.data.persistence

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRecoveryPreservationAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private data class SnapshotSlot(
        val databaseFile: File,
        val metadataFile: File,
        val sequence: Long
    )

    private data class RecoveryEventObservation(
        val id: String,
        val storage: String,
        val kind: String,
        val action: String
    )

    @Test
    fun corruptLiveIsQuarantinedBeforeVerifiedSnapshotRestoresIt() = runBlocking {
        val databaseFile = prepareHealthyClosedDatabase()
        val healthyCopy = copyHealthyDatabaseToCache(databaseFile)
        val marker =
            ChatEntity(
                id = "room_recovery_marker_${UUID.randomUUID()}",
                title = "Room recovery marker"
            )
        val quarantineRoot = File(context.noBackupFilesDir, "storage-recovery/quarantine")
        val corruptPayload = "room-live-corruption-must-survive-quarantine".toByteArray()

        try {
            AppDatabase.getDatabase(context).chatDao().insertChat(marker)
            AppDatabase.closeDatabase()
            RoomRecoveryStorage.invalidatePreparedState()
            val quarantineBefore = quarantineRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
            val eventIdsBefore = recoveryEventIds()
            databaseFile.writeBytes(corruptPayload)
            RoomRecoveryStorage.invalidatePreparedState()

            RoomRecoveryStorage.prepareForOpen(context)

            val quarantine = findNewRoomQuarantine(quarantineRoot, quarantineBefore)
            assertArrayEquals(corruptPayload, File(quarantine, databaseFile.name).readBytes())
            assertTrue(databaseFile.isFile)
            assertFalse(databaseFile.readBytes().contentEquals(corruptPayload))
            assertEquals(marker, AppDatabase.getDatabase(context).chatDao().getChatById(marker.id))
            AppDatabase.closeDatabase()
            assertTrue(RoomRecoveryStorage.validateDatabaseSet(context, databaseFile))
            assertNewRoomEvent(eventIdsBefore, "room_corruption", "restored_snapshot")
        } finally {
            restoreHealthyDatabase(databaseFile, healthyCopy)
            deleteChatMarker(marker.id)
        }
    }

    @Test
    fun corruptLiveWithoutSnapshotRemainsAvailableAndIsQuarantined() {
        val databaseFile = prepareHealthyClosedDatabase()
        val healthyCopy = copyHealthyDatabaseToCache(databaseFile)
        val recoveryRoot = File(context.noBackupFilesDir, "storage-recovery")
        val snapshotRoot = File(recoveryRoot, "room")
        val heldSnapshotRoot = File(recoveryRoot, "room_test_hold_${UUID.randomUUID()}")
        val quarantineRoot = File(recoveryRoot, "quarantine")
        val quarantineBefore = quarantineRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
        val eventIdsBefore = recoveryEventIds()
        val corruptPayload = "room-corruption-without-recovery-slot".toByteArray()
        var snapshotsHeld = false

        try {
            check(snapshotRoot.isDirectory)
            check(snapshotRoot.renameTo(heldSnapshotRoot))
            snapshotsHeld = true
            databaseFile.writeBytes(corruptPayload)
            RoomRecoveryStorage.invalidatePreparedState()

            try {
                RoomRecoveryStorage.prepareForOpen(context)
                fail("Corrupt Room database unexpectedly opened without a verified snapshot")
            } catch (_: StorageRecoveryException) {
                assertArrayEquals(corruptPayload, databaseFile.readBytes())
            }

            val quarantine = findNewRoomQuarantine(quarantineRoot, quarantineBefore)
            assertArrayEquals(corruptPayload, File(quarantine, databaseFile.name).readBytes())
            assertNewRoomEvent(
                eventIdsBefore,
                "room_corruption",
                "preserved_without_snapshot"
            )
        } finally {
            try {
                restoreHealthyDatabase(databaseFile, healthyCopy)
            } finally {
                if (snapshotsHeld) {
                    if (snapshotRoot.exists()) check(snapshotRoot.deleteRecursively())
                    check(heldSnapshotRoot.renameTo(snapshotRoot))
                }
            }
        }
    }

    @Test
    fun corruptNewestSnapshotDoesNotBlockOlderVerifiedSlotOrDeleteIt() {
        val databaseFile = prepareHealthyClosedDatabase()
        RoomRecoveryStorage.checkpointClosed(context)
        RoomRecoveryStorage.checkpointClosed(context)
        val healthyCopy = copyHealthyDatabaseToCache(databaseFile)
        val recoveryRoot = File(context.noBackupFilesDir, "storage-recovery")
        val snapshotRoot = File(recoveryRoot, "room")
        val snapshotBackup = File(recoveryRoot, "room_test_backup_${UUID.randomUUID()}")
        val quarantineRoot = File(recoveryRoot, "quarantine")
        val quarantineBefore = quarantineRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
        val eventIdsBefore = recoveryEventIds()
        val corruptLivePayload = "room-live-corruption-with-corrupt-newest-slot".toByteArray()
        val corruptSlotPayload = "room-newest-slot-corruption".toByteArray()
        var snapshotBackupHeld = false

        try {
            check(snapshotRoot.renameTo(snapshotBackup))
            snapshotBackupHeld = true
            check(snapshotBackup.copyRecursively(snapshotRoot, overwrite = false))
            val newestSlot = newestSnapshotSlot(snapshotRoot)
            newestSlot.databaseFile.writeBytes(corruptSlotPayload)
            rewriteSnapshotIntegrity(newestSlot, corruptSlotPayload)
            databaseFile.writeBytes(corruptLivePayload)
            RoomRecoveryStorage.invalidatePreparedState()

            RoomRecoveryStorage.prepareForOpen(context)

            val quarantine = findNewRoomQuarantine(quarantineRoot, quarantineBefore)
            assertArrayEquals(corruptLivePayload, File(quarantine, databaseFile.name).readBytes())
            assertTrue(RoomRecoveryStorage.validateDatabaseSet(context, databaseFile))
            assertTrue(newestSlot.databaseFile.isFile)
            assertArrayEquals(corruptSlotPayload, newestSlot.databaseFile.readBytes())
            assertNewRoomEvent(eventIdsBefore, "room_corruption", "restored_snapshot")
        } finally {
            try {
                restoreHealthyDatabase(databaseFile, healthyCopy)
            } finally {
                if (snapshotBackupHeld) {
                    if (snapshotRoot.exists()) check(snapshotRoot.deleteRecursively())
                    check(snapshotBackup.renameTo(snapshotRoot))
                }
            }
        }
    }

    @Test
    fun missingDatabasePathRemainsAnOperationalValidationFailure() {
        val missingDatabase =
            context.getDatabasePath("room_missing_validation_${UUID.randomUUID()}")
        deleteDatabaseSet(missingDatabase)

        try {
            RoomRecoveryStorage.validateDatabaseSet(context, missingDatabase)
            fail("Missing Room database was incorrectly classified as physical corruption")
        } catch (error: StorageRecoveryException) {
            assertTrue(error.hasCause<SQLiteCantOpenDatabaseException>())
            assertFalse(error.hasCause<SQLiteDatabaseCorruptException>())
        } finally {
            deleteDatabaseSet(missingDatabase)
        }
    }

    private fun prepareHealthyClosedDatabase(): File {
        AppDatabase.getDatabase(context).openHelper.writableDatabase
        AppDatabase.closeDatabase()
        RoomRecoveryStorage.invalidatePreparedState()
        return context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME).also { databaseFile ->
            check(databaseFile.isFile)
            check(RoomRecoveryStorage.validateDatabaseSet(context, databaseFile))
        }
    }

    private fun copyHealthyDatabaseToCache(databaseFile: File): File {
        val copy = File(context.cacheDir, "room_healthy_${UUID.randomUUID()}.db")
        databaseFile.copyTo(copy, overwrite = false)
        return copy
    }

    private fun restoreHealthyDatabase(databaseFile: File, healthyCopy: File) {
        AppDatabase.closeDatabase()
        deleteDatabaseSet(databaseFile)
        healthyCopy.copyTo(databaseFile, overwrite = false)
        check(healthyCopy.delete())
        RoomRecoveryStorage.invalidatePreparedState()
    }

    private suspend fun deleteChatMarker(markerId: String) {
        try {
            AppDatabase.getDatabase(context).chatDao().deleteChat(markerId)
        } finally {
            AppDatabase.closeDatabase()
            RoomRecoveryStorage.invalidatePreparedState()
        }
    }

    private fun findNewRoomQuarantine(root: File, before: Set<String>): File =
        root.listFiles().orEmpty().single { candidate ->
            candidate.name.startsWith("room_") && candidate.name !in before
        }

    private fun recoveryEventIds(): Set<String> = recoveryEvents().map { it.id }.toSet()

    private fun assertNewRoomEvent(before: Set<String>, kind: String, action: String) {
        val newRoomEvents =
            recoveryEvents().filter { event ->
                event.id !in before &&
                    event.storage == RoomRecoveryStorage.DATABASE_NAME
            }
        assertEquals(1, newRoomEvents.size)
        val event = newRoomEvents.single()
        assertEquals(kind, event.kind)
        assertEquals(action, event.action)
    }

    private fun recoveryEvents(): List<RecoveryEventObservation> {
        val eventFile = File(context.noBackupFilesDir, "storage-recovery/events.json")
        if (!eventFile.isFile) return emptyList()
        return Json.parseToJsonElement(eventFile.readText()).jsonArray.map { element ->
            val event = element.jsonObject
            RecoveryEventObservation(
                id = checkNotNull(event["id"]).jsonPrimitive.content,
                storage = checkNotNull(event["storage"]).jsonPrimitive.content,
                kind = checkNotNull(event["kind"]).jsonPrimitive.content,
                action = checkNotNull(event["action"]).jsonPrimitive.content
            )
        }
    }

    private fun newestSnapshotSlot(snapshotRoot: File): SnapshotSlot =
        (0..1)
            .map { slot ->
                val metadataFile = File(snapshotRoot, "app_database.$slot.json")
                val metadata = Json.parseToJsonElement(metadataFile.readText()).jsonObject
                SnapshotSlot(
                    databaseFile = File(snapshotRoot, "app_database.$slot.db"),
                    metadataFile = metadataFile,
                    sequence = checkNotNull(metadata["sequence"]).jsonPrimitive.long
                )
            }
            .maxBy { it.sequence }

    private fun rewriteSnapshotIntegrity(slot: SnapshotSlot, payload: ByteArray) {
        val metadata = Json.parseToJsonElement(slot.metadataFile.readText()).jsonObject
        val rewritten =
            buildJsonObject {
                metadata.forEach { (key, value) ->
                    when (key) {
                        "size" -> put(key, JsonPrimitive(payload.size.toLong()))
                        "sha256" -> put(key, JsonPrimitive(sha256(payload)))
                        else -> put(key, value)
                    }
                }
            }
        slot.metadataFile.writeText(rewritten.toString())
    }

    private fun deleteDatabaseSet(databaseFile: File) {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val candidate = File(databaseFile.absolutePath + suffix)
            if (candidate.exists()) check(candidate.deleteRecursively())
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            val cause = current.cause
            if (cause === current) break
            current = cause
        }
        return false
    }
}
