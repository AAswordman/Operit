package com.ai.assistance.operit.api.speech.tts

import android.content.Context
import com.ai.assistance.operit.api.speech.tts.prefetch.writeTtsAudio
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.tts.prefetch.PrefetchingTtsService
import com.ai.assistance.operit.api.speech.tts.prefetch.executeTtsRequest
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 硅基流动的合成请求与播放器分离，分句请求交给公共预取队列。 */
class SiliconFlowVoiceProvider(
    private val context: Context,
    private val apiKey: String,
    initialVoiceId: String,
    private val initialModelName: String = "",
) : PrefetchingTtsService {
    companion object {
        private const val TAG = "SiliconFlowVoiceProvider"
        private const val API_URL = "https://api.siliconflow.cn/v1/audio/speech"
        private const val RESPONSE_FORMAT = "mp3"
        private const val SAMPLE_RATE = 32000
        private const val GAIN = 0

        // 可用音色列表 - 根据硅基流动官方文档
        // 注意：现在只列出音色名称，模型在设置中独立配置
        fun getAvailableVoices(context: Context): List<TtsService.Voice> = listOf(
            TtsService.Voice("alex", context.getString(R.string.siliconflow_voice_alex), "zh-CN", "MALE"),
            TtsService.Voice("benjamin", context.getString(R.string.siliconflow_voice_benjamin), "zh-CN", "MALE"),
            TtsService.Voice("charles", context.getString(R.string.siliconflow_voice_charles), "zh-CN", "MALE"),
            TtsService.Voice("david", context.getString(R.string.siliconflow_voice_david), "zh-CN", "MALE"),
            TtsService.Voice("anna", context.getString(R.string.siliconflow_voice_anna), "zh-CN", "FEMALE"),
            TtsService.Voice("bella", context.getString(R.string.siliconflow_voice_bella), "zh-CN", "FEMALE"),
            TtsService.Voice("claire", context.getString(R.string.siliconflow_voice_claire), "zh-CN", "FEMALE"),
            TtsService.Voice("diana", context.getString(R.string.siliconflow_voice_diana), "zh-CN", "FEMALE")
        )
        val DEFAULT_VOICE_ID = "charles"
    }


    private var voiceId = initialVoiceId.ifBlank { DEFAULT_VOICE_ID }
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean get() = _isInitialized.value
    private val playback = QueuedTtsPlayback(TAG) { fetchAudioFile(it) }
    override val isSpeaking: Boolean get() = playback.isSpeaking
    override val speakingStateFlow: Flow<Boolean> get() = playback.speakingStateFlow
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                throw TtsException(context.getString(R.string.siliconflow_error_api_key_not_set))
            }
            if (voiceId.isBlank()) {
                throw TtsException(context.getString(R.string.siliconflow_error_voice_id_not_set))
            }

            _isInitialized.value = true
            AppLogger.i(TAG, "硅基流动TTS初始化成功")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "硅基流动TTS初始化失败", e)
            _isInitialized.value = false
            if (e is TtsException) throw e
            throw TtsException(context.getString(R.string.siliconflow_error_init_failed), cause = e)
        }
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>,
    ): Boolean = playback.speak(text, interrupt, rate, pitch, mapOf("voice" to voiceId) + extraParams)

    private suspend fun fetchAudioFile(request: QueuedTtsPlayback.Request): File? {
        if (!isInitialized && !initialize()) return null
        try {
            val effectiveRate = request.rate ?: SpeechServicesPreferences(context.applicationContext).ttsSpeechRateFlow.first()
            val input = request.text.replace(Regex("<[^>]+>"), "").trim()
            if (input.isBlank()) return null
            val model = request.extraParams["model"] ?: initialModelName.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
            val voice = (request.extraParams["voice"] ?: voiceId).trim()
            val voiceValue = if (voice.startsWith("speech:") || voice.contains(":")) voice else "$model:$voice"
            val body = buildJsonObject {
                put("model", model)
                put("input", input)
                put("voice", voiceValue)
                put("response_format", RESPONSE_FORMAT)
                put("sample_rate", SAMPLE_RATE)
                put("speed", effectiveRate.toDouble())
                put("gain", GAIN)
            }
            val httpRequest = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.executeTtsRequest(httpRequest)
            if (!response.isSuccessful) {
                throw TtsException(
                    message = "TTS request failed with code ${response.code}",
                    httpStatusCode = response.code,
                    errorBody = response.text(),
                )
            }
            return File(context.cacheDir, "siliconflow_tts_${UUID.randomUUID()}.mp3").writeTtsAudio(response.bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e(TAG, "硅基流动TTS合成失败", error)
            if (error is TtsException) throw error
            throw TtsException("TTS speak failed", cause = error)
        }
    }

    override suspend fun stop(): Boolean = playback.stop()
    override suspend fun pause(): Boolean = playback.pause()
    override suspend fun resume(): Boolean = playback.resume()
    override fun shutdown() {
        playback.shutdown()
        _isInitialized.value = false
    }
    override suspend fun getAvailableVoices(): List<TtsService.Voice> = Companion.getAvailableVoices(context)
    override suspend fun setVoice(voiceId: String): Boolean {
        if (Companion.getAvailableVoices(context).any { it.id == voiceId } || voiceId.startsWith("speech:")) {
            this.voiceId = voiceId
            AppLogger.d(TAG, "设置音色: $voiceId")
            return true
        }
        AppLogger.w(TAG, "不支持的音色ID: $voiceId")
        return false
    }
}
