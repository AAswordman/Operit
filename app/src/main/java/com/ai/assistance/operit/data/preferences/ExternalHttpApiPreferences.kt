package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.externalHttpApiDataStore: DataStore<Preferences> by recoverablePreferencesDataStore(
    name = "external_http_api_preferences"
)

data class ExternalHttpApiConfig(
    val enabled: Boolean,
    val port: Int,
    val bearerToken: String
)

class ExternalHttpApiPreferences private constructor(private val context: Context) {

    val enabledFlow: Flow<Boolean> =
        context.externalHttpApiDataStore.data.map { preferences ->
            preferences[KEY_ENABLED] ?: false
        }

    val portFlow: Flow<Int> =
        context.externalHttpApiDataStore.data.map { preferences ->
            preferences[KEY_PORT] ?: DEFAULT_PORT
        }

    val bearerTokenFlow: Flow<String> =
        context.externalHttpApiDataStore.data.map { preferences ->
            preferences[KEY_BEARER_TOKEN].orEmpty()
        }

    suspend fun repairPersistedState(): Boolean =
        repairPreferenceState(
            context = context,
            storeName = PreferenceStoreCatalog.EXTERNAL_HTTP_API,
            dataStore = context.externalHttpApiDataStore
        ) { current ->
            val values = current.asMap().entries.associate { it.key.name to it.value }
            val mutable = current.toMutablePreferences()
            val issues = linkedSetOf<String>()

            fun removeName(name: String) {
                mutable.asMap().keys.filter { it.name == name }.forEach { mutable.remove(it) }
            }

            fun replaceEnabled(value: Boolean) {
                removeName(KEY_ENABLED.name)
                mutable[KEY_ENABLED] = value
                issues += KEY_ENABLED.name
            }

            val rawEnabled = values[KEY_ENABLED.name]
            var enabled = rawEnabled as? Boolean ?: false
            if (rawEnabled != null && rawEnabled !is Boolean) {
                replaceEnabled(false)
                enabled = false
            }

            val rawPort = values[KEY_PORT.name]
            if (rawPort != null && (rawPort !is Int || !isValidPort(rawPort))) {
                removeName(KEY_PORT.name)
                mutable[KEY_PORT] = DEFAULT_PORT
                issues += KEY_PORT.name
            }

            val rawBearer = values[KEY_BEARER_TOKEN.name]
            val bearerToken = rawBearer as? String
            if (rawBearer != null && rawBearer !is String) {
                removeName(KEY_BEARER_TOKEN.name)
                issues += KEY_BEARER_TOKEN.name
            }
            if (enabled && bearerToken.isNullOrBlank()) {
                replaceEnabled(false)
            }

            PreferenceStateRepairResult(mutable.toPreferences(), issues)
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.externalHttpApiDataStore.edit { preferences ->
            preferences[KEY_ENABLED] = enabled
        }
    }

    suspend fun setPort(port: Int) {
        require(isValidPort(port)) { "Invalid port: $port" }
        context.externalHttpApiDataStore.edit { preferences ->
            preferences[KEY_PORT] = port
        }
    }

    suspend fun ensureBearerToken(): String {
        val existing = getConfig().bearerToken
        if (existing.isNotBlank()) {
            return existing
        }
        val generated = generateBearerToken()
        context.externalHttpApiDataStore.edit { preferences ->
            preferences[KEY_BEARER_TOKEN] = generated
        }
        return generated
    }

    suspend fun resetBearerToken(): String {
        val generated = generateBearerToken()
        context.externalHttpApiDataStore.edit { preferences ->
            preferences[KEY_BEARER_TOKEN] = generated
        }
        return generated
    }

    suspend fun setBearerToken(token: String) {
        context.externalHttpApiDataStore.edit { preferences ->
            preferences[KEY_BEARER_TOKEN] = token
        }
    }

    suspend fun getConfig(): ExternalHttpApiConfig {
        val preferences = context.externalHttpApiDataStore.data.first()
        return ExternalHttpApiConfig(
            enabled = preferences[KEY_ENABLED] ?: false,
            port = preferences[KEY_PORT] ?: DEFAULT_PORT,
            bearerToken = preferences[KEY_BEARER_TOKEN].orEmpty()
        )
    }

    fun getConfigSync(): ExternalHttpApiConfig = runBlocking {
        getConfig()
    }

    fun getEnabled(): Boolean = runBlocking {
        enabledFlow.first()
    }

    fun getPort(): Int = runBlocking {
        portFlow.first()
    }

    fun getBearerToken(): String = runBlocking {
        bearerTokenFlow.first()
    }

    companion object {
        const val DEFAULT_PORT = 8094

        private val KEY_ENABLED = booleanPreferencesKey("external_http_api_enabled")
        private val KEY_PORT = intPreferencesKey("external_http_api_port")
        private val KEY_BEARER_TOKEN = stringPreferencesKey("external_http_api_bearer_token")

        @Volatile
        private var INSTANCE: ExternalHttpApiPreferences? = null

        fun getInstance(context: Context): ExternalHttpApiPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = ExternalHttpApiPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun isValidPort(port: Int): Boolean {
            return port in 1..65535
        }

        private fun generateBearerToken(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }
    }
}
