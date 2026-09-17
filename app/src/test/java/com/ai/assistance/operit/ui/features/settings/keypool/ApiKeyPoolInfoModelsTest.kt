package com.ai.assistance.operit.ui.features.settings.keypool

import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyPoolInfoModelsTest {
    @Test
    fun keepsCurrentListOrderAndNumbersFromOne() {
        val keys = listOf(key("b"), key("a"), key("c"))
        val rows = buildApiKeyPoolInfoRows(keys, emptyList(), nowMs = 10_000L)

        assertEquals(listOf("b", "a", "c"), rows.map { it.key.id })
        assertEquals(listOf(1, 2, 3), rows.map { it.index })
    }

    @Test
    fun lastAttemptUsesHttpStatusAnd24hCounts() {
        val now = 100_000L
        val records =
            listOf(
                record("a", occurredAtMs = now - 1_000L, success = false, httpStatus = 401),
                record("a", occurredAtMs = now - 2_000L, success = true, httpStatus = null),
                record("a", occurredAtMs = now - API_KEY_POOL_INFO_WINDOW_MS - 1L, success = false, httpStatus = 403),
            )
        val rows = buildApiKeyPoolInfoRows(listOf(key("a")), records, nowMs = now)
        val row = rows.single()

        assertEquals(false, row.lastSuccess)
        assertEquals(401, row.lastHttpStatus)
        assertEquals(1, row.successCount24h)
        assertEquals(1, row.failCount24h)
        assertTrue(row.isRecent)
    }

    @Test
    fun recentBadgePrefersLatestSuccess() {
        val now = 50_000L
        val records =
            listOf(
                record("a", occurredAtMs = now - 5_000L, success = true),
                record("b", occurredAtMs = now - 1_000L, success = false, httpStatus = 402),
            )
        val rows = buildApiKeyPoolInfoRows(listOf(key("a"), key("b")), records, nowMs = now)

        assertTrue(rows[0].isRecent)
        assertFalse(rows[1].isRecent)
    }

    private fun key(id: String): ApiKeyInfo =
        ApiKeyInfo(
            id = id,
            key = "sk-$id",
            name = "API Key $id",
            availabilityStatus = ApiKeyAvailabilityStatus.UNTESTED,
        )

    private fun record(
        keyId: String,
        occurredAtMs: Long,
        success: Boolean,
        httpStatus: Int? = null,
    ): ApiKeyAttemptRecordEntity =
        ApiKeyAttemptRecordEntity(
            occurredAtMs = occurredAtMs,
            configId = "cfg",
            keyId = keyId,
            model = "model",
            success = success,
            errorClass = if (success) "NONE" else "AUTH",
            httpStatus = httpStatus,
            attemptIndex = 1,
        )
}