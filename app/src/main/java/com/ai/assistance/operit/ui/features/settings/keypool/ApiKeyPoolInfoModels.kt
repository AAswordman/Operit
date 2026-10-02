package com.ai.assistance.operit.ui.features.settings.keypool

import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity
import com.ai.assistance.operit.data.model.ApiKeyInfo

internal const val API_KEY_POOL_INFO_WINDOW_MS = 24L * 60L * 60L * 1000L

internal data class ApiKeyPoolInfoRow(
    val key: ApiKeyInfo,
    val index: Int,
    val lastOccurredAtMs: Long?,
    val lastSuccess: Boolean?,
    val lastHttpStatus: Int?,
    val successCount24h: Int,
    val failCount24h: Int,
    val isRecent: Boolean,
)

internal fun buildApiKeyPoolInfoRows(
    keys: List<ApiKeyInfo>,
    records: List<ApiKeyAttemptRecordEntity>,
    nowMs: Long,
    windowMs: Long = API_KEY_POOL_INFO_WINDOW_MS,
): List<ApiKeyPoolInfoRow> {
    val windowStart = nowMs - windowMs
    val recordsByKey = records.groupBy { it.keyId }
    val snapshots =
        keys.mapIndexed { index, key ->
            val keyRecords = recordsByKey[key.id].orEmpty()
            val latest = keyRecords.maxByOrNull { it.occurredAtMs }
            val windowRecords = keyRecords.filter { it.occurredAtMs >= windowStart }
            ApiKeyPoolInfoRow(
                key = key,
                index = index + 1,
                lastOccurredAtMs = latest?.occurredAtMs,
                lastSuccess = latest?.success,
                lastHttpStatus = latest?.httpStatus,
                successCount24h = windowRecords.count { it.success },
                failCount24h = windowRecords.count { !it.success },
                isRecent = false,
            )
        }
    val recentKeyId =
        snapshots
            .filter { it.lastSuccess == true }
            .maxByOrNull { it.lastOccurredAtMs ?: Long.MIN_VALUE }
            ?.key
            ?.id
            ?: snapshots
                .filter { it.lastOccurredAtMs != null }
                .maxByOrNull { it.lastOccurredAtMs ?: Long.MIN_VALUE }
                ?.key
                ?.id
    return snapshots.map { row -> row.copy(isRecent = row.key.id == recentKeyId) }
}