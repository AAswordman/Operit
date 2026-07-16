package com.ai.assistance.operit.api.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ai.assistance.operit.util.AppLogger
import com.k2fsa.sherpa.mnn.*
import com.ai.assistance.operit.api.speech.SpeechPrerollStore
import java.io.File
import com.ai.assistance.operit.util.AssetCopyUtils
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10

/**
 * 基于sherpa-mnn的本地语音识别实现，集成VAD能力
 * sherpa-mnn是基于MNN的语音识别引擎，支持流式识别和VAD
 */
@SuppressLint("MissingPermission")
class SherpaMnnSpeechProvider(private val context: Context) : SpeechService {
    companion object {
        private const val TAG = "SherpaMnnSpeechProvider"
    }

    private var recognizer: OnlineRecognizer? = null
    private var vad: Vad? = null
    private var sileroVad: OnnxSileroVad? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _recognitionState = MutableStateFlow(SpeechService.RecognitionState.UNINITIALIZED)
    override val currentState: SpeechService.RecognitionState
        get() = _recognitionState.value
    override val recognitionStateFlow: StateFlow<SpeechService.RecognitionState> =
            _recognitionState.asStateFlow()

    private val _recognitionResult = MutableStateFlow(SpeechService.RecognitionResult(""))
    override val recognitionResultFlow: StateFlow<SpeechService.RecognitionResult> =
            _recognitionResult.asStateFlow()

    private val _recognitionError = MutableStateFlow(SpeechService.RecognitionError(0, ""))
    override val recognitionErrorFlow: StateFlow<SpeechService.RecognitionError> =
            _recognitionError.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _volumeLevelFlow = MutableStateFlow(0f)
    override val volumeLevelFlow: StateFlow<Float> = _volumeLevelFlow.asStateFlow()

    override val isRecognizing: Boolean
        get() = currentState == SpeechService.RecognitionState.RECOGNIZING

    // 音量计算相关常量
    private val VOLUME_SMOOTHING_FACTOR = 0.1f
    private var currentVolume = 0f

    override suspend fun initialize(): Boolean {
        if (isInitialized.value) return true
        AppLogger.d(TAG, "Initializing sherpa-mnn...")
        return try {
            withContext(Dispatchers.IO) {
                createRecognizer()
                // VAD 是可选的，失败不影响整体初始化
                try {
                    createSileroVad()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to create VAD, continuing without VAD support", e)
                    vad = null
                    sileroVad = null
                }
                
                if (recognizer != null) {
                    AppLogger.d(
                        TAG,
                        "sherpa-mnn initialized successfully (VAD: ${if (vad != null || sileroVad != null) "enabled" else "disabled"})"
                    )
                    _isInitialized.value = true
                    _recognitionState.value = SpeechService.RecognitionState.IDLE
                    true
                } else {
                    AppLogger.e(TAG, "Failed to create sherpa-mnn recognizer")
                    _recognitionState.value = SpeechService.RecognitionState.ERROR
                    _recognitionError.value =
                            SpeechService.RecognitionError(-1, "Failed to initialize recognizer")
                    false
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize sherpa-mnn", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value =
                    SpeechService.RecognitionError(-1, e.message ?: "Unknown error")
            false
        }
    }

    private fun createRecognizer() {
        val localModelDir: File
        try {
            val modelDirName = "sherpa-mnn-streaming-zipformer-bilingual-zh-en-2023-02-20"
            val assetModelDir = "models/$modelDirName"
            localModelDir = AssetCopyUtils.copyAssetDirToCache(context, assetModelDir, context.filesDir)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Failed to copy model assets.", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value =
                    SpeechService.RecognitionError(-1, "Failed to prepare model files.")
            return
        }

        val featConfig = FeatureConfig(
            sampleRate = 16000,
            featureDim = 80
        )

        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(localModelDir, "encoder-epoch-99-avg-1.int8.mnn").absolutePath,
                decoder = File(localModelDir, "decoder-epoch-99-avg-1.int8.mnn").absolutePath,
                joiner = File(localModelDir, "joiner-epoch-99-avg-1.int8.mnn").absolutePath,
            ),
            tokens = File(localModelDir, "tokens.txt").absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "zipformer"
        )

        val endpointConfig = EndpointConfig(
            rule1 = EndpointRule(false, 2.4f, 0.0f),
            rule2 = EndpointRule(true, 1.2f, 0.0f),
            rule3 = EndpointRule(false, 0.0f, 20.0f)
        )

        val recognizerConfig = OnlineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            endpointConfig = endpointConfig,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )

