package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.error.RequestFailure
import com.ai.assistance.operit.api.chat.error.RequestFailureDescription
import com.ai.assistance.operit.api.chat.error.FirstTokenTimeoutException
import com.ai.assistance.operit.api.chat.request.LlmRetryPolicy
import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response

internal fun parseRetryAfterMs(response: Response): Long? {
    val header = response.header("Retry-After")?.trim().orEmpty()
    if (header.isEmpty()) return null
    val seconds = header.toLongOrNull() ?: return null
    if (seconds <= 0L) return null
    return (seconds * 1000L).coerceAtMost(LlmRetryPolicy.RATE_LIMIT_COOLDOWN_MAX_MS)
}

internal suspend fun currentApiKeySession(): ApiKeyRequestSession? =
    currentCoroutineContext()[ApiKeyRequestElement]?.session

internal fun ApiKeyTriedSummary.formatForLog(): String {
    val parts = mutableListOf<String>()
    if (quota > 0) parts += "${quota} quota"
    if (rateLimit > 0) parts += "${rateLimit} rate-limit"
    if (auth > 0) parts += "${auth} auth"
    if (model > 0) parts += "${model} model"
    if (server > 0) parts += "${server} server"
    if (network > 0) parts += "${network} network"
    if (firstTokenTimeout > 0) parts += "${firstTokenTimeout} first-token"
    val detail = if (parts.isEmpty()) "" else parts.joinToString(", ")
    return if (detail.isEmpty()) {
        "tried $attempted key(s)"
    } else {
        "tried $attempted key(s): $detail"
    }
}

internal fun formatApiKeyPoolExhaustedMessage(
    context: Context,
    summary: ApiKeyTriedSummary,
    lastError: String,
): String {
    return context.getString(
        R.string.api_key_pool_exhausted,
        summary.attempted,
        summary.quota,
        summary.rateLimit,
        lastError,
    )
}

internal suspend fun reportKeyAwareFailure(
    exception: Throwable,
    model: String,
    stream: Boolean,
    attemptNumber: Int,
) {
    // 选钥失败发生在 HTTP 之前，直接结束，避免虚构网络失败与再次选空池。
    ApiKeyFailureClassifier.poolExhausted(exception)?.let { throw it }
    if (ApiRequestRetryPolicy.mode(exception) == ApiRequestRetryMode.STOP) return
    currentApiKeySession()?.reportOutcome(
        ApiKeyAttemptReport(
            success = false,
            errorClass = ApiKeyFailureClassifier.classify(exception),
            httpStatus = ApiKeyFailureClassifier.httpStatus(exception),
            retryAfterMs = ApiKeyFailureClassifier.retryAfterMs(exception),
            model = model,
            stream = stream,
            attemptIndex = attemptNumber,
        )
    )
}

