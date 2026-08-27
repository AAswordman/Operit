package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.api.CodexUsageSnapshot
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.codexUsageDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "codex_usage_preferences")

@Serializable
data class CodexStoredUsageSnapshot(
    val accountId: String,
    val usage: CodexUsageSnapshot,
    val fetchedAtMillis: Long,
)

class CodexUsagePreferences private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val snapshotFlow: Flow<CodexStoredUsageSnapshot?> =
        appContext.codexUsageDataStore.data.map { preferences ->
            val encoded = preferences[SNAPSHOT_KEY] ?: return@map null
            try {
                json.decodeFromString<CodexStoredUsageSnapshot>(encoded)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to decode persisted Codex usage snapshot", error)
                null
            }
        }

    suspend fun save(accountId: String, usage: CodexUsageSnapshot) {
        val snapshot = CodexStoredUsageSnapshot(
            accountId = accountId,
            usage = usage,
            fetchedAtMillis = System.currentTimeMillis(),
        )
        appContext.codexUsageDataStore.edit { preferences ->
            preferences[SNAPSHOT_KEY] = json.encodeToString(snapshot)
        }
    }

    companion object {
        private const val TAG = "CodexUsagePreferences"
        private val SNAPSHOT_KEY = stringPreferencesKey("latest_snapshot")

        @Volatile
        private var instance: CodexUsagePreferences? = null

        fun getInstance(context: Context): CodexUsagePreferences {
            return instance ?: synchronized(this) {
                instance ?: CodexUsagePreferences(context).also { instance = it }
            }
        }
    }
}
