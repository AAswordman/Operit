package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Serializable
data class CodexUsageWindow(
    val usedPercent: Int,
    val windowDurationSeconds: Long?,
    val resetsAtEpochSeconds: Long?,
) {
    val remainingPercent: Int
        get() = (100 - usedPercent).coerceIn(0, 100)
}

@Serializable
data class CodexUsageSnapshot(
    val planType: String?,
    val fiveHourWindow: CodexUsageWindow?,
    val sevenDayWindow: CodexUsageWindow?,
)

class CodexUsageClient(
    private val client: OkHttpClient,
) {
    suspend fun fetch(
        accessToken: String,
        accountId: String,
        residency: String?,
    ): Result<CodexUsageSnapshot> {
        return try {
            val request = Request.Builder()
                .url(CodexOAuthProtocol.CODEX_USAGE_ENDPOINT)
                .get()
                .header("Authorization", "Bearer $accessToken")
                .header("ChatGPT-Account-ID", accountId)
                .header("originator", "operit")
                .header("User-Agent", "Operit/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .apply {
                    residency?.let { header("x-openai-internal-codex-residency", it) }
                }
                .build()

            val body = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Codex usage request failed with HTTP ${response.code}")
                    }
                    responseBody
                }
            }

            Result.success(parseUsage(body))
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to fetch Codex usage", error)
            Result.failure(error)
        }
    }

    internal fun parseUsage(responseBody: String): CodexUsageSnapshot {
        val root = JSONObject(responseBody)
        val rateLimit = root.optJSONObject("rate_limit")
        val windows = listOf(
            parseWindow(rateLimit?.optJSONObject("primary_window")),
            parseWindow(rateLimit?.optJSONObject("secondary_window")),
        ).filterNotNull()
        return CodexUsageSnapshot(
            planType = root.optString("plan_type", "").trim().takeIf { it.isNotEmpty() },
            fiveHourWindow = windows.firstOrNull {
                it.windowDurationSeconds == FIVE_HOUR_WINDOW_SECONDS
            },
            sevenDayWindow = windows.firstOrNull {
                it.windowDurationSeconds == SEVEN_DAY_WINDOW_SECONDS
            },
        )
    }

    private fun parseWindow(window: JSONObject?): CodexUsageWindow? {
        window ?: return null
        if (!window.has("used_percent")) return null

        val usedPercent = window.optInt("used_percent", -1)
        if (usedPercent < 0) return null

        val durationSeconds = window.optLong("limit_window_seconds", -1L)
            .takeIf { it > 0L }
        val resetsAt = window.optLong("reset_at", -1L)
            .takeIf { it > 0L }
        return CodexUsageWindow(
            usedPercent = usedPercent.coerceAtMost(100),
            windowDurationSeconds = durationSeconds,
            resetsAtEpochSeconds = resetsAt,
        )
    }

    private companion object {
        const val TAG = "CodexUsageClient"
        const val FIVE_HOUR_WINDOW_SECONDS = 5L * 60L * 60L
        const val SEVEN_DAY_WINDOW_SECONDS = 7L * 24L * 60L * 60L
    }
}
