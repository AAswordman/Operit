package com.ai.assistance.operit.api.speech.stt

import kotlinx.coroutines.ensureActive

import android.annotation.SuppressLint
import android.content.Context
import com.ai.assistance.operit.R
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ai.assistance.operit.util.logging.AppLogger
import com.k2fsa.sherpa.ncnn.*
import com.ai.assistance.operit.api.speech.audio.SpeechPrerollStore
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.data.storage.LocalModelUsageHandle
import java.io.File
import com.ai.assistance.operit.data.resources.OfflineResources
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import com.ai.assistance.operit.api.speech.audio.OnnxSileroVad

/**
 * 基于sherpa-ncnn的本地语音识别实现 sherpa-ncnn是一个轻量级、高性能的语音识别引擎，比Whisper更适合移动端 参考:
 * https://github.com/k2-fsa/sherpa-ncnn
 */
@SuppressLint("MissingPermission")
class SherpaSpeechProvider(private val context: Context) : SttService {
    companion object {
        private const val TAG = "SherpaSpeechProvider"
    }

    private var recognizer: SherpaNcnn? = null
    private var modelUsageHandle: LocalModelUsageHandle? = null
    private var vad: OnnxSileroVad? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun clearAndReleaseAudioRecord() {
        val record = audioRecord
        audioRecord = null

        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error stopping AudioRecord", e)
            }
            try {
                record.release()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error releasing AudioRecord", e)
            }
        }
    }

    private val _recognitionState = MutableStateFlow(SttService.RecognitionState.UNINITIALIZED)
    override val currentState: SttService.RecognitionState
        get() = _recognitionState.value
    override val recognitionStateFlow: StateFlow<SttService.RecognitionState> =
            _recognitionState.asStateFlow()

    private val _recognitionResult = MutableStateFlow(SttService.RecognitionResult(""))
    override val recognitionResultFlow: StateFlow<SttService.RecognitionResult> =
            _recognitionResult.asStateFlow()

    private val _recognitionError = MutableStateFlow(SttService.RecognitionError(0, ""))
    override val recognitionErrorFlow: StateFlow<SttService.RecognitionError> =
            _recognitionError.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // 添加音量级别Flow实现
    private val _volumeLevelFlow = MutableStateFlow(0f)
    override val volumeLevelFlow: StateFlow<Float> = _volumeLevelFlow.asStateFlow()

    override val isRecognizing: Boolean
        get() = currentState == SttService.RecognitionState.RECOGNIZING

    // 音量计算相关常量
    private val VOLUME_SMOOTHING_FACTOR = 0.1f // 平滑因子
    private var currentVolume = 0f

    private val initializeMutex = Mutex()
    private val recognitionMutex = Mutex()

    override suspend fun initialize(): Boolean {
        if (isInitialized.value) return true
        return initializeMutex.withLock {
            if (isInitialized.value) return@withLock true
            AppLogger.d(TAG, "Initializing sherpa-ncnn...")
            try {
                withContext(Dispatchers.IO) {
                    createRecognizer()
                    if (recognizer != null) {
                        AppLogger.d(TAG, "sherpa-ncnn initialized successfully")
                        _isInitialized.value = true
                        _recognitionState.value = SttService.RecognitionState.IDLE
                        true
                    } else {
                        AppLogger.e(TAG, "Failed to create sherpa-ncnn recognizer")
                        _recognitionState.value = SttService.RecognitionState.ERROR
                        _recognitionError.value =
                            SttService.RecognitionError(-1, "Failed to initialize recognizer")
                        false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize sherpa-ncnn", e)
                _recognitionState.value = SttService.RecognitionState.ERROR
                _recognitionError.value =
                    SttService.RecognitionError(-1, e.message ?: "Unknown error")
                false
            }
        }
    }

    private suspend fun createRecognizer() = OfflineResources.withSpeechModel(context) { localModelDir ->
        val featConfig = getFeatureExtractorConfig(sampleRate = 16000.0f, featureDim = 80)

        val modelConfig =
                ModelConfig(
                        encoderParam =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        encoderBin =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        decoderParam =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        decoderBin =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        joinerParam =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        joinerBin =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.bin").absolutePath,
                        tokens = File(localModelDir, "tokens.txt").absolutePath,
                        numThreads = 4,
                        useGPU = false
                )

        val decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4)

        val recognizerConfig =
                RecognizerConfig(
                        featConfig = featConfig,
                        modelConfig = modelConfig,
                        decoderConfig = decoderConfig,
                        enableEndpoint = true,
                        rule1MinTrailingSilence = 2.4f,
                        rule2MinTrailingSilence = 1.2f,
                        rule3MinUtteranceLength = 20.0f,
                        hotwordsFile = "",
                        hotwordsScore = 1.5f
                )

        val usageHandle = LocalModelRuntimeRegistry.acquire(localModelDir)
        if (usageHandle == null) {
            AppLogger.e(TAG, "Speech model is being deleted; skip recognizer creation.")
            _recognitionState.value = SttService.RecognitionState.ERROR
            _recognitionError.value =
                SttService.RecognitionError(-1, "Failed to initialize recognizer")
            return@withSpeechModel
        }
        recognizer =
                try {
                    SherpaNcnn(
                            config = recognizerConfig,
                            assetManager = null // 模型位于应用数据目录，使用文件接口加载
                    )
                } catch (error: Exception) {
                    usageHandle.close()
                    throw error
                }
        modelUsageHandle = usageHandle
    }

    /**
     * 计算音频缓冲区的音量级别
     * 
     * @param buffer 音频数据缓冲区
     * @return 音量级别，范围在0.0-1.0之间
     */
    private fun calculateVolumeLevel(buffer: ShortArray, size: Int): Float {
        if (size <= 0) return 0f
        
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        
        // 计算平均振幅
        val average = sum / size
        
        // 转换为分贝值 (相对于最大振幅)
        val maxAmplitude = 32768.0
        val db = if (average > 0) 20 * log10(average / maxAmplitude) else -160.0
        
        // 将分贝值映射到0-1范围 (典型语音范围约为-60dB到0dB)
        val normalizedDb = (db + 60.0) / 60.0
        val volume = normalizedDb.coerceIn(0.0, 1.0).toFloat()
        
        // 应用平滑处理
        currentVolume = currentVolume * (1 - VOLUME_SMOOTHING_FACTOR) + volume * VOLUME_SMOOTHING_FACTOR
        
        return currentVolume
    }

    override suspend fun startRecognition(
            languageCode: String,
            continuousMode: Boolean,
            partialResults: Boolean,
            audioSource: Int,
    ): Boolean {
        return recognitionMutex.withLock {
            if (!isInitialized.value) {
                if (!initialize()) return@withLock false
            }
            if (
                currentState == SttService.RecognitionState.PREPARING ||
                    currentState == SttService.RecognitionState.PROCESSING ||
                    currentState == SttService.RecognitionState.RECOGNIZING ||
                    recordingJob?.isActive == true
            ) {
                return@withLock false
            }

            // 防御性清理：避免上一次异常/竞态导致 AudioRecord 遗留
            clearAndReleaseAudioRecord()

            _recognitionState.value = SttService.RecognitionState.PREPARING
            // 清空上一轮的识别结果，避免新的订阅者立刻收到旧的 StateFlow 值
            _recognitionResult.value = SttService.RecognitionResult(text = "", isFinal = false, confidence = 0f)
            recognizer?.reset(false) // 使用SherpaNcnn中的reset方法，参数为false不重新创建识别器

            val pendingPcm = SpeechPrerollStore.consumePending()
            if (pendingPcm != null && pendingPcm.isNotEmpty()) {
                try {
                    recognizer?.let { recognizerInstance ->
                        val samples = FloatArray(pendingPcm.size) { i -> pendingPcm[i] / 32768.0f }
                        recognizerInstance.acceptSamples(samples)
                        while (recognizerInstance.isReady()) {
                            recognizerInstance.decode()
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
                _recognitionState.value = SttService.RecognitionState.ERROR
                _recognitionError.value = SttService.RecognitionError(-2, "Invalid AudioRecord buffer size")
                return@withLock false
            }

            audioRecord =
                    AudioRecord(
                            audioSource,
                            sampleRateInHz,
                            channelConfig,
                            audioFormat,
                            minBufferSize * 2
                    )
            val recordInstance = audioRecord
            if (recordInstance == null || recordInstance.state != AudioRecord.STATE_INITIALIZED) {
                AppLogger.e(TAG, "AudioRecord is not initialized (state=${recordInstance?.state})")
                clearAndReleaseAudioRecord()
                _recognitionState.value = SttService.RecognitionState.ERROR
                _recognitionError.value = SttService.RecognitionError(-3, "AudioRecord not initialized")
                return@withLock false
            }
            try {
                recordInstance.startRecording()
            } catch (e: Exception) {
                AppLogger.e(TAG, "AudioRecord.startRecording failed", e)
                clearAndReleaseAudioRecord()
                _recognitionState.value = SttService.RecognitionState.ERROR
                _recognitionError.value = SttService.RecognitionError(-4, e.message ?: "AudioRecord start failed")
                return@withLock false
            }
            _recognitionState.value = SttService.RecognitionState.RECOGNIZING
            // 重置音量
            currentVolume = 0f
            _volumeLevelFlow.value = 0f
            AppLogger.d(TAG, "Started recording")

            recordingJob =
                    scope.launch {
                    try {
                        val bufferSize = minBufferSize
                        val audioBuffer = ShortArray(bufferSize)
                        var lastText = ""

                        val vadInstance = try {
                            (vad ?: OnnxSileroVad(context = context, speechDurationMs = 0)).also { created ->
                                vad = created
                                created.reset()
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed to initialize Silero VAD, falling back to non-VAD mode", e)
                            null
                        }

                        val vadFrameSize = 512
                        val vadFrame = ShortArray(vadFrameSize)
                        var vadFramePos = 0
                        var vadSpeechActive = false

                        while (isActive &&
                                _recognitionState.value == SttService.RecognitionState.RECOGNIZING) {
                            val ret = try {
                                audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "AudioRecord.read failed", e)
                                break
                            }
                            if (ret <= 0) break

                            SpeechPrerollStore.appendPcm(audioBuffer, ret)
                            val volumeLevel = calculateVolumeLevel(audioBuffer, ret)
                            _volumeLevelFlow.value = volumeLevel

                            recognizer?.let { recognizerInstance ->
                                if (vadInstance == null) {
                                    val samples = FloatArray(ret) { i -> audioBuffer[i] / 32768.0f }
                                    recognizerInstance.acceptSamples(samples)
                                    while (recognizerInstance.isReady()) {
                                        recognizerInstance.decode()
                                    }

                                    val isEndpoint = recognizerInstance.isEndpoint()
                                    val text = recognizerInstance.text

                                    if (text.isNotBlank()) {
                                        if (isEndpoint) {
                                            lastText = text
                                            _recognitionResult.value =
                                                SttService.RecognitionResult(text = text, isFinal = true)
                                        } else if (partialResults && lastText != text) {
                                            lastText = text
                                            _recognitionResult.value =
                                                SttService.RecognitionResult(text = text, isFinal = false)
                                        }
                                    }

                                    if (isEndpoint) {
                                        recognizerInstance.reset(false)
                                        if (!continuousMode) {
                                            _recognitionState.value = SttService.RecognitionState.IDLE
                                            return@launch
                                        }
                                    }
                                } else {
                                    var idx = 0
                                    while (idx < ret) {
                                        val toCopy = minOf(vadFrameSize - vadFramePos, ret - idx)
                                        java.lang.System.arraycopy(audioBuffer, idx, vadFrame, vadFramePos, toCopy)
                                        vadFramePos += toCopy
                                        idx += toCopy

                                        if (vadFramePos == vadFrameSize) {
                                            val isSpeech = vadInstance.isSpeech(vadFrame)

                                            if (isSpeech) {
                                                vadSpeechActive = true

                                                val frameSamples = FloatArray(vadFrameSize) { i -> vadFrame[i] / 32768.0f }
                                                recognizerInstance.acceptSamples(frameSamples)
                                                while (recognizerInstance.isReady()) {
                                                    recognizerInstance.decode()
                                                }

                                                val text = recognizerInstance.text
                                                if (partialResults && text.isNotBlank() && lastText != text) {
                                                    lastText = text
                                                    _recognitionResult.value =
                                                        SttService.RecognitionResult(text = text, isFinal = false)
                                                }
                                            } else if (vadSpeechActive) {
                                                recognizerInstance.inputFinished()
                                                while (recognizerInstance.isReady()) {
                                                    recognizerInstance.decode()
                                                }

                                                val finalText = recognizerInstance.text
                                                if (finalText.isNotBlank()) {
                                                    lastText = finalText
                                                    _recognitionResult.value =
                                                        SttService.RecognitionResult(text = finalText, isFinal = true)
                                                }

                                                recognizerInstance.reset(false)
                                                vadInstance.reset()
                                                vadSpeechActive = false

                                                if (!continuousMode) {
                                                    _recognitionState.value = SttService.RecognitionState.IDLE
                                                    return@launch
                                                }
                                            }

                                            vadFramePos = 0
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Recording loop failed", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            _recognitionState.value = SttService.RecognitionState.IDLE
                            _volumeLevelFlow.value = 0f
                        }
                        AppLogger.d(TAG, "Stopped recording.")
                    }
                    }
            return@withLock true
        }
    }

    override suspend fun stopRecognition(): Boolean {
        return recognitionMutex.withLock {
            if (recordingJob?.isActive == true &&
                            _recognitionState.value == SttService.RecognitionState.RECOGNIZING
            ) {
                AppLogger.d(TAG, "Stopping recognition...")
                try {
                    audioRecord?.stop()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error stopping AudioRecord", e)
                }
                _recognitionState.value =
                        SttService.RecognitionState.PROCESSING // Indicate processing then idle

                recordingJob?.cancel()
                try {
                    recordingJob?.join()
                } catch (_: Exception) {
                }
                recordingJob = null

                // Finalize recognition
                try {
                    recognizer?.inputFinished()
                    val text = recognizer?.text ?: ""
                    _recognitionResult.value = SttService.RecognitionResult(text = text, isFinal = true)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Finalize recognition failed", e)
                }

                clearAndReleaseAudioRecord()
                _recognitionState.value = SttService.RecognitionState.IDLE
                _volumeLevelFlow.value = 0f // 重置音量
                return@withLock true
            }
            return@withLock false
        }
    }

    override suspend fun cancelRecognition() {
        recognitionMutex.withLock {
            if (recordingJob?.isActive == true) {
                recordingJob?.cancel()
                try {
                    recordingJob?.join()
                } catch (_: Exception) {
                }
            }
            recordingJob = null
            clearAndReleaseAudioRecord()
            _recognitionState.value = SttService.RecognitionState.IDLE
            _volumeLevelFlow.value = 0f // 重置音量
            // 同步清空识别文本，避免下次订阅拿到旧文本
            _recognitionResult.value = SttService.RecognitionResult(text = "", isFinal = false, confidence = 0f)
            try {
                vad?.reset()
            } catch (_: Exception) {
            }
        }
    }

    override fun shutdown() {
        runBlocking {
            try {
                cancelRecognition()
            } catch (_: Exception) {
            }
            withContext(Dispatchers.IO) {
                try {
                    recognizer?.release()
                } catch (_: Exception) {
                } finally {
                    recognizer = null
                    modelUsageHandle?.close()
                    modelUsageHandle = null
                }
                try {
                    vad?.close()
                } catch (_: Exception) {
                }
                vad = null
            }
            _isInitialized.value = false
            _recognitionState.value = SttService.RecognitionState.UNINITIALIZED
            _volumeLevelFlow.value = 0f // 重置音量
            _recognitionResult.value = SttService.RecognitionResult(text = "", isFinal = false, confidence = 0f)
        }
    }

    override suspend fun getSupportedLanguages(): List<String> =
            withContext(Dispatchers.IO) {
                return@withContext listOf("zh", "en")
            }

    /** 语音条先保存录音，再使用同一个本地引擎转写，避免重复打开麦克风。 */
    override suspend fun recognize(audioData: FloatArray) {
        recognitionMutex.withLock {
            if (!isInitialized.value && !initialize()) return@withLock
            check(recordingJob?.isActive != true) { context.getString(R.string.voice_message_stt_busy) }
            _recognitionError.value = SttService.RecognitionError(0, "")
            _recognitionResult.value = SttService.RecognitionResult("")
            _recognitionState.value = SttService.RecognitionState.PROCESSING
            try {
                val text = withContext(Dispatchers.IO) {
                    val engine = checkNotNull(recognizer)
                    engine.reset(false)
                    try {
                        var offset = 0
                        while (offset < audioData.size) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val end = minOf(offset + 16000, audioData.size)
                            engine.acceptSamples(audioData.copyOfRange(offset, end))
                            while (engine.isReady()) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                engine.decode()
                            }
                            offset = end
                        }
                        engine.inputFinished()
                        while (engine.isReady()) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            engine.decode()
                        }
                        engine.text.trim()
                    } finally {
                        engine.reset(false)
                    }
                }
                _recognitionResult.value = SttService.RecognitionResult(text, isFinal = true)
                _recognitionState.value = SttService.RecognitionState.IDLE
            } catch (error: CancellationException) {
                _recognitionState.value = SttService.RecognitionState.IDLE
                throw error
            } catch (error: Exception) {
                AppLogger.e(TAG, "转写预录语音失败", error)
                _recognitionError.value = SttService.RecognitionError(-10, error.message.orEmpty())
                _recognitionState.value = SttService.RecognitionState.ERROR
            }
        }
    }
}
