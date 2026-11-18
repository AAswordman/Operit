package com.ai.assistance.operit.api.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class QueuedTtsPlayback(
    private val tag: String,
    private val prepareAudioFile: suspend (Request) -> File?,
) {
    data class Request(
        val text: String,
        val rate: Float?,
        val pitch: Float?,
        val extraParams: Map<String, String>,
        val generation: Long,
        val completion: CompletableDeferred<Boolean>,
    )

    private data class PreparedRequest(
        val request: Request,
        val audioFile: File,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakQueue = Channel<Request>(Channel.UNLIMITED)
    private val playbackQueue = Channel<PreparedRequest>(capacity = 1)
    private val stopGeneration = AtomicLong(0)
    private val isPaused = AtomicBoolean(false)
    private val _isSpeaking = MutableStateFlow(false)
    private var mediaPlayer: MediaPlayer? = null

    val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()
    val isSpeaking: Boolean
        get() = _isSpeaking.value

    init {
        scope.launch {
            for (request in speakQueue) {
                try {
                    val audioFile = prepareAudioFile(request)
                    if (audioFile == null) {
                        request.completion.complete(false)
                    } else {
                        // 合成完成即放行调用方（complete 必须在 send 之前）：调用方拿到结果后
                        // 就能提交下一句，当前句播放期间下一句照常预合成，流水线得以保持。
                        // 若等播放结束才返回，调用方（ChatViewModel 的串行消费者）每句都要
                        // 等上一句播完才发起合成，句间会出现可感知的空窗。
                        // 播放顺序仍由 playbackQueue 串行保证，打断/停止语义由
                        // generation 与 isCurrent 校验保证，均不受影响。
                        request.completion.complete(true)
                        playbackQueue.send(PreparedRequest(request, audioFile))
                    }
                } catch (e: Exception) {
                    request.completion.completeExceptionally(e)
                }
            }
        }

        scope.launch {
            for (prepared in playbackQueue) {
                try {
                    // 调用方在合成完成时已拿到结果，播放阶段的失败（文件损坏等，
                    // playAudioFile 内部已记录日志）只影响本句发声，不再回传。
                    playPreparedRequest(prepared)
                } catch (e: Exception) {
                    AppLogger.e(tag, "TTS播放队列处理失败", e)
                }
            }
        }
    }

    /**
     * 提交一段文本到播放队列。返回 true 表示合成成功且已进入播放队列（不等待播放结束），
     * false 表示合成失败。播放由 playbackQueue 串行推进，打断/停止通过 generation 校验生效。
     */
    suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>,
    ): Boolean = withContext(Dispatchers.IO) {
        if (interrupt) {
            clearForInterrupt()
        }

        val completion = CompletableDeferred<Boolean>()
        val request = Request(
            text = text,
            rate = rate,
            pitch = pitch,
            extraParams = extraParams,
            generation = stopGeneration.get(),
            completion = completion,
        )
        speakQueue.send(request)
        completion.await()
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    isPaused.set(true)
                    _isSpeaking.value = false
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            AppLogger.e(tag, "暂停TTS播放失败", e)
            false
        }
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    isPaused.set(false)
                    _isSpeaking.value = true
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            AppLogger.e(tag, "恢复TTS播放失败", e)
            false
        }
    }

    fun shutdown() {
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            AppLogger.w(tag, "释放TTS播放器失败", e)
        }
        mediaPlayer = null
        scope.cancel()
        speakQueue.close()
        playbackQueue.close()
    }

    fun isCurrent(request: Request): Boolean {
        return request.generation == stopGeneration.get()
    }

    private fun clearPendingRequests() {
        while (true) {
            val request = speakQueue.tryReceive().getOrNull() ?: break
            request.completion.complete(false)
        }
    }

    private fun clearPendingPlayback() {
        while (true) {
            val prepared = playbackQueue.tryReceive().getOrNull() ?: break
            prepared.request.completion.complete(false)
        }
    }

    private fun clearForInterrupt() {
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
    }

    private fun stopPlaybackOnly(): Boolean {
        return try {
            isPaused.set(false)
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                _isSpeaking.value = false
                true
            } ?: false
        } catch (e: Exception) {
            AppLogger.e(tag, "停止TTS播放失败", e)
            false
        }
    }

    private suspend fun playPreparedRequest(prepared: PreparedRequest) {
        if (!isCurrent(prepared.request)) {
            return
        }
        playAudioFile(prepared.audioFile)
    }

    private suspend fun playAudioFile(audioFile: File) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            AppLogger.e(tag, "Audio file is invalid: ${audioFile.absolutePath}")
            return
        }

        try {
            withContext(Dispatchers.IO) {
                isPaused.set(false)
                FileInputStream(audioFile).use { fis ->
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(fis.fd)
                        prepare()
                        start()
                    }

                    mediaPlayer?.release()
                    mediaPlayer = mp
                    _isSpeaking.value = true
                }
            }

            mediaPlayer?.let {
                while (it.isPlaying || isPaused.get()) {
                    delay(100)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(tag, "播放TTS音频失败", e)
        } finally {
            _isSpeaking.value = false
            isPaused.set(false)
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
            mediaPlayer = null
        }
    }
}
