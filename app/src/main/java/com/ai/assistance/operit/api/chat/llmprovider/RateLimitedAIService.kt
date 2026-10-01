package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.RevisableTextStream
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

class RateLimitedAIService(
    private val delegate: AIService,
    private val rateLimiter: SlidingWindowRateLimiter?,
    private val concurrencySemaphore: Semaphore?
) : AIService by delegate {
    private val queuedRequests = ConcurrentHashMap.newKeySet<AtomicBoolean>()
    private val cancellationLock = Any()
    private var cancellationEpoch = 0L

    override fun cancelStreaming() {
        synchronized(cancellationLock) {
            cancellationEpoch += 1
            queuedRequests.forEach { request -> request.set(true) }
        }
        delegate.cancelStreaming()
    }

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
        onUsageReported: (suspend (com.ai.assistance.operit.data.stats.ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> {
        val requestEpoch = synchronized(cancellationLock) { cancellationEpoch }
        // 保存点会在正文之前发出。消费端是后订阅的，必须重放，否则回滚没有起点。
        val eventChannel = MutableSharedStream<TextStreamEvent>(
            replay = Int.MAX_VALUE,
            extraBufferCapacity = Int.MAX_VALUE,
        )
        val response = com.ai.assistance.operit.util.stream.stream {
            val cancelled = AtomicBoolean(false)
            // A cold stream can be cancelled before collection starts; register and compare atomically.
            synchronized(cancellationLock) {
                if (cancellationEpoch != requestEpoch) {
                    cancelled.set(true)
                }
                queuedRequests.add(cancelled)
            }
            var concurrencyAcquired = false

            try {
                fun throwIfCancelled() {
                    if (cancelled.get()) throw CancellationException("AI request was cancelled")
                }

                suspend fun awaitRateLimit() {
                    val limiter = rateLimiter ?: return
                    while (true) {
                        throwIfCancelled()
                        val retryAfterMs = limiter.tryAcquire()
                        if (retryAfterMs <= 0L) return
                        delay(retryAfterMs.coerceAtMost(CANCELLATION_POLL_INTERVAL_MS))
                    }
                }

                suspend fun acquireConcurrency(semaphore: Semaphore) {
                    while (true) {
                        throwIfCancelled()
                        if (semaphore.tryAcquire()) {
                            concurrencyAcquired = true
                            return
                        }
                        delay(CANCELLATION_POLL_INTERVAL_MS)
                    }
                }

                throwIfCancelled()
                awaitRateLimit()
                throwIfCancelled()
                val semaphore = concurrencySemaphore
                if (semaphore != null) {
                    acquireConcurrency(semaphore)
                }
                throwIfCancelled()
                val response = delegate.sendMessage(
                    context = context,
                    chatHistory = chatHistory,
                    modelParameters = modelParameters,
                    enableThinking = enableThinking,
                    stream = stream,
                    availableTools = availableTools,
                    preserveThinkInHistory = preserveThinkInHistory,
                    onTokensUpdated = onTokensUpdated,
                    onUsageReported = onUsageReported,
                    onNonFatalError = onNonFatalError,
                    enableRetry = enableRetry,
                    recordTokenUsage = recordTokenUsage,
                    onUsageFinalized = { attempt ->
                        throwIfCancelled()
                        onUsageFinalized?.invoke(attempt)
                    },
                )
                val carrier = response as? TextStreamEventCarrier
                if (carrier == null) {
                    response.collect { chunk ->
                        throwIfCancelled()
                        emit(chunk)
                    }
                } else {
                    coroutineScope {
                        val eventJob = launch {
                            carrier.eventChannel.collect { event -> eventChannel.emit(event) }
                        }
                        try {
                            response.collect { chunk ->
                                throwIfCancelled()
                                emit(chunk)
                            }
                        } finally {
                            eventJob.cancel()
                        }
                    }
                }
                throwIfCancelled()
            } finally {
                if (concurrencyAcquired) concurrencySemaphore?.release()
                synchronized(cancellationLock) {
                    queuedRequests.remove(cancelled)
                }
            }
        }
        return RateLimitedRevisableStream(response, eventChannel)
    }

    private class RateLimitedRevisableStream(
        private val delegate: Stream<String>,
        override val eventChannel: SharedStream<TextStreamEvent>,
    ) : RevisableTextStream {
        override val isLocked: Boolean get() = delegate.isLocked
        override val bufferedCount: Int get() = delegate.bufferedCount
        override suspend fun lock() = delegate.lock()
        override suspend fun unlock() = delegate.unlock()
        override fun clearBuffer() = delegate.clearBuffer()
        override suspend fun collect(collector: StreamCollector<String>) = delegate.collect(collector)
    }

    private companion object {
        const val CANCELLATION_POLL_INTERVAL_MS = 50L
    }
}
