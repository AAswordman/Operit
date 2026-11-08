package com.ai.assistance.operit.api.speech.tts

import com.ai.assistance.operit.api.speech.tts.prefetch.PrefetchingTtsService
import com.ai.assistance.operit.api.speech.tts.prefetch.executeTtsRequest
import kotlinx.coroutines.CancellationException
import android.content.Context
import android.media.AudioAttributes
import com.ai.assistance.operit.api.speech.tts.prefetch.writeTtsAudio
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAIVoiceProvider(
    private val context: Context,
    private val endpointUrl: String,
    private val apiKey: String,
    private val model: String,
    initialVoiceId: String
) : PrefetchingTtsService {

    companion object {
        private const val TAG = "OpenAIVoiceProvider"
        private const val DEFAULT_TIMEOUT_SECONDS = 30

        val AVAILABLE_VOICES = listOf(
            TtsService.Voice("alloy", "alloy", "en-US", "NEUTRAL"),
            TtsService.Voice("echo", "echo", "en-US", "NEUTRAL"),
            TtsService.Voice("fable", "fable", "en-US", "NEUTRAL"),
            TtsService.Voice("onyx", "onyx", "en-US", "NEUTRAL"),
            TtsService.Voice("nova", "nova", "en-US", "NEUTRAL"),
            TtsService.Voice("shimmer", "shimmer", "en-US", "NEUTRAL")
        )
    }

    @Serializable
    private data class OpenAiSpeechRequest(
        val model: String,
        val input: String,
        val voice: String,
        val response_format: String? = null,
        val speed: Double? = null
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private var voiceId: String = initialVoiceId

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    private val playback = QueuedTtsPlayback(TAG, audioUsage = AudioAttributes.USAGE_ASSISTANT) { fetchAudioFile(it) }
    override val isSpeaking: Boolean get() = playback.isSpeaking
    override val speakingStateFlow: Flow<Boolean> get() = playback.speakingStateFlow

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (endpointUrl.isBlank()) {
                throw TtsException(context.getString(R.string.openai_tts_error_url_not_set))
            }
            if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
                throw TtsException(context.getString(R.string.openai_tts_error_url_invalid_scheme))
            }
            if (!endpointUrl.contains("/audio/speech")) {
                throw TtsException(context.getString(R.string.openai_tts_error_url_invalid_path))
            }
            if (apiKey.isBlank()) {
                throw TtsException(context.getString(R.string.openai_tts_error_api_key_not_set))
            }
            if (model.isBlank()) {
                throw TtsException(context.getString(R.string.openai_tts_error_model_not_set))
            }
            if (voiceId.isBlank()) {
                throw TtsException(context.getString(R.string.openai_tts_error_voice_not_set))
            }

            _isInitialized.value = true
            true
        } catch (e: Exception) {
            _isInitialized.value = false
            AppLogger.e(TAG, "OpenAI TTS initialize failed", e)
            if (e is TtsException) throw e
            throw TtsException(context.getString(R.string.openai_tts_error_init_failed), cause = e)
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
            val prefs = SpeechServicesPreferences(context.applicationContext)
            val effectiveRate = request.rate ?: prefs.ttsSpeechRateFlow.first()
            val requestModel = request.extraParams["model"]?.takeIf { it.isNotBlank() } ?: model
            val requestVoice = request.extraParams["voice"]?.takeIf { it.isNotBlank() } ?: voiceId
            val responseFormat = request.extraParams["response_format"]?.takeIf { it.isNotBlank() } ?: "mp3"
            val speed = (request.extraParams["speed"]?.toDoubleOrNull() ?: effectiveRate.toDouble()).coerceIn(0.25, 4.0)
            val payload = OpenAiSpeechRequest(requestModel, request.text, requestVoice, responseFormat, speed)
            val httpRequest = Request.Builder()
                .url(endpointUrl)
                .post(Json.encodeToString(payload).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            val response = try {
                httpClient.executeTtsRequest(httpRequest)
            } catch (error: IOException) {
                throw TtsException(context.getString(R.string.openai_tts_error_request_failed), cause = error)
            }
            if (!response.isSuccessful) {
                throw TtsException(
                    message = "OpenAI TTS request failed with code ${response.code}",
                    httpStatusCode = response.code,
                    errorBody = response.text(),
                )
            }
            val safeExt = when (responseFormat.lowercase()) {
                "mp3", "opus", "aac", "flac", "wav", "pcm" -> responseFormat.lowercase()
                else -> "mp3"
            }
            return File(context.cacheDir, "openai_tts_${UUID.randomUUID()}.$safeExt").writeTtsAudio(response.bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e(TAG, "OpenAI TTS合成失败", error)
            if (error is TtsException) throw error
            throw TtsException("OpenAI TTS speak failed", cause = error)
        }
    }

    override suspend fun stop(): Boolean = playback.stop()
    override suspend fun pause(): Boolean = playback.pause()
    override suspend fun resume(): Boolean = playback.resume()
    override fun shutdown() {
        playback.shutdown()
        _isInitialized.value = false
    }

    override suspend fun getAvailableVoices(): List<TtsService.Voice> {
        return AVAILABLE_VOICES
    }

    override suspend fun setVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        this@OpenAIVoiceProvider.voiceId = voiceId
        true
    }
}
