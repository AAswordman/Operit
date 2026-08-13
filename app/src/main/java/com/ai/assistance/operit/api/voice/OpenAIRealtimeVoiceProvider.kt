package com.ai.assistance.operit.api.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    initialVoiceId: String,
    private val defaultSpeechRate: Float,
) : VoiceService {

    companion object {
        private const val TAG = "OpenAIRealtimeVoiceProvider"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val OUTPUT_AUDIO_FORMAT = "audio/pcm"
        private const val OUTPUT_SAMPLE_RATE = 24_000
        private const val OUTPUT_CHANNEL_COUNT = 1

        val AVAILABLE_VOICES = listOf(
            VoiceService.Voice("alloy", "alloy", "en-US", "NEUTRAL"),
            VoiceService.Voice("ash", "ash", "en-US", "NEUTRAL"),
            VoiceService.Voice("ballad", "ballad", "en-US", "NEUTRAL"),
            VoiceService.Voice("cedar", "cedar", "en-US", "NEUTRAL"),
            VoiceService.Voice("coral", "coral", "en-US", "NEUTRAL"),
            VoiceService.Voice("echo", "echo", "en-US", "NEUTRAL"),
            VoiceService.Voice("marin", "marin", "en-US", "NEUTRAL"),
            VoiceService.Voice("sage", "sage", "en-US", "NEUTRAL"),
            VoiceService.Voice("shimmer", "shimmer", "en-US", "NEUTRAL"),
            VoiceService.Voice("verse", "verse", "en-US", "NEUTRAL")
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

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    private val playbackMutex = Mutex()
    private val stateLock = Any()

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaybackDone: CompletableDeferred<Boolean>? = null
    private var currentPlaybackFile: File? = null
    private var currentResponseDeferred: CompletableDeferred<ByteArray>? = null
    private var currentWebSocket: WebSocket? = null

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
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        playbackMutex.withLock {
            if (!isInitialized) {
                val initOk = initialize()
                if (!initOk) return@withLock false
            }

            try {
                if (interrupt) {
                    stop()
                }

                val effectiveRate = rate ?: defaultSpeechRate
                val requestModel = extraParams["model"]?.takeIf { it.isNotBlank() } ?: model
                val requestVoice = extraParams["voice"]?.takeIf { it.isNotBlank() } ?: voiceId
                val speed = effectiveRate.coerceIn(0.25f, 1.5f)

                val pcmAudio = requestAudio(
                    text = text,
                    requestModel = requestModel,
                    requestVoice = requestVoice,
                    speed = speed
                )

                if (pcmAudio.isEmpty()) {
                    return@withLock false
                }

                val tempFile = File(context.cacheDir, "openai_realtime_tts_${UUID.randomUUID()}.wav")
                FileOutputStream(tempFile).use { output ->
                    output.write(wrapPcm16AsWav(pcmAudio))
                }

                return@withLock playAudioFileAndAwait(tempFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "OpenAI Realtime TTS speak failed", e)
                if (e is TtsException) throw e
                throw TtsException(context.getString(R.string.openai_realtime_tts_error_request_failed), cause = e)
            }
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

        synchronized(stateLock) {
            currentResponseDeferred = deferred
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(stateLock) {
                    currentWebSocket = webSocket
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

            override fun onMessage(webSocket: WebSocket, textMessage: String) {
                val json = runCatching { JSONObject(textMessage) }.getOrNull() ?: return
                val type = json.optString("type")

                when (type) {
                    "error" -> {
                        val error = json.optJSONObject("error")
                        val message = error?.optString("message").orEmpty()
                            .ifBlank { context.getString(R.string.openai_realtime_tts_error_request_failed) }
                        failResponse(deferred, message)
                        webSocket.cancel()
                    }
                    // OpenAI docs currently show both response.output_audio.* and response.audio.* names.
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
                            val details = responseJson.optJSONObject("status_details")
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
                synchronized(stateLock) {
                    if (currentWebSocket === webSocket) {
                        currentWebSocket = null
                    }
                    if (currentResponseDeferred === deferred) {
                        currentResponseDeferred = null
                    }
                }

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
                synchronized(stateLock) {
                    if (currentWebSocket === webSocket) {
                        currentWebSocket = null
                    }
                    if (currentResponseDeferred === deferred) {
                        currentResponseDeferred = null
                    }
                }

                if (!deferred.isCompleted) {
                    completeAudioResponse(deferred, audioBuffer)
                }
            }
        }

        val request = Request.Builder()
            .url(realtimeUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val webSocket = webSocketClient.newWebSocket(request, listener)
        synchronized(stateLock) {
            currentWebSocket = webSocket
        }

        try {
            deferred.await()
        } finally {
            synchronized(stateLock) {
                if (currentWebSocket === webSocket) {
                    currentWebSocket = null
                }
                if (currentResponseDeferred === deferred) {
                    currentResponseDeferred = null
                }
            }
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

    private suspend fun playAudioFileAndAwait(file: File): Boolean {
        val done = CompletableDeferred<Boolean>()
        synchronized(stateLock) {
            currentPlaybackDone = done
            currentPlaybackFile = file
        }

        try {
            withContext(Dispatchers.Main) {
                try {
                    synchronized(stateLock) {
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }

                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .build()
                        )
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            finishPlayback(true, done, file)
                        }
                        setOnErrorListener { _, what, extra ->
                            AppLogger.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                            finishPlayback(false, done, file)
                            true
                        }
                        prepare()
                        start()
                    }

                    synchronized(stateLock) {
                        mediaPlayer = mp
                    }
                    _isSpeaking.value = true
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Play audio failed", e)
                    finishPlayback(false, done, file)
                }
            }

            return done.await()
        } finally {
            if (!done.isCompleted) {
                finishPlayback(false, done, file)
            }
        }
    }

    private fun finishPlayback(success: Boolean, done: CompletableDeferred<Boolean>, file: File) {
        synchronized(stateLock) {
            if (currentPlaybackDone === done) {
                currentPlaybackDone = null
            }
            if (currentPlaybackFile == file) {
                currentPlaybackFile = null
            }
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
            mediaPlayer = null
        }
        _isSpeaking.value = false
        runCatching { file.delete() }
        if (!done.isCompleted) {
            done.complete(success)
        }
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        try {
            val responseDeferred: CompletableDeferred<ByteArray>?
            val webSocket: WebSocket?
            val playbackDeferred: CompletableDeferred<Boolean>?
            val playbackFile: File?

            synchronized(stateLock) {
                responseDeferred = currentResponseDeferred
                webSocket = currentWebSocket
                currentResponseDeferred = null
                currentWebSocket = null

                playbackDeferred = currentPlaybackDone
                playbackFile = currentPlaybackFile
                currentPlaybackDone = null
                currentPlaybackFile = null

                mediaPlayer?.apply {
                    if (isPlaying) {
                        stop()
                    }
                    release()
                }
                mediaPlayer = null
            }

            webSocket?.cancel()
            if (responseDeferred != null && !responseDeferred.isCompleted) {
                responseDeferred.complete(ByteArray(0))
            }
            if (playbackDeferred != null && !playbackDeferred.isCompleted) {
                playbackDeferred.complete(false)
            }
            runCatching { playbackFile?.delete() }

            _isSpeaking.value = false
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Stop failed", e)
            false
        }
    }

    override suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            synchronized(stateLock) {
                mediaPlayer?.pause()
            }
            _isSpeaking.value = false
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Pause failed", e)
            false
        }
    }

    override suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            synchronized(stateLock) {
                mediaPlayer?.start()
            }
            _isSpeaking.value = true
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Resume failed", e)
            false
        }
    }

    override fun shutdown() {
        val responseDeferred: CompletableDeferred<ByteArray>?
        val playbackDeferred: CompletableDeferred<Boolean>?
        val playbackFile: File?

        synchronized(stateLock) {
            responseDeferred = currentResponseDeferred
            currentResponseDeferred = null
            currentWebSocket?.cancel()
            currentWebSocket = null

            playbackDeferred = currentPlaybackDone
            currentPlaybackDone = null
            playbackFile = currentPlaybackFile
            currentPlaybackFile = null

            mediaPlayer?.release()
            mediaPlayer = null
        }
        if (responseDeferred != null && !responseDeferred.isCompleted) {
            responseDeferred.complete(ByteArray(0))
        }
        if (playbackDeferred != null && !playbackDeferred.isCompleted) {
            playbackDeferred.complete(false)
        }
        runCatching { playbackFile?.delete() }
        _isSpeaking.value = false
        _isInitialized.value = false
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        return AVAILABLE_VOICES
    }

    override suspend fun setVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        this@OpenAIRealtimeVoiceProvider.voiceId = voiceId
        true
    }
}
