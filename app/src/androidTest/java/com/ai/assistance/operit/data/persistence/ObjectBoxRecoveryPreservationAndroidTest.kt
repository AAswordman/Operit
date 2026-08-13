package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import io.objectbox.kotlin.boxFor
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObjectBoxRecoveryPreservationAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private data class SnapshotSlot(
        val dataFile: File,
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
    fun corruptLiveIsQuarantinedBeforeMarkerSnapshotRestoresIt() {
        val profileId = uniqueProfileId("restore")
        val markerUuid = "objectbox_recovery_marker_${UUID.randomUUID()}"
        val markerContent = "verified ObjectBox recovery marker"
        val corruptPayload = "operit-storage-corruption-fixture".toByteArray()
        val quarantineBefore = quarantineNames()
        val eventIdsBefore = recoveryEventIds()

        try {
            prepareMarkerAndDualSlots(profileId, markerUuid, markerContent)
            val dataFile = liveDataFile(profileId)
            dataFile.writeBytes(corruptPayload)
            ObjectBoxManager.invalidatePreparedState()

            ObjectBoxManager.preflightAll(context)

            val recoveredStore = ObjectBoxManager.get(context, profileId)
            val recoveredMarker =
                recoveredStore.boxFor<Memory>().all.single { memory ->
                    memory.uuid == markerUuid
                }
            assertEquals(markerContent, recoveredMarker.content)
            ObjectBoxManager.close(profileId)

            val quarantine = findNewProfileQuarantine(profileId, quarantineBefore)
            assertArrayEquals(corruptPayload, File(quarantine, "data.mdb").readBytes())
            assertNewObjectBoxEvent(
                eventIdsBefore,
                "objectbox_corruption",
                "restored_snapshot"
            )
        } finally {
            cleanupTestProfile(profileId, heldSnapshotDirectory = null)
        }
    }

    @Test
    fun corruptLiveWithoutSlotsRemainsAvailableAndIsQuarantined() {
        val profileId = uniqueProfileId("preserve")
        val markerUuid = "objectbox_preservation_marker_${UUID.randomUUID()}"
        val markerContent = "marker retained only in held ObjectBox slots"
        val corruptPayload = "operit-storage-corruption-fixture".toByteArray()
        val snapshotDirectory = recoveryProfileDirectory(profileId)
        val heldSnapshotDirectory =
            File(
                File(context.noBackupFilesDir, "storage-recovery"),
                "objectbox_test_hold_${UUID.randomUUID()}"
            )
        val quarantineBefore = quarantineNames()
        val eventIdsBefore = recoveryEventIds()
        var snapshotsHeld = false

        try {
            prepareMarkerAndDualSlots(profileId, markerUuid, markerContent)
            assertDualSlots(snapshotDirectory)
            check(snapshotDirectory.renameTo(heldSnapshotDirectory))
            snapshotsHeld = true

            val dataFile = liveDataFile(profileId)
            dataFile.writeBytes(corruptPayload)
            ObjectBoxManager.invalidatePreparedState()

            try {
                ObjectBoxManager.preflightAll(context)
                fail("Corrupt ObjectBox profile unexpectedly opened without a verified snapshot")
            } catch (_: StorageRecoveryException) {
                assertArrayEquals(corruptPayload, dataFile.readBytes())
            }

            val quarantine = findNewProfileQuarantine(profileId, quarantineBefore)
            assertArrayEquals(corruptPayload, File(quarantine, "data.mdb").readBytes())
            assertNewObjectBoxEvent(
                eventIdsBefore,
                "objectbox_corruption",
                "preserved_without_snapshot"
            )
        } finally {
            cleanupTestProfile(
                profileId,
                heldSnapshotDirectory.takeIf { snapshotsHeld }
            )
        }
    }

    @Test
    fun corruptNewestSnapshotDoesNotBlockOlderVerifiedSlotOrDeleteIt() {
        val profileId = uniqueProfileId("older_slot")
        val markerUuid = "objectbox_older_slot_marker_${UUID.randomUUID()}"
        val markerContent = "marker retained in both ObjectBox recovery slots"
        val corruptLivePayload = "objectbox-live-corruption-with-corrupt-newest-slot".toByteArray()
        val corruptSlotPayload = "objectbox-newest-slot-corruption".toByteArray()
        val quarantineBefore = quarantineNames()
        val eventIdsBefore = recoveryEventIds()

        try {
            prepareMarkerAndDualSlots(profileId, markerUuid, markerContent)
            refreshDualSlots(profileId)
            val newestSlot = newestSnapshotSlot(recoveryProfileDirectory(profileId))
            newestSlot.dataFile.writeBytes(corruptSlotPayload)
            rewriteSnapshotIntegrity(newestSlot, corruptSlotPayload)
            liveDataFile(profileId).writeBytes(corruptLivePayload)
            ObjectBoxManager.invalidatePreparedState()

            ObjectBoxManager.preflightAll(context)

            val recoveredStore = ObjectBoxManager.get(context, profileId)
            val recoveredMarker =
                recoveredStore.boxFor<Memory>().all.single { memory ->
                    memory.uuid == markerUuid
                }
            assertEquals(markerContent, recoveredMarker.content)
            assertArrayEquals(corruptSlotPayload, newestSlot.dataFile.readBytes())

            val quarantine = findNewProfileQuarantine(profileId, quarantineBefore)
            assertArrayEquals(corruptLivePayload, File(quarantine, "data.mdb").readBytes())
            assertNewObjectBoxEvent(
                eventIdsBefore,
                "objectbox_corruption",
                "restored_snapshot"
            )
        } finally {
            cleanupTestProfile(profileId, heldSnapshotDirectory = null)
        }
    }

    private fun prepareMarkerAndDualSlots(
        profileId: String,
        markerUuid: String,
        markerContent: String
    ) {
        val store = ObjectBoxManager.get(context, profileId)
        store.boxFor<Memory>().put(
            Memory(
                uuid = markerUuid,
                title = markerUuid,
                content = markerContent,
                source = "storage_recovery_test"
            )
        )
        ObjectBoxManager.close(profileId)
        ObjectBoxManager.invalidatePreparedState()
        assertDualSlots(recoveryProfileDirectory(profileId))
    }

    private fun assertDualSlots(directory: File) {
        assertTrue(directory.isDirectory)
        (0..1).forEach { slot ->
            assertTrue(File(directory, "data.$slot.mdb").isFile)
            assertTrue(File(directory, "data.$slot.json").isFile)
        }
    }

    private fun refreshDualSlots(profileId: String) {
        ObjectBoxManager.get(context, profileId)
        ObjectBoxManager.close(profileId)
        ObjectBoxManager.invalidatePreparedState()
        assertDualSlots(recoveryProfileDirectory(profileId))
    }

    private fun newestSnapshotSlot(directory: File): SnapshotSlot =
        (0..1)
            .map { slot ->
                val metadataFile = File(directory, "data.$slot.json")
                val metadata = Json.parseToJsonElement(metadataFile.readText()).jsonObject
                SnapshotSlot(
                    dataFile = File(directory, "data.$slot.mdb"),
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

    private fun cleanupTestProfile(profileId: String, heldSnapshotDirectory: File?) {
        try {
            ObjectBoxManager.close(profileId)
        } finally {
            ObjectBoxManager.invalidatePreparedState()
            deleteIfPresent(liveProfileDirectory(profileId))
            deleteIfPresent(recoveryProfileDirectory(profileId))
            heldSnapshotDirectory?.let(::deleteIfPresent)
            profileQuarantines(profileId).forEach(::deleteIfPresent)
        }
    }

    private fun findNewProfileQuarantine(profileId: String, before: Set<String>): File =
        profileQuarantines(profileId).single { quarantine -> quarantine.name !in before }

    private fun profileQuarantines(profileId: String): List<File> {
        val prefix = "objectbox_${safeProfileName(profileId)}_"
        return quarantineRoot().listFiles().orEmpty().filter { candidate ->
            candidate.isDirectory && candidate.name.startsWith(prefix)
        }
    }

    private fun quarantineNames(): Set<String> =
        quarantineRoot().listFiles()?.map { it.name }?.toSet().orEmpty()

    private fun recoveryEventIds(): Set<String> = recoveryEvents().map { it.id }.toSet()

    private fun assertNewObjectBoxEvent(before: Set<String>, kind: String, action: String) {
        val matching =
            recoveryEvents().filter { event ->
                event.id !in before &&
                    event.storage == "objectbox" &&
                    event.kind == kind &&
                    event.action == action
            }
        assertEquals(1, matching.size)
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

    private fun uniqueProfileId(scenario: String): String =
        "objectbox_${scenario}_${UUID.randomUUID().toString().replace("-", "")}"

    private fun liveProfileDirectory(profileId: String): File =
        File(context.filesDir, "objectbox_$profileId")

    private fun liveDataFile(profileId: String): File =
        File(liveProfileDirectory(profileId), "data.mdb")

    private fun recoveryProfileDirectory(profileId: String): File =
        File(
            File(context.noBackupFilesDir, "storage-recovery/objectbox"),
            safeProfileName(profileId)
        )

    private fun quarantineRoot(): File =
        File(context.noBackupFilesDir, "storage-recovery/quarantine")

    private fun safeProfileName(profileId: String): String {
        val readable = profileId.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(64)
        val hash =
            MessageDigest.getInstance("SHA-256")
                .digest(profileId.toByteArray(Charsets.UTF_8))
                .take(6)
                .joinToString("") { byte -> "%02x".format(byte) }
        return "${readable}_$hash"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun deleteIfPresent(file: File) {
        if (file.exists()) check(file.deleteRecursively())
    }
}
