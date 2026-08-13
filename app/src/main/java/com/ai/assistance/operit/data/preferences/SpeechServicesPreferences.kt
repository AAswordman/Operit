package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.core.toPreferences
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.mergeNormalizedJsonFields
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.speechServicesDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "speech_services_preferences")

/**
 * Legacy single-config store for speech services.
 *
 * New code manages profiles through [SpeechServiceProfilesPreferences]. This released store is
 * retained only as the source for the one-time migration into independent profiles.
 */
class SpeechServicesPreferences(private val context: Context) {

    internal data class PublishedSpeechState(
        val hasTtsValues: Boolean,
        val hasSttValues: Boolean,
        val ttsServiceType: VoiceServiceFactory.VoiceServiceType,
        val ttsHttpConfig: TtsHttpConfig,
        val ttsVitsPackageConfig: VitsTtsPackageConfig,
        val ttsCleanerRegexs: List<String>,
        val ttsSpeechRate: Float,
        val ttsPitch: Float,
        val sttServiceType: SpeechServiceFactory.SpeechServiceType,
        val sttHttpConfig: SttHttpConfig,
    )

    private val dataStore
        get() = context.speechServicesDataStore
    private val serializerJson = Json { ignoreUnknownKeys = true }

    private val tag = "SpeechServicesPrefs"

    @Serializable
    data class TtsHttpConfig(
        val urlTemplate: String,
        val apiKey: String, // Keep apiKey for header-based auth
        val headers: Map<String, String>,
        val httpMethod: String = "GET", // HTTP方法：GET 或 POST
        val requestBody: String = "", // POST请求的body模板，支持占位符如{text}
        val contentType: String = "application/json", // POST请求的Content-Type
        val localeTag: String = "", // 通用 TTS 语言标签，如 zh-CN、en-US
        val voiceId: String = "", // 特定于TTS提供商的音色ID
        val modelName: String = "", // TTS模型名称（用于SiliconFlow等）
        val responsePipeline: List<HttpTtsResponsePipelineStep> = emptyList()
    )

    @Serializable
    data class VitsTtsPackageConfig(
        val packagePath: String = "",
        val speakerId: String = "",
        val options: Map<String, String> = emptyMap()
    )

    @Serializable
    data class SttHttpConfig(
        val endpointUrl: String,
        val apiKey: String,
        val modelName: String,
    )

    companion object {
        // TTS Preference Keys
        val TTS_SERVICE_TYPE = stringPreferencesKey("tts_service_type")
        val TTS_HTTP_CONFIG = stringPreferencesKey("tts_http_config")
        val TTS_VITS_PACKAGE_CONFIG = stringPreferencesKey("tts_vits_package_config")
        val TTS_CLEANER_REGEXS = stringSetPreferencesKey("tts_cleaner_regexs")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")

        // STT Preference Keys
        val STT_SERVICE_TYPE = stringPreferencesKey("stt_service_type")
        val STT_HTTP_CONFIG = stringPreferencesKey("stt_http_config")

        // Default Values
        val DEFAULT_TTS_SERVICE_TYPE = VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS
        val DEFAULT_STT_SERVICE_TYPE = SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN

        const val DEFAULT_TTS_SPEECH_RATE = 1.0f
        const val DEFAULT_TTS_PITCH = 1.0f

        // HTTP TTS的默认预设
        val DEFAULT_HTTP_TTS_PRESET = TtsHttpConfig(
            urlTemplate = "",
            apiKey = "",
            headers = emptyMap(),
            httpMethod = "GET",
            requestBody = "",
            contentType = "application/json",
            localeTag = "",
            voiceId = "",
            modelName = "",
            responsePipeline = emptyList()
        )

        val DEFAULT_VITS_TTS_PACKAGE_CONFIG = VitsTtsPackageConfig()

        val DEFAULT_STT_HTTP_PRESET = SttHttpConfig(
            endpointUrl = "https://api.openai.com/v1/audio/transcriptions",
            apiKey = "",
            modelName = "whisper-1",
        )

        // TTS Cleaner 的默认正则表达式列表（去除中英文括号内容）
        val DEFAULT_TTS_CLEANER_REGEXS = listOf(
            "\\([^)]+\\)",  // 英文括号
            "（[^）]+）"     // 中文括号
        )

        private fun normalizeSttServiceTypeName(raw: String?): String {
            if (raw == null) return DEFAULT_STT_SERVICE_TYPE.name
            if (raw == "SHERPA_MNN") return SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN.name
            return SpeechServiceFactory.SpeechServiceType.values()
                .firstOrNull { it.name == raw }
                ?.name
                ?: DEFAULT_STT_SERVICE_TYPE.name
        }

        private fun parseSttServiceType(raw: String?): SpeechServiceFactory.SpeechServiceType {
            if (raw == null) return DEFAULT_STT_SERVICE_TYPE
            if (raw == "SHERPA_MNN") return SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN
            return SpeechServiceFactory.SpeechServiceType.values()
                .firstOrNull { it.name == raw }
                ?: DEFAULT_STT_SERVICE_TYPE
        }
    }

