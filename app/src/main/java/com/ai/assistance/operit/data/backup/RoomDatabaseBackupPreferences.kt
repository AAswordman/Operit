package com.ai.assistance.operit.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.databaseBackupDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "database_backup_settings")

class RoomDatabaseBackupPreferences private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: RoomDatabaseBackupPreferences? = null

        fun getInstance(context: Context): RoomDatabaseBackupPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = RoomDatabaseBackupPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private const val DEFAULT_ENABLE_DAILY_BACKUP = true
        const val DEFAULT_MAX_BACKUP_COUNT = 10

        private val KEY_ENABLE_DAILY_BACKUP = booleanPreferencesKey("enable_daily_room_db_backup")
        private val KEY_LAST_BACKUP_DAY = stringPreferencesKey("room_db_backup_last_day")
        private val KEY_LAST_SUCCESS_TIME = longPreferencesKey("room_db_backup_last_success_time")
        private val KEY_LAST_ERROR = stringPreferencesKey("room_db_backup_last_error")
        private val KEY_MAX_BACKUP_COUNT = intPreferencesKey("room_db_backup_max_count")
    }

    val enableDailyBackupFlow: Flow<Boolean> =
        context.databaseBackupDataStore.data.map { it[KEY_ENABLE_DAILY_BACKUP] ?: DEFAULT_ENABLE_DAILY_BACKUP }

    val lastBackupDayFlow: Flow<String?> =
        context.databaseBackupDataStore.data.map { it[KEY_LAST_BACKUP_DAY] }

    val lastSuccessTimeFlow: Flow<Long> =
        context.databaseBackupDataStore.data.map { it[KEY_LAST_SUCCESS_TIME] ?: 0L }

    val lastErrorFlow: Flow<String> =
        context.databaseBackupDataStore.data.map { it[KEY_LAST_ERROR] ?: "" }

    val maxBackupCountFlow: Flow<Int> =
        context.databaseBackupDataStore.data.map { it[KEY_MAX_BACKUP_COUNT] ?: DEFAULT_MAX_BACKUP_COUNT }

    suspend fun isDailyBackupEnabled(): Boolean = enableDailyBackupFlow.first()

    suspend fun setDailyBackupEnabled(enabled: Boolean) {
        context.databaseBackupDataStore.edit { it[KEY_ENABLE_DAILY_BACKUP] = enabled }
    }

    suspend fun markSuccess(day: String, timestamp: Long) {
        context.databaseBackupDataStore.edit {
            it[KEY_LAST_BACKUP_DAY] = day
            it[KEY_LAST_SUCCESS_TIME] = timestamp
            it[KEY_LAST_ERROR] = ""
        }
    }

    suspend fun markFailure(error: String) {
        context.databaseBackupDataStore.edit { it[KEY_LAST_ERROR] = error }
    }

    suspend fun getMaxBackupCount(): Int = maxBackupCountFlow.first()

    suspend fun setMaxBackupCount(count: Int) {
        val safe = count.coerceIn(1, 100)
        context.databaseBackupDataStore.edit { it[KEY_MAX_BACKUP_COUNT] = safe }
    }
}
