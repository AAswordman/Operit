package com.ai.assistance.operit.data.persistence

import android.content.Context
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.UUID

class StorageRecoveryException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

object StorageRecoveryCoordinator {
    private const val TAG = "StorageRecovery"

    suspend fun recoverPreferences(context: Context) {
        val appContext = context.applicationContext
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
        if (userPreferencesManager.repairPersistedState()) {
            repairedStoreCount++
        }
        if (ApiPreferences.getInstance(appContext).repairPersistedState()) repairedStoreCount++
        if (SpeechServicesPreferences(appContext).repairPersistedState()) repairedStoreCount++

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
     * A raw restore replaces the complete published storage epoch. Recovery slots from the state
     * that existed before the restore must not be allowed to overwrite an intentionally restored
     * file, so retain them for manual rescue and start fresh slots for the imported state.
     */
    @Synchronized
    fun archiveSnapshotsBeforeRawRestoreRecovery(context: Context) {
        val appContext = context.applicationContext
        val root = File(appContext.noBackupFilesDir, "storage-recovery")
        val sources =
            listOf("preferences", "room", "objectbox")
                .map { name -> File(root, name) }
                .filter { it.exists() }
        if (sources.isEmpty()) return

        val archive =
            File(
                File(root, "quarantine"),
                "recovery_epoch_${System.currentTimeMillis()}_${UUID.randomUUID()}"
            )
        check(archive.mkdirs()) { "Failed to create recovery epoch archive" }
        sources.forEach { source ->
            val target = File(archive, source.name)
            check(source.renameTo(target)) {
                "Failed to archive recovery snapshots: ${source.name}"
            }
        }
        PreferenceRecoveryStorage.recordStorageEvent(
            appContext,
            "raw_snapshot",
            "recovery_epoch",
            "archived_previous_snapshots"
        )
    }
}
