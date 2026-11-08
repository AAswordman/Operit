package com.ai.assistance.operit.ui.floating.voice

import android.content.Context
import com.ai.assistance.operit.api.speech.tts.TtsService
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory
import com.ai.assistance.operit.api.speech.tts.prefetch.PrefetchingTtsService
import com.ai.assistance.operit.api.speech.tts.prefetch.TtsSubmissionOrder
import com.ai.assistance.operit.core.speech.text.TtsCleaner
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 消息提交和播放独立于界面更新，避免静态分句或消息定稿取消尚未返回的云端请求。 */
internal class MessageSpeechPlayback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onFailure: () -> Unit,
) {
    private val profiles = SpeechServiceProfilesPreferences(context.applicationContext)
    private val order = TtsSubmissionOrder()
    private val lock = Any()
    private val jobs = linkedSetOf<Job>()
    private var service: TtsService? = null
    private var serviceProfile: SpeechServiceProfilesPreferences.TtsProfile? = null
    private var roundProfile: SpeechServiceProfilesPreferences.TtsProfile? = null
    private var closed = false
    private var stopBarrier: Job? = null

    // 包括初始化、文本清理和等待入队，不能只依据播放器状态恢复麦克风。
    val hasPending: Boolean get() = synchronized(lock) { jobs.isNotEmpty() }
    val isSpeaking: Boolean get() = synchronized(lock) { service?.isSpeaking == true }

    fun speak(text: String, interrupt: Boolean) {
        if (text.isBlank()) return
        val job = synchronized(lock) {
            if (closed) return
            val turn = order.reserve(interrupt)
            if (interrupt) jobs.toList().forEach { it.cancel() }
            val priorStop = stopBarrier
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    priorStop?.join()
                    if (!turn.awaitTurn()) return@launch
                    // 同一轮采用同一档案快照，下一轮再读取设置，避免中途换参数或释放旧服务。
                    if (interrupt || roundProfile == null) {
                        val selected = profiles.getCurrentTtsProfile()
                        if (!turn.awaitTurn()) return@launch
                        roundProfile = selected
                    }
                    val profile = checkNotNull(roundProfile)
                    val cleaned = WaifuMessageProcessor.cleanContentForWaifu(
                        TtsCleaner.cleanForSpeech(text, profile.cleanerRegexs)
                    )
                    if (cleaned.isBlank()) return@launch
                    val currentService = serviceFor(profile)
                    if (!currentService.isInitialized && !currentService.initialize()) {
                        if (turn.failIfCurrent()) onFailure()
                        return@launch
                    }
                    if (!turn.awaitTurn()) return@launch
                    val success = if (currentService is PrefetchingTtsService) {
                        coroutineScope {
                            val playback = async(start = CoroutineStart.UNDISPATCHED) {
                                currentService.speak(cleaned, interrupt, profile.speechRate, profile.pitch)
                            }
                            // 云端队列在第一次挂起前入队，后句不必等这一句播完。
                            turn.markSubmitted()
                            playback.await()
                        }
                    } else {
                        currentService.speak(cleaned, interrupt, profile.speechRate, profile.pitch)
                    }
                    if (!success && turn.failIfCurrent()) onFailure()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLogger.e("MessageSpeechPlayback", "消息朗读失败", error)
                    if (turn.failIfCurrent()) onFailure()
                } finally {
                    turn.markSubmitted()
                }
            }.also { pending ->
                jobs += pending
                pending.invokeOnCompletion {
                    // LAZY 任务可能在启动前被取消，此时协程的 finally 不会执行。
                    turn.markSubmitted()
                    synchronized(lock) { jobs -= pending }
                }
            }
        }
        job.start()
    }

    fun stop(): Job {
        val stopJob = synchronized(lock) {
            order.stop()
            val cancelled = jobs.toList()
            cancelled.forEach { it.cancel() }
            val previous = stopBarrier
            val currentService = service
            // 界面销毁也必须完成停止，不能依赖已经取消的界面协程。
            scope.launch(NonCancellable, start = CoroutineStart.LAZY) {
                previous?.join()
                try {
                    cancelled.forEach { it.join() }
                    withContext(Dispatchers.IO) { currentService?.stop() }
                } catch (error: Exception) {
                    AppLogger.e("MessageSpeechPlayback", "停止消息朗读失败", error)
                }
            }.also { stopBarrier = it }
        }
        // 新朗读会先等待此任务，避免迟到的 stop 清掉新轮次。
        stopJob.start()
        return stopJob
    }

    private suspend fun serviceFor(profile: SpeechServiceProfilesPreferences.TtsProfile): TtsService {
        val existing = synchronized(lock) { service }
        if (existing != null && synchronized(lock) { serviceProfile == profile }) return existing
        if (existing != null) withContext(Dispatchers.IO) { existing.shutdown() }
        // 使用独立实例，主聊天或旧窗口释放服务不会取消本窗口的云端队列。
        val created = TtsServiceFactory.createVoiceService(context.applicationContext, profile)
        synchronized(lock) {
            service = created
            serviceProfile = profile
        }
        return created
    }

    fun shutdown() {
        synchronized(lock) { closed = true }
        val stopped = stop()
        scope.launch(NonCancellable) {
            stopped.join()
            try {
                withContext(Dispatchers.IO) {
                    val owned = synchronized(lock) {
                        service.also {
                            service = null
                            serviceProfile = null
                        }
                    }
                    owned?.shutdown()
                }
            } catch (error: Exception) {
                AppLogger.e("MessageSpeechPlayback", "释放消息朗读服务失败", error)
            }
        }
    }
}
