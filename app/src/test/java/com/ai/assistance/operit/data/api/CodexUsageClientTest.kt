package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.data.preferences.CodexStoredUsageSnapshot
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CodexUsageClientTest {
    private val client = CodexUsageClient(OkHttpClient())

    @Test
    fun parsesPlanAndRollingWindows() {
        val usage = client.parseUsage(
            """
            {
              "plan_type": "plus",
                "rate_limit": {
                  "primary_window": {
                  "used_percent": 52,
                  "limit_window_seconds": 604800,
                  "reset_at": 1700400123
                  },
                  "secondary_window": {
                  "used_percent": 18,
                  "limit_window_seconds": 18000,
                  "reset_at": 1700000123
                  }
              }
            }
            """.trimIndent(),
        )

        assertEquals("plus", usage.planType)
        assertEquals(82, usage.fiveHourWindow?.remainingPercent)
        assertEquals(48, usage.sevenDayWindow?.remainingPercent)
        assertEquals(18000L, usage.fiveHourWindow?.windowDurationSeconds)
        assertEquals(1700400123L, usage.sevenDayWindow?.resetsAtEpochSeconds)
    }

    @Test
    fun classifiesSevenDayWindowFromPrimarySlot() {
        val usage = client.parseUsage(
            """
            {
              "plan_type": "plus",
              "rate_limit": {
                "primary_window": {
                  "used_percent": 52,
                  "limit_window_seconds": 604800,
                  "reset_at": 1700400123
                }
              }
            }
            """.trimIndent(),
        )

        assertNull(usage.fiveHourWindow)
        assertNotNull(usage.sevenDayWindow)
        assertEquals(48, usage.sevenDayWindow?.remainingPercent)
    }

    @Test
    fun persistedSnapshotRoundTripsWithoutTokenData() {
        val usage = CodexUsageSnapshot(
            planType = "plus",
            fiveHourWindow = null,
            sevenDayWindow = CodexUsageWindow(
                usedPercent = 55,
                windowDurationSeconds = 604800L,
                resetsAtEpochSeconds = 1700400123L,
            ),
        )
        val stored = CodexStoredUsageSnapshot(
            accountId = "account-1",
            usage = usage,
            fetchedAtMillis = 1700000000000L,
        )

        val decoded = Json.decodeFromString<CodexStoredUsageSnapshot>(
            Json.encodeToString(stored),
        )

        assertEquals(stored, decoded)
    }
}
