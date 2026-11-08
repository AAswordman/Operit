package com.ai.assistance.operit.api.speech.tts.prefetch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 首句先准备，后续请求并发；唯一消费者始终按提交顺序播放。 */
internal class OrderedTtsPrefetchQueue<Request, Audio : Any>(
    private val prepare: suspend (Request) -> Audio?,
    private val play: suspend (Audio, () -> Boolean) -> Boolean,
    private val release: (Audio) -> Unit,
    private val stopPlayback: () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Any()
    private val queue = Channel<Pending>(Channel.UNLIMITED)
    private val pending = linkedSetOf<Pending>()
    private var active: Pending? = null
    private var closed = false
    private val pendingState = MutableStateFlow(false)
    val pendingStateFlow: StateFlow<Boolean> = pendingState.asStateFlow()

    private inner class Generation {
        val job = SupervisorJob(scope.coroutineContext[Job])
        val firstReady = CompletableDeferred<Unit>()
        var firstSubmitted = false
    }

    private inner class Pending(
        val request: Request,
        val generation: Generation,
        val first: Boolean,
    ) {
        val completion = CompletableDeferred<Boolean>()
        lateinit var preparation: Deferred<Unit>
        var audio: Audio? = null

        fun isCurrent(): Boolean = generation.job.isActive && completion.isActive
    }

    private var generation = Generation()

    val hasPending: Boolean
        get() = synchronized(lock) { pending.isNotEmpty() }

    init {
        scope.launch {
            for (entry in queue) {
                try {
                    entry.preparation.await()
                    if (!entry.isCurrent()) continue
                    val audio = synchronized(lock) {
                        if (entry.isCurrent()) {
                            active = entry
                            entry.audio
                        } else null
                    }
                    val success = audio != null && play(audio, entry::isCurrent)
                    entry.completion.complete(success && entry.isCurrent())
                    if (!success) failGeneration(entry.generation)
                } catch (cancelled: CancellationException) {
                    // 单个旧请求取消不能结束队列消费者，新一轮朗读仍需处理。
                    currentCoroutineContext().ensureActive()
                    val wasCurrent = entry.isCurrent()
                    entry.completion.complete(false)
                    if (wasCurrent) failGeneration(entry.generation)
                } catch (error: Exception) {
                    entry.completion.completeExceptionally(error)
                    failGeneration(entry.generation)
                } finally {
                    finish(entry)
                }
            }
        }
    }

    suspend fun speak(requests: List<Request>, interrupt: Boolean): Boolean {
        if (requests.isEmpty()) return false
        // 入队先于任何挂起点，避免后到分句抢先入队或被首句的中断操作清掉。
        val entries = synchronized(lock) {
            check(!closed) { "语音预取队列已关闭" }
            if (interrupt) {
                invalidate(generation)
                generation = Generation()
            }
            if (!generation.job.isActive) return false
            requests.map { request ->
                val first = !generation.firstSubmitted
                generation.firstSubmitted = true
                Pending(request, generation, first).also { entry ->
                    entry.preparation = scope.async(entry.generation.job, start = CoroutineStart.LAZY) {
                        var unclaimedAudio: Audio? = null
                        try {
                            if (!entry.first) entry.generation.firstReady.await()
                            ensureActive()
                            unclaimedAudio = prepare(entry.request)
                            ensureActive()
                            synchronized(lock) {
                                if (entry.isCurrent()) {
                                    entry.audio = unclaimedAudio
                                    unclaimedAudio = null
                                }
                            }
                        } finally {
                            unclaimedAudio?.let(release)
                            if (entry.first) entry.generation.firstReady.complete(Unit)
                        }
                    }
                    pending += entry
                    pendingState.value = true
                    check(queue.trySend(entry).isSuccess)
                    entry.preparation.start()
                }
            }
        }
        try {
            for (entry in entries) {
                if (!entry.completion.await()) return false
            }
            return true
        } finally {
            if (!currentCoroutineContext().isActive) {
                synchronized(lock) { entries.forEach(::cancelEntry) }
            }
        }
    }

    fun stop() = synchronized(lock) {
        invalidate(generation)
        generation = Generation()
    }

    fun shutdown() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        invalidate(generation)
        queue.close()
        scope.cancel()
    }

    private fun failGeneration(failed: Generation) = synchronized(lock) {
        if (generation === failed) invalidate(failed)
    }

    private fun invalidate(previous: Generation) {
        previous.job.cancel()
        previous.firstReady.complete(Unit)
        stopPlayback()
        pending.filter { it.generation === previous }.forEach(::cancelEntry)
    }

    private fun cancelEntry(entry: Pending) {
        if (active === entry) {
            stopPlayback()
            active = null
        }
        entry.preparation.cancel()
        entry.completion.complete(false)
        if (entry.first) entry.generation.firstReady.complete(Unit)
        finish(entry)
    }

    private fun finish(entry: Pending) {
        val audio = synchronized(lock) {
            pending.remove(entry)
            pendingState.value = pending.isNotEmpty()
            if (active === entry) active = null
            entry.audio.also { entry.audio = null }
        }
        audio?.let(release)
    }
}
