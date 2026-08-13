package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.RecoverablePreferenceDataStores
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val Context.speechServiceProfilesDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "speech_service_profiles")

/**
 * Owns independent TTS and STT profiles after the released single-config store is imported once.
 * Startup recovery establishes every invariant before factories or UI code can read this store.
 */
class SpeechServiceProfilesPreferences(context: Context) {

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
        private const val FORMAT_VERSION = 1
        internal const val LEGACY_TTS_PROFILE_ID = "legacy-tts-profile"
        internal const val LEGACY_STT_PROFILE_ID = "legacy-stt-profile"
        internal const val DEFAULT_TTS_PROFILE_ID = "default-tts-profile"
        internal const val DEFAULT_STT_PROFILE_ID = "default-stt-profile"

        internal val TTS_PROFILES = stringPreferencesKey("tts_profiles")
        internal val STT_PROFILES = stringPreferencesKey("stt_profiles")
        internal val CURRENT_TTS_PROFILE_ID = stringPreferencesKey("current_tts_profile_id")
        internal val CURRENT_STT_PROFILE_ID = stringPreferencesKey("current_stt_profile_id")
        internal val MIGRATION_VERSION_KEY =
            intPreferencesKey("speech_profiles_migration_version")

        private val initializationMutex = Mutex()
        private val ttsProfileFields =
            setOf(
                "id",
                "name",
                "serviceType",
                "httpConfig",
                "vitsConfig",
                "cleanerRegexs",
                "speechRate",
                "pitch",
                "createdAt",
                "updatedAt",
            )
        private val sttProfileFields =
            setOf("id", "name", "serviceType", "httpConfig", "createdAt", "updatedAt")
        private val ttsHttpFields =
            setOf(
                "urlTemplate",
                "apiKey",
                "headers",
                "httpMethod",
                "requestBody",
                "contentType",
                "localeTag",
                "voiceId",
                "modelName",
                "responsePipeline",
            )
        private val vitsFields = setOf("packagePath", "speakerId", "options")
        private val sttHttpFields = setOf("endpointUrl", "apiKey", "modelName")
        private val pipelineFields = setOf("type", "path", "headers")
    }

    private val appContext = context.applicationContext
    private val dataStore = appContext.speechServiceProfilesDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private data class InvariantState(
        val ttsProfiles: List<TtsProfile>,
        val sttProfiles: List<SttProfile>,
        val currentTts: TtsProfile,
        val currentStt: SttProfile,
    )

    private data class StoredProfile<T>(val value: T, val raw: JsonObject)

    private data class StoredState(
        val ttsProfiles: List<StoredProfile<TtsProfile>>,
        val sttProfiles: List<StoredProfile<SttProfile>>,
        val currentTtsId: String,
        val currentSttId: String,
    )

    private data class RepairInputs(
        val now: Long,
        val published: SpeechServicesPreferences.PublishedSpeechState?,
        val migratedTtsName: String,
        val migratedSttName: String,
        val defaultTtsName: String,
        val defaultSttName: String,
    )

    private data class NormalizedProfile<T>(
        val value: T,
        val raw: JsonObject,
        val hasIssue: Boolean,
    )

    private data class NormalizedDomain<T>(
        val profiles: List<NormalizedProfile<T>>,
        val hasIssue: Boolean,
    )

    private data class NormalizedState(
        val preferences: Preferences,
        val issueKeys: Set<String>,
        val changed: Boolean,
    )

    private data class StringField(val value: String, val hasIssue: Boolean)

    private data class StringMapField(
        val value: Map<String, String>,
        val hasIssue: Boolean,
    )

    private data class NormalizedTtsHttp(
        val value: SpeechServicesPreferences.TtsHttpConfig,
        val raw: JsonObject,
        val hasIssue: Boolean,
    )

    private data class NormalizedVits(
        val value: SpeechServicesPreferences.VitsTtsPackageConfig,
        val raw: JsonObject,
        val hasIssue: Boolean,
    )

    private data class NormalizedSttHttp(
        val value: SpeechServicesPreferences.SttHttpConfig,
        val raw: JsonObject,
        val hasIssue: Boolean,
    )

    private data class NormalizedPipeline(
        val value: List<HttpTtsResponsePipelineStep>,
        val raw: JsonArray,
        val hasIssue: Boolean,
    )

    private val stateFlow: Flow<InvariantState> = dataStore.data.map(::decodeInvariantState)

    val ttsProfilesFlow: Flow<List<TtsProfile>> = stateFlow.map { it.ttsProfiles }
    val sttProfilesFlow: Flow<List<SttProfile>> = stateFlow.map { it.sttProfiles }
    val currentTtsProfileIdFlow: Flow<String> = stateFlow.map { it.currentTts.id }
    val currentSttProfileIdFlow: Flow<String> = stateFlow.map { it.currentStt.id }
    val currentTtsProfileFlow: Flow<TtsProfile> = stateFlow.map { it.currentTts }
    val currentSttProfileFlow: Flow<SttProfile> = stateFlow.map { it.currentStt }
    val currentTtsProfileOrNullFlow: Flow<TtsProfile?> = stateFlow.map { it.currentTts }
    val currentSttProfileOrNullFlow: Flow<SttProfile?> = stateFlow.map { it.currentStt }

    /**
     * Imports released settings when needed, repairs logical damage, and commits all related keys
     * atomically. The return value only reports actual corruption repair, not normal initialization.
     */
    suspend fun initializeAndRepair(): Boolean = initializationMutex.withLock {
        val before = dataStore.data.first()
        val rawVersion = valuesByName(before)[MIGRATION_VERSION_KEY.name]
        if (rawVersion is Int && rawVersion > FORMAT_VERSION) {
            error("Unsupported speech profile format version: $rawVersion")
        }
        val migrationPending = rawVersion !is Int || rawVersion < FORMAT_VERSION
        val legacy = SpeechServicesPreferences(appContext)
        val legacyRepaired = migrationPending && legacy.repairPersistedState()
        val published = if (migrationPending) legacy.readMigrationSeed() else null
        val inputs =
            RepairInputs(
                now = System.currentTimeMillis(),
                published = published,
                migratedTtsName = appContext.getString(R.string.speech_services_profile_migrated_tts),
                migratedSttName = appContext.getString(R.string.speech_services_profile_migrated_stt),
                defaultTtsName = appContext.getString(R.string.speech_services_profile_default_tts),
                defaultSttName = appContext.getString(R.string.speech_services_profile_default_stt),
            )

        var profileRepaired = false
        dataStore.updateData { current ->
            val normalized = normalizeState(current, inputs)
            if (!normalized.changed) return@updateData current

            val validation = normalizeState(normalized.preferences, inputs)
            check(!validation.changed && validation.issueKeys.isEmpty()) {
                "Speech profile repair did not converge; " +
                    "remainingIssueCount=${validation.issueKeys.size}"
            }
            if (normalized.issueKeys.isNotEmpty()) {
                // Keep the exact unreadable logical state before replacing damaged fields.
                RecoverablePreferenceDataStores.quarantineLogicalState(
                    appContext,
                    PreferenceStoreCatalog.SPEECH_SERVICE_PROFILES,
                    current,
                    normalized.issueKeys,
                )
                profileRepaired = true
            }
            normalized.preferences
        }
        legacyRepaired || profileRepaired
    }

    suspend fun getCurrentTtsProfile(): TtsProfile =
        decodeInvariantState(dataStore.data.first()).currentTts

    suspend fun getCurrentSttProfile(): SttProfile =
        decodeInvariantState(dataStore.data.first()).currentStt

    suspend fun createTtsProfile(name: String, template: TtsProfile? = null): TtsProfile {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        var created: TtsProfile? = null
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            val active = state.ttsProfiles.first { it.value.id == state.currentTtsId }
            val storedTemplate =
                template?.let { candidate ->
                    checkNotNull(state.ttsProfiles.firstOrNull { it.value.id == candidate.id }) {
                        "TTS template profile does not exist: ${candidate.id}"
                    }
                }
            val source = storedTemplate ?: active
            val profile =
                validateTtsForWrite(template ?: source.value).copy(
                    id = id,
                    name = requireProfileName(name),
                    createdAt = now,
                    updatedAt = now,
                )
            created = profile
            writeTtsState(
                current,
                state.ttsProfiles + StoredProfile(profile, mergeTtsProfile(source.raw, profile)),
                id,
            )
        }
        return checkNotNull(created)
    }

    suspend fun updateTtsProfile(profile: TtsProfile): TtsProfile {
        val now = System.currentTimeMillis()
        var updated: TtsProfile? = null
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            val index = state.ttsProfiles.indexOfFirst { it.value.id == profile.id }
            check(index >= 0) { "TTS profile does not exist: ${profile.id}" }
            val existing = state.ttsProfiles[index]
            val normalized =
                validateTtsForWrite(profile).copy(
                    id = existing.value.id,
                    createdAt = existing.value.createdAt,
                    updatedAt = maxOf(now, existing.value.createdAt, existing.value.updatedAt),
                )
            updated = normalized
            val profiles = state.ttsProfiles.toMutableList()
            profiles[index] = StoredProfile(normalized, mergeTtsProfile(existing.raw, normalized))
            writeTtsState(current, profiles, state.currentTtsId)
        }
        return checkNotNull(updated)
    }

    suspend fun createSttProfile(name: String, template: SttProfile? = null): SttProfile {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        var created: SttProfile? = null
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            val active = state.sttProfiles.first { it.value.id == state.currentSttId }
            val storedTemplate =
                template?.let { candidate ->
                    checkNotNull(state.sttProfiles.firstOrNull { it.value.id == candidate.id }) {
                        "STT template profile does not exist: ${candidate.id}"
                    }
                }
            val source = storedTemplate ?: active
            val profile =
                validateSttForWrite(template ?: source.value).copy(
                    id = id,
                    name = requireProfileName(name),
                    createdAt = now,
                    updatedAt = now,
                )
            created = profile
            writeSttState(
                current,
                state.sttProfiles + StoredProfile(profile, mergeSttProfile(source.raw, profile)),
                id,
            )
        }
        return checkNotNull(created)
    }

    suspend fun updateSttProfile(profile: SttProfile): SttProfile {
        val now = System.currentTimeMillis()
        var updated: SttProfile? = null
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            val index = state.sttProfiles.indexOfFirst { it.value.id == profile.id }
            check(index >= 0) { "STT profile does not exist: ${profile.id}" }
            val existing = state.sttProfiles[index]
            val normalized =
                validateSttForWrite(profile).copy(
                    id = existing.value.id,
                    createdAt = existing.value.createdAt,
                    updatedAt = maxOf(now, existing.value.createdAt, existing.value.updatedAt),
                )
            updated = normalized
            val profiles = state.sttProfiles.toMutableList()
            profiles[index] = StoredProfile(normalized, mergeSttProfile(existing.raw, normalized))
            writeSttState(current, profiles, state.currentSttId)
        }
        return checkNotNull(updated)
    }

    suspend fun selectTtsProfile(id: String) {
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            check(state.ttsProfiles.any { it.value.id == id }) { "TTS profile does not exist: $id" }
            writeString(current, CURRENT_TTS_PROFILE_ID, id)
        }
    }

    suspend fun selectSttProfile(id: String) {
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            check(state.sttProfiles.any { it.value.id == id }) { "STT profile does not exist: $id" }
            writeString(current, CURRENT_STT_PROFILE_ID, id)
        }
    }

    suspend fun deleteTtsProfile(id: String) {
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            check(state.currentTtsId != id) { "The active TTS profile cannot be deleted" }
            check(state.ttsProfiles.any { it.value.id == id }) { "TTS profile does not exist: $id" }
            writeTtsState(
                current,
                state.ttsProfiles.filterNot { it.value.id == id },
                state.currentTtsId,
            )
        }
    }

    suspend fun deleteSttProfile(id: String) {
        dataStore.updateData { current ->
            val state = decodeStoredState(current)
            check(state.currentSttId != id) { "The active STT profile cannot be deleted" }
            check(state.sttProfiles.any { it.value.id == id }) { "STT profile does not exist: $id" }
            writeSttState(
                current,
                state.sttProfiles.filterNot { it.value.id == id },
                state.currentSttId,
            )
        }
    }

    private fun normalizeState(current: Preferences, inputs: RepairInputs): NormalizedState {
        val values = valuesByName(current)
        val issues = linkedSetOf<String>()
        val rawVersion = values[MIGRATION_VERSION_KEY.name]
        val version =
            when (rawVersion) {
                null -> 0
                is Int -> {
                    check(rawVersion <= FORMAT_VERSION) {
                        "Unsupported speech profile format version: $rawVersion"
                    }
                    if (rawVersion < 0) {
                        issues += MIGRATION_VERSION_KEY.name
                        0
                    } else {
                        rawVersion
                    }
                }
                else -> {
                    issues += MIGRATION_VERSION_KEY.name
                    0
                }
            }

        val ttsDomain = normalizeTtsDomain(values[TTS_PROFILES.name], version, inputs)
        val sttDomain = normalizeSttDomain(values[STT_PROFILES.name], version, inputs)
        if (ttsDomain.hasIssue) issues += TTS_PROFILES.name
        if (sttDomain.hasIssue) issues += STT_PROFILES.name

        val currentTtsId =
            normalizeCurrentId(
                raw = values[CURRENT_TTS_PROFILE_ID.name],
                validIds = ttsDomain.profiles.map { it.value.id },
                migrationPending = version < FORMAT_VERSION,
                keyName = CURRENT_TTS_PROFILE_ID.name,
                issues = issues,
            )
        val currentSttId =
            normalizeCurrentId(
                raw = values[CURRENT_STT_PROFILE_ID.name],
                validIds = sttDomain.profiles.map { it.value.id },
                migrationPending = version < FORMAT_VERSION,
                keyName = CURRENT_STT_PROFILE_ID.name,
                issues = issues,
            )

        val mutable = current.toMutablePreferences()
        replacePreference(mutable, TTS_PROFILES, JsonArray(ttsDomain.profiles.map { it.raw }).toString())
        replacePreference(mutable, STT_PROFILES, JsonArray(sttDomain.profiles.map { it.raw }).toString())
        replacePreference(mutable, CURRENT_TTS_PROFILE_ID, currentTtsId)
        replacePreference(mutable, CURRENT_STT_PROFILE_ID, currentSttId)
        removeByName(mutable, MIGRATION_VERSION_KEY.name)
        mutable[MIGRATION_VERSION_KEY] = FORMAT_VERSION
        val normalized = mutable.toPreferences()
        return NormalizedState(
            preferences = normalized,
            issueKeys = issues,
            changed = normalized.asMap() != current.asMap(),
        )
    }

    private fun normalizeTtsDomain(
        rawValue: Any?,
        version: Int,
        inputs: RepairInputs,
    ): NormalizedDomain<TtsProfile> {
        val parsed = parseProfileArray(rawValue)
        if (parsed == null) {
            val seed = normalizedSeedTts(version, inputs)
            return NormalizedDomain(
                profiles = listOf(seed),
                hasIssue = rawValue != null || version >= FORMAT_VERSION,
            )
        }

        val usedIds = linkedSetOf<String>()
        var repairedIdCount = 0
        var issue = false
        val profiles =
            parsed.mapNotNull { element ->
                val raw = element as? JsonObject
                if (raw == null) {
                    issue = true
                    null
                } else {
                    normalizeTtsProfile(
                        raw = raw,
                        usedIds = usedIds,
                        nextRepairId = {
                            repairedIdCount++
                            deterministicRepairId(
                                "repaired-tts-profile",
                                repairedIdCount,
                                usedIds,
                            )
                        },
                        inputs = inputs,
                    ).also {
                        issue = issue || it.hasIssue
                    }
                }
            }.toMutableList()
        if (profiles.isEmpty()) {
            profiles += normalizedSeedTts(version, inputs)
            if (parsed.isNotEmpty() || version >= FORMAT_VERSION) issue = true
        }
        return NormalizedDomain(profiles, issue)
    }

    private fun normalizeSttDomain(
        rawValue: Any?,
        version: Int,
        inputs: RepairInputs,
    ): NormalizedDomain<SttProfile> {
        val parsed = parseProfileArray(rawValue)
        if (parsed == null) {
            val seed = normalizedSeedStt(version, inputs)
            return NormalizedDomain(
                profiles = listOf(seed),
                hasIssue = rawValue != null || version >= FORMAT_VERSION,
            )
        }

        val usedIds = linkedSetOf<String>()
        var repairedIdCount = 0
        var issue = false
        val profiles =
            parsed.mapNotNull { element ->
                val raw = element as? JsonObject
                if (raw == null) {
                    issue = true
                    null
                } else {
                    normalizeSttProfile(
                        raw = raw,
                        usedIds = usedIds,
                        nextRepairId = {
                            repairedIdCount++
                            deterministicRepairId(
                                "repaired-stt-profile",
                                repairedIdCount,
                                usedIds,
                            )
                        },
                        inputs = inputs,
                    ).also {
                        issue = issue || it.hasIssue
                    }
                }
            }.toMutableList()
        if (profiles.isEmpty()) {
            profiles += normalizedSeedStt(version, inputs)
            if (parsed.isNotEmpty() || version >= FORMAT_VERSION) issue = true
        }
        return NormalizedDomain(profiles, issue)
    }

    private fun parseProfileArray(rawValue: Any?): JsonArray? {
        val text = rawValue as? String ?: return null
        return try {
            json.parseToJsonElement(text) as? JsonArray
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeTtsProfile(
        raw: JsonObject,
        usedIds: MutableSet<String>,
        nextRepairId: () -> String,
        inputs: RepairInputs,
    ): NormalizedProfile<TtsProfile> {
        var issue = false
        val idField = requiredString(raw, "id", "")
        var id = idField.value.trim()
        issue = issue || idField.hasIssue || id != idField.value
        if (id.isEmpty() || id in usedIds) {
            id = nextRepairId()
            issue = true
        }
        usedIds += id

        val nameField = requiredString(raw, "name", inputs.defaultTtsName)
        val name = nameField.value.trim().ifEmpty { inputs.defaultTtsName }
        issue = issue || nameField.hasIssue || name != nameField.value

        val serviceField = requiredString(raw, "serviceType", "")
        val serviceType =
            VoiceServiceFactory.VoiceServiceType.entries.firstOrNull {
                it.name == serviceField.value
            } ?: SpeechServicesPreferences.DEFAULT_TTS_SERVICE_TYPE.also { issue = true }
        issue = issue || serviceField.hasIssue

        val http = normalizeTtsHttp(raw["httpConfig"])
        val vits = normalizeVits(raw["vitsConfig"])
        val cleaners = normalizeCleanerRegexs(raw["cleanerRegexs"])
        val speechRate = normalizeSpeechScalar(raw["speechRate"], SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE)
        val pitch = normalizeSpeechScalar(raw["pitch"], SpeechServicesPreferences.DEFAULT_TTS_PITCH)
        issue = issue || http.hasIssue || vits.hasIssue || cleaners.second || speechRate.second || pitch.second

        val created = normalizeCreatedAt(raw["createdAt"], inputs.now)
        val updated = normalizeUpdatedAt(raw["updatedAt"], created.first)
        issue = issue || created.second || updated.second

        val profile =
            TtsProfile(
                id = id,
                name = name,
                serviceType = serviceType,
                httpConfig = http.value,
                vitsConfig = vits.value,
                cleanerRegexs = cleaners.first,
                speechRate = speechRate.first,
                pitch = pitch.first,
                createdAt = created.first,
                updatedAt = updated.first,
            )
        val encoded = json.encodeToJsonElement(profile).jsonObject
        val merged = replaceKnownFields(raw, encoded, ttsProfileFields).toMutableMap()
        merged["httpConfig"] = http.raw
        merged["vitsConfig"] = vits.raw
        return NormalizedProfile(profile, JsonObject(merged), issue)
    }

    private fun normalizeSttProfile(
        raw: JsonObject,
        usedIds: MutableSet<String>,
        nextRepairId: () -> String,
        inputs: RepairInputs,
    ): NormalizedProfile<SttProfile> {
        var issue = false
        val idField = requiredString(raw, "id", "")
        var id = idField.value.trim()
        issue = issue || idField.hasIssue || id != idField.value
        if (id.isEmpty() || id in usedIds) {
            id = nextRepairId()
            issue = true
        }
        usedIds += id

        val nameField = requiredString(raw, "name", inputs.defaultSttName)
        val name = nameField.value.trim().ifEmpty { inputs.defaultSttName }
        issue = issue || nameField.hasIssue || name != nameField.value

        val serviceField = requiredString(raw, "serviceType", "")
        val serviceType =
            SpeechServiceFactory.SpeechServiceType.entries.firstOrNull {
                it.name == serviceField.value
            } ?: SpeechServicesPreferences.DEFAULT_STT_SERVICE_TYPE.also { issue = true }
        issue = issue || serviceField.hasIssue

        val http = normalizeSttHttp(raw["httpConfig"])
        val created = normalizeCreatedAt(raw["createdAt"], inputs.now)
        val updated = normalizeUpdatedAt(raw["updatedAt"], created.first)
        issue = issue || http.hasIssue || created.second || updated.second

        val profile =
            SttProfile(
                id = id,
                name = name,
                serviceType = serviceType,
                httpConfig = http.value,
                createdAt = created.first,
                updatedAt = updated.first,
            )
        val encoded = json.encodeToJsonElement(profile).jsonObject
        val merged = replaceKnownFields(raw, encoded, sttProfileFields).toMutableMap()
        merged["httpConfig"] = http.raw
        return NormalizedProfile(profile, JsonObject(merged), issue)
    }

    private fun normalizeTtsHttp(element: JsonElement?): NormalizedTtsHttp {
        val defaults = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET
        val raw = element as? JsonObject ?: JsonObject(emptyMap())
        var issue = element !is JsonObject
        val url = requiredString(raw, "urlTemplate", defaults.urlTemplate)
        val apiKey = requiredString(raw, "apiKey", defaults.apiKey)
        val headers = stringMap(raw["headers"], required = true)
        val methodField = optionalString(raw, "httpMethod", defaults.httpMethod)
        val normalizedMethod = methodField.value.trim().uppercase(Locale.ROOT)
        val method =
            normalizedMethod.takeIf { it == "GET" || it == "POST" }
                ?: defaults.httpMethod.also { issue = true }
        val requestBody = optionalString(raw, "requestBody", defaults.requestBody)
        val contentType = optionalString(raw, "contentType", defaults.contentType)
        val localeTag = optionalString(raw, "localeTag", defaults.localeTag)
        val voiceId = optionalString(raw, "voiceId", defaults.voiceId)
        val modelName = optionalString(raw, "modelName", defaults.modelName)
        val pipeline = normalizePipeline(raw["responsePipeline"])
        issue =
            issue || url.hasIssue || apiKey.hasIssue || headers.hasIssue || methodField.hasIssue ||
                normalizedMethod != methodField.value || requestBody.hasIssue || contentType.hasIssue ||
                localeTag.hasIssue || voiceId.hasIssue || modelName.hasIssue || pipeline.hasIssue

        val value =
            SpeechServicesPreferences.TtsHttpConfig(
                urlTemplate = url.value,
                apiKey = apiKey.value,
                headers = headers.value,
                httpMethod = method,
                requestBody = requestBody.value,
                contentType = contentType.value,
                localeTag = localeTag.value,
                voiceId = voiceId.value,
                modelName = modelName.value,
                responsePipeline = pipeline.value,
            )
        val encoded = json.encodeToJsonElement(value).jsonObject
        val merged = replaceKnownFields(raw, encoded, ttsHttpFields).toMutableMap()
        if (pipeline.value.isEmpty()) {
            merged.remove("responsePipeline")
        } else {
            merged["responsePipeline"] = pipeline.raw
        }
        return NormalizedTtsHttp(value, JsonObject(merged), issue)
    }

    private fun normalizeVits(element: JsonElement?): NormalizedVits {
        val defaults = SpeechServicesPreferences.DEFAULT_VITS_TTS_PACKAGE_CONFIG
        val raw = element as? JsonObject ?: JsonObject(emptyMap())
        val packagePath = optionalString(raw, "packagePath", defaults.packagePath)
        val speakerId = optionalString(raw, "speakerId", defaults.speakerId)
        val options = stringMap(raw["options"], required = false)
        val issue =
            element !is JsonObject || packagePath.hasIssue || speakerId.hasIssue || options.hasIssue
        val value =
            SpeechServicesPreferences.VitsTtsPackageConfig(
                packagePath = packagePath.value,
                speakerId = speakerId.value,
                options = options.value,
            )
        return NormalizedVits(
            value,
            replaceKnownFields(raw, json.encodeToJsonElement(value).jsonObject, vitsFields),
            issue,
        )
    }

    private fun normalizeSttHttp(element: JsonElement?): NormalizedSttHttp {
        val defaults = SpeechServicesPreferences.DEFAULT_STT_HTTP_PRESET
        val raw = element as? JsonObject ?: JsonObject(emptyMap())
        val endpoint = requiredString(raw, "endpointUrl", defaults.endpointUrl)
        val apiKey = requiredString(raw, "apiKey", defaults.apiKey)
        val model = requiredString(raw, "modelName", defaults.modelName)
        val issue = element !is JsonObject || endpoint.hasIssue || apiKey.hasIssue || model.hasIssue
        val value =
            SpeechServicesPreferences.SttHttpConfig(endpoint.value, apiKey.value, model.value)
        return NormalizedSttHttp(
            value,
            replaceKnownFields(raw, json.encodeToJsonElement(value).jsonObject, sttHttpFields),
            issue,
        )
    }

    private fun normalizePipeline(element: JsonElement?): NormalizedPipeline {
        if (element == null) return NormalizedPipeline(emptyList(), JsonArray(emptyList()), false)
        val array = element as? JsonArray
            ?: return NormalizedPipeline(emptyList(), JsonArray(emptyList()), true)
        var issue = false
        val values = mutableListOf<HttpTtsResponsePipelineStep>()
        val rawValues = mutableListOf<JsonObject>()
        array.forEach { item ->
            val raw = item as? JsonObject
            if (raw == null) {
                issue = true
                return@forEach
            }
            val typeField = requiredString(raw, "type", "")
            val type = typeField.value.trim().lowercase(Locale.ROOT)
            val pathField = optionalString(raw, "path", "")
            val path = pathField.value.trim()
            val headers = stringMap(raw["headers"], required = false)
            val valid =
                type in HttpTtsResponsePipelineStep.SUPPORTED_TYPES &&
                    (type != HttpTtsResponsePipelineStep.TYPE_PICK || isValidPickPath(path))
            if (!valid) {
                issue = true
                return@forEach
            }
            issue =
                issue || typeField.hasIssue || type != typeField.value || pathField.hasIssue ||
                    path != pathField.value || headers.hasIssue
            val step = HttpTtsResponsePipelineStep(type, path, headers.value)
            values += step
            rawValues +=
                replaceKnownFields(raw, json.encodeToJsonElement(step).jsonObject, pipelineFields)
        }
        return NormalizedPipeline(values, JsonArray(rawValues), issue)
    }

    private fun normalizeCleanerRegexs(element: JsonElement?): Pair<List<String>, Boolean> {
        val array = element as? JsonArray
            ?: return SpeechServicesPreferences.DEFAULT_TTS_CLEANER_REGEXS to true
        var issue = false
        val seen = linkedSetOf<String>()
        array.forEach { item ->
            val primitive = item as? JsonPrimitive
            val value = primitive?.takeIf { it.isString }?.contentOrNull
            val normalized = value?.trim()
            if (normalized.isNullOrEmpty() || normalized in seen) {
                issue = true
                return@forEach
            }
            try {
                // Invalid persisted patterns would otherwise fail later while a speech request runs.
                Regex(normalized)
                seen += normalized
                if (normalized != value) issue = true
            } catch (_: Exception) {
                issue = true
            }
        }
        return seen.toList() to issue
    }

    private fun normalizeSpeechScalar(element: JsonElement?, default: Float): Pair<Float, Boolean> {
        val primitive = element as? JsonPrimitive
        val value = primitive?.takeUnless { it.isString }?.floatOrNull
        return if (value != null && value.isFinite() && value in 0.5f..2.0f) {
            value to false
        } else {
            default to true
        }
    }

    private fun normalizeCreatedAt(element: JsonElement?, now: Long): Pair<Long, Boolean> {
        val primitive = element as? JsonPrimitive
        val value = primitive?.takeUnless { it.isString }?.longOrNull
        return if (value != null && value > 0L) value to false else now to true
    }

    private fun normalizeUpdatedAt(element: JsonElement?, createdAt: Long): Pair<Long, Boolean> {
        val primitive = element as? JsonPrimitive
        val value = primitive?.takeUnless { it.isString }?.longOrNull
        return if (value != null && value >= createdAt) value to false else createdAt to true
    }

    private fun requiredString(raw: JsonObject, key: String, default: String): StringField {
        val primitive = raw[key] as? JsonPrimitive
        val value = primitive?.takeIf { it.isString }?.contentOrNull
        return if (value == null) StringField(default, true) else StringField(value, false)
    }

    private fun optionalString(raw: JsonObject, key: String, default: String): StringField {
        val element = raw[key] ?: return StringField(default, false)
        val primitive = element as? JsonPrimitive
        val value = primitive?.takeIf { it.isString }?.contentOrNull
        return if (value == null) StringField(default, true) else StringField(value, false)
    }

    private fun stringMap(element: JsonElement?, required: Boolean): StringMapField {
        if (element == null) return StringMapField(emptyMap(), required)
        val raw = element as? JsonObject ?: return StringMapField(emptyMap(), true)
        var issue = false
        val values = linkedMapOf<String, String>()
        raw.forEach { (key, item) ->
            val primitive = item as? JsonPrimitive
            val value = primitive?.takeIf { it.isString }?.contentOrNull
            if (value == null) issue = true else values[key] = value
        }
        return StringMapField(values, issue)
    }

    private fun normalizeCurrentId(
        raw: Any?,
        validIds: List<String>,
        migrationPending: Boolean,
        keyName: String,
        issues: MutableSet<String>,
    ): String {
        check(validIds.isNotEmpty()) { "Speech profile normalization produced an empty domain" }
        val text = raw as? String
        val normalized = text?.trim()
        if (normalized != null && normalized in validIds) {
            if (normalized != text) issues += keyName
            return normalized
        }
        if (raw != null || !migrationPending) issues += keyName
        return validIds.first()
    }

    private fun seedTts(version: Int, inputs: RepairInputs): TtsProfile {
        val published = inputs.published
        return if (version < FORMAT_VERSION && published?.hasTtsValues == true) {
            TtsProfile(
                LEGACY_TTS_PROFILE_ID,
                inputs.migratedTtsName,
                published.ttsServiceType,
                published.ttsHttpConfig,
                published.ttsVitsPackageConfig,
                published.ttsCleanerRegexs,
                published.ttsSpeechRate,
                published.ttsPitch,
                inputs.now,
                inputs.now,
            )
        } else {
            TtsProfile(
                DEFAULT_TTS_PROFILE_ID,
                inputs.defaultTtsName,
                SpeechServicesPreferences.DEFAULT_TTS_SERVICE_TYPE,
                SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET,
                SpeechServicesPreferences.DEFAULT_VITS_TTS_PACKAGE_CONFIG,
                SpeechServicesPreferences.DEFAULT_TTS_CLEANER_REGEXS,
                SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE,
                SpeechServicesPreferences.DEFAULT_TTS_PITCH,
                inputs.now,
                inputs.now,
            )
        }
    }

    private fun seedStt(version: Int, inputs: RepairInputs): SttProfile {
        val published = inputs.published
        return if (version < FORMAT_VERSION && published?.hasSttValues == true) {
            SttProfile(
                LEGACY_STT_PROFILE_ID,
                inputs.migratedSttName,
                published.sttServiceType,
                published.sttHttpConfig,
                inputs.now,
                inputs.now,
            )
        } else {
            SttProfile(
                DEFAULT_STT_PROFILE_ID,
                inputs.defaultSttName,
                SpeechServicesPreferences.DEFAULT_STT_SERVICE_TYPE,
                SpeechServicesPreferences.DEFAULT_STT_HTTP_PRESET,
                inputs.now,
                inputs.now,
            )
        }
    }

    private fun normalizedSeedTts(
        version: Int,
        inputs: RepairInputs,
    ): NormalizedProfile<TtsProfile> {
        val seed = seedTts(version, inputs)
        val normalized =
            normalizeTtsProfile(
                raw = json.encodeToJsonElement(seed).jsonObject,
                usedIds = linkedSetOf(),
                nextRepairId = { error("Canonical TTS seed has an invalid ID") },
                inputs = inputs,
            )
        return normalized.copy(hasIssue = false)
    }

    private fun normalizedSeedStt(
        version: Int,
        inputs: RepairInputs,
    ): NormalizedProfile<SttProfile> {
        val seed = seedStt(version, inputs)
        val normalized =
            normalizeSttProfile(
                raw = json.encodeToJsonElement(seed).jsonObject,
                usedIds = linkedSetOf(),
                nextRepairId = { error("Canonical STT seed has an invalid ID") },
                inputs = inputs,
            )
        return normalized.copy(hasIssue = false)
    }

    private fun deterministicRepairId(
        prefix: String,
        ordinal: Int,
        usedIds: Set<String>,
    ): String {
        val base = "$prefix-$ordinal"
        if (base !in usedIds) return base
        var suffix = 2
        while ("$base-$suffix" in usedIds) suffix++
        return "$base-$suffix"
    }

    private fun decodeInvariantState(preferences: Preferences): InvariantState {
        val stored = decodeStoredState(preferences)
        val currentTts = stored.ttsProfiles.first { it.value.id == stored.currentTtsId }.value
        val currentStt = stored.sttProfiles.first { it.value.id == stored.currentSttId }.value
        return InvariantState(
            stored.ttsProfiles.map { it.value },
            stored.sttProfiles.map { it.value },
            currentTts,
            currentStt,
        )
    }

    private fun decodeStoredState(preferences: Preferences): StoredState {
        val values = valuesByName(preferences)
        val version = values[MIGRATION_VERSION_KEY.name]
        check(version == FORMAT_VERSION) { "Speech profiles are not initialized" }
        val tts = decodeTtsProfiles(values[TTS_PROFILES.name])
        val stt = decodeSttProfiles(values[STT_PROFILES.name])
        check(tts.isNotEmpty() && stt.isNotEmpty()) { "Speech profile domain is empty" }
        val currentTts = values[CURRENT_TTS_PROFILE_ID.name] as? String
            ?: error("Current TTS profile ID is missing")
        val currentStt = values[CURRENT_STT_PROFILE_ID.name] as? String
            ?: error("Current STT profile ID is missing")
        check(tts.any { it.value.id == currentTts }) { "Current TTS profile is missing: $currentTts" }
        check(stt.any { it.value.id == currentStt }) { "Current STT profile is missing: $currentStt" }
        return StoredState(tts, stt, currentTts, currentStt)
    }

    private fun decodeTtsProfiles(raw: Any?): List<StoredProfile<TtsProfile>> {
        val array = json.parseToJsonElement(raw as? String ?: error("TTS profiles are missing")) as JsonArray
        return array.map { element ->
            val objectValue = element as JsonObject
            StoredProfile(json.decodeFromJsonElement<TtsProfile>(objectValue), objectValue)
        }
    }

    private fun decodeSttProfiles(raw: Any?): List<StoredProfile<SttProfile>> {
        val array = json.parseToJsonElement(raw as? String ?: error("STT profiles are missing")) as JsonArray
        return array.map { element ->
            val objectValue = element as JsonObject
            StoredProfile(json.decodeFromJsonElement<SttProfile>(objectValue), objectValue)
        }
    }

    private fun validateTtsForWrite(profile: TtsProfile): TtsProfile {
        val rate = requireSpeechScalar(profile.speechRate, "TTS speech rate")
        val pitch = requireSpeechScalar(profile.pitch, "TTS pitch")
        val cleaners =
            profile.cleanerRegexs.map { value ->
                value.trim().also {
                    require(it.isNotEmpty()) { "TTS cleaner expression is empty" }
                    Regex(it)
                }
            }.distinct()
        val method = profile.httpConfig.httpMethod.trim().uppercase(Locale.ROOT)
        require(method == "GET" || method == "POST") { "Unsupported TTS HTTP method" }
        val pipeline =
            profile.httpConfig.responsePipeline.map { step ->
                val type = step.normalizedType
                require(type in HttpTtsResponsePipelineStep.SUPPORTED_TYPES) {
                    "Unsupported TTS response pipeline step"
                }
                val path = step.path.trim()
                require(type != HttpTtsResponsePipelineStep.TYPE_PICK || path.isNotEmpty()) {
                    "TTS response pipeline pick step requires a path"
                }
                if (type == HttpTtsResponsePipelineStep.TYPE_PICK) {
                    HttpTtsResponsePipelineStep.requireValidPickPath(path)
                }
                step.copy(type = type, path = path)
            }
        return profile.copy(
            name = requireProfileName(profile.name),
            httpConfig = profile.httpConfig.copy(httpMethod = method, responsePipeline = pipeline),
            cleanerRegexs = cleaners,
            speechRate = rate,
            pitch = pitch,
        )
    }

    private fun validateSttForWrite(profile: SttProfile): SttProfile =
        profile.copy(name = requireProfileName(profile.name))

    private fun requireProfileName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Speech profile name is empty" } }

    private fun requireSpeechScalar(value: Float, label: String): Float {
        require(value.isFinite() && value in 0.5f..2.0f) {
            "$label must be between 0.5 and 2.0"
        }
        return value
    }

    private fun isValidPickPath(path: String): Boolean {
        if (path.isEmpty()) return false
        return try {
            HttpTtsResponsePipelineStep.requireValidPickPath(path)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun mergeTtsProfile(raw: JsonObject, profile: TtsProfile): JsonObject {
        val encoded = json.encodeToJsonElement(profile).jsonObject
        val merged = replaceKnownFields(raw, encoded, ttsProfileFields).toMutableMap()
        merged["httpConfig"] =
            mergeTtsHttp(raw["httpConfig"] as? JsonObject ?: JsonObject(emptyMap()), profile.httpConfig)
        merged["vitsConfig"] =
            replaceKnownFields(
                raw["vitsConfig"] as? JsonObject ?: JsonObject(emptyMap()),
                json.encodeToJsonElement(profile.vitsConfig).jsonObject,
                vitsFields,
            )
        return JsonObject(merged)
    }

    private fun mergeSttProfile(raw: JsonObject, profile: SttProfile): JsonObject {
        val encoded = json.encodeToJsonElement(profile).jsonObject
        val merged = replaceKnownFields(raw, encoded, sttProfileFields).toMutableMap()
        merged["httpConfig"] =
            replaceKnownFields(
                raw["httpConfig"] as? JsonObject ?: JsonObject(emptyMap()),
                json.encodeToJsonElement(profile.httpConfig).jsonObject,
                sttHttpFields,
            )
        return JsonObject(merged)
    }

    private fun mergeTtsHttp(
        raw: JsonObject,
        config: SpeechServicesPreferences.TtsHttpConfig,
    ): JsonObject {
        val encoded = json.encodeToJsonElement(config).jsonObject
        val merged = replaceKnownFields(raw, encoded, ttsHttpFields).toMutableMap()
        val encodedPipeline = encoded["responsePipeline"] as? JsonArray
        if (encodedPipeline == null) {
            merged.remove("responsePipeline")
        } else {
            val rawPipeline = raw["responsePipeline"] as? JsonArray
            merged["responsePipeline"] =
                if (rawPipeline != null && rawPipeline.size == encodedPipeline.size) {
                    JsonArray(
                        encodedPipeline.indices.map { index ->
                            val rawStep = rawPipeline[index] as? JsonObject ?: JsonObject(emptyMap())
                            val encodedStep = encodedPipeline[index] as JsonObject
                            replaceKnownFields(rawStep, encodedStep, pipelineFields)
                        }
                    )
                } else {
                    encodedPipeline
                }
        }
        return JsonObject(merged)
    }

    private fun replaceKnownFields(
        raw: JsonObject,
        encoded: JsonObject,
        knownFields: Set<String>,
    ): JsonObject {
        val merged = raw.toMutableMap()
        knownFields.forEach { key ->
            val value = encoded[key]
            if (value == null) merged.remove(key) else merged[key] = value
        }
        return JsonObject(merged)
    }

    private fun writeTtsState(
        current: Preferences,
        profiles: List<StoredProfile<TtsProfile>>,
        currentId: String,
    ): Preferences {
        val mutable = current.toMutablePreferences()
        replacePreference(mutable, TTS_PROFILES, JsonArray(profiles.map { it.raw }).toString())
        replacePreference(mutable, CURRENT_TTS_PROFILE_ID, currentId)
        return mutable.toPreferences()
    }

    private fun writeSttState(
        current: Preferences,
        profiles: List<StoredProfile<SttProfile>>,
        currentId: String,
    ): Preferences {
        val mutable = current.toMutablePreferences()
        replacePreference(mutable, STT_PROFILES, JsonArray(profiles.map { it.raw }).toString())
        replacePreference(mutable, CURRENT_STT_PROFILE_ID, currentId)
        return mutable.toPreferences()
    }

    private fun writeString(
        current: Preferences,
        key: Preferences.Key<String>,
        value: String,
    ): Preferences {
        val mutable = current.toMutablePreferences()
        replacePreference(mutable, key, value)
        return mutable.toPreferences()
    }

    private fun replacePreference(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        value: String,
    ) {
        removeByName(preferences, key.name)
        preferences[key] = value
    }

    private fun removeByName(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        name: String,
    ) {
        preferences.asMap().keys.filter { it.name == name }.forEach { key ->
            preferences.remove(key)
        }
    }

    private fun valuesByName(preferences: Preferences): Map<String, Any> =
        preferences.asMap().entries.associate { it.key.name to it.value }
}
