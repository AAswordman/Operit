package com.ai.assistance.operit.ui.features.tokenstats

import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTimeRanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun activityRangeAnchorDate(
    range: TokenStatsTimeRange,
    zone: ZoneId,
): LocalDate = Instant.ofEpochMilli(range.endMs - 1L).atZone(zone).toLocalDate()

internal fun activityRangeForMode(
    mode: TokenActivityViewMode,
    anchorDate: LocalDate,
    historyStartDate: LocalDate?,
    zone: ZoneId,
): TokenStatsTimeRange? {
    val startDate = when (mode) {
        TokenActivityViewMode.DAILY -> anchorDate
        TokenActivityViewMode.WEEKLY -> {
            // Keep the range aligned with TokenActivityAggregator's Sunday-first weeks.
            anchorDate.minusDays((anchorDate.dayOfWeek.value % 7).toLong())
        }
        TokenActivityViewMode.CUMULATIVE -> historyStartDate ?: return null
    }
    if (startDate.isAfter(anchorDate)) return null
    return TokenStatsTimeRanges.customRange(
        startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        anchorDate.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
