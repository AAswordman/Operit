package com.ai.assistance.operit.api.speech

import android.media.MediaRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 语音识别服务接口 定义与不同语音识别引擎进行交互的标准方法 */
interface SpeechService {
    /** 当前识别引擎状态枚举 */
    enum class RecognitionState {
        /** 未初始化 */
        UNINITIALIZED,
        /** 空闲中 */
        IDLE,
        /** 准备中 */
        PREPARING,
        /** 识别中 */
        RECOGNIZING,
        /** 等待最终结果 */
        PROCESSING,
        /** 出错 */
        ERROR
    }

    /**
     * 识别结果类型
     * @param text 识别的文本
     * @param isFinal 是否是最终结果
     * @param confidence 置信度 0.0-1.0
     */
    data class RecognitionResult(
            val text: String,
            val isFinal: Boolean = false,
            val confidence: Float = 0f
    )

    /** 识别错误类型 */
    data class RecognitionError(val code: Int, val message: String)

    /** 当前识别引擎是否初始化完成 */
    val isInitialized: StateFlow<Boolean>

    /** 当前识别引擎是否正在识别 */
    val isRecognizing: Boolean

    /** 当前识别状态 */
    val currentState: RecognitionState

    /** 识别状态Flow，用于UI观察状态变化 */
    val recognitionStateFlow: StateFlow<RecognitionState>

    /** 识别结果Flow，包含中间结果和最终结果 */
    val recognitionResultFlow: StateFlow<RecognitionResult>

    /** 识别错误Flow */
    val recognitionErrorFlow: StateFlow<RecognitionError>
    
    /** 音量级别Flow，范围为0.0-1.0，代表当前麦克风输入的音量级别 */
    val volumeLevelFlow: StateFlow<Float>

    /**
     * 初始化语音识别引擎
     *
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean

    /**
     * 开始语音识别
     *
     * @param languageCode 语言代码，例如 "zh-CN"、"en-US"
     * @param continuousMode 是否持续识别模式，true表示不会自动停止，直到调用stop()
     * @param partialResults 是否返回部分结果，若为false则只返回最终结果
     * @param audioSource 录音源，默认使用 VOICE_COMMUNICATION
     * @return 开始识别是否成功
     */
    suspend fun startRecognition(
            languageCode: String = "en-US",
            continuousMode: Boolean = false,
            partialResults: Boolean = true,
            audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
    ): Boolean

    /**
     * 停止语音识别
     *
     * @return 停止识别是否成功
     */
    suspend fun stopRecognition(): Boolean

    /** 取消语音识别，不会返回任何结果 */
    suspend fun cancelRecognition()

    /** 释放语音识别引擎资源 */
    fun shutdown()

    /**
     * 获取支持的语言列表
     *
     * @return 支持的语言代码列表
     */
    suspend fun getSupportedLanguages(): List<String>

    /**
     * 识别预先录制的音频数据
     * 这个方法主要由本地语音识别引擎实现，如WhisperSpeechProvider
     * 
     * @param audioData 音频数据，格式为浮点数组表示的PCM数据
     */
    suspend fun recognize(audioData: FloatArray)
}
