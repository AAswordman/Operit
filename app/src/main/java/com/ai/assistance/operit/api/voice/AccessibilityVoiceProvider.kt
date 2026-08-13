package com.ai.assistance.operit.api.voice

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 基于Android系统TextToSpeech API的语音服务实现
 *
 * 此实现利用Android的TextToSpeech API进行文本转语音
 */
class SimpleVoiceProvider(
    private val context: Context,
    initialLocaleTag: String = "",
    initialVoiceId: String = "",
    private val defaultSpeechRate: Float,
    private val defaultPitch: Float,
) : VoiceService {
    companion object {
        private const val TAG = "SimpleVoiceProvider"
        private const val SPEECH_PREVIEW_MAX = 48
    }

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(SPEECH_PREVIEW_MAX)
    }

    private data class PendingUtterance(
        val utteranceId: String,
        val text: String
    )

    // TextToSpeech引擎实例
    private var tts: TextToSpeech? = null

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

    // 当前语音参数
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentVoiceId: String? = initialVoiceId.takeIf { it.isNotBlank() }
    private var currentLocaleTag: String = initialLocaleTag.trim()

    private val queueLock = Any()
    private val queuedUtterances = ArrayDeque<PendingUtterance>()
    private var currentUtteranceId: String? = null
    private var currentUtteranceText: String = ""
    private var currentUtteranceRangeStart: Int = 0
    private var pausedSegments: List<String> = emptyList()
    private var isPausedInternally: Boolean = false

    private fun logQueueState(event: String, extra: String = "") {
        val suffix = if (extra.isNotBlank()) " $extra" else ""
        AppLogger.d(
            TAG,
            "queue[$event] initialized=${_isInitialized.value} speaking=${_isSpeaking.value} size=${queuedUtterances.size} current=$currentUtteranceId rangeStart=$currentUtteranceRangeStart paused=$isPausedInternally pausedSegments=${pausedSegments.size}$suffix"
        )
    }

    private fun updateSpeakingStateLocked(event: String) {
        _isSpeaking.value = queuedUtterances.isNotEmpty()
        logQueueState(event)
    }

    private fun removeQueuedUtteranceLocked(utteranceId: String): PendingUtterance? {
        val iterator = queuedUtterances.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.utteranceId == utteranceId) {
                iterator.remove()
                return entry
            }
        }
        return null
    }

    private fun buildPausedSegmentsLocked(): List<String> {
        if (queuedUtterances.isEmpty()) return emptyList()

        val currentId = currentUtteranceId
        if (currentId == null) {
            return queuedUtterances.map { it.text }.filter { it.isNotBlank() }
        }

        val result = mutableListOf<String>()
        var foundCurrent = false
        queuedUtterances.forEach { entry ->
            if (!foundCurrent && entry.utteranceId == currentId) {
                foundCurrent = true
                val safeStart = currentUtteranceRangeStart.coerceIn(0, entry.text.length)
                val remaining = entry.text.substring(safeStart).trimStart()
                if (remaining.isNotBlank()) {
                    result += remaining
                }
            } else if (foundCurrent && entry.text.isNotBlank()) {
                result += entry.text
            }
        }

        if (!foundCurrent) {
            return queuedUtterances.map { it.text }.filter { it.isNotBlank() }
        }
        return result
    }

    private fun submitUtterance(
        textToSpeech: TextToSpeech,
        entry: PendingUtterance,
        queueMode: Int
    ): Boolean {
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = entry.utteranceId

        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(entry.text, queueMode, null, entry.utteranceId)
            } else {
                @Suppress("DEPRECATION")
                textToSpeech.speak(entry.text, queueMode, params)
            }
        return result == TextToSpeech.SUCCESS
    }

    /** 初始化TTS引擎 */
    override suspend fun initialize(): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext suspendCancellableCoroutine { continuation ->
                if (_isInitialized.value && tts != null) {
                    continuation.resume(true)
                    return@suspendCancellableCoroutine
                }

                tts =
                    TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            tts?.setSpeechRate(currentRate)
                            tts?.setPitch(currentPitch)
                            AppLogger.d(
                                TAG,
                                "initialize success locale=$currentLocaleTag voice=$currentVoiceId rate=$currentRate pitch=$currentPitch"
                            )

                            tts?.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String) {
                                        synchronized(queueLock) {
                                            val entry =
                                                queuedUtterances.firstOrNull {
                                                    it.utteranceId == utteranceId
                                                }
                                            if (entry == null) {
                                                AppLogger.w(
                                                    TAG,
                                                    "listener onStart ignored stale utteranceId=$utteranceId"
                                                )
                                                return
                                            }
                                            currentUtteranceId = utteranceId
                                            currentUtteranceText = entry.text
                                            currentUtteranceRangeStart = 0
                                            _isSpeaking.value = true
                                            logQueueState(
                                                event = "listener.onStart",
                                                extra = "utteranceId=$utteranceId len=${entry.text.length}"
                                            )
                                        }
                                    }

                                    override fun onDone(utteranceId: String) {
                                        synchronized(queueLock) {
                                            val removed =
                                                removeQueuedUtteranceLocked(utteranceId)
                                            if (currentUtteranceId == utteranceId) {
                                                currentUtteranceId = null
                                                currentUtteranceText = ""
                                                currentUtteranceRangeStart = 0
                                            }
                                            updateSpeakingStateLocked("listener.onDone")
                                            AppLogger.d(
                                                TAG,
                                                "listener onDone utteranceId=$utteranceId removed=${removed != null}"
                                            )
                                        }
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun onError(utteranceId: String) {
                                        synchronized(queueLock) {
                                            removeQueuedUtteranceLocked(utteranceId)
                                            if (currentUtteranceId == utteranceId) {
                                                currentUtteranceId = null
                                                currentUtteranceText = ""
                                                currentUtteranceRangeStart = 0
                                            }
                                            updateSpeakingStateLocked("listener.onError")
                                        }
                                        AppLogger.e(
                                            TAG,
                                            "listener onError utteranceId=$utteranceId"
                                        )
                                    }

                                    override fun onError(
                                        utteranceId: String,
                                        errorCode: Int
                                    ) {
                                        super.onError(utteranceId, errorCode)
                                        synchronized(queueLock) {
                                            removeQueuedUtteranceLocked(utteranceId)
                                            if (currentUtteranceId == utteranceId) {
                                                currentUtteranceId = null
                                                currentUtteranceText = ""
                                                currentUtteranceRangeStart = 0
                                            }
                                            updateSpeakingStateLocked("listener.onErrorCode")
                                        }
                                        AppLogger.e(
                                            TAG,
                                            "TTS错误: utteranceId=$utteranceId, errorCode=$errorCode"
                                        )
                                    }

                                    override fun onRangeStart(
                                        utteranceId: String,
                                        start: Int,
                                        end: Int,
                                        frame: Int
                                    ) {
                                        synchronized(queueLock) {
                                            if (utteranceId == currentUtteranceId) {
                                                currentUtteranceRangeStart =
                                                    start.coerceIn(
                                                        0,
                                                        currentUtteranceText.length
                                                    )
                                            }
                                            logQueueState(
                                                event = "listener.onRangeStart",
                                                extra = "utteranceId=$utteranceId start=$start end=$end frame=$frame"
                                            )
                                        }
                                    }
                                }
                            )

                            _isInitialized.value = true
                            continuation.resume(true)
                        } else {
                            AppLogger.e(TAG, "TTS初始化失败: $status")
                            _isInitialized.value = false
                            continuation.resumeWith(
                                Result.failure(
                                    TtsException(
                                        context.getString(
                                            R.string.accessibility_tts_init_failed,
                                            status
                                        )
                                    )
                                )
                            )
                        }
                    }

                continuation.invokeOnCancellation { shutdown() }
            }
        }

    /** 将文本转换为语音并播放 */
    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!isInitialized) {
                val initResult = initialize()
                if (!initResult) {
                    return@withContext false
                }
            }

            val effectiveRate = rate ?: defaultSpeechRate
            val effectivePitch = pitch ?: defaultPitch
            val queueSizeBefore = synchronized(queueLock) { queuedUtterances.size }
            AppLogger.d(
                TAG,
                "speak request interrupt=$interrupt len=${text.length} preview=\"${speechPreview(text)}\" rate=$effectiveRate pitch=$effectivePitch voice=$currentVoiceId locale=$currentLocaleTag initialized=$isInitialized speaking=$isSpeaking queueSize=$queueSizeBefore paused=$isPausedInternally"
            )

            return@withContext suspendCancellableCoroutine { continuation ->
                tts?.let { textToSpeech ->
                    ensureVoiceAndLocaleReady(textToSpeech)

                    if (currentRate != effectiveRate) {
                        textToSpeech.setSpeechRate(effectiveRate)
                        currentRate = effectiveRate
                    }

                    if (currentPitch != effectivePitch) {
                        textToSpeech.setPitch(effectivePitch)
                        currentPitch = effectivePitch
                    }

                    val success =
                        synchronized(queueLock) {
                            if (interrupt) {
                                AppLogger.d(
                                    TAG,
                                    "speak request flushing existing queue size=${queuedUtterances.size}"
                                )
                                queuedUtterances.clear()
                                pausedSegments = emptyList()
                                isPausedInternally = false
                                currentUtteranceId = null
                                currentUtteranceText = ""
                                currentUtteranceRangeStart = 0
                            }

                            if (isPausedInternally && !interrupt) {
                                pausedSegments = pausedSegments + text
                                logQueueState(
                                    event = "speak.bufferedWhilePaused",
                                    extra = "len=${text.length} preview=\"${speechPreview(text)}\""
                                )
                                return@synchronized true
                            }

                            val entry =
                                PendingUtterance(
                                    utteranceId = UUID.randomUUID().toString(),
                                    text = text
                                )
                            queuedUtterances.addLast(entry)

                            val submitSuccess =
                                submitUtterance(
                                    textToSpeech = textToSpeech,
                                    entry = entry,
                                    queueMode =
                                        if (interrupt) {
                                            TextToSpeech.QUEUE_FLUSH
                                        } else {
                                            TextToSpeech.QUEUE_ADD
                                        }
                                )
                            if (!submitSuccess) {
                                removeQueuedUtteranceLocked(entry.utteranceId)
                                updateSpeakingStateLocked("speak.submitFailed")
                                AppLogger.e(TAG, "TTS播放失败: submitResult=false")
                            } else {
                                _isSpeaking.value = true
                                logQueueState(
                                    event = "speak.submitted",
                                    extra = "utteranceId=${entry.utteranceId} queueMode=${if (interrupt) "QUEUE_FLUSH" else "QUEUE_ADD"} len=${entry.text.length}"
                                )
                            }
                            submitSuccess
                        }

                    continuation.resume(success)
                } ?: run {
                    AppLogger.e(TAG, "TTS引擎未初始化")
                    continuation.resume(false)
                }
            }
        }

    /** 停止当前正在播放的语音 */
    override suspend fun stop(): Boolean =
        withContext(Dispatchers.IO) {
            if (!isInitialized) return@withContext false

            AppLogger.d(TAG, "stop request speaking=$isSpeaking")
            tts?.let { textToSpeech ->
                synchronized(queueLock) {
                    queuedUtterances.clear()
                    pausedSegments = emptyList()
                    isPausedInternally = false
                    currentUtteranceId = null
                    currentUtteranceText = ""
                    currentUtteranceRangeStart = 0
                    _isSpeaking.value = false
                    logQueueState("stop.prepare")
                }
                val result = textToSpeech.stop() == TextToSpeech.SUCCESS
                synchronized(queueLock) {
                    _isSpeaking.value = false
                    logQueueState("stop.done", "result=$result")
                }
                return@withContext result
            }
            return@withContext false
        }

    /** 暂停当前正在播放的语音 */
    override suspend fun pause(): Boolean =
        withContext(Dispatchers.IO) {
            if (!isInitialized) return@withContext false

            AppLogger.d(TAG, "pause request speaking=$isSpeaking")
            tts?.let { textToSpeech ->
                val segmentsToResume =
                    synchronized(queueLock) {
                        val captured =
                            if (isPausedInternally && pausedSegments.isNotEmpty()) {
                                pausedSegments
                            } else {
                                buildPausedSegmentsLocked()
                            }
                        if (captured.isEmpty()) {
                            logQueueState("pause.skip", "reason=noPendingSegments")
                            return@synchronized emptyList()
                        }
                        pausedSegments = captured
                        isPausedInternally = true
                        queuedUtterances.clear()
                        currentUtteranceId = null
                        currentUtteranceText = ""
                        currentUtteranceRangeStart = 0
                        _isSpeaking.value = false
                        logQueueState(
                            "pause.capture",
                            "lengths=${captured.joinToString(prefix = "[", postfix = "]") { it.length.toString() }}"
                        )
                        captured
                    }
                if (segmentsToResume.isEmpty()) {
                    return@withContext false
                }

                val result = textToSpeech.stop() == TextToSpeech.SUCCESS
                synchronized(queueLock) {
                    if (!result) {
                        isPausedInternally = false
                        pausedSegments = emptyList()
                    }
                    _isSpeaking.value = false
                    logQueueState(
                        "pause.done",
                        "result=$result resumeSegments=${segmentsToResume.size}"
                    )
                }
                return@withContext result
            }
            return@withContext false
        }

    /** 继续播放暂停的语音 */
    override suspend fun resume(): Boolean =
        withContext(Dispatchers.IO) {
            if (!isInitialized) return@withContext false

            val segmentsToResume =
                synchronized(queueLock) {
                    if (pausedSegments.isEmpty()) {
                        logQueueState("resume.skip", "reason=noPausedSegments")
                        return@synchronized emptyList()
                    }
                    val captured = pausedSegments
                    pausedSegments = emptyList()
                    isPausedInternally = false
                    captured
                }
            if (segmentsToResume.isEmpty()) {
                AppLogger.d(
                    TAG,
                    "resume request initialized=$isInitialized speaking=$isSpeaking result=false reason=noPausedSegments"
                )
                return@withContext false
            }

            AppLogger.d(
                TAG,
                "resume request initialized=$isInitialized speaking=$isSpeaking segments=${segmentsToResume.size} lengths=${segmentsToResume.joinToString(prefix = "[", postfix = "]") { it.length.toString() }}"
            )

            val textToSpeech = tts ?: return@withContext false
            ensureVoiceAndLocaleReady(textToSpeech)

            val success =
                synchronized(queueLock) {
                    var failed = false
                    segmentsToResume.forEachIndexed { index, segment ->
                        val entry =
                            PendingUtterance(
                                utteranceId = UUID.randomUUID().toString(),
                                text = segment
                            )
                        queuedUtterances.addLast(entry)
                        val submitSuccess =
                            submitUtterance(
                                textToSpeech = textToSpeech,
                                entry = entry,
                                queueMode =
                                    if (index == 0) {
                                        TextToSpeech.QUEUE_FLUSH
                                    } else {
                                        TextToSpeech.QUEUE_ADD
                                    }
                            )
                        if (!submitSuccess) {
                            failed = true
                        }
                    }

                    if (failed) {
                        queuedUtterances.clear()
                        currentUtteranceId = null
                        currentUtteranceText = ""
                        currentUtteranceRangeStart = 0
                        _isSpeaking.value = false
                        logQueueState("resume.submitFailed")
                        false
                    } else {
                        _isSpeaking.value = queuedUtterances.isNotEmpty()
                        logQueueState("resume.submitted")
                        true
                    }
                }

            if (!success) {
                textToSpeech.stop()
            }
            return@withContext success
        }

    /** 释放TTS引擎资源 */
    override fun shutdown() {
        AppLogger.d(TAG, "shutdown request initialized=$isInitialized speaking=$isSpeaking")
        tts?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (e: Exception) {
                AppLogger.e(TAG, "关闭TTS引擎失败", e)
            } finally {
                synchronized(queueLock) {
                    queuedUtterances.clear()
                    pausedSegments = emptyList()
                    isPausedInternally = false
                    currentUtteranceId = null
                    currentUtteranceText = ""
                    currentUtteranceRangeStart = 0
                }
                tts = null
                _isInitialized.value = false
                _isSpeaking.value = false
            }
        }
    }

    /** 获取可用的语音列表 */
    override suspend fun getAvailableVoices(): List<VoiceService.Voice> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<VoiceService.Voice>()

            if (!isInitialized) {
                val initResult = initialize()
                if (!initResult) {
                    return@withContext emptyList<VoiceService.Voice>()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.let { textToSpeech ->
                    val voices = textToSpeech.voices
                    if (voices != null) {
                        for (voice in voices) {
                            val gender =
                                when {
                                    voice.name.contains("female", ignoreCase = true) -> "FEMALE"
                                    voice.name.contains("male", ignoreCase = true) -> "MALE"
                                    else -> "NEUTRAL"
                                }

                            result.add(
                                VoiceService.Voice(
                                    id = voice.name,
                                    name = voice.name,
                                    locale = voice.locale.toLanguageTag(),
                                    gender = gender
                                )
                            )
                        }
                    }
                }
            } else {
                AppLogger.w(TAG, "当前Android版本不支持获取TTS语音列表")
            }

            return@withContext result
        }

    /** 设置当前使用的语音 */
    override suspend fun setVoice(voiceId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isInitialized) {
                val initResult = initialize()
                if (!initResult) {
                    return@withContext false
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.let { textToSpeech ->
                    val voices = textToSpeech.voices
                    if (voices != null) {
                        val voice = voices.find { it.name == voiceId }
                        if (voice != null) {
                            val result = textToSpeech.setVoice(voice) == TextToSpeech.SUCCESS
                            if (result) {
                                currentVoiceId = voiceId
                                currentLocaleTag = voice.locale.toLanguageTag()
                            }
                            return@withContext result
                        } else {
                            AppLogger.e(TAG, "未找到ID为'$voiceId'的语音")
                            return@withContext false
                        }
                    }
                }
            } else {
                AppLogger.w(TAG, "当前Android版本不支持设置TTS语音")
            }

            return@withContext false
        }

    private fun ensureVoiceAndLocaleReady(textToSpeech: TextToSpeech) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !currentVoiceId.isNullOrBlank()) {
            val voices = textToSpeech.voices
            val selectedVoice = voices?.firstOrNull { it.name == currentVoiceId }
            if (selectedVoice != null) {
                val result = textToSpeech.setVoice(selectedVoice)
                if (result == TextToSpeech.SUCCESS) {
                    currentLocaleTag = selectedVoice.locale.toLanguageTag()
                    return
                }
            }
        }

        val requestedTag = currentLocaleTag.ifBlank { Locale.getDefault().toLanguageTag() }
        val targetLocale = resolveBestLocale(textToSpeech, requestedTag)
        if (targetLocale == null) {
            throw TtsException(
                context.getString(R.string.accessibility_tts_locale_not_supported, requestedTag)
            )
        }

        val result = textToSpeech.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw TtsException(
                context.getString(R.string.accessibility_tts_locale_not_supported, requestedTag)
            )
        }

        currentLocaleTag = targetLocale.toLanguageTag()
    }

    private fun resolveBestLocale(textToSpeech: TextToSpeech, requestedTag: String): Locale? {
        val normalizedTag = requestedTag.trim().replace('_', '-')
        if (normalizedTag.isBlank()) {
            return Locale.getDefault()
        }

        val requestedLocale = Locale.forLanguageTag(normalizedTag)
        val availableLocales = linkedSetOf<Locale>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.voices
                ?.mapNotNull { it.locale }
                ?.forEach { availableLocales.add(it) }
        }

        textToSpeech.availableLanguages
            ?.forEach { availableLocales.add(it) }

        availableLocales.firstOrNull {
            it.toLanguageTag().equals(normalizedTag, ignoreCase = true)
        }?.let {
            return it
        }

        val requestedLanguage = requestedLocale.language
        if (requestedLanguage.isNotBlank()) {
            availableLocales.firstOrNull {
                it.language.equals(requestedLanguage, ignoreCase = true)
            }?.let {
                return it
            }
        }

        return if (textToSpeech.isLanguageAvailable(requestedLocale) >= TextToSpeech.LANG_AVAILABLE) {
            requestedLocale
        } else {
            null
        }
    }
}
