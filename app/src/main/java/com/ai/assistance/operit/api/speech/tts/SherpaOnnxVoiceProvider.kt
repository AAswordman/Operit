package com.ai.assistance.operit.api.speech.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.SystemClock
import com.ai.assistance.operit.api.speech.tts.TtsService
import com.ai.assistance.operit.data.resources.models.SpeechModelPurpose
import com.ai.assistance.operit.util.logging.AppLogger
import com.k2fsa.sherpa.onnx.OfflineTts
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import com.k2fsa.sherpa.onnx.GeneratedAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.api.speech.runtime.SherpaModelSession

/** VITS、Matcha 与 Kitten 使用各自的原生前端，不混用旧 Piper 包协议。 */
class SherpaOnnxVoiceProvider(
    private val context: Context,
    private val modelKey: String,
    initialSpeaker: String,
    private val initialRate: Float,
    private val initialPitch: Float,
) : TtsService {
    private val nativeLock = Any()
    private val playbackLock = Any()
    private val commands = Mutex()
    private val generation = AtomicLong()
    private var session: SherpaModelSession? = null
    private var engine: OfflineTts? = null
    private var track: AudioTrack? = null
    private var speaker = if (initialSpeaker.isBlank()) 0 else requireNotNull(initialSpeaker.toIntOrNull()) { "说话人编号必须是整数" }
    @Volatile private var closed = false
    private val speaking = MutableStateFlow(false)
    override val speakingStateFlow = speaking.asStateFlow()
    override val isSpeaking get() = speaking.value
    override val isInitialized get() = synchronized(nativeLock) { engine != null }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        synchronized(nativeLock) {
            check(!closed) { "语音服务已释放" }
            if (engine != null) return@synchronized true
            val created = SherpaModelSession(context, modelKey, SpeechModelPurpose.TTS)
            try {
                val tts = OfflineTts(config = created.ttsConfig())
                try {
                    require(speaker in 0 until tts.numSpeakers()) { "说话人编号超出模型范围" }
                    engine = tts
                    session = created
                } catch (error: Throwable) { tts.release(); throw error }
                true
            } catch (error: Throwable) { created.close(); throw error }
        }
    }
    override suspend fun speak(text: String, interrupt: Boolean, rate: Float?, pitch: Float?, extraParams: Map<String, String>): Boolean {
        if (interrupt) stop()
        val token = generation.get()
        return commands.withLock {
            withContext(Dispatchers.IO) {
                if (token != generation.get() || closed) return@withContext false
                initialize()
                val speed = rate ?: initialRate
                val tone = pitch ?: initialPitch
                require(speed.isFinite() && speed > 0f && tone.isFinite() && tone > 0f)
                val job = currentCoroutineContext()
                speaking.value = true
                try {
                    // 当前句播放期间只预合成下一句；无缓冲通道限制 PCM 占用，不积累整篇音频。
                    val sentences = text.split(Regex("(?<=[。！？.!?\n])")).flatMap { it.chunked(240) }.filter(String::isNotBlank)
                    coroutineScope {
                        val audioChunks = Channel<GeneratedAudio>(Channel.RENDEZVOUS)
                        val producer = launch {
                            try {
                                for ((index, sentence) in sentences.withIndex()) {
                                    ensureActive()
                                    if (token != generation.get() || closed) break
                                    val started = SystemClock.elapsedRealtime()
                                    val audio = synchronized(nativeLock) {
                                        checkNotNull(engine).generateWithCallback(sentence, speaker, speed) {
                                            if (isActive && token == generation.get() && !closed) 1 else 0
                                        }
                                    }
                                    ensureActive()
                                    if (token != generation.get() || closed) break
                                    AppLogger.d("SherpaOnnxTTS", "分句合成 index=$index chars=${sentence.length} inferenceMs=${SystemClock.elapsedRealtime() - started} audioMs=${audio.samples.size.toLong() * 1000 / audio.sampleRate}")
                                    audioChunks.send(audio)
                                }
                            } finally {
                                audioChunks.close()
                            }
                        }
                        try {
                            for (audio in audioChunks) {
                                job.ensureActive()
                                if (token != generation.get() || closed) return@coroutineScope false
                                if (!play(audio.samples, audio.sampleRate, tone, token)) return@coroutineScope false
                            }
                            token == generation.get() && !closed
                        } finally {
                            producer.cancel()
                            audioChunks.cancel()
                        }
                    }
                } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) {
                    AppLogger.e("SherpaOnnxTTS", "本地语音合成失败", error)
                    throw error
                } finally {
                    synchronized(playbackLock) { track?.release(); track = null }
                    speaking.value = false
                }
            }
        }
    }
    private suspend fun play(samples: FloatArray, sampleRate: Int, pitch: Float, token: Long): Boolean {
        if (samples.isEmpty()) return true
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        require(minimum > 0) { "设备不支持模型的音频格式" }
        val player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(minimum, 8192)).setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            synchronized(playbackLock) {
                if (token != generation.get() || closed) return false
                track = player
                player.playbackParams = PlaybackParams().setSpeed(1f).setPitch(pitch)
                player.play()
            }
            var offset = 0
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                if (token != generation.get()) return false
                val count = player.write(samples, offset, minOf(2048, samples.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                check(count >= 0) { "音频播放失败：$count" }
                offset += count
                if (count == 0) delay(10)
            }
            while (player.playbackHeadPosition.toLong() < samples.size.toLong()) {
                currentCoroutineContext().ensureActive()
                if (token != generation.get()) return false
                delay(15)
            }
            return true
        } finally {
            synchronized(playbackLock) { if (track === player) track = null; player.release() }
        }
    }
    override suspend fun stop(): Boolean {
        generation.incrementAndGet()
        synchronized(playbackLock) { track?.pause(); track?.flush() }
        return true
    }
    override suspend fun pause(): Boolean = synchronized(playbackLock) { track?.pause(); track != null }
    override suspend fun resume(): Boolean = synchronized(playbackLock) { track?.play(); track != null }
    override suspend fun getAvailableVoices(): List<TtsService.Voice> {
        initialize()
        return synchronized(nativeLock) {
            val model = checkNotNull(session).model
            List(checkNotNull(engine).numSpeakers()) { id -> TtsService.Voice(id.toString(), "${model.name} · $id", model.languages.firstOrNull().orEmpty(), "NEUTRAL") }
        }
    }
    override suspend fun setVoice(voiceId: String): Boolean = commands.withLock {
        initialize()
        val selected = voiceId.toIntOrNull() ?: return@withLock false
        synchronized(nativeLock) {
            if (selected !in 0 until checkNotNull(engine).numSpeakers()) false else { speaker = selected; true }
        }
    }
    override fun shutdown() {
        closed = true
        generation.incrementAndGet()
        synchronized(playbackLock) { track?.pause(); track?.flush() }
        synchronized(nativeLock) { engine?.release(); engine = null; session?.close(); session = null }
    }
}
