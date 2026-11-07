package com.ai.assistance.operit.ui.floating.voice

import android.content.Intent
import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.api.speech.SpeechPrerollStore
import com.ai.assistance.operit.api.speech.WakePhraseSnapshot
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.util.TtsCleaner
import com.ai.assistance.operit.util.WaifuMessageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SpeechInteractionManager"

/**
 * 通用语音交互管理器
 * 封装了：语音识别、TTS、文本累积、静默检测等逻辑
 */
class SpeechInteractionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onSpeechResult: (String, Boolean) -> Unit, // (text, isFinal)
    private val onStateChange: (String) -> Unit // 更新状态提示文本
) {
    // ===== 状态 =====
    var isRecording by mutableStateOf(false)
        private set
    var isProcessingSpeech by mutableStateOf(false)
        private set

    // 文本累积
    var userMessage by mutableStateOf("")
        private set
    private var accumulatedText = ""
    private var latestPartialText = ""

    private var wakePhraseSnapshot: WakePhraseSnapshot? = null

    // 任务控制
    private var timeoutJob: Job? = null
    private var silenceTimeoutJob: Job? = null

    // ===== 服务 =====
    val speechService = SpeechServiceFactory.getInstance(context)
    val voiceService
        get() = VoiceServiceFactory.getInstance(context)

    // 暴露 Flow 给外部使用
    val volumeLevelFlow get() = speechService.volumeLevelFlow
    val recognitionResultFlow get() = speechService.recognitionResultFlow

    // ===== 初始化与清理 =====
    suspend fun initialize() {
        resetState()
        try {
            speechService.initialize()
            voiceService.initialize()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize speech services", e)
        }
    }

    fun cleanup() {
        stopListening(isCancel = true)
        coroutineScope.launch {
            speechService.cancelRecognition()
            voiceService.stop()
        }
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
    }

    private fun resetState() {
        isRecording = false
        isProcessingSpeech = false
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
        wakePhraseSnapshot = null
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
    }

    // ===== 语音识别流程 =====

    // 麦克风的 while-in-use 资格由 FloatingChatService 提供：它声明了 microphone
    // 前台服务类型并贯穿整个浮窗会话。AIForegroundService 在每轮 AI 回复结束时自毁，
    // 无法跨轮持有该类型，所以这里不再走它的 promote 路径，只负责唤醒交接和启动识别。
    fun startListening(onStartFailure: ((String) -> Unit)? = null) {
        // 重置超时
        timeoutJob?.cancel()

        // 重置文本状态
        isRecording = true
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
        wakePhraseSnapshot = SpeechPrerollStore.consumePendingWakePhrase()
        onStateChange(context.getString(R.string.floating_listening))

        // 启动监听
        coroutineScope.launch {
            try {
                try {
                    context.startService(Intent(context, AIForegroundService::class.java).apply {
                        action = AIForegroundService.ACTION_PREPARE_WAKE_HANDOFF
                    })
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to request wake handoff prepare", e)
                }

                // Give AIForegroundService a moment to stop wake listening and release microphone
                delay(180)

                var ok = false
                var attempt = 0
                while (!ok && attempt < 12) {
                    if (attempt > 0) {
                        delay(160)
                    }
                    ok = speechService.startRecognition(
                        languageCode = "zh-CN",
                        continuousMode = true,
                        partialResults = true
                    )
                    attempt++
                }

                if (!ok) {
                    isRecording = false
                    isProcessingSpeech = false
                    onStateChange(context.getString(R.string.floating_hold_microphone))
                    onStartFailure?.invoke(context.getString(R.string.floating_start_recording_failed))
                }
            } catch (e: Exception) {
                isRecording = false
                isProcessingSpeech = false
                onStateChange(context.getString(R.string.floating_hold_microphone))
                onStartFailure?.invoke(e.message ?: context.getString(R.string.floating_start_recording_failed))
            }
        }

        // 监听结果流 (假设这是 ViewModel 或者外部调用者通过 collectLatest 连接到 handleRecognitionResult)
        // 注意：SpeechService 的结果是通过 Flow 发出的，这里我们需要一个桥接方式，
        // 或者让 ViewModel 监听 Flow 并调用这里的 handleRecognitionResult。
        // 为了简化，这里我们假设 ViewModel 负责监听 SpeechService 的 Flow 并调用本类的 handleRecognitionResult
    }

    fun stopListening(isCancel: Boolean) {
        if (!isRecording) return
        
        isRecording = false
        silenceTimeoutJob?.cancel()

        coroutineScope.launch {
            if (isCancel) {
                speechService.cancelRecognition()
                isProcessingSpeech = false
                resetState()
                onStateChange(context.getString(R.string.floating_hold_microphone))
            } else {
                isProcessingSpeech = true
                onStateChange(context.getString(R.string.floating_recognizing))
                speechService.stopRecognition()
                startFallbackTimeout()
            }
        }
    }

    // 处理识别结果
    fun handleRecognitionResult(resultText: String, isFinal: Boolean, autoSendSilence: Boolean = false) {
        val effectiveText = stripWakePhrasePrefixIfNeeded(resultText)
        if (isRecording) {
            if (effectiveText.isNotBlank()) {
                // 处理增量
                if (latestPartialText.isNotEmpty() && !effectiveText.startsWith(latestPartialText)) {
                    accumulatedText += (if (accumulatedText.isNotEmpty()) "。" else "") + latestPartialText
                }
                latestPartialText = effectiveText

                // 静默检测
                if (autoSendSilence) {
                    silenceTimeoutJob?.cancel()
                    silenceTimeoutJob = coroutineScope.launch {
                        delay(2000)
                        AppLogger.d(TAG, "Silence timeout, sending...")
                        finalizeSpeechInput()
                    }
                }
            }
            userMessage = accumulatedText + (if (accumulatedText.isNotEmpty() || latestPartialText.isNotEmpty()) latestPartialText else "")
            
        } else if (isProcessingSpeech && isFinal) {
            timeoutJob?.cancel()
            accumulatedText += (if (accumulatedText.isNotEmpty() && effectiveText.isNotBlank()) "。" else "") + effectiveText
            finalizeSpeechInput()
        }
    }

    private fun stripWakePhrasePrefixIfNeeded(text: String): String {
        val snapshot = wakePhraseSnapshot ?: return text
        if (text.isBlank()) return text

        val leadingRegex = Regex("^[\\s\\p{Punct}，。！？；：、“”‘’【】（）()\\[\\]{}<>《》]+")
        val leadingMatch = leadingRegex.find(text)
        val startIdx = leadingMatch?.value?.length ?: 0
        val trimmed = text.substring(startIdx)

        if (trimmed.isBlank()) return text

        if (snapshot.regexEnabled) {
            return try {
                val m = Regex(snapshot.phrase).find(trimmed)
                if (m != null && m.range.first == 0) {
                    val remainder = trimmed.substring(m.value.length)
                    remainder.replaceFirst(leadingRegex, "")
                } else {
                    text
                }
            } catch (_: Exception) {
                text
            }
        }

        val phrase = snapshot.phrase
        if (phrase.isBlank()) return text
        return if (trimmed.startsWith(phrase)) {
            val remainder = trimmed.substring(phrase.length)
            remainder.replaceFirst(leadingRegex, "")
        } else {
            text
        }
    }

    private fun startFallbackTimeout() {
        timeoutJob = coroutineScope.launch {
            delay(3000)
            if (isProcessingSpeech) {
                AppLogger.w(TAG, "Fallback timeout")
                finalizeSpeechInput()
            }
        }
    }

    private fun finalizeSpeechInput() {
        isProcessingSpeech = false
        val text = userMessage.ifBlank { accumulatedText }
        
        if (text.isNotBlank()) {
            onSpeechResult(text, true)
            onStateChange(context.getString(R.string.floating_thinking_2))
        } else {
            onStateChange(context.getString(R.string.floating_didnt_hear_clearly))
        }

        // 如果不是静默超时触发的，重置文本（静默超时可能意味着这一句结束，准备下一句，或者直接发送）
        // 这里我们统一重置，因为 onSpeechResult 通常会触发发送
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
    }
    
    // ===== TTS 辅助 =====
    
    fun speak(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        coroutineScope.launch {
            try {
                voiceService.speak(text, interrupt)
            } catch (e: Exception) {
                AppLogger.e(TAG, "TTS Error", e)
            }
        }
    }
    
    fun cleanTextForTts(text: String, regexs: List<String>): String {
        return WaifuMessageProcessor.cleanContentForWaifu(TtsCleaner.cleanForSpeech(text, regexs))
    }
}
