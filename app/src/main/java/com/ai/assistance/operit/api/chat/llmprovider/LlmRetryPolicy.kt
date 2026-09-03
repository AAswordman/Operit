package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.LlmRetryMode
import com.ai.assistance.operit.data.preferences.LlmRetrySettings
import kotlinx.coroutines.flow.first

public data class RuntimeRetryMetadata(
    val retryAttempt: Int,
    val maxRetryAttempts: Int,
    val retryAfterMs: Long,
    val errorCode: String? = null,
    val providerCode: String? = null,
    val httpStatusCode: Int? = null,
    val errorMessage: String? = null
)

internal data class LlmRetryPolicySnapshot(
    val maxRetryAttempts: Int,
    val initialDelayMs: Long,
    val maxDelayMs: Long
) {
    fun nextDelayMs(retryAttempt: Int): Long {
        val normalizedAttempt = retryAttempt.coerceAtLeast(1)
        var delayMs = initialDelayMs.coerceIn(1L, maxDelayMs)
        repeat(normalizedAttempt - 1) {
            delayMs =
                if (delayMs >= maxDelayMs / 2L) {
                    maxDelayMs
                } else {
                    (delayMs * 2L).coerceAtMost(maxDelayMs)
                }
        }
        return delayMs
    }

    fun delayScheduleMs(): List<Long> =
        (1..maxRetryAttempts).map(::nextDelayMs)
}

internal object LlmRetryPolicy {
    private const val FAST_INITIAL_DELAY_MS = 1_000L
    private const val FAST_MAX_DELAY_MS = 4_000L
    private const val STANDARD_INITIAL_DELAY_MS = 1_000L
    private const val STANDARD_MAX_DELAY_MS = 16_000L
    private const val STABLE_INITIAL_DELAY_MS = 2_000L
    private const val STABLE_MAX_DELAY_MS = 30_000L

    internal fun isRetryableClientStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429
    }

    suspend fun snapshot(context: Context): LlmRetryPolicySnapshot {
        val settings =
            DisplayPreferencesManager.getInstance(context).llmRetrySettings.first()
        return fromSettings(settings)
    }

    internal fun fromSettings(settings: LlmRetrySettings): LlmRetryPolicySnapshot {
        val (initialDelayMs, maxDelayMs) =
            when (settings.mode) {
                LlmRetryMode.FAST -> FAST_INITIAL_DELAY_MS to FAST_MAX_DELAY_MS
                LlmRetryMode.STANDARD -> STANDARD_INITIAL_DELAY_MS to STANDARD_MAX_DELAY_MS
                LlmRetryMode.STABLE -> STABLE_INITIAL_DELAY_MS to STABLE_MAX_DELAY_MS
                LlmRetryMode.CUSTOM -> {
                    val customMaxDelayMs =
                        settings.customMaxDelaySeconds.coerceAtLeast(1).toLong() * 1_000L
                    val customInitialDelayMs =
                        settings.customInitialDelaySeconds
                            .coerceAtLeast(1)
                            .toLong()
                            .times(1_000L)
                            .coerceAtMost(customMaxDelayMs)
                    customInitialDelayMs to customMaxDelayMs
                }
            }
        return LlmRetryPolicySnapshot(
            maxRetryAttempts =
                settings.maxAttempts.coerceIn(
                    DisplayPreferencesManager.MIN_LLM_RETRY_ATTEMPTS,
                    DisplayPreferencesManager.MAX_LLM_RETRY_ATTEMPTS
                ),
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs
        )
    }
}
