package com.ai.assistance.operit.core.chat.voice

import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import kotlin.math.roundToInt

enum class VoiceInputMode { TEXT, AUDIO }

/** 旧语音保留转写文字，新语音保留可直接理解的录音。 */
data class VoiceMessageData(
    val attachment: AttachmentInfo,
    val durationMs: Long,
    val waveform: List<Float>,
    val transcript: String,
    val inputMode: VoiceInputMode = VoiceInputMode.TEXT,
    val transcriptResolved: Boolean = false,
    val audioContext: String = "",
    val requestContext: String = "",
)

object VoiceMessageMarkup {
    private val block = Regex("<voice_bar\\b[^>]*>[\\s\\S]*?</voice_bar>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-z_]+)=\"([^\"]*)\"")
    private val attachment = Regex("<attachment\\b[^>]*?/>")
    private val transcript = Regex("<transcript>([\\s\\S]*?)</transcript>")
    private val audioContext = Regex(transcript.pattern.replace("transcript", "audio_context"))
    private val requestContext = Regex(transcript.pattern.replace("transcript", "request_context"))

    fun encode(message: VoiceMessageData): String {
        require(message.durationMs > 0 && (message.inputMode == VoiceInputMode.AUDIO || message.transcript.isNotBlank()))
        require(message.attachment.mimeType == "audio/wav")
        require(message.attachment.filePath.isNotBlank() && message.attachment.fileName.isNotBlank())
        require(message.attachment.fileSize >= 44)
        require(message.waveform.size <= 64 && message.waveform.all { it.isFinite() && it in 0f..1f })
        val waves = message.waveform.joinToString(",") { ((it.coerceIn(0f, 1f) * 100).roundToInt() / 100f).toString() }
        val version = if (message.inputMode == VoiceInputMode.AUDIO) "2" else "1"
        val input = if (message.inputMode == VoiceInputMode.AUDIO) " input=\"audio\"" else ""
        require(!message.transcriptResolved || (message.inputMode == VoiceInputMode.AUDIO && message.transcript.isNotBlank()))
        val resolved = if (message.transcriptResolved) " resolved=\"true\"" else ""
        return "<voice_bar version=\"$version\"$input$resolved duration_ms=\"${message.durationMs}\" waveform=\"$waves\">" +
            attachmentReference(message) +
            "<transcript>${escape(message.transcript)}</transcript>" +
            (if (message.audioContext.isBlank()) "" else "<audio_context>${escape(message.audioContext)}</audio_context>") +
            (if (message.requestContext.isBlank()) "" else "<request_context>${escape(message.requestContext)}</request_context>") +
            "</voice_bar>"
    }

    private fun attachmentReference(message: VoiceMessageData): String =
        "<attachment id=\"${escape(message.attachment.filePath)}\" filename=\"${escape(message.attachment.fileName)}\" type=\"audio/wav\" size=\"${message.attachment.fileSize}\" />"

    /** 请求解开语音条后仍携带原录音引用；该描述不回写转写正文或增加界面附件标签。 */
    fun withRequestAttachment(message: VoiceMessageData, input: String): String =
        listOf(input, attachmentReference(message)).filter { it.isNotBlank() }.joinToString("\n")

