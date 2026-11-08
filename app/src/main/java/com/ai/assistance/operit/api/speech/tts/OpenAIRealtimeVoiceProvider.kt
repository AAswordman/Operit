package com.ai.assistance.operit.api.speech.tts

import android.content.Context
import android.media.AudioAttributes
import com.ai.assistance.operit.api.speech.tts.prefetch.writeTtsAudio
import com.ai.assistance.operit.api.speech.tts.prefetch.PrefetchingTtsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import android.net.Uri
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

class OpenAIRealtimeVoiceProvider(
    private val context: Context,
    private val endpointUrl: String,
    private val apiKey: String,
    private val model: String,
    initialVoiceId: String
) : PrefetchingTtsService {

    companion object {
        private const val TAG = "OpenAIRealtimeVoiceProvider"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val OUTPUT_AUDIO_FORMAT = "audio/pcm"
        private const val OUTPUT_SAMPLE_RATE = 24_000
        private const val OUTPUT_CHANNEL_COUNT = 1

        val AVAILABLE_VOICES = listOf(
            TtsService.Voice("alloy", "alloy", "en-US", "NEUTRAL"),
            TtsService.Voice("ash", "ash", "en-US", "NEUTRAL"),
            TtsService.Voice("ballad", "ballad", "en-US", "NEUTRAL"),
            TtsService.Voice("cedar", "cedar", "en-US", "NEUTRAL"),
            TtsService.Voice("coral", "coral", "en-US", "NEUTRAL"),
            TtsService.Voice("echo", "echo", "en-US", "NEUTRAL"),
            TtsService.Voice("marin", "marin", "en-US", "NEUTRAL"),
            TtsService.Voice("sage", "sage", "en-US", "NEUTRAL"),
            TtsService.Voice("shimmer", "shimmer", "en-US", "NEUTRAL"),
            TtsService.Voice("verse", "verse", "en-US", "NEUTRAL")
        )
    }

    private val webSocketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
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
                throw TtsException(context.getString(R.string.openai_realtime_tts_error_url_not_set))
            }
            if (!endpointUrl.startsWith("ws://") && !endpointUrl.startsWith("wss://")) {
                throw TtsException(context.getString(R.string.openai_realtime_tts_error_url_invalid_scheme))
            }
            if (apiKey.isBlank()) {
                throw TtsException(context.getString(R.string.openai_realtime_tts_error_api_key_not_set))
            }
            if (model.isBlank()) {
                throw TtsException(context.getString(R.string.openai_realtime_tts_error_model_not_set))
            }
            if (voiceId.isBlank()) {
                throw TtsException(context.getString(R.string.openai_realtime_tts_error_voice_not_set))
            }

            _isInitialized.value = true
            true
        } catch (e: Exception) {
            _isInitialized.value = false
            AppLogger.e(TAG, "OpenAI Realtime TTS initialize failed", e)
            if (e is TtsException) throw e
            throw TtsException(context.getString(R.string.openai_realtime_tts_error_init_failed), cause = e)
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
            val pcmAudio = requestAudio(request.text, requestModel, requestVoice, effectiveRate.coerceIn(0.25f, 1.5f))
            if (pcmAudio.isEmpty()) return null
            return File(context.cacheDir, "openai_realtime_tts_${UUID.randomUUID()}.wav").writeTtsAudio(wrapPcm16AsWav(pcmAudio))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e(TAG, "OpenAI Realtime TTS合成失败", error)
            if (error is TtsException) throw error
            throw TtsException(context.getString(R.string.openai_realtime_tts_error_request_failed), cause = error)
        }
    }

    private suspend fun requestAudio(
        text: String,
        requestModel: String,
        requestVoice: String,
        speed: Float
    ): ByteArray = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<ByteArray>()
        val audioBuffer = ByteArrayOutputStream()
        val realtimeUrl = buildRealtimeUrl(endpointUrl, requestModel)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!deferred.isActive) {
                    webSocket.cancel()
                    return
                }

                if (!webSocket.send(buildConversationItemCreateEvent(text).toString())) {
                    failResponse(deferred, context.getString(R.string.openai_realtime_tts_error_send_failed))
                    webSocket.cancel()
                    return
                }

                if (!webSocket.send(buildResponseCreateEvent(requestVoice, speed).toString())) {
                    failResponse(deferred, context.getString(R.string.openai_realtime_tts_error_send_failed))
                    webSocket.cancel()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val type = json.optString("type")

                when (type) {
                    "error" -> {
                        val error = json.optJSONObject("error")
                        val message = error?.optString("message").orEmpty()
                            .ifBlank { context.getString(R.string.openai_realtime_tts_error_request_failed) }
                        failResponse(deferred, message)
                        webSocket.cancel()
                    }
                    // 兼容现有协议的两组音频事件名称，各分句使用独立连接和缓冲区。
                    "response.output_audio.delta", "response.audio.delta" -> {
                        val delta = json.optString("delta")
                        if (delta.isNotBlank()) {
                            runCatching {
                                Base64.decode(delta, Base64.DEFAULT)
                            }.onSuccess { chunk ->
                                synchronized(audioBuffer) {
                                    audioBuffer.write(chunk)
                                }
                            }.onFailure {
                                failResponse(
                                    deferred,
                                    context.getString(R.string.openai_realtime_tts_error_request_failed)
                                )
                                webSocket.cancel()
                            }
                        }
                    }
                    "response.output_audio.done", "response.audio.done" -> {
                        completeAudioResponse(deferred, audioBuffer)
                        webSocket.close(1000, "audio_complete")
                    }
                    "response.done" -> {
                        val responseJson = json.optJSONObject("response")
                        val status = responseJson?.optString("status").orEmpty()
                        if (status.equals("failed", ignoreCase = true)) {
                            val details = responseJson?.optJSONObject("status_details")
                            val errorMessage = details?.optString("error").orEmpty()
                                .ifBlank { context.getString(R.string.openai_realtime_tts_error_request_failed) }
                            failResponse(deferred, errorMessage)
                            webSocket.cancel()
                        } else if (!deferred.isCompleted) {
                            completeAudioResponse(deferred, audioBuffer)
                            webSocket.close(1000, "response_complete")
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (deferred.isCompleted) {
                    return
                }

                val errorMessage =
                    runCatching {
                        response?.body?.string()
                    }.getOrNull().orEmpty().ifBlank {
                        t.message ?: context.getString(R.string.openai_realtime_tts_error_request_failed)
                    }

                deferred.completeExceptionally(
                    TtsException(
                        message = errorMessage,
                        httpStatusCode = response?.code,
                        cause = t
                    )
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!deferred.isCompleted) {
                    completeAudioResponse(deferred, audioBuffer)
                }
            }
        }

        val request = Request.Builder()
            .url(realtimeUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        // 每个请求独立持有连接，取消旧轮次时不会误取消新一句的连接。
        val webSocket = webSocketClient.newWebSocket(request, listener)
        try {
            withTimeout(DEFAULT_TIMEOUT_SECONDS * 1000) { deferred.await() }
        } finally {
            deferred.cancel()
            webSocket.cancel()
        }
    }

    private fun completeAudioResponse(
        deferred: CompletableDeferred<ByteArray>,
        audioBuffer: ByteArrayOutputStream
    ) {
        if (deferred.isCompleted) return
        val audioBytes = synchronized(audioBuffer) { audioBuffer.toByteArray() }
        if (audioBytes.isNotEmpty()) {
            deferred.complete(audioBytes)
        } else {
            deferred.completeExceptionally(
                TtsException(context.getString(R.string.openai_realtime_tts_error_empty_audio))
            )
        }
    }

    private fun failResponse(
        deferred: CompletableDeferred<ByteArray>,
        message: String
    ) {
        if (!deferred.isCompleted) {
            deferred.completeExceptionally(TtsException(message))
        }
    }

    private fun buildRealtimeUrl(baseUrl: String, model: String): String {
        val uri = Uri.parse(baseUrl)
        val builder = uri.buildUpon().clearQuery()
        val queryNames = uri.queryParameterNames
        queryNames.forEach { key ->
            if (!key.equals("model", ignoreCase = true)) {
                uri.getQueryParameters(key).forEach { value ->
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        builder.appendQueryParameter("model", model)
        return builder.build().toString()
    }

    private fun buildConversationItemCreateEvent(text: String): JSONObject {
        return JSONObject().apply {
            put("type", "conversation.item.create")
            put(
                "item",
                JSONObject().apply {
                    put("type", "message")
                    put("role", "user")
                    put(
                        "content",
                        JSONArray().put(
                            JSONObject().apply {
                                put("type", "input_text")
                                put("text", text)
                            }
                        )
                    )
                }
            )
        }
    }

    private fun buildResponseCreateEvent(voice: String, speed: Float): JSONObject {
        return JSONObject().apply {
            put("type", "response.create")
            put(
                "response",
                JSONObject().apply {
                    put("output_modalities", JSONArray().put("audio"))
                    put(
                        "audio",
                        JSONObject().apply {
                            put(
                                "output",
                                JSONObject().apply {
                                    put("format", JSONObject().put("type", OUTPUT_AUDIO_FORMAT))
                                    put("voice", buildVoiceValue(voice))
                                    put("speed", speed.toDouble())
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    private fun buildVoiceValue(voice: String): Any {
        return if (voice.startsWith("voice_")) {
            JSONObject().put("id", voice)
        } else {
            voice
        }
    }

    private fun wrapPcm16AsWav(pcmData: ByteArray): ByteArray {
        val bitsPerSample = 16
        val byteRate = OUTPUT_SAMPLE_RATE * OUTPUT_CHANNEL_COUNT * bitsPerSample / 8
        val blockAlign = OUTPUT_CHANNEL_COUNT * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36

        return ByteArrayOutputStream(44 + pcmData.size).use { output ->
            output.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
            writeIntLE(output, totalDataLen)
            output.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
            output.write(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
            writeIntLE(output, 16)
            writeShortLE(output, 1)
            writeShortLE(output, OUTPUT_CHANNEL_COUNT)
            writeIntLE(output, OUTPUT_SAMPLE_RATE)
            writeIntLE(output, byteRate)
            writeShortLE(output, blockAlign)
            writeShortLE(output, bitsPerSample)
            output.write(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            writeIntLE(output, pcmData.size)
            output.write(pcmData)
            output.toByteArray()
        }
    }

    private fun writeIntLE(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
        output.write(value shr 16 and 0xFF)
        output.write(value shr 24 and 0xFF)
    }

    private fun writeShortLE(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
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
        this@OpenAIRealtimeVoiceProvider.voiceId = voiceId
        true
    }
}
