package com.ai.assistance.operit.core.chat.voice

import java.io.IOException
import org.json.JSONObject

/** 原话用于展示，辅助观察只在文本请求中提供给对话模型。 */
data class VoiceAnalysis(val transcript: String, val audioContext: String = "") {
    fun toModelText(requestContext: String = ""): String = buildList {
        add(transcript)
        if (audioContext.isNotBlank()) {
            add("[Audio observations from an auxiliary model; these are not words spoken by the user]" + '\n' + audioContext)
        }
        if (requestContext.isNotBlank()) {
            add("[Additional request context; not part of the voice transcript]" + '\n' + requestContext)
        }
    }.filter { it.isNotBlank() }.joinToString("\n")
}

/** 描述非空不能代表成功转写；两个字段必须分别读取并验证。 */
internal object VoiceAnalysisCodec {
    fun decode(value: String): VoiceAnalysis {
        val json = JSONObject(value)
        val transcript = json.get("transcript")
        val context = json.get("audio_context")
        if (transcript !is String || context !is String) {
            throw IOException("Audio analysis fields must be strings")
        }
        val words = transcript.trim()
        if (words.isBlank()) throw IOException("Audio analysis returned no spoken transcript")
        return VoiceAnalysis(words, context.trim())
    }
}

/** 保留旧钩子附加的请求文字，但不能把它写进新的原话转写。 */
internal fun VoiceMessageData.additionalRequestContext(): String =
    listOf(requestContext, if (transcriptResolved || inputMode != VoiceInputMode.AUDIO) "" else transcript)
        .filter { it.isNotBlank() }.distinct().joinToString("\n")

/** 已完成转写的语音复用文字，历史消息和手动重试不再发送已拒绝的音频。 */
internal fun VoiceMessageData.canSendNativeAudio(enabled: Boolean): Boolean = enabled && !transcriptResolved