internal suspend fun handleKeyAwareRetry(
    context: Context,
    exception: Exception,
    enableRetry: Boolean,
    onNonFatalError: suspend (String) -> Unit,
    onRetryAccepted: suspend () -> Unit,
    buildRetryMessage: (String, Int, Long) -> String,
    logTag: String,
    attemptNumber: Int,
): ApiKeyRetryDecision {
    if (exception is UserCancellationException || exception is CancellationException) {
        throw exception
    }
    ApiKeyFailureClassifier.poolExhausted(exception)?.let { throw it }
    val retryMode = ApiRequestRetryPolicy.mode(exception)
    val errorClass = ApiKeyFailureClassifier.classify(exception)
    val requestFailure = RequestFailure.describe(exception)?.takeIf {
        it.reason != null || it.providerError != null || it.requestContext != null
    }
    val endpointHint = if (ApiKeyFailureClassifier.isEndpointError(exception)) {
        context.getString(R.string.api_key_pool_endpoint_error, exception.message.orEmpty())
    } else null
    val errorText =
        endpointHint ?: requestFailure?.json(
            attempt = attemptNumber,
            retryable = enableRetry && retryMode == ApiRequestRetryMode.RETRY,
        )
            ?: exception.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.provider_error_network_interrupted)

    if (!enableRetry || retryMode == ApiRequestRetryMode.STOP) {
        throw IOException(errorText, exception)
    }

    val session = currentApiKeySession()
    val retryAfterMs = ApiKeyFailureClassifier.retryAfterMs(exception)
    val decision =
        session?.decideRetry(errorClass, retryAfterMs)
            ?: ApiKeyRetryDecision(
                exhausted = retryMode == ApiRequestRetryMode.ROTATE_KEY || attemptNumber > LlmRetryPolicy.MAX_RETRY_ATTEMPTS,
                delayMs =
                    if (retryMode == ApiRequestRetryMode.ROTATE_KEY || attemptNumber > LlmRetryPolicy.MAX_RETRY_ATTEMPTS) {
                        0L
                    } else {
                        LlmRetryPolicy.nextDelayMs(attemptNumber)
                    },
                switchedKey = false,
            )

    if (decision.exhausted) {
        val summary = session?.takeIf { it.poolMode }?.triedSummary()
        val message =
            requestFailure?.json(
                attempt = attemptNumber,
                retryable = false,
            ) ?: if (summary != null && summary.attempted > 0) {
                formatApiKeyPoolExhaustedMessage(context, summary, errorText)
            } else if (retryMode == ApiRequestRetryMode.ROTATE_KEY) {
                errorText
            } else {
                context.getString(
                    R.string.openai_error_connection_timeout,
                    LlmRetryPolicy.MAX_RETRY_ATTEMPTS,
                    errorText,
                )
            }
        AppLogger.e(logTag, message, exception)
        throw IOException(message, exception)
    }

    onRetryAccepted()
    // 换钥同样报告本次失败，不能只更新“正在重试”却隐藏具体原因。
    val retryMessage =
        requestFailure?.json(
            attempt = attemptNumber,
            retry = RequestFailureDescription.RetryInfo(
                nextAttempt = attemptNumber + 1,
                delayMs = decision.delayMs,
            ),
        ) ?: buildRetryMessage(errorText, attemptNumber, decision.delayMs)
    if (decision.delayMs > 0L) {
        AppLogger.w(logTag, "$errorText，将在 ${decision.delayMs}ms 后重试", exception)
        onNonFatalError(retryMessage)
        delay(decision.delayMs)
    } else {
        AppLogger.w(logTag, "$errorText，立即换钥继续", exception)
        onNonFatalError(retryMessage)
    }
    return decision
}

internal class FirstTokenWatchdog(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = LlmRetryPolicy.FIRST_TOKEN_TIMEOUT_MS,
) {
    @Volatile
    var timedOut: Boolean = false
        private set

    @Volatile
    private var receivedFirstToken: Boolean = false
    private var startedAtMs: Long = 0L
    private var firstTokenAtMs: Long = 0L
    private var job: Job? = null

    fun start(call: Call?) {
        job?.cancel()
        timedOut = false
        receivedFirstToken = false
        startedAtMs = System.currentTimeMillis()
        firstTokenAtMs = 0L
        if (timeoutMs <= 0L) return
        job =
            scope.launch {
                delay(timeoutMs)
                if (!receivedFirstToken) {
                    timedOut = true
                    runCatching { call?.cancel() }
                }
            }
    }

    fun markFirstToken() {
        if (!receivedFirstToken) {
            receivedFirstToken = true
            firstTokenAtMs = System.currentTimeMillis()
        }
        job?.cancel()
    }

    fun ttftMs(): Long? {
        if (firstTokenAtMs <= 0L || startedAtMs <= 0L) return null
        return (firstTokenAtMs - startedAtMs).coerceAtLeast(0L)
    }

    fun tokensPerSec(outputTokens: Long): Double? {
        val ttft = ttftMs() ?: return null
        val elapsed = (System.currentTimeMillis() - startedAtMs - ttft).coerceAtLeast(1L)
        if (outputTokens <= 0L) return null
        return outputTokens.toDouble() / (elapsed / 1000.0)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun throwIfTimedOut(context: Context) {
        if (!timedOut) return
        throw FirstTokenTimeoutException(
            context.getString(R.string.api_key_first_token_timeout)
        )
    }
}