        recognizer = OnlineRecognizer(
            assetManager = null, // Force using newFromFile
            config = recognizerConfig
        )
    }

    private fun createVad() {
        // 暂时禁用 VAD 功能，因为模型加载会导致崩溃
        // TODO: 需要检查 VAD 模型格式或使用兼容的模型版本
        AppLogger.w(TAG, "VAD is temporarily disabled due to compatibility issues")
        vad = null
        
        // 原始代码（已注释，等待修复）:
        /*
        try {
            val assetVadPath = "models/silero_vad.onnx"
            val vadModelFile = AssetCopyUtils.copyAssetToFile(
                context,
                assetVadPath,
                File(context.filesDir, assetVadPath.substringAfterLast('/'))
            )
            
            // 验证文件是否存在且可读
            if (!vadModelFile.exists() || !vadModelFile.canRead()) {
                AppLogger.e(TAG, "VAD model file does not exist or is not readable: ${vadModelFile.absolutePath}")
                return
            }
            
            // 验证文件大小（silero_vad.onnx 通常约 1-2MB）
            val fileSize = vadModelFile.length()
            if (fileSize < 1024) { // 小于 1KB 可能是损坏的文件
                AppLogger.e(TAG, "VAD model file seems too small (${fileSize} bytes): ${vadModelFile.absolutePath}")
                return
            }
            
            AppLogger.d(TAG, "Loading VAD model from: ${vadModelFile.absolutePath} (size: ${fileSize} bytes)")

            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModelFile.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 5.0f
                ),
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )

            vad = Vad(
                assetManager = null,
                config = vadConfig
            )
            AppLogger.d(TAG, "VAD loaded successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create VAD, continuing without VAD", e)
            vad = null
        }
        */
    }

    private fun createSileroVad() {
        try {
            sileroVad?.close()
        } catch (_: Exception) {
        }
        sileroVad = OnnxSileroVad(context = context)
    }

    private fun detectSpeechBySilero(
        vad: OnnxSileroVad,
        pending: ShortArray,
        buffer: ShortArray,
        size: Int,
    ): Pair<Boolean, ShortArray> {
        val frameSize = 512
        val merged = ShortArray(pending.size + size)
        if (pending.isNotEmpty()) {
            java.lang.System.arraycopy(pending, 0, merged, 0, pending.size)
        }
        java.lang.System.arraycopy(buffer, 0, merged, pending.size, size)

        val frame = ShortArray(frameSize)
        var offset = 0
        var hasSpeech = false
        while (offset + frameSize <= merged.size) {
            java.lang.System.arraycopy(merged, offset, frame, 0, frameSize)
            if (vad.isSpeech(frame)) {
                hasSpeech = true
            }
            offset += frameSize
        }

        val remain = merged.size - offset
        val newPending = if (remain > 0) merged.copyOfRange(offset, merged.size) else ShortArray(0)
        return Pair(hasSpeech, newPending)
    }

    /**
     * 计算音频缓冲区的音量级别
     */
    private fun calculateVolumeLevel(buffer: ShortArray, size: Int): Float {
        if (size <= 0) return 0f
        
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        
        val average = sum / size
        val maxAmplitude = 32768.0
        val db = if (average > 0) 20 * log10(average / maxAmplitude) else -160.0
        val normalizedDb = (db + 60.0) / 60.0
        val volume = normalizedDb.coerceIn(0.0, 1.0).toFloat()
        
        currentVolume = currentVolume * (1 - VOLUME_SMOOTHING_FACTOR) + volume * VOLUME_SMOOTHING_FACTOR
        
        return currentVolume
    }

    override suspend fun startRecognition(
            languageCode: String,
            continuousMode: Boolean,
            partialResults: Boolean,
            audioSource: Int,
    ): Boolean {
        if (!isInitialized.value) {
            if (!initialize()) return false
        }
        if (isRecognizing) return false

        _recognitionState.value = SpeechService.RecognitionState.PREPARING
        _recognitionResult.value = SpeechService.RecognitionResult(text = "", isFinal = false, confidence = 0f)
        
        // 重置 VAD 和创建新的 stream
        vad?.reset()
        sileroVad?.reset()
        // 安全释放旧的 stream
        try {
            stream?.release()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error releasing old stream", e)
        }
        stream = null
        stream = recognizer?.createStream()

        val pendingPcm = SpeechPrerollStore.consumePending()
        if (pendingPcm != null && pendingPcm.isNotEmpty()) {
            try {
                val samples = FloatArray(pendingPcm.size) { i -> pendingPcm[i] / 32768.0f }
                val streamInstance = stream
                if (streamInstance != null) {
                    streamInstance.acceptWaveform(samples, 16000)
                    recognizer?.let { recognizerInstance ->
                        while (recognizerInstance.isReady(streamInstance)) {
                            recognizerInstance.decode(streamInstance)
                        }
                    }
                }
                AppLogger.d(TAG, "Applied preroll: samples=${pendingPcm.size}")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to apply preroll", e)
            }
        }

        val sampleRateInHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            AppLogger.e(TAG, "AudioRecord.getMinBufferSize returned invalid size: $minBufferSize")
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-2, "Invalid AudioRecord buffer size")
            return false
        }

        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )

        val recordInstance = audioRecord
        if (recordInstance == null || recordInstance.state != AudioRecord.STATE_INITIALIZED) {
            AppLogger.e(TAG, "AudioRecord is not initialized (state=${recordInstance?.state})")
            try {
                recordInstance?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-3, "AudioRecord not initialized")
            return false
        }

        try {
            recordInstance.startRecording()
        } catch (e: Exception) {
            AppLogger.e(TAG, "AudioRecord.startRecording failed", e)
            try {
                recordInstance.release()
            } catch (_: Exception) {
            }
            audioRecord = null
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value = SpeechService.RecognitionError(-4, e.message ?: "AudioRecord start failed")
            return false
        }
        _recognitionState.value = SpeechService.RecognitionState.RECOGNIZING
        currentVolume = 0f
        _volumeLevelFlow.value = 0f
        AppLogger.d(TAG, "Started recording")

        recordingJob = scope.launch {
            val bufferSize = minBufferSize
            val audioBuffer = ShortArray(bufferSize)
            var lastText = ""
            var hasSpeechDetected = false
            var vadPending = ShortArray(0)

            while (isActive &&
                    _recognitionState.value == SpeechService.RecognitionState.RECOGNIZING) {
                val ret = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                // 如果读取失败或录音已停止，退出循环
                if (ret <= 0) {
                    AppLogger.d(TAG, "AudioRecord read returned $ret, stopping loop")
                    break
                }
                if (ret > 0) {
                    SpeechPrerollStore.appendPcm(audioBuffer, ret)
                    // 计算并更新音量级别
                    val volumeLevel = calculateVolumeLevel(audioBuffer, ret)
                    _volumeLevelFlow.value = volumeLevel
                    
                    val samples = FloatArray(ret) { i -> audioBuffer[i] / 32768.0f }
                    
                    // VAD 检测
                    val silero = sileroVad
                    if (silero != null) {
                        val (isSpeech, newPending) =
                            detectSpeechBySilero(silero, vadPending, audioBuffer, ret)
                        vadPending = newPending

                        if (isSpeech) {
                            hasSpeechDetected = true
                            // 有语音时送入识别器
                            stream?.let { streamInstance: OnlineStream ->
                                streamInstance.acceptWaveform(samples, sampleRateInHz)
                                
                                // 检查是否可以解码
                                recognizer?.let { recognizerInstance: OnlineRecognizer ->
                                    while (recognizerInstance.isReady(streamInstance)) {
                                        recognizerInstance.decode(streamInstance)
                                    }
                                    
                                    val result = recognizerInstance.getResult(streamInstance)
                                    val isEndpoint = recognizerInstance.isEndpoint(streamInstance)
                                    
                                    if (result.text.isNotBlank() && lastText != result.text) {
                                        lastText = result.text
                                        _recognitionResult.value = SpeechService.RecognitionResult(
                                            text = result.text,
                                            isFinal = isEndpoint
                                        )
                                    }
                                    
                                    if (isEndpoint) {
                                        recognizerInstance.reset(streamInstance)
                                        if (!continuousMode) {
                                            _recognitionState.value = SpeechService.RecognitionState.IDLE
                                            return@launch
                                        }
                                    }
                                }
                            }
                        } else if (hasSpeechDetected) {
                            // 之前有语音，现在静默，可能需要处理端点
                            stream?.let { streamInstance: OnlineStream ->
                                recognizer?.let { recognizerInstance: OnlineRecognizer ->
                                    if (recognizerInstance.isEndpoint(streamInstance)) {
                                        recognizerInstance.reset(streamInstance)
                                        if (!continuousMode) {
                                            _recognitionState.value = SpeechService.RecognitionState.IDLE
                                            return@launch
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 如果没有 VAD，直接送入识别器（降级模式）
                        stream?.let { streamInstance: OnlineStream ->
                            streamInstance.acceptWaveform(samples, sampleRateInHz)
                            recognizer?.let { recognizerInstance: OnlineRecognizer ->
                                while (recognizerInstance.isReady(streamInstance)) {
                                    recognizerInstance.decode(streamInstance)
                                }
                                
                                val result = recognizerInstance.getResult(streamInstance)
                                val isEndpoint = recognizerInstance.isEndpoint(streamInstance)
                                
                                if (result.text.isNotBlank() && lastText != result.text) {
                                    lastText = result.text
                                    _recognitionResult.value = SpeechService.RecognitionResult(
                                        text = result.text,
                                        isFinal = isEndpoint
                                    )
                                }
                                
                                if (isEndpoint) {
                                    recognizerInstance.reset(streamInstance)
                                    if (!continuousMode) {
                                        _recognitionState.value = SpeechService.RecognitionState.IDLE
                                        return@launch
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 注意：不要在这里释放 stream，让 stopRecognition 来处理
            // 这样可以避免在 stopRecognition 使用 stream 时它已被释放
            // stream 会在 stopRecognition 或下次 startRecognition 时释放

            withContext(Dispatchers.Main) {
                _recognitionState.value = SpeechService.RecognitionState.IDLE
                _volumeLevelFlow.value = 0f
            }
            AppLogger.d(TAG, "Stopped recording.")
        }
        return true
    }

    override suspend fun stopRecognition(): Boolean {
        if (recordingJob?.isActive == true &&
                _recognitionState.value == SpeechService.RecognitionState.RECOGNIZING
        ) {
            AppLogger.d(TAG, "Stopping recognition...")
            
            // 先停止录音，这样 recordingJob 的循环会自然退出
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error stopping AudioRecord", e)
            }
            _recognitionState.value = SpeechService.RecognitionState.PROCESSING
            
            // 等待 recordingJob 完成，确保它不再使用 stream
            try {
                recordingJob?.join()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error waiting for recording job", e)
            }
            
            // 现在在 IO 线程上安全地完成识别
            withContext(Dispatchers.IO) {
                try {
                    val streamInstance = stream
                    if (streamInstance != null && recognizer != null) {
                        // 标记输入完成
                        streamInstance.inputFinished()
                        
                        // 完成所有解码
                        val recognizerInstance = recognizer!!
                        while (recognizerInstance.isReady(streamInstance)) {
                            recognizerInstance.decode(streamInstance)
                        }
                        
                        // 获取最终结果
                        val result = recognizerInstance.getResult(streamInstance)
                        withContext(Dispatchers.Main) {
                            _recognitionResult.value = SpeechService.RecognitionResult(
                                text = result.text,
                                isFinal = true
                            )
                        }
                    } else {
                        AppLogger.w(TAG, "Stream or recognizer is null, cannot finalize recognition")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error finalizing recognition", e)
                }
            }

            // 释放资源（包括 stream）
            try {
                stream?.release()
                stream = null
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error releasing stream", e)
            }
            
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error releasing AudioRecord", e)
            }
            audioRecord = null
            _recognitionState.value = SpeechService.RecognitionState.IDLE
            _volumeLevelFlow.value = 0f
            return true
        }
        return false
    }

    override suspend fun cancelRecognition() {
        AppLogger.d(TAG, "Cancelling recognition...")
        
        // 先停止录音，让 recordingJob 自然退出
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping AudioRecord", e)
        }
        
        // 取消 recordingJob 并等待完成
        if (recordingJob?.isActive == true) {
            recordingJob?.cancel()
            try {
                recordingJob?.join()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error waiting for recording job to cancel", e)
            }
        }
        
        // 现在安全地释放资源
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        
        // 注意：不要在这里释放 stream，因为它可能被 recordingJob 使用
        // stream 会在下次 startRecognition 时重新创建
        // stream?.release()
        // stream = null
        
        vad?.reset()
        _recognitionState.value = SpeechService.RecognitionState.IDLE
        _volumeLevelFlow.value = 0f
        _recognitionResult.value = SpeechService.RecognitionResult(text = "", isFinal = false, confidence = 0f)
    }

    override fun shutdown() {
        runBlocking {
            try {
                cancelRecognition()
            } catch (_: Exception) {
            }
            withContext(Dispatchers.IO) {
                try {
                    stream?.release()
                } catch (_: Exception) {
                }
                stream = null
                try {
                    vad?.release()
                } catch (_: Exception) {
                }
                vad = null
                try {
                    sileroVad?.close()
                } catch (_: Exception) {
                }
                sileroVad = null
                try {
                    recognizer?.release()
                } catch (_: Exception) {
                }
                recognizer = null
            }
            _isInitialized.value = false
            _recognitionState.value = SpeechService.RecognitionState.UNINITIALIZED
            _volumeLevelFlow.value = 0f
            _recognitionResult.value = SpeechService.RecognitionResult(text = "", isFinal = false, confidence = 0f)
        }
    }

    override suspend fun getSupportedLanguages(): List<String> =
            withContext(Dispatchers.IO) {
                return@withContext listOf("zh", "en", "es", "ko", "id", "ms", "pt-BR")
            }

    override suspend fun recognize(audioData: FloatArray) {
        // Not implemented for streaming recognizer
        withContext(Dispatchers.Main) {
            _recognitionError.value =
                    SpeechService.RecognitionError(
                            -10,
                            "Batch recognition not supported in this provider"
                    )
            _recognitionState.value = SpeechService.RecognitionState.ERROR
        }
    }
}

