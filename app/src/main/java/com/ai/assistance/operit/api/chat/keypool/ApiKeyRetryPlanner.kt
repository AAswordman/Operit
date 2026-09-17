package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.LlmRetryPolicy

object ApiKeyRetryPlanner {
    fun maxHttpAttempts(candidateCount: Int, poolMode: Boolean): Int {
        if (!poolMode) {
            return LlmRetryPolicy.MAX_RETRY_ATTEMPTS + 1
        }
        return candidateCount.coerceIn(1, LlmRetryPolicy.MAX_POOL_ATTEMPTS)
    }

    fun decide(
        errorClass: ApiKeyErrorClass,
        sameKeyAttempts: Int,
        remainingCandidatesAfterExclude: Int,
        httpAttemptsIncludingThis: Int,
        maxHttpAttempts: Int,
        retryAfterMs: Long? = null,
    ): ApiKeyRetryDecision {
        val switchNow = switchesKeyImmediately(errorClass, remainingCandidatesAfterExclude)
        val canRetrySameKey = !switchNow && sameKeyAttempts < 2
        val willExclude = switchNow || !canRetrySameKey
        val hasAnotherKey = remainingCandidatesAfterExclude > 0
        val canContinueOnSameKey = !willExclude && httpAttemptsIncludingThis < maxHttpAttempts
        val canContinueOnOtherKey =
            willExclude && hasAnotherKey && httpAttemptsIncludingThis < maxHttpAttempts
        val exhausted = !canContinueOnSameKey && !canContinueOnOtherKey
        val delayMs =
            when {
                exhausted -> 0L
                willExclude -> 0L
                else -> LlmRetryPolicy.nextDelayMs(sameKeyAttempts)
            }
        return ApiKeyRetryDecision(
            exhausted = exhausted,
            delayMs = delayMs,
            switchedKey = willExclude && hasAnotherKey,
        )
    }

    fun cooldownMs(errorClass: ApiKeyErrorClass, retryAfterMs: Long?, nowMs: Long): Long {
        val duration =
            when (errorClass) {
                ApiKeyErrorClass.NONE -> 0L
                ApiKeyErrorClass.AUTH -> LlmRetryPolicy.AUTH_COOLDOWN_MS
                ApiKeyErrorClass.QUOTA -> LlmRetryPolicy.QUOTA_COOLDOWN_MS
                ApiKeyErrorClass.RATE_LIMIT ->
                    (retryAfterMs ?: LlmRetryPolicy.RATE_LIMIT_COOLDOWN_MS)
                        .coerceIn(1_000L, LlmRetryPolicy.RATE_LIMIT_COOLDOWN_MAX_MS)
                ApiKeyErrorClass.MODEL -> LlmRetryPolicy.MODEL_COOLDOWN_MS
                ApiKeyErrorClass.SERVER -> LlmRetryPolicy.SERVER_COOLDOWN_MS
                ApiKeyErrorClass.NETWORK -> LlmRetryPolicy.NETWORK_COOLDOWN_MS
                ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT -> LlmRetryPolicy.FIRST_TOKEN_COOLDOWN_MS
            }
        return if (duration <= 0L) 0L else nowMs + duration
    }

    fun switchesKeyImmediately(
        errorClass: ApiKeyErrorClass,
        remainingCandidatesAfterExclude: Int = 0,
    ): Boolean {
        if (errorClass == ApiKeyErrorClass.NONE) return false
        if (remainingCandidatesAfterExclude > 0) return true
        return when (errorClass) {
            ApiKeyErrorClass.AUTH,
            ApiKeyErrorClass.QUOTA,
            ApiKeyErrorClass.RATE_LIMIT,
            ApiKeyErrorClass.MODEL,
            ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT -> true
            ApiKeyErrorClass.SERVER,
            ApiKeyErrorClass.NETWORK,
            ApiKeyErrorClass.NONE -> false
        }
    }
}