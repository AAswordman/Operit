package com.ai.assistance.operit.core.chat.voice

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.runtime.MultiServiceManager
import com.ai.assistance.operit.core.chat.media.MediaLinkBuilder
import com.ai.assistance.operit.api.speech.audio.RecordedVoiceMessage
import com.ai.assistance.operit.api.speech.stt.transcribeVoiceMessage
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.voice.VoiceAnalysis
import com.ai.assistance.operit.core.chat.voice.VoiceAnalysisCodec
import com.ai.assistance.operit.core.chat.voice.VoiceInputMode
import com.ai.assistance.operit.core.chat.voice.VoiceMessageData
import com.ai.assistance.operit.core.chat.voice.VoiceMessageMarkup
import com.ai.assistance.operit.core.chat.voice.VoiceTextResult
import com.ai.assistance.operit.core.chat.voice.VoiceTextSource
import com.ai.assistance.operit.core.chat.voice.additionalRequestContext
import com.ai.assistance.operit.core.chat.voice.collectVoiceResponse
import com.ai.assistance.operit.core.chat.voice.resolveVoiceText
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.chat.context.ChatUtils
import com.ai.assistance.operit.util.media.MediaPoolManager
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withEventChannel
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 一次对话请求持有音频副本，直到回复与后续工具轮次全部结束才清理。 */
internal class VoiceAudioRequest(
    private val context: Context,
    private val services: MultiServiceManager,
    private val onResolved: suspend (VoiceMessageData, VoiceAnalysis) -> Unit,
) {
    private val mediaByPath = linkedMapOf<String, String>()
    private val nativeEntries = linkedMapOf<String, VoiceMessageData>()
    private val resolved = mutableMapOf<VoiceMessageData, VoiceTextResult>()

    fun formatForEstimate(value: String, nativeAudio: Boolean): String = VoiceMessageMarkup.rewrite(value) { voice ->
        val input = if (voice.inputMode == VoiceInputMode.AUDIO && voice.canSendNativeAudio(nativeAudio) && File(voice.attachment.filePath).isFile) {
            listOf(audioLink(voice), voice.transcript, voice.requestContext).filter { it.isNotBlank() }.joinToString("\n")
        } else {
            VoiceAnalysis(voice.transcript.ifBlank { "[Voice message]" }, voice.audioContext).toModelText(voice.requestContext)
        }
        if (voice.inputMode == VoiceInputMode.AUDIO) VoiceMessageMarkup.withRequestAttachment(voice, input) else input
    }

    suspend fun format(value: String, nativeAudio: Boolean): String {
        val replacements = mutableMapOf<VoiceMessageData, String>()
        for (voice in VoiceMessageMarkup.messages(value)) {
            if (voice.inputMode != VoiceInputMode.AUDIO || voice in replacements) continue
            val text = if (voice.canSendNativeAudio(nativeAudio) && voice !in resolved) {
                try {
                    val link = audioLink(voice)
                    val input = VoiceMessageMarkup.withRequestAttachment(
                        voice,
                        listOf(link, voice.transcript, voice.requestContext).filter { it.isNotBlank() }.joinToString("\n"),
                    )
                    nativeEntries[input] = voice
                    input
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("VoiceAudioRequest", "准备直接音频失败，按用户指定顺序处理录音", error)
                    resolvedInput(voice)
                }
            } else {
                resolvedInput(voice)
            }
            replacements[voice] = text
        }
        return VoiceMessageMarkup.rewrite(value) { voice ->
            if (voice.inputMode == VoiceInputMode.AUDIO) replacements.getValue(voice) else voice.transcript
        }
    }

    suspend fun send(
        history: List<PromptTurn>,
        request: suspend (List<PromptTurn>, suspend (String) -> Unit) -> Stream<String>,
        onNonFatalError: suspend (String) -> Unit,
        onHistoryResolved: suspend (List<PromptTurn>) -> Unit,
    ): Stream<String> {
        currentCoroutineContext().ensureActive()
        val active = nativeEntries.filterKeys { input -> history.any { input in it.content } }
        if (active.isEmpty()) return request(history, onNonFatalError)
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        return stream<String> {
            collectVoiceResponse(
                native = { forward, notices -> collectAttempt(request(history, notices), events, forward) },
                text = { forward, notices ->
                    val replacements = mutableMapOf<String, String>()
                    for ((input, voice) in active) replacements[input] = resolvedInput(voice)
                    val textHistory = history.map { turn ->
                        var content = turn.content
                        for ((link, text) in replacements) content = content.replace(link, text)
                        turn.copy(content = content)
                    }
                    onHistoryResolved(textHistory)
                    collectAttempt(request(textHistory, notices), events, forward)
                },
                emit = { emit(it) },
                onNonFatalError = onNonFatalError,
                onNativeFailure = { error ->
                    AppLogger.e("VoiceAudioRequest", "对话模型直接音频请求失败，尝试音频理解功能模型和 STT", error)
                },
            )
        }.withEventChannel(events)
    }

    private suspend fun collectAttempt(
        source: Stream<String>,
        events: MutableSharedStream<TextStreamEvent>,
        forward: suspend (String) -> Unit,
    ) = coroutineScope {
        val attemptId = UUID.randomUUID().toString()
        val eventJob = (source as? TextStreamEventCarrier)?.let { carrier ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                carrier.eventChannel.collect { event -> events.emit(event.copy(id = "$attemptId:${event.id}")) }
            }
        }
        try {
            source.collect { forward(it) }
        } finally {
            eventJob?.cancelAndJoin()
        }
    }

    private suspend fun resolvedInput(voice: VoiceMessageData): String {
        val result = resolve(voice)
        val input = VoiceAnalysis(result.text, result.audioContext).toModelText(voice.additionalRequestContext())
        return VoiceMessageMarkup.withRequestAttachment(voice, input)
    }

    private suspend fun resolve(voice: VoiceMessageData): VoiceTextResult {
        currentCoroutineContext().ensureActive()
        resolved[voice]?.let { return it }
        var audioContext = ""
        val textResult = try {
            resolveVoiceText(
                // 钩子文字不代表已经理解录音，只有持久化标记明确时才复用。
                cachedTranscript = voice.transcript.takeIf { voice.transcriptResolved },
                hasAudioModel = { services.hasAudioRecognitionConfigured() },
                audioModel = {
                    val analysis = understandAudio(voice)
                    audioContext = analysis.audioContext
                    analysis.transcript
                },
                stt = {
                    transcribeVoiceMessage(context, RecordedVoiceMessage(
                        File(voice.attachment.filePath), voice.durationMs, voice.waveform,
                    )).transcript
                },
                onAudioModelFailure = { error ->
                    AppLogger.e("VoiceAudioRequest", "音频理解功能模型失败，使用当前 STT 配置", error)
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IOException(context.getString(R.string.voice_message_understanding_failed), error)
        }
        val result = textResult.copy(audioContext = when (textResult.source) {
            VoiceTextSource.AUDIO_MODEL -> audioContext
            VoiceTextSource.CACHED -> voice.audioContext
            VoiceTextSource.STT -> ""
        })
        if (result.source != VoiceTextSource.CACHED) {
            onResolved(voice, VoiceAnalysis(result.text, result.audioContext))
        }
        resolved[voice] = result
        AppLogger.d("VoiceAudioRequest", "语音输入处理完成，来源=${result.source}")
        return result
    }

    private suspend fun understandAudio(voice: VoiceMessageData): VoiceAnalysis {
        val result = withTimeoutOrNull(60000L) {
            val lease = services.acquireServiceForFunction(FunctionType.AUDIO_RECOGNITION)
            try {
                check(lease.modelConfig.enableDirectAudioProcessing)
                val instructions = context.getString(R.string.voice_message_understanding_prompt)
                val content = StringBuilder()
                lease.service.sendMessage(
                    context = context,
                    chatHistory = listOf(
                        PromptTurn(PromptTurnKind.SYSTEM, instructions),
                        PromptTurn(PromptTurnKind.USER, audioLink(voice)),
                    ),
                    modelParameters = lease.modelParameters,
                    stream = false,
                    availableTools = null,
                ).collect { content.append(it) }
                VoiceAnalysisCodec.decode(ChatUtils.removeThinkingContent(content.toString()).trim())
            } finally {
                withContext(NonCancellable) { lease.close() }
            }
        }
        return result ?: throw IOException(context.getString(R.string.voice_message_understanding_timeout))
    }

    private fun audioLink(voice: VoiceMessageData): String {
        val path = voice.attachment.filePath
        val id = mediaByPath.getOrPut(path) {
            val created = MediaPoolManager.addMedia(path, voice.attachment.mimeType)
            check(created != "error") { context.getString(R.string.voice_message_play_failed) }
            created
        }
        return MediaLinkBuilder.audio(context, id)
    }

    fun close() {
        mediaByPath.values.forEach(MediaPoolManager::removeMedia)
        mediaByPath.clear()
        nativeEntries.clear()
        resolved.clear()
    }
}
