package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.speechServiceProfilesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "speech_service_profiles")

/**
 * Stores independent TTS and STT configuration profiles.
 *
 * The legacy speech preferences remain the runtime projection consumed by the existing providers.
 * Keeping that projection aligned with the active profile avoids changing a provider's request
 * contract while published installations migrate to the new profile store.
 */
class SpeechServiceProfilesPreferences(private val context: Context) {

    @Serializable
    data class TtsProfile(
        val id: String,
        val name: String,
        val serviceType: VoiceServiceFactory.VoiceServiceType,
        val httpConfig: SpeechServicesPreferences.TtsHttpConfig,
        val vitsConfig: SpeechServicesPreferences.VitsTtsPackageConfig,
        val cleanerRegexs: List<String>,
        val speechRate: Float,
        val pitch: Float,
        val createdAt: Long,
        val updatedAt: Long,
    )

    @Serializable
    data class SttProfile(
        val id: String,
        val name: String,
        val serviceType: SpeechServiceFactory.SpeechServiceType,
        val httpConfig: SpeechServicesPreferences.SttHttpConfig,
        val createdAt: Long,
        val updatedAt: Long,
    )

    companion object {
        private const val MIGRATION_VERSION = 1
        private const val LEGACY_TTS_PROFILE_ID = "legacy-tts-profile"
        private const val LEGACY_STT_PROFILE_ID = "legacy-stt-profile"

        private val TTS_PROFILES = stringPreferencesKey("tts_profiles")
        private val STT_PROFILES = stringPreferencesKey("stt_profiles")
        private val CURRENT_TTS_PROFILE_ID = stringPreferencesKey("current_tts_profile_id")
        private val CURRENT_STT_PROFILE_ID = stringPreferencesKey("current_stt_profile_id")
        private val MIGRATION_VERSION_KEY = intPreferencesKey("speech_profiles_migration_version")
    }

    private val dataStore = context.speechServiceProfilesDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val legacyPreferences = SpeechServicesPreferences(context.applicationContext)

    val ttsProfilesFlow: Flow<List<TtsProfile>> = dataStore.data.onStart {
        ensureMigrated()
    }.map { preferences ->
        decodeTtsProfiles(preferences[TTS_PROFILES])
    }

    val sttProfilesFlow: Flow<List<SttProfile>> = dataStore.data.onStart {
        ensureMigrated()
    }.map { preferences ->
        decodeSttProfiles(preferences[STT_PROFILES])
    }

    val currentTtsProfileIdFlow: Flow<String> = dataStore.data.onStart {
        ensureMigrated()
    }.map { preferences ->
        preferences[CURRENT_TTS_PROFILE_ID].orEmpty()
    }

    val currentSttProfileIdFlow: Flow<String> = dataStore.data.onStart {
        ensureMigrated()
    }.map { preferences ->
        preferences[CURRENT_STT_PROFILE_ID].orEmpty()
    }

    val currentTtsProfileFlow: Flow<TtsProfile> =
        ttsProfilesFlow.combine(currentTtsProfileIdFlow) { profiles, id ->
            profiles.first { profile -> profile.id == id }
        }

    val currentSttProfileFlow: Flow<SttProfile> =
        sttProfilesFlow.combine(currentSttProfileIdFlow) { profiles, id ->
            profiles.first { profile -> profile.id == id }
        }

    val currentTtsProfileOrNullFlow: Flow<TtsProfile?> =
        ttsProfilesFlow.combine(currentTtsProfileIdFlow) { profiles, id ->
            profiles.find { profile -> profile.id == id }
        }

    val currentSttProfileOrNullFlow: Flow<SttProfile?> =
        sttProfilesFlow.combine(currentSttProfileIdFlow) { profiles, id ->
            profiles.find { profile -> profile.id == id }
        }

