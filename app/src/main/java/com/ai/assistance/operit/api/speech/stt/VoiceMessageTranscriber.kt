package com.ai.assistance.operit.api.speech.stt

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.stt.SttServiceFactory
import com.ai.assistance.operit.core.chat.voice.VoiceMessageData
import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import com.ai.assistance.operit.util.android.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.ai.assistance.operit.api.speech.audio.RecordedVoiceMessage
import com.ai.assistance.operit.api.speech.audio.VoiceMessageRecorder

internal suspend fun transcribeVoiceMessage(context: Context, recording: RecordedVoiceMessage): VoiceMessageData =
    withContext(Dispatchers.IO) {
        val bytes = recording.file.length() - VoiceMessageRecorder.HEADER_SIZE
        require(bytes in 2..(VoiceMessageRecorder.SAMPLE_RATE * 2L * 60))
        val pcm = ByteArray(bytes.toInt())
        RandomAccessFile(recording.file, "r").use { input -> input.seek(44); input.readFully(pcm) }
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray(pcm.size / 2) { input.short / 32768f }
        val service = SttServiceFactory.createSpeechService(context)
        val busyStates = setOf(
            com.ai.assistance.operit.api.speech.stt.SttService.RecognitionState.PREPARING,
            com.ai.assistance.operit.api.speech.stt.SttService.RecognitionState.RECOGNIZING,
            com.ai.assistance.operit.api.speech.stt.SttService.RecognitionState.PROCESSING,
        )
        check(service.currentState !in busyStates) { context.getString(R.string.voice_message_stt_busy) }
        try {
            check(service.initialize()) {
                context.getString(R.string.voice_message_stt_failed) + " " + service.recognitionErrorFlow.value.message
            }
            try {
                val locale = LocaleUtils.getLocalizedContext(context).resources.configuration.locales[0]
                withTimeout(60000L) { service.recognize(samples, locale.toLanguageTag()) }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw IllegalStateException(context.getString(R.string.voice_message_stt_failed), error)
            }
            val error = service.recognitionErrorFlow.value
            check(error.code == 0) { context.getString(R.string.voice_message_stt_failed) + " " + error.message }
            val result = service.recognitionResultFlow.value
            check(result.isFinal && result.text.isNotBlank()) { context.getString(R.string.voice_message_no_text) }
            VoiceMessageData(
                attachment = AttachmentInfo(recording.file.absolutePath, recording.file.name, "audio/wav", recording.file.length()),
                durationMs = recording.durationMs,
                waveform = recording.waveform,
                transcript = result.text.trim(),
            )
        } finally {
            service.shutdown()
        }
    }
