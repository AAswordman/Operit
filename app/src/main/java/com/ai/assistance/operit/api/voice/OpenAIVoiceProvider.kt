package com.ai.assistance.operit.api.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    initialVoiceId: String,
    private val defaultSpeechRate: Float,
) : VoiceService {

    companion object {
        private const val TAG = "OpenAIVoiceProvider"
        private const val DEFAULT_TIMEOUT_SECONDS = 30

        val AVAILABLE_VOICES = listOf(
            VoiceService.Voice("alloy", "alloy", "en-US", "NEUTRAL"),
            VoiceService.Voice("echo", "echo", "en-US", "NEUTRAL"),
            VoiceService.Voice("fable", "fable", "en-US", "NEUTRAL"),
            VoiceService.Voice("onyx", "onyx", "en-US", "NEUTRAL"),
            VoiceService.Voice("nova", "nova", "en-US", "NEUTRAL"),
            VoiceService.Voice("shimmer", "shimmer", "en-US", "NEUTRAL")
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

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    private val playbackMutex = Mutex()
    private val playerLock = Any()
    private var currentPlaybackDone: CompletableDeferred<Boolean>? = null
    private var currentPlaybackFile: File? = null

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
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        playbackMutex.withLock {
            if (!isInitialized) {
                val initOk = initialize()
                if (!initOk) return@withLock false
            }

            try {
                if (interrupt && isSpeaking) {
                    stop()
                }

                val effectiveRate = rate ?: defaultSpeechRate

                val requestModel = extraParams["model"]?.takeIf { it.isNotBlank() } ?: model
                val requestVoice = extraParams["voice"]?.takeIf { it.isNotBlank() } ?: voiceId
                val responseFormat =
                    extraParams["response_format"]?.takeIf { it.isNotBlank() } ?: "mp3"
                val speed = (extraParams["speed"]?.toDoubleOrNull() ?: effectiveRate.toDouble())
                    .coerceIn(0.25, 4.0)

                val payload = OpenAiSpeechRequest(
                    model = requestModel,
                    input = text,
                    voice = requestVoice,
                    response_format = responseFormat,
                    speed = speed
                )

                val bodyJson = Json.encodeToString(payload)

                val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(endpointUrl)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = try {
                    httpClient.newCall(request).execute()
                } catch (e: IOException) {
                    throw TtsException(context.getString(R.string.openai_tts_error_request_failed), cause = e)
                }

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    response.close()
                    throw TtsException(
                        message = "OpenAI TTS request failed with code ${response.code}",
                        httpStatusCode = response.code,
                        errorBody = errorBody
                    )
                }

                val safeExt = when (responseFormat.lowercase()) {
                    "mp3", "opus", "aac", "flac", "wav", "pcm" -> responseFormat.lowercase()
                    else -> "mp3"
                }
                val tempFile = File(context.cacheDir, "openai_tts_${UUID.randomUUID()}.$safeExt")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                response.close()

                return@withLock playAudioFileAndAwait(tempFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "OpenAI TTS speak failed", e)
                if (e is TtsException) throw e
                throw TtsException("OpenAI TTS speak failed", cause = e)
            }
        }
    }

     private suspend fun playAudioFileAndAwait(file: File): Boolean {
         val done = CompletableDeferred<Boolean>()
         synchronized(playerLock) {
             currentPlaybackDone = done
             currentPlaybackFile = file
         }

         try {
             withContext(Dispatchers.Main) {
                 try {
                     synchronized(playerLock) {
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

                     synchronized(playerLock) {
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
         synchronized(playerLock) {
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
         try {
             file.delete()
         } catch (_: Exception) {
         }

         if (!done.isCompleted) {
             done.complete(success)
         }
     }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val done: CompletableDeferred<Boolean>?
            val file: File?
            synchronized(playerLock) {
                done = currentPlaybackDone
                file = currentPlaybackFile
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
            _isSpeaking.value = false
            file?.delete()
            if (done != null && !done.isCompleted) {
                done.complete(false)
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Stop failed", e)
            false
        }
    }

    override suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            synchronized(playerLock) {
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
            synchronized(playerLock) {
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
        synchronized(playerLock) {
            mediaPlayer?.release()
            mediaPlayer = null
            currentPlaybackDone = null
            currentPlaybackFile = null
        }
        _isSpeaking.value = false
        _isInitialized.value = false
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        return AVAILABLE_VOICES
    }

    override suspend fun setVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        this@OpenAIVoiceProvider.voiceId = voiceId
        true
    }
}
