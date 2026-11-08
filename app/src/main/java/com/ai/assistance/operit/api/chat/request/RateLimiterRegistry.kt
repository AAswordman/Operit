package com.ai.assistance.operit.api.chat.request

import java.util.concurrent.ConcurrentHashMap

object RateLimiterRegistry {
    private val limiters = ConcurrentHashMap<String, SlidingWindowRateLimiter>()

    fun getOrCreate(key: String, maxRequestsPerMinute: Int): SlidingWindowRateLimiter {
        require(maxRequestsPerMinute > 0) { "maxRequestsPerMinute must be > 0" }

        return limiters.compute(key) { _, existing ->
            if (existing == null || existing.maxRequestsPerMinute != maxRequestsPerMinute) {
                SlidingWindowRateLimiter(maxRequestsPerMinute = maxRequestsPerMinute)
            } else {
                existing
            }
        }!!
    }
}
