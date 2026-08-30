package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.ui.theme.NATIVE_THEME_V1_DEFINITION_ID
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerIdV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerV1
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.themeStyleInstancesDataStore: DataStore<Preferences> by
    versionedPreferencesDataStore(
        name = "theme_style_instances",
        currentVersion = 1,
    ) { version, _ ->
        when (version) {
            0 -> Unit
            else -> missingPreferencesSchemaMigration(version)
        }
    }

@Serializable
internal data class ThemeStyleInstanceRecordV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val styleApiMajor: Int = STYLE_API_MAJOR,
    val styleApiMinor: Int = STYLE_API_MINOR,
    val definitionId: String = NATIVE_THEME_V1_DEFINITION_ID,
    val instanceLayer: NativeThemeStyleLayerV1,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported theme style instance schema version: $schemaVersion"
        }
        require(definitionId == NATIVE_THEME_V1_DEFINITION_ID) {
            "Unsupported theme style definition: $definitionId"
        }
        require(styleApiMajor == STYLE_API_MAJOR && styleApiMinor == STYLE_API_MINOR) {
            "Unsupported theme Style API version: $styleApiMajor.$styleApiMinor"
        }
        require(instanceLayer.id == INSTANCE_LAYER_ID) {
            "Unsupported theme style instance layer: ${instanceLayer.id.value}"
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val STYLE_API_MAJOR = 1
        const val STYLE_API_MINOR = 0
        val INSTANCE_LAYER_ID = NativeThemeStyleLayerIdV1("operit.style.instance")

        fun empty(): ThemeStyleInstanceRecordV1 =
            ThemeStyleInstanceRecordV1(
                instanceLayer = NativeThemeStyleLayerV1(INSTANCE_LAYER_ID),
            )
    }
}

internal class ThemeStyleInstancePreferences private constructor(
    private val context: Context,
) {
    val recordsFlow: Flow<Map<String, ThemeStyleInstanceRecordV1>> =
        context.themeStyleInstancesDataStore.data.map(::readRecords)

    suspend fun has(target: ActivePrompt): Boolean =
        recordsFlow.first().containsKey(target.themeStyleInstanceKey())

    suspend fun replace(
        target: ActivePrompt,
        record: ThemeStyleInstanceRecordV1,
    ) {
        context.themeStyleInstancesDataStore.edit { preferences ->
            val records = readRecords(preferences).toMutableMap()
            records[target.themeStyleInstanceKey()] = record
            preferences[THEME_STYLE_INSTANCE_RECORDS] = json.encodeToString(records)
        }
    }

    suspend fun clear(target: ActivePrompt) {
        context.themeStyleInstancesDataStore.edit { preferences ->
            val records = readRecords(preferences).toMutableMap()
            records.remove(target.themeStyleInstanceKey())
            if (records.isEmpty()) {
                preferences.remove(THEME_STYLE_INSTANCE_RECORDS)
            } else {
                preferences[THEME_STYLE_INSTANCE_RECORDS] = json.encodeToString(records)
            }
        }
    }

    suspend fun clone(
        source: ActivePrompt,
        target: ActivePrompt,
    ) {
        context.themeStyleInstancesDataStore.edit { preferences ->
            val records = readRecords(preferences).toMutableMap()
            val sourceRecord = records[source.themeStyleInstanceKey()]
            if (sourceRecord == null) {
                records.remove(target.themeStyleInstanceKey())
            } else {
                records[target.themeStyleInstanceKey()] = sourceRecord.copy()
            }
            if (records.isEmpty()) {
                preferences.remove(THEME_STYLE_INSTANCE_RECORDS)
            } else {
                preferences[THEME_STYLE_INSTANCE_RECORDS] = json.encodeToString(records)
            }
        }
    }

    companion object {
        private val THEME_STYLE_INSTANCE_RECORDS = stringPreferencesKey("theme_style_instance_records")
        private val json =
            Json {
                encodeDefaults = true
                explicitNulls = false
                ignoreUnknownKeys = false
            }

        @Volatile
        private var instance: ThemeStyleInstancePreferences? = null

        fun getInstance(context: Context): ThemeStyleInstancePreferences =
            instance ?: synchronized(this) {
                instance ?: ThemeStyleInstancePreferences(context.applicationContext).also { instance = it }
            }

        private fun readRecords(preferences: Preferences): Map<String, ThemeStyleInstanceRecordV1> =
            preferences[THEME_STYLE_INSTANCE_RECORDS]?.let(json::decodeFromString) ?: emptyMap()
    }
}

internal fun ActivePrompt.themeStyleInstanceKey(): String =
    when (this) {
        is ActivePrompt.CharacterCard -> "character_card:$id"
        is ActivePrompt.CharacterGroup -> "character_group:$id"
    }
