package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.LlmRetryPolicy
import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
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

internal fun shouldSuppressRetryNotice(
    exception: Exception,
    decision: ApiKeyRetryDecision,
    logTag: String,
): Boolean {
    if (!decision.switchedKey) return false
    val errorClass = ApiKeyFailureClassifier.classify(exception)
    AppLogger.w(logTag, "密钥池换钥，跳过中间提示。class=$errorClass", exception)
    return true
}

internal suspend fun handleKeyAwareRetry(
    context: Context,
    exception: Exception,
    enableRetry: Boolean,
    onNonFatalError: suspend (String) -> Unit,
    onRetryAccepted: suspend () -> Unit,
    buildRetryMessage: (String, Int) -> String,
    logTag: String,
    attemptNumber: Int,
): ApiKeyRetryDecision {
    if (exception is UserCancellationException || exception is CancellationException) {
        throw exception
    }

    val errorText =
        exception.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.provider_error_network_interrupted)

    if (!enableRetry) {
        throw IOException(errorText, exception)
    }

    val session = currentApiKeySession()
    val errorClass = ApiKeyFailureClassifier.classify(exception)
    val retryAfterMs = ApiKeyFailureClassifier.retryAfterMs(exception)
    val decision =
        session?.decideRetry(errorClass, retryAfterMs)
            ?: ApiKeyRetryDecision(
                exhausted = attemptNumber > LlmRetryPolicy.MAX_RETRY_ATTEMPTS,
                delayMs =
                    if (attemptNumber > LlmRetryPolicy.MAX_RETRY_ATTEMPTS) {
                        0L
                    } else {
                        LlmRetryPolicy.nextDelayMs(attemptNumber)
                    },
                switchedKey = false,
            )

    if (decision.exhausted) {
        val summary = session?.triedSummary()
        val message =
            if (summary != null && summary.attempted > 0) {
                formatApiKeyPoolExhaustedMessage(context, summary, errorText)
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
    val retryMessage =
        if (shouldSuppressRetryNotice(exception, decision, logTag)) {
            ""
        } else {
            buildRetryMessage(errorText, attemptNumber + 1)
        }
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