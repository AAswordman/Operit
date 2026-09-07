package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.hasIndependentCacheWriteBilling

/**
 * Repairs only the in-memory shape of v20->v21 history rows.
 *
 * The old import left cache-write usage null. Known providers whose cache-write
 * cost is included in input pricing can treat that missing component as a
 * confirmed zero; independently billed and unknown providers stay unknown.
 */
internal fun normalizeLegacyCacheWriteUsage(
    row: TokenUsageModelAggregateRow,
): TokenUsageModelAggregateRow {
    if (
        row.configId.isNotEmpty() ||
            row.cacheWriteKnown >= row.requests ||
            hasIndependentCacheWriteBilling(row.provider)
    ) {
        return row
    }
    return row.copy(
        cacheWriteTokens = 0L,
        cacheWriteKnown = row.requests,
    )
}
