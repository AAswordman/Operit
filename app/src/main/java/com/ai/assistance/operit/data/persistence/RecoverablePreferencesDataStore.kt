package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ai.assistance.operit.util.AppLogger
import kotlin.properties.ReadOnlyProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun recoverablePreferencesDataStore(
    name: String
): ReadOnlyProperty<Context, DataStore<Preferences>> {
    require(name in PreferenceStoreCatalog.all) { "Unregistered Preferences DataStore: $name" }
    return ReadOnlyProperty { context, _ -> RecoverablePreferenceDataStores.get(context, name) }
}

object RecoverablePreferenceDataStores {
    private const val TAG = "RecoverableDataStore"
    private val lock = Any()
    private val holders = mutableMapOf<String, Holder>()
    private val stableStores = mutableMapOf<String, RebindingDataStore>()

    private data class Holder(
        val scope: CoroutineScope,
        val store: SnapshottingDataStore
    )

    private data class PendingSourceRecovery(
        val issue: PreferenceSourceIssue,
        val snapshot: Preferences?
    )

    fun get(context: Context, name: String): DataStore<Preferences> {
        require(name in PreferenceStoreCatalog.all) { "Unregistered Preferences DataStore: $name" }
        val appContext = context.applicationContext
        return StorageReplacementGate.withStorageAccess {
            synchronized(lock) {
                stableStores[name]
                    ?: RebindingDataStore(appContext, name).also { store ->
                        stableStores[name] = store
                    }
            }
        }
    }

    private fun getHolder(context: Context, name: String): Holder {
        require(name in PreferenceStoreCatalog.all) { "Unregistered Preferences DataStore: $name" }
        val appContext = context.applicationContext
        return StorageReplacementGate.withStorageAccess {
            synchronized(lock) {
                holders[name]
                    ?: createStore(appContext, name).also { holder -> holders[name] = holder }
            }
        }
    }

    suspend fun preflightKnownStores(context: Context): List<String> {
        val failed = mutableListOf<String>()
        PreferenceStoreCatalog.all.forEach { name ->
            try {
                get(context, name).data.first()
            } catch (e: Exception) {
                failed += name
                AppLogger.e(TAG, "Preferences preflight failed for $name", e)
            }
        }
        return failed
    }

    suspend fun checkpointKnownStores(context: Context) {
        PreferenceStoreCatalog.all.forEach { name ->
            try {
                getHolder(context, name).store.checkpointCurrentState()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Preferences checkpoint failed for $name", e)
            }
        }
    }

    suspend fun checkpoint(context: Context, name: String, preferences: Preferences) {
        withContext(Dispatchers.IO) {
            StorageReplacementGate.withStorageAccess {
                PreferenceRecoveryStorage.checkpoint(
                    context.applicationContext,
                    name,
                    preferences
                )
            }
        }
    }

    suspend fun quarantineLogicalState(
        context: Context,
        name: String,
        preferences: Preferences,
        issueKeys: Collection<String>
    ) {
        withContext(Dispatchers.IO) {
            PreferenceRecoveryStorage.quarantineLogicalState(
                context.applicationContext,
                name,
                preferences,
                issueKeys
            )
        }
    }

    fun closeAll() {
        val scopes = synchronized(lock) {
            val result = holders.values.map { it.scope }
            holders.clear()
            result
        }
        scopes.forEach { it.cancel() }
    }

    suspend fun closeAllAndAwait() {
        val jobs = synchronized(lock) {
            val result = holders.values.mapNotNull { it.scope.coroutineContext[Job] }
            holders.clear()
            result
        }
        jobs.forEach { it.cancelAndJoin() }
    }

    private fun createStore(context: Context, name: String): Holder {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sourceFile = context.preferencesDataStoreFile(name)
        val pendingSourceRecovery =
            when {
                !sourceFile.exists() ->
                    PreferenceRecoveryStorage.readSnapshotForMissingFile(context, name)
                        ?.let { snapshot ->
                            PendingSourceRecovery(
                                issue = PreferenceSourceIssue.MISSING_FILE,
                                snapshot = snapshot
                            )
                        }
                !sourceFile.isFile ->
                    PendingSourceRecovery(
                        issue = PreferenceSourceIssue.INVALID_PATH,
                        snapshot =
                            PreferenceRecoveryStorage.prepareInvalidSourcePathRecovery(
                                context,
                                name,
                                sourceFile
                            )
                    )
                else -> null
            }
        val sourceRecoveryMigration: DataMigration<Preferences>? =
            pendingSourceRecovery?.let { recovery ->
                object : DataMigration<Preferences> {
                    private var migrated = false

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean = true

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        migrated = true
                        return recovery.snapshot ?: currentData
                    }

                    override suspend fun cleanUp() {
                        if (migrated) {
                            // cleanUp runs only after DataStore commits the migration. Recording
                            // earlier could report a recovery that never became the live state.
                            PreferenceRecoveryStorage.recordSourceRecovery(
                                context = context,
                                storeName = name,
                                issue = recovery.issue,
                                restoredSnapshot = recovery.snapshot != null
                            )
                        }
                    }
                }
            }
        val delegate =
            PreferenceDataStoreFactory.create(
                corruptionHandler =
                    ReplaceFileCorruptionHandler { exception ->
                        PreferenceRecoveryStorage.recoverFromCorruption(
                            context,
                            name,
                            sourceFile,
                            exception
                        )
                    },
                migrations = listOfNotNull(sourceRecoveryMigration),
                scope = scope,
                produceFile = { sourceFile }
            )
        return Holder(scope, SnapshottingDataStore(context, name, delegate))
    }

    private class RebindingDataStore(
        private val context: Context,
        private val name: String
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> =
            flow {
                emitAll(getHolder(context, name).store.data)
            }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = getHolder(context, name).store.updateData(transform)
    }

    private class SnapshottingDataStore(
        private val context: Context,
        private val name: String,
        private val delegate: DataStore<Preferences>
    ) : DataStore<Preferences> {
        private val updateMutex = Mutex()

        override val data: Flow<Preferences> = delegate.data

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = updateMutex.withLock {
            val updated = delegate.updateData(transform)
            try {
                checkpoint(context, name, updated)
            } catch (e: Exception) {
                // The DataStore commit already succeeded; snapshot failure must remain observable
                // without turning a valid user update into a reported write failure.
                AppLogger.e(TAG, "Failed to checkpoint $name after update", e)
            }
            updated
        }

        suspend fun checkpointCurrentState() = updateMutex.withLock {
            checkpoint(context, name, delegate.data.first())
        }
    }
}
