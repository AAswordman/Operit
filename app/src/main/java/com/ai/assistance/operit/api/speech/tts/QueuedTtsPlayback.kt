package com.ai.assistance.operit.api.speech.tts

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.ai.assistance.operit.api.speech.tts.prefetch.OrderedTtsPrefetchQueue
import com.ai.assistance.operit.core.speech.text.TtsSegmenter
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal class QueuedTtsPlayback(
    private val tag: String,
    private val deleteAudioAfterPlayback: Boolean = true,
    private val audioUsage: Int = AudioAttributes.USAGE_MEDIA,
    private val prepareAudioFile: suspend (Request) -> File?,
) {
    data class Request(
        val text: String,
        val sequence: Long,
        val rate: Float?,
        val pitch: Float?,
        val extraParams: Map<String, String>,
    )

    private val playerLock = Any()
    private val requestSequence = AtomicLong(0)
    private val paused = MutableStateFlow(false)
    private var mediaPlayer: MediaPlayer? = null
    private var playbackDone: CompletableDeferred<Boolean>? = null
    private val queue = OrderedTtsPrefetchQueue<Request, File>(
        prepare = { request ->
            val started = System.nanoTime()
            AppLogger.d(tag, "云端分句准备开始 sequence=${request.sequence} chars=${request.text.length}")
            prepareAudioFile(request).also {
                AppLogger.d(tag, "云端分句准备完成 sequence=${request.sequence} chars=${request.text.length} prepareMs=${(System.nanoTime() - started) / 1_000_000}")
            }
        },
        play = ::playAudioFile,
        release = { file ->
            if (deleteAudioAfterPlayback) {
                runCatching { file.delete() }.onFailure { AppLogger.w(tag, "清理TTS临时音频失败", it) }
            }
        },
        stopPlayback = ::stopPlaybackOnly,
    )

    // 排队和下载也属于本轮朗读，避免句间请求未完成时语音通话提前恢复收音。
    val speakingStateFlow: Flow<Boolean> = combine(queue.pendingStateFlow, paused) { pending, isPaused ->
        pending && !isPaused
    }.distinctUntilChanged()
    val isSpeaking: Boolean get() = queue.hasPending && !paused.value

    suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>,
    ): Boolean = queue.speak(
        TtsSegmenter.split(text).map { Request(it, requestSequence.incrementAndGet(), rate, pitch, extraParams.toMap()) },
        interrupt,
    )

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        queue.stop()
        true
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val hasPending = queue.hasPending
        synchronized(playerLock) {
            paused.value = true
            mediaPlayer?.let { if (it.isPlaying) it.pause() }
            hasPending
        }
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        val hasPending = queue.hasPending
        synchronized(playerLock) {
            paused.value = false
            mediaPlayer?.let {
                it.start()
            }
            hasPending
        }
    }

    fun shutdown() = queue.shutdown()

    private suspend fun playAudioFile(file: File, isCurrent: () -> Boolean): Boolean {
        if (!file.exists() || file.length() == 0L) {
            AppLogger.e(tag, "TTS音频文件为空或已不存在")
            return false
        }
        val done = CompletableDeferred<Boolean>()
        var player: MediaPlayer? = null
        try {
            synchronized(playerLock) {
                if (!isCurrent()) return false
                val created = MediaPlayer()
                player = created
                mediaPlayer = created
                playbackDone = done
                created.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(audioUsage)
                        .build()
                )
                created.setDataSource(file.absolutePath)
                created.setOnCompletionListener { finishPlayback(created, done, true) }
                created.setOnErrorListener { _, what, extra ->
                    AppLogger.e(tag, "TTS播放器错误 what=$what extra=$extra")
                    finishPlayback(created, done, false)
                    true
                }
                created.prepare()
                if (!paused.value) {
                    created.start()
                }
            }
            return done.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e(tag, "播放TTS音频失败", error)
            return false
        } finally {
            player?.let { finishPlayback(it, done, false) }
        }
    }

    private fun finishPlayback(player: MediaPlayer, done: CompletableDeferred<Boolean>, success: Boolean) {
        synchronized(playerLock) {
            // 旧播放器的迟到回调不能释放已经开始播放的新一句。
            if (mediaPlayer === player) {
                mediaPlayer = null
                playbackDone = null
                runCatching { player.release() }.onFailure { AppLogger.w(tag, "释放TTS播放器失败", it) }
            }
            done.complete(success)
        }
    }

    private fun stopPlaybackOnly() {
        synchronized(playerLock) {
            paused.value = false
            val player = mediaPlayer
            val done = playbackDone
            mediaPlayer = null
            playbackDone = null
            runCatching { player?.release() }.onFailure { AppLogger.w(tag, "停止TTS播放器失败", it) }
            done?.complete(false)
        }
    }
}
