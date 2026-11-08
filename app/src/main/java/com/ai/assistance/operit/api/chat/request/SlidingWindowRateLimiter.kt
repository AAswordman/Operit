package com.ai.assistance.operit.api.chat.request

import java.util.ArrayDeque
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SlidingWindowRateLimiter(
    val maxRequestsPerMinute: Int,
    private val windowMs: Long = 60_000L
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun tryAcquire(nowMs: Long = System.currentTimeMillis()): Long {
        if (maxRequestsPerMinute <= 0) return 0L

        return mutex.withLock {
            while (timestamps.isNotEmpty() && nowMs - timestamps.first() >= windowMs) {
                timestamps.removeFirst()
            }

            if (timestamps.size >= maxRequestsPerMinute) {
                val oldest = timestamps.first()
                (windowMs - (nowMs - oldest)).coerceAtLeast(1L)
            } else {
                timestamps.addLast(nowMs)
                0L
            }
        }
    }

    suspend fun acquire() {
        while (true) {
            val retryAfterMs = tryAcquire()
            if (retryAfterMs <= 0L) {
                return
            }
            delay(retryAfterMs)
        }
    }
}