    private fun parseTtsServiceType(raw: String?): VoiceServiceFactory.VoiceServiceType {
        if (raw == null) return DEFAULT_TTS_SERVICE_TYPE
        return VoiceServiceFactory.VoiceServiceType.values().firstOrNull { it.name == raw }
            ?: DEFAULT_TTS_SERVICE_TYPE.also {
                AppLogger.e(tag, "Invalid persisted TTS service type: $raw")
            }
    }

    private fun normalizeHttpConfig(config: TtsHttpConfig): TtsHttpConfig {
        val method = config.httpMethod.trim().uppercase(Locale.ROOT)
        require(method == "GET" || method == "POST") { "Unsupported TTS HTTP method" }
        val pipeline =
            config.responsePipeline.map { step ->
                val type = step.normalizedType
                require(type in HttpTtsResponsePipelineStep.SUPPORTED_TYPES) {
                    "Unsupported TTS response pipeline step"
                }
                require(type != HttpTtsResponsePipelineStep.TYPE_PICK || step.path.isNotBlank()) {
                    "TTS response pipeline pick step requires a path"
                }
                step.copy(type = type)
            }
        return config.copy(httpMethod = method, responsePipeline = pipeline)
    }

    private fun parseTtsHttpConfig(raw: String?): TtsHttpConfig {
        if (raw == null) return DEFAULT_HTTP_TTS_PRESET
        return try {
            normalizeHttpConfig(serializerJson.decodeFromString<TtsHttpConfig>(raw))
        } catch (e: Exception) {
            AppLogger.e(tag, "Invalid persisted HTTP TTS configuration", e)
            DEFAULT_HTTP_TTS_PRESET
        }
    }

    private fun parseVitsConfig(raw: String?): VitsTtsPackageConfig {
        if (raw == null) return DEFAULT_VITS_TTS_PACKAGE_CONFIG
        return try {
            serializerJson.decodeFromString<VitsTtsPackageConfig>(raw)
        } catch (e: Exception) {
            AppLogger.e(tag, "Invalid persisted VITS TTS configuration", e)
            DEFAULT_VITS_TTS_PACKAGE_CONFIG
        }
    }

    private fun parseSttHttpConfig(raw: String?): SttHttpConfig {
        if (raw == null) return DEFAULT_STT_HTTP_PRESET
        return try {
            serializerJson.decodeFromString<SttHttpConfig>(raw)
        } catch (e: Exception) {
            AppLogger.e(tag, "Invalid persisted HTTP STT configuration", e)
            DEFAULT_STT_HTTP_PRESET
        }
    }

    private fun validCleanerRegexs(values: Collection<*>): Set<String> =
        values.mapNotNull { value ->
            val regex = value as? String
            if (regex.isNullOrBlank()) {
                null
            } else {
                try {
                    Regex(regex)
                    regex
                } catch (e: Exception) {
                    AppLogger.e(tag, "Invalid persisted TTS cleaner regular expression", e)
                    null
                }
            }
        }.toSet()

    private fun isValidSpeechScalar(value: Float): Boolean = value.isFinite() && value in 0.5f..2.0f

