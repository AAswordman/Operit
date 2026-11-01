package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.audio.RecordedVoiceMessage
import com.ai.assistance.operit.api.speech.audio.VoiceMessageRecorder
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.core.chat.voice.VoiceInputMode
import com.ai.assistance.operit.core.chat.voice.VoiceMessageData
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class VoiceMessagePhase { PREPARING, RECORDING, READY, ERROR }
internal data class VoiceMessageState(
    val phase: VoiceMessagePhase = VoiceMessagePhase.PREPARING,
    val levels: List<Float> = emptyList(),
    val elapsedMs: Long = 0,
    val recording: RecordedVoiceMessage? = null,
    val message: VoiceMessageData? = null,
    val error: String? = null,
)

/** 录音会话由底部面板持有，关闭后不再发布结果或保留未发送文件。 */
internal class VoiceMessageSession(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(VoiceMessageState())
    val state = mutableState.asStateFlow()
    private var recorder: VoiceMessageRecorder? = null
    private var job: Job? = null
    @Volatile private var retained = false
    private var sending = false
    private var closed = false

    fun start() {
        if (closed || job?.isActive == true) return
        recorder?.cancel()
        mutableState.value.recording?.file?.delete()
        mutableState.value = VoiceMessageState()
        val activeRecorder = VoiceMessageRecorder(context)
        recorder = activeRecorder
        job = scope.launch {
            try {
                val recording = activeRecorder.record { level, elapsed ->
                    mutableState.value = mutableState.value.copy(
                        phase = VoiceMessagePhase.RECORDING,
                        levels = (mutableState.value.levels + level).takeLast(48),
                        elapsedMs = elapsed,
                    )
                }
                mutableState.value = mutableState.value.copy(recording = recording, elapsedMs = recording.durationMs)
                mutableState.value = mutableState.value.copy(
                    phase = VoiceMessagePhase.READY,
                    message = VoiceMessageData(
                        AttachmentInfo(recording.file.absolutePath, recording.file.name, "audio/wav", recording.file.length()),
                        recording.durationMs, recording.waveform, "", VoiceInputMode.AUDIO,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    fun finish() { recorder?.finish() }

    private fun fail(error: Exception) {
        AppLogger.e("VoiceMessageSession", "录制语音消息失败", error)
        mutableState.value = mutableState.value.copy(phase = VoiceMessagePhase.ERROR,
            error = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.voice_message_record_failed))
    }

    fun permissionDenied() {
        mutableState.value = mutableState.value.copy(phase = VoiceMessagePhase.ERROR,
            error = context.getString(R.string.voice_message_permission))
    }
    fun sendFailed(error: Exception) {
        AppLogger.e("VoiceMessageSession", "提交语音消息失败", error)
        mutableState.value = mutableState.value.copy(error = context.getString(R.string.message_send_failed, error.message.orEmpty()))
    }
    fun beginSend() { sending = true }
    fun retainSentRecording() { retained = true }
    fun finishSend() {
        sending = false
        if (closed && !retained) mutableState.value.recording?.file?.delete()
    }
    fun close() {
        if (closed) return
        closed = true
        if (!retained && !sending) {
            recorder?.cancel()
            mutableState.value.recording?.file?.delete()
        }
        scope.cancel()
    }
}
