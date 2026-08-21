package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
        val ttsHttpRaw: JsonObject,
        val ttsVitsRaw: JsonObject,
        val sttHttpRaw: JsonObject,
    )

    private val dataStore
        get() = context.speechServicesDataStore
    private val serializerJson = Json { ignoreUnknownKeys = true }

    private val tag = "SpeechServicesPrefs"
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
            "responsePipeline"
        )
    private val vitsFields = setOf("packagePath", "speakerId", "options")
    private val sttHttpFields = setOf("endpointUrl", "apiKey", "modelName")
    private val pipelineFields = setOf("type", "path", "headers")

    private data class NormalizedJson<T>(
        val value: T,
        val raw: JsonObject,
        val hasIssue: Boolean
    )

    private data class StringField(val value: String, val hasIssue: Boolean)

    private data class StringMapField(
        val value: Map<String, String>,
        val hasIssue: Boolean
    )

    private data class PipelineField(
        val value: List<HttpTtsResponsePipelineStep>,
        val raw: JsonArray,
        val hasIssue: Boolean
    )

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

    private fun normalizeTtsHttpJson(rawText: String): NormalizedJson<TtsHttpConfig> {
        val element =
            try {
                serializerJson.parseToJsonElement(rawText)
            } catch (e: Exception) {
                AppLogger.e(tag, "Repairing unreadable HTTP TTS configuration", e)
                return defaultTtsHttpRepair()
            }
        val raw = element as? JsonObject ?: return defaultTtsHttpRepair()
        val defaults = DEFAULT_HTTP_TTS_PRESET
        var hasIssue = false
        val url = requiredString(raw, "urlTemplate", defaults.urlTemplate)
        val apiKey = requiredString(raw, "apiKey", defaults.apiKey)
        val headers = stringMap(raw["headers"], required = true)
        val methodField = optionalString(raw, "httpMethod", defaults.httpMethod)
        val normalizedMethod = methodField.value.trim().uppercase(Locale.ROOT)
        val method =
            normalizedMethod.takeIf { it == "GET" || it == "POST" }
                ?: defaults.httpMethod.also { hasIssue = true }
        val requestBody = optionalString(raw, "requestBody", defaults.requestBody)
        val contentType = optionalString(raw, "contentType", defaults.contentType)
        val localeTag = optionalString(raw, "localeTag", defaults.localeTag)
        val voiceId = optionalString(raw, "voiceId", defaults.voiceId)
        val modelName = optionalString(raw, "modelName", defaults.modelName)
        val pipeline = normalizePipeline(raw["responsePipeline"])
        hasIssue =
            hasIssue || url.hasIssue || apiKey.hasIssue || headers.hasIssue ||
                methodField.hasIssue || normalizedMethod != methodField.value ||
                requestBody.hasIssue || contentType.hasIssue || localeTag.hasIssue ||
                voiceId.hasIssue || modelName.hasIssue || pipeline.hasIssue

        val value =
            TtsHttpConfig(
                urlTemplate = url.value,
                apiKey = apiKey.value,
                headers = headers.value,
                httpMethod = method,
                requestBody = requestBody.value,
                contentType = contentType.value,
                localeTag = localeTag.value,
                voiceId = voiceId.value,
                modelName = modelName.value,
                responsePipeline = pipeline.value
            )
        val merged =
            replaceKnownFields(
                raw,
                serializerJson.encodeToJsonElement(value).jsonObject,
                ttsHttpFields
            ).toMutableMap()
        if (pipeline.value.isNotEmpty()) merged["responsePipeline"] = pipeline.raw
        return NormalizedJson(value, JsonObject(merged), hasIssue)
    }

    private fun defaultTtsHttpRepair(): NormalizedJson<TtsHttpConfig> =
        NormalizedJson(
            DEFAULT_HTTP_TTS_PRESET,
            serializerJson.encodeToJsonElement(DEFAULT_HTTP_TTS_PRESET).jsonObject,
            true
        )

    private fun normalizeVitsJson(rawText: String): NormalizedJson<VitsTtsPackageConfig> {
        val element =
            try {
                serializerJson.parseToJsonElement(rawText)
            } catch (e: Exception) {
                AppLogger.e(tag, "Repairing unreadable VITS TTS configuration", e)
                return defaultVitsRepair()
            }
        val raw = element as? JsonObject ?: return defaultVitsRepair()
        val defaults = DEFAULT_VITS_TTS_PACKAGE_CONFIG
        val packagePath = optionalString(raw, "packagePath", defaults.packagePath)
        val speakerId = optionalString(raw, "speakerId", defaults.speakerId)
        val options = stringMap(raw["options"], required = false)
        val value = VitsTtsPackageConfig(packagePath.value, speakerId.value, options.value)
        return NormalizedJson(
            value,
            replaceKnownFields(
                raw,
                serializerJson.encodeToJsonElement(value).jsonObject,
                vitsFields
            ),
            packagePath.hasIssue || speakerId.hasIssue || options.hasIssue
        )
    }

    private fun defaultVitsRepair(): NormalizedJson<VitsTtsPackageConfig> =
        NormalizedJson(
            DEFAULT_VITS_TTS_PACKAGE_CONFIG,
            serializerJson.encodeToJsonElement(DEFAULT_VITS_TTS_PACKAGE_CONFIG).jsonObject,
            true
        )

    private fun normalizeSttHttpJson(rawText: String): NormalizedJson<SttHttpConfig> {
        val element =
            try {
                serializerJson.parseToJsonElement(rawText)
            } catch (e: Exception) {
                AppLogger.e(tag, "Repairing unreadable HTTP STT configuration", e)
                return defaultSttHttpRepair()
            }
        val raw = element as? JsonObject ?: return defaultSttHttpRepair()
        val defaults = DEFAULT_STT_HTTP_PRESET
        val endpoint = requiredString(raw, "endpointUrl", defaults.endpointUrl)
        val apiKey = requiredString(raw, "apiKey", defaults.apiKey)
        val modelName = requiredString(raw, "modelName", defaults.modelName)
        val value = SttHttpConfig(endpoint.value, apiKey.value, modelName.value)
        return NormalizedJson(
            value,
            replaceKnownFields(
                raw,
                serializerJson.encodeToJsonElement(value).jsonObject,
                sttHttpFields
            ),
            endpoint.hasIssue || apiKey.hasIssue || modelName.hasIssue
        )
    }

    private fun defaultSttHttpRepair(): NormalizedJson<SttHttpConfig> =
        NormalizedJson(
            DEFAULT_STT_HTTP_PRESET,
            serializerJson.encodeToJsonElement(DEFAULT_STT_HTTP_PRESET).jsonObject,
            true
        )

    private fun normalizePipeline(element: JsonElement?): PipelineField {
        if (element == null) return PipelineField(emptyList(), JsonArray(emptyList()), false)
        val array = element as? JsonArray
            ?: return PipelineField(emptyList(), JsonArray(emptyList()), true)
        var hasIssue = false
        val values = mutableListOf<HttpTtsResponsePipelineStep>()
        val rawValues = mutableListOf<JsonObject>()
        array.forEach { item ->
            val raw = item as? JsonObject
            if (raw == null) {
                hasIssue = true
                return@forEach
            }
            val typeField = requiredString(raw, "type", "")
            val type = typeField.value.trim().lowercase(Locale.ROOT)
            val pathField = optionalString(raw, "path", "")
            val path = pathField.value.trim()
            val headers = stringMap(raw["headers"], required = false)
            var valid = type in HttpTtsResponsePipelineStep.SUPPORTED_TYPES
            if (valid && type == HttpTtsResponsePipelineStep.TYPE_PICK) {
                valid =
                    try {
                        HttpTtsResponsePipelineStep.requireValidPickPath(path)
                        path.isNotEmpty()
                    } catch (e: IllegalArgumentException) {
                        AppLogger.e(tag, "Repairing invalid TTS response pipeline path", e)
                        false
                    }
            }
            if (!valid) {
                hasIssue = true
                return@forEach
            }
            hasIssue =
                hasIssue || typeField.hasIssue || type != typeField.value ||
                    pathField.hasIssue || path != pathField.value || headers.hasIssue
            val step = HttpTtsResponsePipelineStep(type, path, headers.value)
            values += step
            rawValues +=
                replaceKnownFields(
                    raw,
                    serializerJson.encodeToJsonElement(step).jsonObject,
                    pipelineFields
                )
        }
        return PipelineField(values, JsonArray(rawValues), hasIssue)
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
        var hasIssue = false
        val values = linkedMapOf<String, String>()
        raw.forEach { (key, item) ->
            val primitive = item as? JsonPrimitive
            val value = primitive?.takeIf { it.isString }?.contentOrNull
            if (value == null) hasIssue = true else values[key] = value
        }
        return StringMapField(values, hasIssue)
    }

    private fun replaceKnownFields(
        raw: JsonObject,
        encoded: JsonObject,
        knownFields: Set<String>
    ): JsonObject {
        val merged = raw.toMutableMap()
        knownFields.forEach { key ->
            val value = encoded[key]
            if (value == null) merged.remove(key) else merged[key] = value
        }
        return JsonObject(merged)
    }

    private fun parseTtsHttpConfig(raw: String?): TtsHttpConfig {
        if (raw == null) return DEFAULT_HTTP_TTS_PRESET
        return normalizeTtsHttpJson(raw).value
    }

    private fun parseVitsConfig(raw: String?): VitsTtsPackageConfig {
        if (raw == null) return DEFAULT_VITS_TTS_PACKAGE_CONFIG
        return normalizeVitsJson(raw).value
    }

    private fun parseSttHttpConfig(raw: String?): SttHttpConfig {
        if (raw == null) return DEFAULT_STT_HTTP_PRESET
        return normalizeSttHttpJson(raw).value
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
        val ttsHttp =
            (values[TTS_HTTP_CONFIG.name] as? String)
                ?.let(::normalizeTtsHttpJson)
                ?: defaultTtsHttpRepair()
        val ttsVits =
            (values[TTS_VITS_PACKAGE_CONFIG.name] as? String)
                ?.let(::normalizeVitsJson)
                ?: defaultVitsRepair()
        val sttHttp =
            (values[STT_HTTP_CONFIG.name] as? String)
                ?.let(::normalizeSttHttpJson)
                ?: defaultSttHttpRepair()

        return PublishedSpeechState(
            hasTtsValues = values.keys.any(ttsKeys::contains),
            hasSttValues = values.keys.any(sttKeys::contains),
            ttsServiceType = parseTtsServiceType(values[TTS_SERVICE_TYPE.name] as? String),
            ttsHttpConfig = ttsHttp.value,
            ttsVitsPackageConfig = ttsVits.value,
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
            sttHttpConfig = sttHttp.value,
            ttsHttpRaw = ttsHttp.raw,
            ttsVitsRaw = ttsVits.raw,
            sttHttpRaw = sttHttp.raw,
        )
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
