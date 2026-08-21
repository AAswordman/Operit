package com.ai.assistance.operit.data.persistence

import android.content.Context
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageRecoveryAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyRecoverablePreferenceStoreRecoversCorruptProtobuf() = runBlocking {
        RecoverablePreferenceDataStores.checkpointKnownStores(context)
        PreferenceStoreCatalog.recoverable.forEach { storeName ->
            val markerKey = stringPreferencesKey("storage_recovery_fault_marker")
            val markerValue = "verified:$storeName"
            RecoverablePreferenceDataStores.get(context, storeName).edit { preferences ->
                preferences[markerKey] = markerValue
            }
            RecoverablePreferenceDataStores.closeAllAndAwait()
            StorageRecoveryFaultFixtures.corruptPreferenceStore(context, storeName)

            val recovered = RecoverablePreferenceDataStores.get(context, storeName).data.first()

            assertTrue(context.preferencesDataStoreFile(storeName).isFile)
            assertEquals(markerValue, recovered[markerKey])

            RecoverablePreferenceDataStores.closeAllAndAwait()
            assertTrue(context.preferencesDataStoreFile(storeName).delete())
            val restoredMissing =
                RecoverablePreferenceDataStores.get(context, storeName).data.first()
            assertEquals(markerValue, restoredMissing[markerKey])
        }
        RecoverablePreferenceDataStores.checkpointKnownStores(context)
    }

    @Test
    fun preferenceStoreRecoversWhenItsFilePathWasReplacedByADirectory() = runBlocking {
        val storeName = PreferenceStoreCatalog.UI_PREFERENCES
        val markerKey = stringPreferencesKey("storage_recovery_invalid_path_marker")
        val markerValue = "verified-directory-recovery"
        RecoverablePreferenceDataStores.get(context, storeName).edit { preferences ->
            preferences[markerKey] = markerValue
        }
        RecoverablePreferenceDataStores.closeAllAndAwait()
        StorageRecoveryFaultFixtures.replacePreferenceStoreWithDirectory(context, storeName)

        val recovered = RecoverablePreferenceDataStores.get(context, storeName).data.first()

        assertTrue(context.preferencesDataStoreFile(storeName).isFile)
        assertEquals(markerValue, recovered[markerKey])
    }

    @Test
    fun cachedPreferenceHandleRebindsAfterActorClose() = runBlocking {
        val storeName = PreferenceStoreCatalog.DISPLAY_PREFERENCES
        val markerKey = stringPreferencesKey("storage_recovery_rebind_marker")
        val store = RecoverablePreferenceDataStores.get(context, storeName)
        store.edit { preferences -> preferences[markerKey] = "before-close" }

        RecoverablePreferenceDataStores.closeAllAndAwait()
        store.edit { preferences -> preferences[markerKey] = "after-close" }

        assertEquals("after-close", store.data.first()[markerKey])
    }

    @Test
    fun managedOnlyTokenStoreRebindsWithoutPublishingRecoverySlots() = runBlocking {
        val storeName = PreferenceStoreCatalog.TOKEN_STATS
        val markerKey = stringPreferencesKey("managed_store_rebind_marker")
        val recoveryDirectory =
            File(context.noBackupFilesDir, "storage-recovery/preferences")
        RecoverablePreferenceDataStores.closeAllAndAwait()
        listOf(0, 1).forEach { slot ->
            val snapshot = File(recoveryDirectory, "$storeName.$slot.json")
            if (snapshot.exists()) assertTrue(snapshot.delete())
        }

        val store = RecoverablePreferenceDataStores.get(context, storeName)
        store.edit { preferences -> preferences[markerKey] = "before-close" }
        RecoverablePreferenceDataStores.closeAllAndAwait()
        store.edit { preferences -> preferences[markerKey] = "after-close" }
        RecoverablePreferenceDataStores.checkpointKnownStores(context)

        assertEquals("after-close", store.data.first()[markerKey])
        listOf(0, 1).forEach { slot ->
            assertFalse(File(recoveryDirectory, "$storeName.$slot.json").exists())
        }
    }

    @Test
    fun roomRestoresVerifiedSnapshotAfterPhysicalCorruption() {
        AppDatabase.getDatabase(context).openHelper.writableDatabase
        AppDatabase.closeDatabase()
        StorageRecoveryFaultFixtures.corruptRoomDatabase(context)

        RoomRecoveryStorage.prepareForOpen(context)

        assertTrue(
            RoomRecoveryStorage.validateDatabaseSet(
                context,
                context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
            )
        )

        val databaseFile = context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val file = File(databaseFile.absolutePath + suffix)
            if (file.exists()) assertTrue(file.delete())
        }
        RoomRecoveryStorage.checkpointClosed(context)
        RoomRecoveryStorage.prepareForOpen(context)
        assertTrue(RoomRecoveryStorage.validateDatabaseSet(context, databaseFile))
    }

    @Test
    fun roomRecoversWhenItsDatabasePathWasReplacedByADirectory() {
        AppDatabase.getDatabase(context).openHelper.writableDatabase
        AppDatabase.closeDatabase()
        val quarantineRoot =
            File(context.noBackupFilesDir, "storage-recovery/quarantine")
        val quarantineBefore = quarantineRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
        val databaseFile = StorageRecoveryFaultFixtures.replaceRoomDatabaseWithDirectory(context)

        RoomRecoveryStorage.prepareForOpen(context)

        assertTrue(databaseFile.isFile)
        assertTrue(RoomRecoveryStorage.validateDatabaseSet(context, databaseFile))
        val newQuarantine =
            quarantineRoot.listFiles().orEmpty().single {
                it.name.startsWith("room_") && it.name !in quarantineBefore
            }
        assertTrue(File(newQuarantine, "app_database/unexpected-remnant").isFile)
        assertTrue(
            File(newQuarantine, "app_database-wal/unexpected-sidecar-remnant").isFile
        )
    }

    @Test
    fun roomRemovesAQuarantinedDirectoryFromItsWalPath() {
        AppDatabase.getDatabase(context).openHelper.writableDatabase
        AppDatabase.closeDatabase()
        val databaseFile = context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
        val quarantineRoot = File(context.noBackupFilesDir, "storage-recovery/quarantine")
        val quarantineBefore = quarantineRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
        StorageRecoveryFaultFixtures.replaceRoomWalWithDirectory(context)

        RoomRecoveryStorage.prepareForOpen(context)

        assertTrue(RoomRecoveryStorage.validateDatabaseSet(context, databaseFile))
        val newQuarantine =
            quarantineRoot.listFiles().orEmpty().single {
                it.name.startsWith("room_") && it.name !in quarantineBefore
            }
        assertTrue(
            File(newQuarantine, "app_database-wal/unexpected-sidecar-remnant").isFile
        )
    }

    @Test
    fun roomStagesAValidatedSnapshotWithoutClosingTheLiveDatabase() {
        AppDatabase.getDatabase(context).openHelper.writableDatabase
        val snapshot = AppDatabase.stageForSnapshotExport(context)
        val stagedFile = snapshot.files.single().file
        try {
            assertTrue(AppDatabase.getDatabase(context).isOpen)
            assertTrue(stagedFile.isFile)
            assertTrue(RoomRecoveryStorage.validateDatabaseSet(context, stagedFile))
        } finally {
            snapshot.close()
            assertFalse(stagedFile.exists())
            AppDatabase.closeDatabase()
        }
    }

    @Test
    fun objectBoxRestoresOnlyTheCorruptProfile() {
        val profileId = "storage_recovery_fixture"
        try {
            ObjectBoxManager.get(context, profileId).validate(0L, true)
            ObjectBoxManager.close(profileId)
            StorageRecoveryFaultFixtures.corruptObjectBoxProfile(context, profileId)
            ObjectBoxManager.invalidatePreparedState()

            ObjectBoxManager.preflightAll(context)
            ObjectBoxManager.get(context, profileId).validate(0L, true)
            ObjectBoxManager.close(profileId)
            val dataFile =
                File(File(context.filesDir, "objectbox_$profileId"), "data.mdb")
            assertTrue(dataFile.delete())
            ObjectBoxManager.invalidatePreparedState()
            ObjectBoxManager.preflightAll(context)
            ObjectBoxManager.get(context, profileId).validate(0L, true)
        } finally {
            ObjectBoxManager.delete(context, profileId)
        }
    }

    @Test
    fun objectBoxRecoversWhenItsDataPathWasReplacedByADirectory() {
        val profileId = "storage_recovery_invalid_path_fixture"
        try {
            ObjectBoxManager.get(context, profileId).validate(0L, true)
            ObjectBoxManager.close(profileId)
            StorageRecoveryFaultFixtures.replaceObjectBoxDataWithDirectory(context, profileId)

            ObjectBoxManager.preflightAll(context)

            ObjectBoxManager.get(context, profileId).validate(0L, true)
            val dataFile = File(File(context.filesDir, "objectbox_$profileId"), "data.mdb")
            assertTrue(dataFile.isFile)
        } finally {
            ObjectBoxManager.delete(context, profileId)
        }
    }

    @Test
    fun objectBoxRemovesAQuarantinedDirectoryFromItsLockPath() {
        val profileId = "storage_recovery_invalid_lock_fixture"
        try {
            ObjectBoxManager.get(context, profileId).validate(0L, true)
            ObjectBoxManager.close(profileId)
            val quarantineRoot = File(context.noBackupFilesDir, "storage-recovery/quarantine")
            val quarantineBefore = quarantineRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
            StorageRecoveryFaultFixtures.replaceObjectBoxLockWithDirectory(context, profileId)

            ObjectBoxManager.preflightAll(context)

            ObjectBoxManager.get(context, profileId).validate(0L, true)
            val newQuarantine =
                quarantineRoot.listFiles().orEmpty().single {
                    it.name.startsWith("objectbox_") && it.name !in quarantineBefore
                }
            assertTrue(File(newQuarantine, "lock.mdb/unexpected-lock-remnant").isFile)
        } finally {
            ObjectBoxManager.delete(context, profileId)
        }
    }

    @Test
    fun objectBoxStagesAValidatedSnapshotWithoutClosingTheLiveStore() {
        val profileId = "storage_recovery_snapshot_fixture"
        var snapshot: ObjectBoxManager.SnapshotExport? = null
        var stagedFile: File? = null
        try {
            val liveStore = ObjectBoxManager.get(context, profileId)
            liveStore.validate(0L, true)
            val stagedSnapshot = ObjectBoxManager.stageAllForSnapshotExport(context)
            snapshot = stagedSnapshot
            val staged =
                stagedSnapshot.files.single {
                    it.relativePath == "objectbox_$profileId/data.mdb"
                }.file
            stagedFile = staged

            assertFalse(liveStore.isClosed)
            assertTrue(staged.isFile)
            assertTrue(
                ObjectBoxManager.validateRecoveryDirectory(
                    context,
                    requireNotNull(staged.parentFile)
                )
            )
        } finally {
            snapshot?.close()
            assertFalse(stagedFile?.exists() == true)
            ObjectBoxManager.delete(context, profileId)
        }
    }

    @Test
    fun providerWriteIsRejectedWhileMainProcessOwnsStorage() {
        assertTrue(StorageProcessLock.mainProcessOwnsStorage())
        val authority = "${context.packageName}.documents.data"
        val root = DocumentsContract.buildDocumentUri(authority, "/")
        val displayName = "storage_lock_probe_${System.nanoTime()}"
        val unexpectedFile = File(context.applicationInfo.dataDir, displayName)
        try {
            DocumentsContract.createDocument(
                context.contentResolver,
                root,
                "text/plain",
                displayName
            )
            fail("Provider write unexpectedly bypassed the main-process storage lease")
        } catch (_: Exception) {
            assertFalse(unexpectedFile.exists())
        } finally {
            if (unexpectedFile.exists()) unexpectedFile.delete()
        }
    }

    @Test
    fun rawReplacementGateRejectsOrdinaryOwnerReopen() = runBlocking {
        val replacement = StorageReplacementGate.acquire()
        try {
            try {
                RecoverablePreferenceDataStores.get(
                    context,
                    PreferenceStoreCatalog.API_SETTINGS
                )
                fail("Preferences owner reopened outside the raw replacement coroutine")
            } catch (_: IllegalStateException) {
            }

            replacement.withAccess {
                RecoverablePreferenceDataStores.get(
                    context,
                    PreferenceStoreCatalog.API_SETTINGS
                ).data.first()
            }
        } finally {
            replacement.close()
        }
    }
}
