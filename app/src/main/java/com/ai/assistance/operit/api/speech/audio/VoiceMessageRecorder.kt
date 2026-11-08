package com.ai.assistance.operit.api.speech.audio

import com.ai.assistance.operit.util.files.OperitPaths

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.audio.OnnxSileroVad
import com.ai.assistance.operit.core.chat.voice.VoiceSilenceGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt

internal data class RecordedVoiceMessage(val file: File, val durationMs: Long, val waveform: List<Float>)

internal class VoiceMessageRecorder(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_MS = 60000L
        const val HEADER_SIZE = 44
    }
    @Volatile private var stopRequested = false
    @Volatile private var cancelled = false
    @Volatile private var activeFile: File? = null
    fun cancel() {
        cancelled = true
        stopRequested = true
        activeFile?.delete()
    }
    fun finish() { stopRequested = true }

    @SuppressLint("MissingPermission")
    suspend fun record(onFrame: (Float, Long) -> Unit): RecordedVoiceMessage = withContext(Dispatchers.IO) {
        check(!cancelled)
        val directory = OperitPaths.cleanOnExitDir()
        check(directory.isDirectory || directory.mkdirs()) { context.getString(R.string.voice_message_record_failed) }
        val file = File(directory, "voice-${UUID.randomUUID()}.wav")
        activeFile = file
        var completed = false
        var recorder: AudioRecord? = null
        try {
            check(!cancelled)
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { context.getString(R.string.voice_message_record_failed) }
            val microphone = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096))
            recorder = microphone
            check(microphone.state == AudioRecord.STATE_INITIALIZED) { context.getString(R.string.voice_message_record_failed) }
            val levels = ArrayList<Float>()
            var samples = 0L
            OnnxSileroVad(context, speechDurationMs = 0, silenceDurationMs = 0).use { vad ->
                RandomAccessFile(file, "rw").use { output ->
                    output.write(ByteArray(HEADER_SIZE))
                    currentCoroutineContext().ensureActive()
                    check(!cancelled)
                    microphone.startRecording()
                    val started = SystemClock.elapsedRealtime()
                    val gate = VoiceSilenceGate(started)
                    val frame = ShortArray(512)
                    var filled = 0
                    while (!stopRequested) {
                        currentCoroutineContext().ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        if (now - started >= MAX_DURATION_MS || samples >= SAMPLE_RATE * 60L || gate.shouldFinish(now)) break
                        val read = microphone.read(frame, filled, frame.size - filled, AudioRecord.READ_NON_BLOCKING)
                        check(read >= 0) { context.getString(R.string.voice_message_record_failed) }
                        if (read == 0) { delay(10); continue }
                        filled += read
                        if (filled < frame.size) continue
                        val bytes = ByteArray(frame.size * 2)
                        var sum = 0.0
                        frame.forEachIndexed { index, sample ->
                            bytes[index * 2] = (sample.toInt() and 255).toByte()
                            bytes[index * 2 + 1] = (sample.toInt() shr 8).toByte()
                            val normalized = sample / 32768.0
                            sum += normalized * normalized
                        }
                        output.write(bytes)
                        samples += frame.size
                        val level = (sqrt(sum / frame.size) * 8).toFloat().coerceIn(0f, 1f)
                        levels.add(level)
                        gate.observe(vad.isSpeech(frame), SystemClock.elapsedRealtime())
                        withContext(Dispatchers.Main.immediate) { onFrame(level, samples * 1000 / SAMPLE_RATE) }
                        filled = 0
                    }
                    currentCoroutineContext().ensureActive()
                    check(!cancelled)
                    check(gate.heardSpeech && samples > 0) { context.getString(R.string.voice_message_no_speech) }
                    writeHeader(output, samples * 2)
                }
            }
            val bucketCount = minOf(48, levels.size)
            val waveform = List(bucketCount) { index ->
                val start = index * levels.size / bucketCount
                val end = (index + 1) * levels.size / bucketCount
                levels.subList(start, end).max()
            }
            completed = true
            RecordedVoiceMessage(file, samples * 1000 / SAMPLE_RATE, waveform)
        } finally {
            try {
                val microphone = recorder
                if (microphone?.recordingState == AudioRecord.RECORDSTATE_RECORDING) microphone.stop()
            } finally {
                recorder?.release()
                if (!completed) file.delete()
            }
        }
    }

    private fun writeHeader(file: RandomAccessFile, pcmSize: Long) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt((pcmSize + 36).toInt()).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
        header.putShort(2).putShort(16).put("data".toByteArray()).putInt(pcmSize.toInt())
        file.seek(0)
        file.write(header.array())
    }
}
