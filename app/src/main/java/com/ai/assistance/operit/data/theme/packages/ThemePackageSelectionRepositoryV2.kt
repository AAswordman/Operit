package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.preferences.missingPreferencesSchemaMigration
import com.ai.assistance.operit.data.preferences.preferenceSchemaMigration
import com.ai.assistance.operit.data.preferences.versionedPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val THEME_INSTANCE_V2_KEY = stringPreferencesKey("theme_instance_v2_json")

private val themeSelectionJsonV2 = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
}

private val Context.themePackageSelectionV2DataStore by versionedPreferencesDataStore(
    name = "theme_package_selection_v2",
    currentVersion = 1,
    createMigration = {
        preferenceSchemaMigration { version, preferences ->
            when (version) {
                0 -> {
                    preferences[THEME_INSTANCE_V2_KEY] =
                        themeSelectionJsonV2.encodeToString(ThemeInstanceV2.defaultBundled())
                }

                else -> missingPreferencesSchemaMigration(version)
            }
        }
    },
)

/** A V2-only global theme selection with no reader for the unpublished V1 record. */
internal class ThemePackageSelectionRepositoryV2 private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.themePackageSelectionV2DataStore

    val selectionFlow: Flow<ThemeInstanceV2> =
        dataStore.data.map { preferences -> preferences.decodeThemeInstanceV2() }

    suspend fun replace(instance: ThemeInstanceV2) {
        dataStore.edit { preferences ->
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(instance)
        }
    }

    companion object {
        @Volatile
        private var instance: ThemePackageSelectionRepositoryV2? = null

        fun getInstance(context: Context): ThemePackageSelectionRepositoryV2 =
            instance ?: synchronized(this) {
                instance ?: ThemePackageSelectionRepositoryV2(context).also { created -> instance = created }
            }
    }
}

private fun Preferences.decodeThemeInstanceV2(): ThemeInstanceV2 {
    val raw =
        this[THEME_INSTANCE_V2_KEY]
            ?: throw IllegalStateException("Global V2 theme selection record is missing.")
    return themeSelectionJsonV2.decodeFromString(raw)
}
