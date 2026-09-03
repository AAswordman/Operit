package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.stream.Stream
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
        onRetryState: suspend (retry: RuntimeRetryMetadata) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> {
        val requestEpoch = synchronized(cancellationLock) { cancellationEpoch }
        return com.ai.assistance.operit.util.stream.stream {
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
                delegate.sendMessage(
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
                    onRetryState = onRetryState,
                    enableRetry = enableRetry,
                    recordTokenUsage = recordTokenUsage,
                    onUsageFinalized = { attempt ->
                        throwIfCancelled()
                        onUsageFinalized?.invoke(attempt)
                    },
                ).collect { chunk ->
                    throwIfCancelled()
                    emit(chunk)
                }
                throwIfCancelled()
            } finally {
                if (concurrencyAcquired) concurrencySemaphore?.release()
                synchronized(cancellationLock) {
                    queuedRequests.remove(cancelled)
                }
            }
        }
    }

    private companion object {
        const val CANCELLATION_POLL_INTERVAL_MS = 50L
    }
}
