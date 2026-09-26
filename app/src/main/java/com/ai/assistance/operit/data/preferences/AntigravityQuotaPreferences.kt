package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.api.AntigravityQuotaSnapshot
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.antigravityQuotaDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "antigravity_quota_preferences")

@Serializable
data class AntigravityStoredQuotaSnapshot(
    val projectId: String,
    val quota: AntigravityQuotaSnapshot,
    val fetchedAtMillis: Long,
)

class AntigravityQuotaPreferences private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val snapshotFlow: Flow<AntigravityStoredQuotaSnapshot?> =
        appContext.antigravityQuotaDataStore.data.map { preferences ->
            val encoded = preferences[SNAPSHOT_KEY] ?: return@map null
            try {
                json.decodeFromString<AntigravityStoredQuotaSnapshot>(encoded)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to decode persisted Antigravity quota snapshot", error)
                null
            }
        }

    suspend fun save(projectId: String, quota: AntigravityQuotaSnapshot) {
        val snapshot = AntigravityStoredQuotaSnapshot(
            projectId = projectId,
            quota = quota,
            fetchedAtMillis = System.currentTimeMillis(),
        )
        appContext.antigravityQuotaDataStore.edit { preferences ->
            preferences[SNAPSHOT_KEY] = json.encodeToString(snapshot)
        }
    }

    companion object {
        private const val TAG = "AntigravityQuotaPreferences"
        private val SNAPSHOT_KEY = stringPreferencesKey("latest_snapshot")

        @Volatile
        private var instance: AntigravityQuotaPreferences? = null

        fun getInstance(context: Context): AntigravityQuotaPreferences {
            return instance ?: synchronized(this) {
                instance ?: AntigravityQuotaPreferences(context).also { instance = it }
            }
        }
    }
}
