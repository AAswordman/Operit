package com.ai.assistance.operit.api.chat.keypool

enum class ApiKeyErrorClass {
    NONE,
    AUTH,
    QUOTA,
    RATE_LIMIT,
    MODEL,
    SERVER,
    NETWORK,
    FIRST_TOKEN_TIMEOUT,
}

class FirstTokenTimeoutException(
    message: String = "Waiting for the first token timed out",
    cause: Throwable? = null,
) : java.io.IOException(message, cause)

class ApiKeyPoolExhaustedException(
    message: String,
    val summary: ApiKeyTriedSummary,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class ApiKeyTriedSummary(
    val attempted: Int = 0,
    val quota: Int = 0,
    val rateLimit: Int = 0,
    val auth: Int = 0,
    val model: Int = 0,
    val server: Int = 0,
    val network: Int = 0,
    val firstTokenTimeout: Int = 0,
) {
    fun plus(errorClass: ApiKeyErrorClass): ApiKeyTriedSummary {
        val nextAttempted = attempted + 1
        return when (errorClass) {
            ApiKeyErrorClass.NONE -> copy(attempted = nextAttempted)
            ApiKeyErrorClass.QUOTA -> copy(attempted = nextAttempted, quota = quota + 1)
            ApiKeyErrorClass.RATE_LIMIT -> copy(attempted = nextAttempted, rateLimit = rateLimit + 1)
            ApiKeyErrorClass.AUTH -> copy(attempted = nextAttempted, auth = auth + 1)
            ApiKeyErrorClass.MODEL -> copy(attempted = nextAttempted, model = model + 1)
            ApiKeyErrorClass.SERVER -> copy(attempted = nextAttempted, server = server + 1)
            ApiKeyErrorClass.NETWORK -> copy(attempted = nextAttempted, network = network + 1)
            ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT ->
                copy(attempted = nextAttempted, firstTokenTimeout = firstTokenTimeout + 1)
        }
    }
}

data class ApiKeySelection(
    val key: String,
    val keyId: String,
    val name: String = "",
    val candidateCount: Int,
    val stickyHit: Boolean = false,
)

data class ApiKeyAttemptReport(
    val success: Boolean,
    val errorClass: ApiKeyErrorClass = ApiKeyErrorClass.NONE,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
    val ttftMs: Long? = null,
    val tokensPerSec: Double? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val model: String,
    val stream: Boolean,
    val attemptIndex: Int,
)

data class ApiKeyRetryDecision(
    val exhausted: Boolean,
    val delayMs: Long,
    val switchedKey: Boolean,
)

interface ApiKeyRequestSession {
    suspend fun nextKey(): ApiKeySelection

    suspend fun reportOutcome(report: ApiKeyAttemptReport)

    suspend fun maxAttempts(): Int

    fun triedSummary(): ApiKeyTriedSummary

    suspend fun decideRetry(errorClass: ApiKeyErrorClass, retryAfterMs: Long?): ApiKeyRetryDecision
}

class ApiKeyRequestElement(
    val session: ApiKeyRequestSession,
) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<ApiKeyRequestElement>
}