    /**
     * Imports the published single-config preferences exactly once.
     */
    suspend fun ensureMigrated() {
        val preferences = dataStore.data.first()
        if ((preferences[MIGRATION_VERSION_KEY] ?: 0) >= MIGRATION_VERSION) return

        val legacyTtsProfile = TtsProfile(
            id = LEGACY_TTS_PROFILE_ID,
            name = context.getString(R.string.speech_services_profile_migrated_tts),
            serviceType = legacyPreferences.ttsServiceTypeFlow.first(),
            httpConfig = legacyPreferences.ttsHttpConfigFlow.first(),
            vitsConfig = legacyPreferences.ttsVitsPackageConfigFlow.first(),
            cleanerRegexs = legacyPreferences.ttsCleanerRegexsFlow.first(),
            speechRate = legacyPreferences.ttsSpeechRateFlow.first(),
            pitch = legacyPreferences.ttsPitchFlow.first(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val legacySttProfile = SttProfile(
            id = LEGACY_STT_PROFILE_ID,
            name = context.getString(R.string.speech_services_profile_migrated_stt),
            serviceType = legacyPreferences.sttServiceTypeFlow.first(),
            httpConfig = legacyPreferences.sttHttpConfigFlow.first(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        dataStore.edit { stored ->
            if ((stored[MIGRATION_VERSION_KEY] ?: 0) >= MIGRATION_VERSION) return@edit

            val storedTtsProfiles = decodeTtsProfiles(stored[TTS_PROFILES])
            val storedSttProfiles = decodeSttProfiles(stored[STT_PROFILES])
            val ttsProfiles = if (storedTtsProfiles.isEmpty()) {
                listOf(legacyTtsProfile)
            } else {
                storedTtsProfiles
            }
            val sttProfiles = if (storedSttProfiles.isEmpty()) {
                listOf(legacySttProfile)
            } else {
                storedSttProfiles
            }
            val currentTtsId = stored[CURRENT_TTS_PROFILE_ID]
            val currentSttId = stored[CURRENT_STT_PROFILE_ID]

            stored[TTS_PROFILES] = json.encodeToString(ttsProfiles)
            stored[STT_PROFILES] = json.encodeToString(sttProfiles)
            stored[CURRENT_TTS_PROFILE_ID] = when {
                currentTtsId != null && ttsProfiles.any { it.id == currentTtsId } -> currentTtsId
                else -> ttsProfiles.first().id
            }
            stored[CURRENT_STT_PROFILE_ID] = when {
                currentSttId != null && sttProfiles.any { it.id == currentSttId } -> currentSttId
                else -> sttProfiles.first().id
            }
            stored[MIGRATION_VERSION_KEY] = MIGRATION_VERSION
        }
    }

    /**
     * Returns the active TTS profile after ensuring the published preference migration has run.
     */
    suspend fun getCurrentTtsProfile(): TtsProfile {
        ensureMigrated()
        val profiles = ttsProfilesFlow.first()
        val id = currentTtsProfileIdFlow.first()
        return profiles.find { it.id == id }
            ?: error("Current TTS profile is missing: $id")
    }

    /**
     * Returns the active STT profile after ensuring the published preference migration has run.
     */
    suspend fun getCurrentSttProfile(): SttProfile {
        ensureMigrated()
        val profiles = sttProfilesFlow.first()
        val id = currentSttProfileIdFlow.first()
        return profiles.find { it.id == id }
            ?: error("Current STT profile is missing: $id")
    }

    /**
     * Creates a TTS profile from an optional existing profile and makes it active.
     */
    suspend fun createTtsProfile(name: String, template: TtsProfile? = null): TtsProfile {
        ensureMigrated()
        val normalizedName = requireProfileName(name)
        val now = System.currentTimeMillis()
        val profile = (template ?: getCurrentTtsProfile()).copy(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            createdAt = now,
            updatedAt = now,
        )
        dataStore.edit { preferences ->
            val profiles = decodeTtsProfiles(preferences[TTS_PROFILES]) + profile
            preferences[TTS_PROFILES] = json.encodeToString(profiles)
            preferences[CURRENT_TTS_PROFILE_ID] = profile.id
        }
        projectTtsProfile(profile)
        return profile
    }

    /**
     * Replaces one TTS profile while preserving its identity and creation time.
     */
    suspend fun updateTtsProfile(profile: TtsProfile): TtsProfile {
        ensureMigrated()
        val normalized = profile.copy(
            name = requireProfileName(profile.name),
            cleanerRegexs = profile.cleanerRegexs.filter(String::isNotBlank),
            speechRate = requirePositive(profile.speechRate, "TTS speech rate"),
            pitch = requirePositive(profile.pitch, "TTS pitch"),
            updatedAt = System.currentTimeMillis(),
        )
        var active = false
        dataStore.edit { preferences ->
            val profiles = decodeTtsProfiles(preferences[TTS_PROFILES])
            val existing = profiles.find { it.id == normalized.id }
                ?: error("TTS profile does not exist: ${normalized.id}")
            val updated = normalized.copy(createdAt = existing.createdAt)
            preferences[TTS_PROFILES] = json.encodeToString(
                profiles.map { item -> if (item.id == updated.id) updated else item },
            )
            active = preferences[CURRENT_TTS_PROFILE_ID] == updated.id
        }
        val updated = getTtsProfile(normalized.id)
        if (active) projectTtsProfile(updated)
        return updated
    }

    /**
     * Creates an STT profile from an optional existing profile and makes it active.
     */
    suspend fun createSttProfile(name: String, template: SttProfile? = null): SttProfile {
        ensureMigrated()
        val normalizedName = requireProfileName(name)
        val now = System.currentTimeMillis()
        val profile = (template ?: getCurrentSttProfile()).copy(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            createdAt = now,
            updatedAt = now,
        )
        dataStore.edit { preferences ->
            val profiles = decodeSttProfiles(preferences[STT_PROFILES]) + profile
            preferences[STT_PROFILES] = json.encodeToString(profiles)
            preferences[CURRENT_STT_PROFILE_ID] = profile.id
        }
        projectSttProfile(profile)
        return profile
    }

    /**
     * Replaces one STT profile while preserving its identity and creation time.
     */
    suspend fun updateSttProfile(profile: SttProfile): SttProfile {
        ensureMigrated()
        val normalized = profile.copy(
            name = requireProfileName(profile.name),
            updatedAt = System.currentTimeMillis(),
        )
        var active = false
        dataStore.edit { preferences ->
            val profiles = decodeSttProfiles(preferences[STT_PROFILES])
            val existing = profiles.find { it.id == normalized.id }
                ?: error("STT profile does not exist: ${normalized.id}")
            val updated = normalized.copy(createdAt = existing.createdAt)
            preferences[STT_PROFILES] = json.encodeToString(
                profiles.map { item -> if (item.id == updated.id) updated else item },
            )
            active = preferences[CURRENT_STT_PROFILE_ID] == updated.id
        }
        val updated = getSttProfile(normalized.id)
        if (active) projectSttProfile(updated)
        return updated
    }

    /**
     * Makes an existing TTS profile active and synchronizes existing providers' input preferences.
     */
    suspend fun selectTtsProfile(id: String) {
        ensureMigrated()
        val profile = getTtsProfile(id)
        dataStore.edit { preferences -> preferences[CURRENT_TTS_PROFILE_ID] = profile.id }
        projectTtsProfile(profile)
    }

    /**
     * Makes an existing STT profile active and synchronizes existing providers' input preferences.
     */
    suspend fun selectSttProfile(id: String) {
        ensureMigrated()
        val profile = getSttProfile(id)
        dataStore.edit { preferences -> preferences[CURRENT_STT_PROFILE_ID] = profile.id }
        projectSttProfile(profile)
    }

    /**
     * Removes an inactive TTS profile.
     */
    suspend fun deleteTtsProfile(id: String) {
        ensureMigrated()
        dataStore.edit { preferences ->
            check(preferences[CURRENT_TTS_PROFILE_ID] != id) {
                "The active TTS profile cannot be deleted"
            }
            val profiles = decodeTtsProfiles(preferences[TTS_PROFILES])
            check(profiles.any { it.id == id }) { "TTS profile does not exist: $id" }
            preferences[TTS_PROFILES] = json.encodeToString(profiles.filterNot { it.id == id })
        }
    }

    /**
     * Removes an inactive STT profile.
     */
    suspend fun deleteSttProfile(id: String) {
        ensureMigrated()
        dataStore.edit { preferences ->
            check(preferences[CURRENT_STT_PROFILE_ID] != id) {
                "The active STT profile cannot be deleted"
            }
            val profiles = decodeSttProfiles(preferences[STT_PROFILES])
            check(profiles.any { it.id == id }) { "STT profile does not exist: $id" }
            preferences[STT_PROFILES] = json.encodeToString(profiles.filterNot { it.id == id })
        }
    }

    private suspend fun getTtsProfile(id: String): TtsProfile {
        return ttsProfilesFlow.first().find { it.id == id }
            ?: error("TTS profile does not exist: $id")
    }

    private suspend fun getSttProfile(id: String): SttProfile {
        return sttProfilesFlow.first().find { it.id == id }
            ?: error("STT profile does not exist: $id")
    }

    private suspend fun projectTtsProfile(profile: TtsProfile) {
        legacyPreferences.saveTtsSettings(
            serviceType = profile.serviceType,
            httpConfig = profile.httpConfig,
            vitsConfig = profile.vitsConfig,
            cleanerRegexs = profile.cleanerRegexs,
            speechRate = profile.speechRate,
            pitch = profile.pitch,
        )
    }

    private suspend fun projectSttProfile(profile: SttProfile) {
        legacyPreferences.saveSttSettings(
            serviceType = profile.serviceType,
            httpConfig = profile.httpConfig,
        )
    }

    private fun decodeTtsProfiles(raw: String?): List<TtsProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return json.decodeFromString(ListSerializer(TtsProfile.serializer()), raw)
    }

    private fun decodeSttProfiles(raw: String?): List<SttProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return json.decodeFromString(ListSerializer(SttProfile.serializer()), raw)
    }

    private fun requireProfileName(name: String): String {
        return name.trim().also { check(it.isNotEmpty()) { "Speech profile name is empty" } }
    }

    private fun requirePositive(value: Float, label: String): Float {
        check(value > 0f) { "$label must be positive" }
        return value
    }
}
