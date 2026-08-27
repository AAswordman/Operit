package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.data.stats.TokenUsageRepository
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.RevisableTextStream
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Records successful formal-inference requests with provider-confirmed usage. */
class TokenTrackingAIService(
    private val delegate: AIService,
    context: Context,
    private val configId: String,
) : AIService {
    private val repository = TokenUsageRepository.getInstance(context.applicationContext)
    private val activeRequests = ConcurrentHashMap.newKeySet<RequestTracker>()
    private val cancellationLock = Any()
    private var cancellationEpoch = 0L

    override val inputTokenCount: Long get() = delegate.inputTokenCount
    override val cachedInputTokenCount: Long get() = delegate.cachedInputTokenCount
    override val outputTokenCount: Long get() = delegate.outputTokenCount
    override val providerModel: String get() = delegate.providerModel

    override fun resetTokenCounts() = delegate.resetTokenCounts()
    override fun cancelStreaming() {
        synchronized(cancellationLock) {
            cancellationEpoch += 1
            activeRequests.forEach { request -> request.cancel() }
        }
        delegate.cancelStreaming()
    }
    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> =
        delegate.getModelsList(context)

    override suspend fun calculateInputTokens(
        chatHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?,
    ): Long = delegate.calculateInputTokens(chatHistory, availableTools)

    override fun release() = delegate.release()

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
        onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> {
        val request = RequestTracker(configId = configId, providerModel = providerModel)
        val requestEpoch = synchronized(cancellationLock) { cancellationEpoch }
        val onStarted: () -> Unit = {
            synchronized(cancellationLock) {
                if (cancellationEpoch != requestEpoch) {
                    request.cancel()
                }
                activeRequests.add(request)
                Unit
            }
        }
        val onFinished: () -> Unit = {
            synchronized(cancellationLock) {
                activeRequests.remove(request)
                Unit
            }
        }
        val inner =
            if (!recordTokenUsage) {
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
                    enableRetry = enableRetry,
                    recordTokenUsage = false,
                    onUsageFinalized = onUsageFinalized,
                )
            } else {
                delegate.sendMessage(
                    context = context,
                    chatHistory = chatHistory,
                    modelParameters = modelParameters,
                    enableThinking = enableThinking,
                    stream = stream,
                    availableTools = availableTools,
                    preserveThinkInHistory = preserveThinkInHistory,
                    onTokensUpdated = onTokensUpdated,
                    onUsageReported = { usage, attempt ->
                        request.onUsage(usage, attempt)
                        forwardUsageObserver(onUsageReported, usage, attempt)
                    },
                    onNonFatalError = onNonFatalError,
                    enableRetry = enableRetry,
                    recordTokenUsage = true,
                    onUsageFinalized = { attempt ->
                        request.onSuccess(attempt)
                        forwardUsageFinalizedObserver(onUsageFinalized, attempt)
                    },
                )
            }
        return wrapStream(
            inner = inner,
            request = request,
            onStarted = onStarted,
            onFinished = onFinished,
        )
    }

    override suspend fun testConnection(context: Context): Result<String> =
        delegate.testConnection(context)

    private suspend fun forwardUsageObserver(
        observer: (suspend (ProviderUsageSnapshot, Int) -> Unit)?,
        usage: ProviderUsageSnapshot,
        attempt: Int,
    ) {
        val callback = observer ?: return
        try {
            callback(usage, attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "usage observer failed", e)
        }
    }

    private suspend fun forwardUsageFinalizedObserver(
        observer: (suspend (Int?) -> Unit)?,
        attempt: Int?,
    ) {
        val callback = observer ?: return
        try {
            callback(attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "usage completion observer failed", e)
        }
    }

    private fun wrapStream(
        inner: Stream<String>,
        request: RequestTracker,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
    ): Stream<String> =
        if (inner is TextStreamEventCarrier) {
            TrackingRevisableStream(inner, inner.eventChannel, request, repository, onStarted, onFinished)
        } else {
            TrackingStream(inner, request, repository, onStarted, onFinished)
        }

    private class TrackingStream(
        private val inner: Stream<String>,
        private val request: RequestTracker,
        private val repository: TokenUsageRepository,
        private val onStarted: () -> Unit,
        private val onFinished: () -> Unit,
    ) : Stream<String> {
        override val isLocked: Boolean get() = inner.isLocked
        override val bufferedCount: Int get() = inner.bufferedCount
        override suspend fun lock() = inner.lock()
        override suspend fun unlock() = inner.unlock()
        override fun clearBuffer() = inner.clearBuffer()

        override suspend fun collect(collector: StreamCollector<String>) {
            try {
                onStarted()
                request.throwIfCancelled()
                inner.collect { value -> collector.emit(value) }
                currentCoroutineContext().ensureActive()
                persist(repository, request, request.finish())
            } finally {
                onFinished()
            }
        }
    }

    private class TrackingRevisableStream(
        private val inner: Stream<String>,
        override val eventChannel: SharedStream<TextStreamEvent>,
        private val request: RequestTracker,
        private val repository: TokenUsageRepository,
        private val onStarted: () -> Unit,
        private val onFinished: () -> Unit,
    ) : RevisableTextStream {
        override val isLocked: Boolean get() = inner.isLocked
        override val bufferedCount: Int get() = inner.bufferedCount
        override suspend fun lock() = inner.lock()
        override suspend fun unlock() = inner.unlock()
        override fun clearBuffer() = inner.clearBuffer()

        override suspend fun collect(collector: StreamCollector<String>) {
            try {
                onStarted()
                request.throwIfCancelled()
                inner.collect { value -> collector.emit(value) }
                currentCoroutineContext().ensureActive()
                persist(repository, request, request.finish())
            } finally {
                onFinished()
            }
        }
    }

    private class RequestTracker(
        private val configId: String,
        private val providerModel: String,
    ) {
        private val startedAtMs = System.currentTimeMillis()
        private val lock = Any()
        private val attempts = linkedMapOf<Int, ProviderUsageSnapshot>()
        private val finished = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)
        private var successfulAttempt: Int? = null

        fun onUsage(usage: ProviderUsageSnapshot, attempt: Int) {
            synchronized(lock) {
                val key = attempt.coerceAtLeast(1)
                attempts[key] = merge(attempts[key], usage)
            }
        }

        fun onSuccess(attempt: Int?) {
            synchronized(lock) {
                successfulAttempt = attempt?.coerceAtLeast(1)
            }
        }

        fun cancel() {
            cancelled.set(true)
        }

        fun throwIfCancelled() {
            if (cancelled.get()) throw CancellationException("AI request was cancelled")
        }

        fun finish(): TokenUsageRecordEntity? {
            if (cancelled.get()) return null
            val snapshot =
                synchronized(lock) {
                    successfulAttempt?.let(attempts::get)
                } ?: return null
            if (cancelled.get()) return null
            if (!snapshot.hasKnownFields()) return null

            val separator = providerModel.indexOf(':')
            require(separator > 0 && separator < providerModel.lastIndex) {
                "provider:model is required for token usage events"
            }
            // 推理不再作为独立统计列落库；provider 明确将其独立于 output 上报时，
            // 先并入持久化 output，保持总量与费用口径完整。
            val reportedOutputTokens = snapshot.outputTokens
            val persistedOutputTokens = when {
                reportedOutputTokens != null && snapshot.reasoningIncludedInOutput == false ->
                    snapshot.reasoningTokens?.let { saturatedAdd(reportedOutputTokens, it) }
                reportedOutputTokens != null -> reportedOutputTokens
                else -> null
            }
            if (snapshot.uncachedInputTokens == null &&
                snapshot.cachedInputTokens == null &&
                snapshot.cacheWriteTokens == null &&
                snapshot.totalInputTokens == null &&
                persistedOutputTokens == null
            ) {
                return null
            }
            return TokenUsageRecordEntity(
                occurredAtMs = startedAtMs,
                configId = configId,
                provider = providerModel.substring(0, separator),
                model = providerModel.substring(separator + 1),
                requestCount = 1L,
                uncachedInputTokens = snapshot.uncachedInputTokens,
                cachedInputTokens = snapshot.cachedInputTokens,
                cacheWriteTokens =
                    if (snapshot.cacheWriteSeparateBilling) snapshot.cacheWriteTokens else 0L,
                totalInputTokens = snapshot.totalInputTokens,
                outputTokens = persistedOutputTokens,
            )
        }

        fun markPersisted(): Boolean = !cancelled.get() && finished.compareAndSet(false, true)

        private fun merge(
            previous: ProviderUsageSnapshot?,
            update: ProviderUsageSnapshot,
        ): ProviderUsageSnapshot {
            if (previous == null || update.completeSnapshot) return update
            return update.copy(
                uncachedInputTokens = update.uncachedInputTokens ?: previous.uncachedInputTokens,
                cachedInputTokens = update.cachedInputTokens ?: previous.cachedInputTokens,
                cacheWriteTokens = update.cacheWriteTokens ?: previous.cacheWriteTokens,
                totalInputTokens = update.totalInputTokens ?: previous.totalInputTokens,
                outputTokens = update.outputTokens ?: previous.outputTokens,
                reasoningTokens = update.reasoningTokens ?: previous.reasoningTokens,
            )
        }
    }

    companion object {
        private const val TAG = "TokenTrackingAIService"

        private suspend fun persist(
            repository: TokenUsageRepository,
            request: RequestTracker,
            record: TokenUsageRecordEntity?,
        ) {
            if (record != null && request.markPersisted()) persist(repository, record)
        }

        private suspend fun persist(
            repository: TokenUsageRepository,
            record: TokenUsageRecordEntity,
        ) {
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    repository.record(record)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "token usage insert failed", e)
                }
            }
        }

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
