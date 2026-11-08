package com.ai.assistance.operit.api.chat.request

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore

object RequestConcurrencyRegistry {
    private data class Entry(
        val maxConcurrentRequests: Int,
        val semaphore: Semaphore
    )

    private val semaphores = ConcurrentHashMap<String, Entry>()

    fun getOrCreate(key: String, maxConcurrentRequests: Int): Semaphore {
        require(maxConcurrentRequests > 0) { "maxConcurrentRequests must be > 0" }

        return semaphores.compute(key) { _, existing ->
            if (existing == null || existing.maxConcurrentRequests != maxConcurrentRequests) {
                Entry(
                    maxConcurrentRequests = maxConcurrentRequests,
                    semaphore = Semaphore(maxConcurrentRequests)
                )
            } else {
                existing
            }
        }!!.semaphore
    }
}
