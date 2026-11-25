package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.VoiceServiceFactory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.speechServicesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "speech_services_preferences")

/**
 * Legacy single-config store for speech services.
 *
 * New code manages profiles through [SpeechServiceProfilesPreferences]. This store remains the
 * active-profile projection consumed by existing providers and published-version integrations.
 */
class SpeechServicesPreferences(private val context: Context) {

    private val dataStore = context.speechServicesDataStore
    private val serializerJson = Json { ignoreUnknownKeys = true }

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
        val responsePipeline: List<HttpTtsResponsePipelineStep> = emptyList(),
        val options: Map<String, String> = emptyMap() // 预置 TTS 的专属可选参数（如阶跃的 response_format/volume/voice_label/instruction）
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

        private fun parseSttServiceType(raw: String?): SpeechServiceFactory.SpeechServiceType {
            if (raw == null) return DEFAULT_STT_SERVICE_TYPE
            if (raw == "SHERPA_MNN") return SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN
            return runCatching { SpeechServiceFactory.SpeechServiceType.valueOf(raw) }
                .getOrElse { DEFAULT_STT_SERVICE_TYPE }
        }
    }

    // --- TTS Flows ---
    val ttsServiceTypeFlow: Flow<VoiceServiceFactory.VoiceServiceType> = dataStore.data.map { prefs ->
        VoiceServiceFactory.VoiceServiceType.valueOf(
            prefs[TTS_SERVICE_TYPE] ?: DEFAULT_TTS_SERVICE_TYPE.name
        )
    }

    val ttsHttpConfigFlow: Flow<TtsHttpConfig> = dataStore.data.map { prefs ->
        val json = prefs[TTS_HTTP_CONFIG]
        if (json != null) {
            try {
                serializerJson.decodeFromString<TtsHttpConfig>(json)
            } catch (e: Exception) {
                DEFAULT_HTTP_TTS_PRESET // Fallback to default preset on parsing error
            }
        } else {
            DEFAULT_HTTP_TTS_PRESET
        }
    }

    val ttsVitsPackageConfigFlow: Flow<VitsTtsPackageConfig> = dataStore.data.map { prefs ->
        val json = prefs[TTS_VITS_PACKAGE_CONFIG]
        if (json == null) {
            DEFAULT_VITS_TTS_PACKAGE_CONFIG
        } else {
            serializerJson.decodeFromString<VitsTtsPackageConfig>(json)
        }
    }

    val ttsCleanerRegexsFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val storedRegexs = prefs[TTS_CLEANER_REGEXS]
        if (storedRegexs == null) {
            DEFAULT_TTS_CLEANER_REGEXS
        } else {
            storedRegexs.toList()
        }
    }

    val ttsSpeechRateFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_SPEECH_RATE] ?: DEFAULT_TTS_SPEECH_RATE
    }

    val ttsPitchFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_PITCH] ?: DEFAULT_TTS_PITCH
    }

    // --- STT Flows ---
    val sttServiceTypeFlow: Flow<SpeechServiceFactory.SpeechServiceType> = dataStore.data.map { prefs ->
        parseSttServiceType(prefs[STT_SERVICE_TYPE])
    }

    val sttHttpConfigFlow: Flow<SttHttpConfig> = dataStore.data.map { prefs ->
        val json = prefs[STT_HTTP_CONFIG]
        if (json != null) {
            try {
                serializerJson.decodeFromString<SttHttpConfig>(json)
            } catch (e: Exception) {
                DEFAULT_STT_HTTP_PRESET
            }
        } else {
            DEFAULT_STT_HTTP_PRESET
        }
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
        dataStore.edit { prefs ->
            prefs[TTS_SERVICE_TYPE] = serviceType.name

            // 系统 TTS 也从这份旧字段读取语言和音色，迁移投影必须保留它们。
            httpConfig?.let { prefs[TTS_HTTP_CONFIG] = serializerJson.encodeToString(it) }

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