    // --- TTS Flows ---
    val ttsServiceTypeFlow: Flow<VoiceServiceFactory.VoiceServiceType> = dataStore.data.map { prefs ->
        parseTtsServiceType(prefs[TTS_SERVICE_TYPE])
    }

    val ttsHttpConfigFlow: Flow<TtsHttpConfig> = dataStore.data.map { prefs ->
        parseTtsHttpConfig(prefs[TTS_HTTP_CONFIG])
    }

    val ttsVitsPackageConfigFlow: Flow<VitsTtsPackageConfig> = dataStore.data.map { prefs ->
        parseVitsConfig(prefs[TTS_VITS_PACKAGE_CONFIG])
    }

    val ttsCleanerRegexsFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val storedRegexs = prefs[TTS_CLEANER_REGEXS]
        if (storedRegexs == null) {
            DEFAULT_TTS_CLEANER_REGEXS
        } else {
            validCleanerRegexs(storedRegexs).toList()
        }
    }

    val ttsSpeechRateFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_SPEECH_RATE]?.takeIf(::isValidSpeechScalar) ?: DEFAULT_TTS_SPEECH_RATE
    }

    val ttsPitchFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_PITCH]?.takeIf(::isValidSpeechScalar) ?: DEFAULT_TTS_PITCH
    }

    // --- STT Flows ---
    val sttServiceTypeFlow: Flow<SpeechServiceFactory.SpeechServiceType> = dataStore.data.map { prefs ->
        parseSttServiceType(prefs[STT_SERVICE_TYPE])
    }

    val sttHttpConfigFlow: Flow<SttHttpConfig> = dataStore.data.map { prefs ->
        parseSttHttpConfig(prefs[STT_HTTP_CONFIG])
    }

    /** Reads every released speech setting from one Preferences snapshot for migration. */
    internal suspend fun readMigrationSeed(): PublishedSpeechState {
        val preferences = dataStore.data.first()
        val values = preferences.asMap().entries.associate { it.key.name to it.value }
        val ttsKeys =
            setOf(
                TTS_SERVICE_TYPE.name,
                TTS_HTTP_CONFIG.name,
                TTS_VITS_PACKAGE_CONFIG.name,
                TTS_CLEANER_REGEXS.name,
                TTS_SPEECH_RATE.name,
                TTS_PITCH.name,
            )
        val sttKeys = setOf(STT_SERVICE_TYPE.name, STT_HTTP_CONFIG.name)
        val cleanerValues = values[TTS_CLEANER_REGEXS.name] as? Set<*>

        return PublishedSpeechState(
            hasTtsValues = values.keys.any(ttsKeys::contains),
            hasSttValues = values.keys.any(sttKeys::contains),
            ttsServiceType = parseTtsServiceType(values[TTS_SERVICE_TYPE.name] as? String),
            ttsHttpConfig = parseTtsHttpConfig(values[TTS_HTTP_CONFIG.name] as? String),
            ttsVitsPackageConfig =
                parseVitsConfig(values[TTS_VITS_PACKAGE_CONFIG.name] as? String),
            ttsCleanerRegexs =
                if (cleanerValues == null) {
                    DEFAULT_TTS_CLEANER_REGEXS
                } else {
                    validCleanerRegexs(cleanerValues).toList()
                },
            ttsSpeechRate =
                (values[TTS_SPEECH_RATE.name] as? Float)
                    ?.takeIf(::isValidSpeechScalar)
                    ?: DEFAULT_TTS_SPEECH_RATE,
            ttsPitch =
                (values[TTS_PITCH.name] as? Float)
                    ?.takeIf(::isValidSpeechScalar)
                    ?: DEFAULT_TTS_PITCH,
            sttServiceType = parseSttServiceType(values[STT_SERVICE_TYPE.name] as? String),
            sttHttpConfig = parseSttHttpConfig(values[STT_HTTP_CONFIG.name] as? String),
        )
    }

    suspend fun repairPersistedState(): Boolean =
        repairPreferenceState(
            context = context,
            storeName = PreferenceStoreCatalog.SPEECH_SERVICES,
            dataStore = dataStore
        ) { current ->
            val values = current.asMap().entries.associate { it.key.name to it.value }
            val mutable = current.toMutablePreferences()
            val issues = linkedSetOf<String>()

            fun replace(key: Preferences.Key<String>, value: String) {
                mutable.asMap().keys.filter { it.name == key.name }.forEach { mutable.remove(it) }
                mutable[key] = value
                issues += key.name
            }

            fun replaceFloat(key: Preferences.Key<Float>, value: Float) {
                mutable.asMap().keys.filter { it.name == key.name }.forEach { mutable.remove(it) }
                mutable[key] = value
                issues += key.name
            }

            val rawTtsType = values[TTS_SERVICE_TYPE.name]
            if (rawTtsType != null) {
                val normalized =
                    (rawTtsType as? String)?.let { raw ->
                        VoiceServiceFactory.VoiceServiceType.values().firstOrNull { it.name == raw }?.name
                    } ?: DEFAULT_TTS_SERVICE_TYPE.name
                if (rawTtsType !is String || normalized != rawTtsType) {
                    replace(TTS_SERVICE_TYPE, normalized)
                }
            }

            val rawHttp = values[TTS_HTTP_CONFIG.name]
            if (rawHttp != null) {
                val rawHttpText = rawHttp as? String
                val parsed =
                    if (rawHttpText != null) {
                        try {
                            serializerJson.decodeFromString<TtsHttpConfig>(rawHttpText)
                        } catch (e: Exception) {
                            AppLogger.e(tag, "Repairing invalid HTTP TTS configuration", e)
                            null
                        }
                    } else {
                        null
                    }
                val normalized =
                    parsed?.let { config ->
                        try {
                            normalizeHttpConfig(config)
                        } catch (e: Exception) {
                            AppLogger.e(tag, "Repairing invalid HTTP TTS configuration", e)
                            null
                        }
                    }
                when {
                    normalized == null ->
                        replace(
                            TTS_HTTP_CONFIG,
                            serializerJson.encodeToString(DEFAULT_HTTP_TTS_PRESET)
                        )
                    normalized != parsed ->
                        replace(
                            TTS_HTTP_CONFIG,
                            mergeNormalizedJsonFields(
                                persisted = serializerJson.parseToJsonElement(requireNotNull(rawHttpText)),
                                decoded = serializerJson.encodeToJsonElement(requireNotNull(parsed)),
                                normalized = serializerJson.encodeToJsonElement(requireNotNull(normalized))
                            ).toString()
                        )
                }
            }

            val rawVits = values[TTS_VITS_PACKAGE_CONFIG.name]
            if (rawVits != null) {
                val valid =
                    if (rawVits is String) {
                        try {
                            serializerJson.decodeFromString<VitsTtsPackageConfig>(rawVits)
                            true
                        } catch (e: Exception) {
                            AppLogger.e(tag, "Repairing invalid VITS TTS configuration", e)
                            false
                        }
                    } else {
                        false
                    }
                if (!valid) {
                    replace(
                        TTS_VITS_PACKAGE_CONFIG,
                        serializerJson.encodeToString(DEFAULT_VITS_TTS_PACKAGE_CONFIG)
                    )
                }
            }

            val rawRegexs = values[TTS_CLEANER_REGEXS.name]
            if (rawRegexs != null) {
                val normalized = validCleanerRegexs((rawRegexs as? Set<*>) ?: emptySet<Any>())
                if (rawRegexs !is Set<*> || normalized != rawRegexs) {
                    mutable.asMap().keys
                        .filter { it.name == TTS_CLEANER_REGEXS.name }
                        .forEach { mutable.remove(it) }
                    mutable[TTS_CLEANER_REGEXS] = normalized
                    issues += TTS_CLEANER_REGEXS.name
                }
            }

            val rawRate = values[TTS_SPEECH_RATE.name]
            if (rawRate != null && (rawRate !is Float || !isValidSpeechScalar(rawRate))) {
                replaceFloat(TTS_SPEECH_RATE, DEFAULT_TTS_SPEECH_RATE)
            }
            val rawPitch = values[TTS_PITCH.name]
            if (rawPitch != null && (rawPitch !is Float || !isValidSpeechScalar(rawPitch))) {
                replaceFloat(TTS_PITCH, DEFAULT_TTS_PITCH)
            }

            val rawSttType = values[STT_SERVICE_TYPE.name]
            if (rawSttType != null) {
                val normalized = normalizeSttServiceTypeName(rawSttType as? String)
                if (rawSttType !is String || normalized != rawSttType) {
                    replace(STT_SERVICE_TYPE, normalized)
                }
            }

            val rawStt = values[STT_HTTP_CONFIG.name]
            if (rawStt != null) {
                val valid =
                    if (rawStt is String) {
                        try {
                            serializerJson.decodeFromString<SttHttpConfig>(rawStt)
                            true
                        } catch (e: Exception) {
                            AppLogger.e(tag, "Repairing invalid HTTP STT configuration", e)
                            false
                        }
                    } else {
                        false
                    }
                if (!valid) {
                    replace(
                        STT_HTTP_CONFIG,
                        serializerJson.encodeToString(DEFAULT_STT_HTTP_PRESET)
                    )
                }
            }

            PreferenceStateRepairResult(mutable.toPreferences(), issues)
        }

    // --- Save TTS Settings ---
    suspend fun saveTtsSettings(
        serviceType: VoiceServiceFactory.VoiceServiceType,
        httpConfig: TtsHttpConfig? = null,
        vitsConfig: VitsTtsPackageConfig? = null,
        cleanerRegexs: List<String>? = null,
        speechRate: Float? = null,
        pitch: Float? = null
    ) {
        require(speechRate == null || isValidSpeechScalar(speechRate)) { "TTS speech rate must be between 0.5 and 2.0" }
        require(pitch == null || isValidSpeechScalar(pitch)) { "TTS pitch must be between 0.5 and 2.0" }
        cleanerRegexs?.forEach { Regex(it) }
        val normalizedHttpConfig = httpConfig?.let(::normalizeHttpConfig)
        dataStore.edit { prefs ->
            prefs[TTS_SERVICE_TYPE] = serviceType.name

            // 系统 TTS 也从这份旧字段读取语言和音色，迁移投影必须保留它们。
            normalizedHttpConfig?.let { prefs[TTS_HTTP_CONFIG] = serializerJson.encodeToString(it) }

            cleanerRegexs?.let {
                prefs[TTS_CLEANER_REGEXS] = it.filter { regex -> regex.isNotBlank() }.toSet()
            }

            speechRate?.let { prefs[TTS_SPEECH_RATE] = it }
            pitch?.let { prefs[TTS_PITCH] = it }

            if (serviceType == VoiceServiceFactory.VoiceServiceType.VITS_TTS) {
                vitsConfig?.let { prefs[TTS_VITS_PACKAGE_CONFIG] = serializerJson.encodeToString(it) }
            }
        }
    }

    /** 只保存 TTS 清理正则列表 */
    suspend fun saveTtsCleanerRegexs(regexs: List<String>) {
        regexs.forEach { Regex(it) }
        dataStore.edit { prefs ->
            prefs[TTS_CLEANER_REGEXS] = regexs.filter { it.isNotBlank() }.toSet()
        }
    }

    // --- Save STT Settings ---
    suspend fun saveSttSettings(
        serviceType: SpeechServiceFactory.SpeechServiceType,
        httpConfig: SttHttpConfig? = null,
    ) {
        dataStore.edit { prefs ->
            prefs[STT_SERVICE_TYPE] = serviceType.name

            when (serviceType) {
                SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> {
                }
                SpeechServiceFactory.SpeechServiceType.OPENAI_STT -> {
                    httpConfig?.let { prefs[STT_HTTP_CONFIG] = serializerJson.encodeToString(it) }
                }
                SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT -> {
                    httpConfig?.let { prefs[STT_HTTP_CONFIG] = serializerJson.encodeToString(it) }
                }
            }
        }
    }
}
