package com.ai.assistance.operit.api.chat.llmprovider

internal object LlmRetryPolicy {
    const val MAX_RETRY_ATTEMPTS = 5
    const val MAX_POOL_ATTEMPTS = 20
    const val FIRST_TOKEN_TIMEOUT_MS = 45_000L
    const val AUTH_COOLDOWN_MS = 12L * 60L * 60L * 1000L
    const val QUOTA_COOLDOWN_MS = 4L * 60L * 60L * 1000L
    const val RATE_LIMIT_COOLDOWN_MS = 45_000L
    const val RATE_LIMIT_COOLDOWN_MAX_MS = 15L * 60L * 1000L
    const val MODEL_COOLDOWN_MS = 30L * 60L * 1000L
    const val SERVER_COOLDOWN_MS = 5_000L
    const val NETWORK_COOLDOWN_MS = 2_000L
    const val FIRST_TOKEN_COOLDOWN_MS = 10_000L
    private const val RETRY_BASE_DELAY_MS = 1_000L
    private const val RETRY_MAX_DELAY_MS = 16_000L

    fun nextDelayMs(retryAttempt: Int): Long {
        val normalizedAttempt = retryAttempt.coerceAtLeast(1)
        val exponent = (normalizedAttempt - 1).coerceAtMost(4)
        return (RETRY_BASE_DELAY_MS * (1L shl exponent)).coerceAtMost(RETRY_MAX_DELAY_MS)
    }
}
