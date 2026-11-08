package com.ai.assistance.operit.core.chat.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class VoiceTextSource { AUDIO_MODEL, STT, CACHED }
internal data class VoiceTextResult(val text: String, val source: VoiceTextSource, val audioContext: String = "")
internal typealias VoiceResponseAttempt = suspend (suspend (String) -> Unit, suspend (String) -> Unit) -> Unit

/** 用户指定的音频理解功能模型与 STT 顺序，取消不触发下一条路径。 */
internal suspend fun resolveVoiceText(
    hasAudioModel: suspend () -> Boolean,
    audioModel: suspend () -> String,
    stt: suspend () -> String,
    onAudioModelFailure: (Exception) -> Unit = {},
    cachedTranscript: String? = null,
): VoiceTextResult {
    currentCoroutineContext().ensureActive()
    if (cachedTranscript != null) return VoiceTextResult(cachedTranscript, VoiceTextSource.CACHED)
    var audioError: Exception? = null
    try {
        if (hasAudioModel()) {
            val text = audioModel().trim()
            check(text.isNotEmpty()) { "Audio understanding returned no content" }
            return VoiceTextResult(text, VoiceTextSource.AUDIO_MODEL)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        audioError = error
        onAudioModelFailure(error)
    }
    try {
        currentCoroutineContext().ensureActive()
        val text = stt().trim()
        check(text.isNotEmpty()) { "Speech recognition returned no content" }
        return VoiceTextResult(text, VoiceTextSource.STT)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        audioError?.let(error::addSuppressed)
        throw error
    }
}

/** 首段内容产生前允许切换输入路径，已有回答或工具内容后不重复执行。 */
internal suspend fun collectVoiceResponse(
    native: VoiceResponseAttempt,
    text: VoiceResponseAttempt,
    emit: suspend (String) -> Unit,
    onNativeFailure: (Exception) -> Unit = {},
    onNonFatalError: suspend (String) -> Unit = {},
) {
    currentCoroutineContext().ensureActive()
    var producedContent = false
    var nativeError: Exception
    val pendingNotices = mutableListOf<String>()
    try {
        native(
            { value ->
                if (value.isNotEmpty() && !producedContent) {
                    producedContent = true
                    // 只有采用该音频响应时才发布其重试通知，避免转写成功后仍留下失败卡片。
                    for (notice in pendingNotices) onNonFatalError(notice)
                    pendingNotices.clear()
                }
                emit(value)
            },
            { notice ->
                if (producedContent) onNonFatalError(notice) else pendingNotices.add(notice)
            },
        )
        check(producedContent) { "Direct audio request returned no content" }
        return
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        if (producedContent) throw error
        nativeError = error
        onNativeFailure(error)
    }
    pendingNotices.clear()
    try {
        currentCoroutineContext().ensureActive()
        var producedText = false
        text(
            { value ->
                if (value.isNotEmpty()) producedText = true
                emit(value)
            },
            onNonFatalError,
        )
        check(producedText) { "Voice text request returned no content" }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        error.addSuppressed(nativeError)
        throw error
    }
}