    fun parse(value: String): VoiceMessageData? {
        val normalized = value.trim()
        val matches = block.findAll(normalized).toList()
        val match = matches.singleOrNull()?.takeIf { it.value == normalized } ?: return null
        val header = match.value.substringBefore('>')
        val attrs = attribute.findAll(header).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        val inputMode = when (attrs["version"]) {
            "1" -> VoiceInputMode.TEXT
            "2" -> if (attrs["input"] == "audio") VoiceInputMode.AUDIO else return null
            else -> return null
        }
        val duration = attrs["duration_ms"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val waveText = attrs["waveform"] ?: return null
        val waves = if (waveText.isEmpty()) emptyList() else waveText.split(',').map { part ->
            val level = part.toFloatOrNull() ?: return null
            if (!level.isFinite() || level !in 0f..1f) return null
            level
        }
        if (waves.size > 64) return null
        val audio = attachment.findAll(match.value).singleOrNull()?.value ?: return null
        val audioAttrs = attribute.findAll(audio).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        if (audioAttrs["type"] != "audio/wav") return null
        val path = audioAttrs["id"]?.takeIf { it.isNotBlank() } ?: return null
        val name = audioAttrs["filename"]?.takeIf { it.isNotBlank() } ?: return null
        val size = audioAttrs["size"]?.toLongOrNull()?.takeIf { it >= 44 } ?: return null
        val textMatch = transcript.findAll(match.value).singleOrNull() ?: return null
        val text = unescape(textMatch.groupValues[1])
        val contextMatches = audioContext.findAll(match.value).toList()
        val requestMatches = requestContext.findAll(match.value).toList()
        if (contextMatches.size > 1 || requestMatches.size > 1) return null
        val audioNotes = contextMatches.singleOrNull()?.groupValues?.get(1)?.let(::unescape).orEmpty()
        val requestNotes = requestMatches.singleOrNull()?.groupValues?.get(1)?.let(::unescape).orEmpty()
        if (inputMode == VoiceInputMode.TEXT && text.isBlank()) return null
        val resolved = when (attrs["resolved"]) {
            null -> false
            "true" -> true
            else -> return null
        }
        if (resolved && (inputMode != VoiceInputMode.AUDIO || text.isBlank())) return null
        val body = match.value.substringAfter('>').substringBeforeLast("</voice_bar>")
        if (body.replace(audio, "").replace(textMatch.value, "").replace(audioContext, "").replace(requestContext, "").isNotBlank()) return null
        return VoiceMessageData(AttachmentInfo(path, name, "audio/wav", size), duration, waves, text, inputMode, resolved, audioNotes, requestNotes)
    }

    /** 聊天消息可以在语音正文之外附带系统生成的工作区等元数据。 */
    fun parseMessage(value: String): VoiceMessageData? {
        val body = value
            .replace(Regex("<workspace_attachment>[\\s\\S]*?</workspace_attachment>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<reply_to\\b[^>]*>[\\s\\S]*?</reply_to>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<proxy_sender\\b[^>]*/>", RegexOption.IGNORE_CASE), "")
        return parse(body)
    }

    fun messages(value: String): List<VoiceMessageData> = block.findAll(value).mapNotNull { parse(it.value) }.toList()

    fun rewrite(value: String, transform: (VoiceMessageData) -> String): String = block.replace(value) { match ->
        val message = parse(match.value)
        if (message != null) transform(message) else match.value
    }

    /** 只在真正的请求边界解开新录音，避免提前丢掉音频。 */
    fun toRequestContent(value: String): String = block.replace(value) { match ->
        val message = parse(match.value)
        when {
            message == null || message.inputMode == VoiceInputMode.AUDIO -> match.value
            else -> message.transcript
        }
    }

    fun toModelText(value: String): String = rewrite(value) { message ->
        message.transcript.ifBlank { "[Voice message]" }
    }

    /** 历史请求只携带文字与已记录的观察，原录音仍只发送于当前用户轮次。 */
    fun toHistoryModelText(value: String): String = rewrite(value) { message ->
        VoiceAnalysis(message.transcript.ifBlank { "[Voice message]" }, message.audioContext)
            .toModelText(message.requestContext)
    }

    /** 理解文字与录音并存；独立标记已解析结果，避免把钩子补充文字误当成转写缓存。 */
    fun withResolvedTranscript(value: String, source: VoiceMessageData, text: String): String = block.replace(value) { match ->
        val message = parse(match.value)
        if (message == source && message.inputMode == VoiceInputMode.AUDIO) {
            encode(message.copy(transcript = text, transcriptResolved = true))
        } else {
            match.value
        }
    }

    /** 新识别结果分别持久化，历史消息缺少辅助字段时仍按原格式读取。 */
    fun withResolvedAnalysis(value: String, source: VoiceMessageData, analysis: VoiceAnalysis): String = block.replace(value) { match ->
        val message = parse(match.value)
        if (message == source && message.inputMode == VoiceInputMode.AUDIO) {
            encode(message.copy(
                transcript = analysis.transcript,
                transcriptResolved = true,
                audioContext = analysis.audioContext,
                requestContext = message.additionalRequestContext(),
            ))
        } else {
            match.value
        }
    }

    private fun escape(value: String): String = value.replace("&", "&" + "amp;").replace("\"", "&" + "quot;")
        .replace("<", "&" + "lt;").replace(">", "&" + "gt;").replace("'", "&" + "apos;")
    private fun unescape(value: String): String = value.replace("&" + "quot;", "\"").replace("&" + "apos;", "'")
        .replace("&" + "lt;", "<").replace("&" + "gt;", ">").replace("&" + "amp;", "&")

}

/** 每次检测到人声重新计时，开始录音后的纯静音同样会结束。 */
class VoiceSilenceGate(startedAtMillis: Long, private val silenceMillis: Long = 3000L) {
    private var lastSpeechAt = startedAtMillis
    var heardSpeech: Boolean = false
        private set
    fun observe(isSpeech: Boolean, nowMillis: Long) {
        if (isSpeech) {
            heardSpeech = true
            lastSpeechAt = nowMillis
        }
    }
    fun shouldFinish(nowMillis: Long): Boolean = nowMillis - lastSpeechAt >= silenceMillis
}

fun canStartVoiceMessage(text: String, attachmentCount: Int, processing: Boolean, hasReply: Boolean): Boolean =
    text.isBlank() && attachmentCount == 0 && !processing && !hasReply
