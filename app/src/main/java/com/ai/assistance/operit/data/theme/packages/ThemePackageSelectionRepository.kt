package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.preferences.preferenceSchemaMigration
import com.ai.assistance.operit.data.preferences.missingPreferencesSchemaMigration
import com.ai.assistance.operit.data.preferences.versionedPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val THEME_INSTANCE_KEY = stringPreferencesKey("theme_instance_json")

private val themeSelectionJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
}

private val Context.themePackageSelectionDataStore by versionedPreferencesDataStore(
    name = "theme_package_selection",
    currentVersion = 2,
    createMigration = {
        preferenceSchemaMigration { version, preferences ->
            when (version) {
                0, 1 -> {
                    preferences[THEME_INSTANCE_KEY] =
                        themeSelectionJson.encodeToString(ThemeInstanceV1.defaultBundled())
                }

                else -> missingPreferencesSchemaMigration(version)
            }
        }
    },
)

/**
 * Single application-level theme selection. Independent from ActivePrompt and per-target theme
 * storage; every theme Compose host observes the same [ThemeInstanceV1] from this repository.
 */
internal class ThemePackageSelectionRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.themePackageSelectionDataStore

    val selectionFlow: Flow<ThemeInstanceV1> =
        dataStore.data.map { preferences -> preferences.decodeThemeInstance() }

    suspend fun replace(instance: ThemeInstanceV1) {
        dataStore.edit { preferences ->
            preferences[THEME_INSTANCE_KEY] = themeSelectionJson.encodeToString(instance)
        }
    }

    companion object {
        @Volatile
        private var instance: ThemePackageSelectionRepository? = null

        fun getInstance(context: Context): ThemePackageSelectionRepository =
            instance ?: synchronized(this) {
                instance ?: ThemePackageSelectionRepository(context).also { instance = it }
            }
    }
}

private fun Preferences.decodeThemeInstance(): ThemeInstanceV1 {
    val raw =
        this[THEME_INSTANCE_KEY]
            ?: throw IllegalStateException("Global theme selection record is missing.")
    return themeSelectionJson.decodeFromString<ThemeInstanceV1>(raw)
}
