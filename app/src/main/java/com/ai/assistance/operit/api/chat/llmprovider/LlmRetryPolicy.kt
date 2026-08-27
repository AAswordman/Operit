package com.ai.assistance.operit.api.chat.llmprovider

internal object LlmRetryPolicy {
    const val MAX_RETRY_ATTEMPTS = 5
    private const val RETRY_BASE_DELAY_MS = 1000L

    internal fun isRetryableClientStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429
    }

    fun nextDelayMs(retryAttempt: Int): Long {
        val normalizedAttempt = retryAttempt.coerceAtLeast(1)
        return RETRY_BASE_DELAY_MS * (1L shl (normalizedAttempt - 1))
    }
}
