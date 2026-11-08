package com.ai.assistance.operit.api.speech.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.ai.assistance.operit.api.speech.audio.OnnxSileroVad

object PersonalWakeEnrollment {

    @SuppressLint("MissingPermission")
    suspend fun recordOneTemplate(
        context: Context,
        maxRecordMs: Long = 6000L,
        minSpeechMs: Long = 250L,
        endSilenceMs: Long = 350L,
    ): FloatArray? = withContext(Dispatchers.Default) {
        val sampleRate = 16000
        val frameSize = 512

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            (minBufferSize.coerceAtLeast(frameSize) * 2)
        )

        val vad = OnnxSileroVad(
            context = context.applicationContext,
            sampleRate = sampleRate,
            frameSize = frameSize,
            mode = OnnxSileroVad.Mode.NORMAL,
            speechDurationMs = 60,
            silenceDurationMs = 300,
        )

        try {
            val buffer = ShortArray(frameSize)
            val speech = ArrayList<Short>()

            var seenSpeech = false
            var silenceMsAfterSpeech = 0L
            var speechMs = 0L

            val startedAt = System.currentTimeMillis()
            audioRecord.startRecording()

            while (true) {
                // 切换页签时中止采集，并由 finally 释放麦克风。
                coroutineContext.ensureActive()
                val now = System.currentTimeMillis()
                if (now - startedAt > maxRecordMs) break

                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val isSpeech = if (read == frameSize) vad.isSpeech(buffer) else false

                if (isSpeech) {
                    seenSpeech = true
                    silenceMsAfterSpeech = 0L
                    speechMs += (read * 1000L) / sampleRate
                    for (i in 0 until read) {
                        speech.add(buffer[i])
                    }
                } else {
                    if (seenSpeech) {
                        silenceMsAfterSpeech += (read * 1000L) / sampleRate
                        if (silenceMsAfterSpeech >= endSilenceMs) break
                    }
                }
            }

            if (!seenSpeech) return@withContext null
            if (speechMs < minSpeechMs) return@withContext null

            val pcm = ShortArray(speech.size)
            for (i in pcm.indices) {
                pcm[i] = speech[i]
            }
            PersonalWakeFeatureExtractor.extractFeatures(pcm, pcm.size)
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord.release()
            } catch (_: Exception) {
            }
            try {
                vad.close()
            } catch (_: Exception) {
            }
        }
    }
}
