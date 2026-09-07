package com.ai.assistance.operit.api.chat.llmprovider

internal object LlmRetryPolicy {
    const val MAX_RETRY_ATTEMPTS = 5
    private const val RETRY_BASE_DELAY_MS = 1_000L
    private const val RETRY_MAX_DELAY_MS = 16_000L

    fun nextDelayMs(retryAttempt: Int): Long {
        val normalizedAttempt = retryAttempt.coerceAtLeast(1)
        val exponent = (normalizedAttempt - 1).coerceAtMost(4)
        return (RETRY_BASE_DELAY_MS * (1L shl exponent)).coerceAtMost(RETRY_MAX_DELAY_MS)
    }
}
