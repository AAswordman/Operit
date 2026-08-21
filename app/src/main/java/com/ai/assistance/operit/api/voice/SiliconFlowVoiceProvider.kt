package com.ai.assistance.operit.api.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 硅基流动TTS语音服务实现
 */
class SiliconFlowVoiceProvider(
    private val context: Context,
    private val apiKey: String,
    initialVoiceId: String,
    private val initialModelName: String = "",
    private val defaultSpeechRate: Float,
) : VoiceService {
    companion object {
        private const val TAG = "SiliconFlowVoiceProvider"
        private const val API_URL = "https://api.siliconflow.cn/v1/audio/speech"
        private const val RESPONSE_FORMAT = "mp3"
        private const val SAMPLE_RATE = 32000
        private const val SPEED = 1.0
        private const val GAIN = 0
        private const val SPEECH_PREVIEW_MAX = 48

        // 可用音色列表 - 根据硅基流动官方文档
        // 注意：现在只列出音色名称，模型在设置中独立配置
        fun getAvailableVoices(context: Context): List<VoiceService.Voice> = listOf(
            VoiceService.Voice("alex", context.getString(R.string.siliconflow_voice_alex), "zh-CN", "MALE"),
            VoiceService.Voice("benjamin", context.getString(R.string.siliconflow_voice_benjamin), "zh-CN", "MALE"),
            VoiceService.Voice("charles", context.getString(R.string.siliconflow_voice_charles), "zh-CN", "MALE"),
            VoiceService.Voice("david", context.getString(R.string.siliconflow_voice_david), "zh-CN", "MALE"),
            VoiceService.Voice("anna", context.getString(R.string.siliconflow_voice_anna), "zh-CN", "FEMALE"),
            VoiceService.Voice("bella", context.getString(R.string.siliconflow_voice_bella), "zh-CN", "FEMALE"),
            VoiceService.Voice("claire", context.getString(R.string.siliconflow_voice_claire), "zh-CN", "FEMALE"),
            VoiceService.Voice("diana", context.getString(R.string.siliconflow_voice_diana), "zh-CN", "FEMALE")
        )
        val DEFAULT_VOICE_ID = "charles"
    }

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(SPEECH_PREVIEW_MAX)
    }

    // 当前音色
    private var voiceId: String = initialVoiceId.ifBlank { DEFAULT_VOICE_ID }


    // MediaPlayer用于播放音频
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaybackFile: File? = null

    private val speakScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakQueue = Channel<SpeakRequest>(Channel.UNLIMITED)
    private val playbackQueue = Channel<PreparedRequest>(Channel.UNLIMITED)
    private val stopGeneration = AtomicLong(0)
    private val isPaused = AtomicBoolean(false)

    // 初始化状态
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    // 播放状态
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    // 播放状态Flow
    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    private data class SpeakRequest(
        val text: String,
        val interrupt: Boolean,
        val rate: Float?,
        val pitch: Float?,
        val extraParams: Map<String, String>,
        val generation: Long,
        val completion: CompletableDeferred<Boolean>
    )

    private data class PreparedRequest(
        val request: SpeakRequest,
        val audioFile: File
    )

    init {
        speakScope.launch {
            for (request in speakQueue) {
                try {
                    val prepared = fetchAudioFile(request)
                    if (prepared == null) {
                        request.completion.complete(false)
                    } else {
                        playbackQueue.send(prepared)
                    }
                } catch (e: Exception) {
                    request.completion.completeExceptionally(e)
                }
            }
        }

        speakScope.launch {
            for (prepared in playbackQueue) {
                try {
                    val result = playPreparedRequest(prepared)
                    prepared.request.completion.complete(result)
                } catch (e: Exception) {
                    prepared.request.completion.completeExceptionally(e)
                }
            }
        }
    }

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
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        AppLogger.d(
            TAG,
            "speak request interrupt=$interrupt len=${text.length} preview=\"${speechPreview(text)}\" rate=$rate pitch=$pitch voice=$voiceId paused=${isPaused.get()} initialized=$isInitialized extraKeys=${extraParams.keys}"
        )
        val completion = CompletableDeferred<Boolean>()
        val request = SpeakRequest(
            text = text,
            interrupt = interrupt,
            rate = rate,
            pitch = pitch,
            extraParams = extraParams,
            generation = stopGeneration.get(),
            completion = completion
        )
        speakQueue.send(request)
        completion.await()
    }

    private suspend fun fetchAudioFile(request: SpeakRequest): PreparedRequest? {
        if (!isInitialized) {
            AppLogger.e(TAG, "TTS未初始化")
            return null
        }

        try {
            if (request.interrupt && isSpeaking) {
                stopPlaybackOnly()
            }

            if (request.generation != stopGeneration.get()) {
                return null
            }

            val effectiveRate = request.rate ?: defaultSpeechRate

            val strippedInput = request.text.replace(Regex("<[^>]+>"), "").trim()
            if (strippedInput.isBlank()) {
                AppLogger.w(TAG, "TTS输入为空，跳过请求")
                return null
            }

            // 从 extraParams 获取自定义的 model 和 voice，如果没有则使用配置或默认值
            val customModel = request.extraParams["model"]
            val customVoice = request.extraParams["voice"]

            val (model, voice) = when {
                // 优先使用 extraParams 中的自定义值
                customModel != null && customVoice != null -> {
                    customModel to customVoice
                }
                customModel != null -> {
                    // 只指定了model，使用voiceId作为voice
                    customModel to voiceId
                }
                customVoice != null -> {
                    // 只指定了voice，使用配置的model或默认model
                    val modelName = initialModelName.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
                    modelName to customVoice
                }
                else -> {
                    // 使用配置的model（或默认）和voiceId
                    val modelName = initialModelName.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
                    modelName to voiceId
                }
            }

            // 清理 voice 字符串，去除可能的空白字符
            val cleanVoice = voice.trim()

            // 构建请求体
            val requestBody = buildString {
                append("{")
                append("\"model\":\"$model\",")
                append("\"input\":\"${strippedInput.replace("\"", "\\\"")}\",")

                // 根据官方文档：
                // 1. 系统预定义音色（如 alex, bella）需要格式为 "model:voice"
                // 2. 用户自定义音色（以 speech: 开头）直接使用完整的 voice ID
                // 两种情况都使用 voice 字段
                val voiceValue = if (cleanVoice.startsWith("speech:")) {
                    // 自定义音色，直接使用完整 ID
                    cleanVoice
                } else {
                    // 预置音色，需要加上模型前缀
                    if (cleanVoice.contains(":")) {
                        // 如果已经包含冒号，说明已经是完整格式
                        cleanVoice
                    } else {
                        // 否则添加模型前缀
                        "$model:$cleanVoice"
                    }
                }
                append("\"voice\":\"$voiceValue\",")

                append("\"response_format\":\"$RESPONSE_FORMAT\",")
                append("\"sample_rate\":$SAMPLE_RATE,")
                append("\"speed\":${effectiveRate.toDouble()},")
                append("\"gain\":$GAIN")
                append("}")
            }

            AppLogger.d(TAG, "TTS请求参数 - model: $model, voice: $voice")
            AppLogger.d(TAG, "TTS请求体: $requestBody")

            // 发送HTTP请求
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // 写入请求体
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 将音频数据保存到临时文件
                val tempFile = File.createTempFile("siliconflow_tts", ".mp3", context.cacheDir)

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (request.generation != stopGeneration.get()) {
                    tempFile.delete()
                    return null
                }

                return PreparedRequest(request, tempFile)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText()
                AppLogger.e(TAG, "TTS请求失败，响应码: $responseCode, Body: $errorBody")
                throw TtsException(
                    message = "TTS request failed with code $responseCode",
                    httpStatusCode = responseCode,
                    errorBody = errorBody
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "TTS speak失败", e)
            if (e is TtsException) throw e
            throw TtsException("TTS speak failed", cause = e)
        }
    }

    private suspend fun playPreparedRequest(prepared: PreparedRequest): Boolean {
        if (prepared.request.generation != stopGeneration.get()) {
            prepared.audioFile.delete()
            return false
        }

        return playAudioFileAndAwait(prepared.audioFile)
    }

    private fun clearPendingRequests() {
        while (true) {
            val request = speakQueue.tryReceive().getOrNull() ?: break
            request.completion.complete(false)
        }
    }

    private fun clearPendingPlayback() {
        while (true) {
            val prepared = playbackQueue.tryReceive().getOrNull() ?: break
            prepared.audioFile.delete()
            prepared.request.completion.complete(false)
        }
    }

    private fun stopPlaybackOnly(): Boolean {
        return try {
            isPaused.set(false)
            mediaPlayer?.apply {
                AppLogger.d(TAG, "stopPlaybackOnly playerExists=true isPlaying=$isPlaying")
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
            mediaPlayer = null
            _isSpeaking.value = false
            currentPlaybackFile?.delete()
            currentPlaybackFile = null
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止播放失败", e)
            false
        }
    }

    private suspend fun playAudioFileAndAwait(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            AppLogger.e(TAG, "Audio file is invalid: ${file.absolutePath}")
            return false
        }

        currentPlaybackFile?.delete()
        currentPlaybackFile = file

        return try {
            AppLogger.d(TAG, "playAudioFileAndAwait start path=${file.absolutePath} size=${file.length()} paused=${isPaused.get()}")
            isPaused.set(false)
            withContext(Dispatchers.Main) {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnErrorListener { _, what, extra ->
                        AppLogger.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                        true
                    }
                    prepare()
                    start()
                }
            }

            _isSpeaking.value = true
            while (true) {
                val player = mediaPlayer ?: break
                if (!player.isPlaying && !isPaused.get()) {
                    break
                }
                delay(100)
            }
            AppLogger.d(TAG, "playAudioFileAndAwait waitLoopExit paused=${isPaused.get()} speaking=${_isSpeaking.value}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "播放音频失败", e)
            false
        } finally {
            _isSpeaking.value = false
            isPaused.set(false)
            AppLogger.d(TAG, "playAudioFileAndAwait finally paused=${isPaused.get()} speaking=${_isSpeaking.value}")
            withContext(Dispatchers.Main) {
                mediaPlayer?.release()
                mediaPlayer = null
            }
            currentPlaybackFile?.delete()
            currentPlaybackFile = null
        }
    }

    override suspend fun stop(): Boolean {
        AppLogger.d(TAG, "stop request paused=${isPaused.get()} speaking=${_isSpeaking.value}")
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        val result = stopPlaybackOnly()
        AppLogger.d(TAG, "stop result=$result paused=${isPaused.get()} speaking=${_isSpeaking.value}")
        return result
    }

    override suspend fun pause(): Boolean {
        return try {
            AppLogger.d(TAG, "pause request paused=${isPaused.get()} speaking=${_isSpeaking.value} playerExists=${mediaPlayer != null}")
            mediaPlayer?.pause()
            isPaused.set(true)
            _isSpeaking.value = false
            AppLogger.d(TAG, "pause result=true paused=${isPaused.get()} speaking=${_isSpeaking.value}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "暂停播放失败", e)
            false
        }
    }

    override suspend fun resume(): Boolean {
        return try {
            AppLogger.d(TAG, "resume request paused=${isPaused.get()} speaking=${_isSpeaking.value} playerExists=${mediaPlayer != null}")
            mediaPlayer?.start()
            isPaused.set(false)
            _isSpeaking.value = true
            AppLogger.d(TAG, "resume result=true paused=${isPaused.get()} speaking=${_isSpeaking.value}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "恢复播放失败", e)
            false
        }
    }

    override fun shutdown() {
        stopGeneration.incrementAndGet()
        speakScope.cancel()
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
        speakQueue.close()
        playbackQueue.close()
        _isInitialized.value = false
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        return getAvailableVoices(context)
    }

    override suspend fun setVoice(voiceId: String): Boolean {
        // 支持系统预置音色和用户自定义音色（以speech:开头）
        if (getAvailableVoices(context).any { it.id == voiceId } || voiceId.startsWith("speech:")) {
            this.voiceId = voiceId
            AppLogger.d(TAG, "设置音色: $voiceId")
            return true
        }
        AppLogger.w(TAG, "不支持的音色ID: $voiceId")
        return false
    }
} 
