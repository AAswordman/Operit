package com.ai.assistance.operit.core.tools.agent

import kotlinx.coroutines.delay

/** Only a temporarily missing window is a startup readiness signal. */
internal class PhoneUiUnavailableException(message: String) : IllegalStateException(message)

internal class PhoneStartupObservation(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun <T> read(
        deadlineMs: Long?,
        checkpoint: suspend () -> Unit,
        observe: suspend () -> T,
    ): T {
        var attempts = 0
        while (true) {
            checkpoint()
            try {
                return observe()
            } catch (e: PhoneUiUnavailableException) {
                attempts++
                val deadline = deadlineMs ?: throw e
                val remaining = deadline - nowMs()
                if (remaining <= 0 || attempts >= 7) throw e
                // Poll only missing startup observations, never launch or input actions. The
                // deadline is shared with foreground checking and cannot interrupt Binder reads.
                delay(minOf(attempts * 1000L, 3000L, remaining))
            }
        }
    }
}
