package com.ai.assistance.operit.ui.features.tokenstats

import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTimeRanges
import java.time.LocalDate
import java.time.ZoneId

internal fun activityRangeForMode(
    mode: TokenActivityViewMode,
    anchorDate: LocalDate,
    historyStartDate: LocalDate?,
    zone: ZoneId,
): TokenStatsTimeRange? {
    // Each periodic mode covers the most recent complete period, because future
    // time has not happened yet and cannot be counted. Weekly, monthly and yearly
    // mirror each other: weekly runs from the same weekday last week through
    // yesterday (exactly seven days); monthly runs from the same day last month
    // through yesterday, so its length equals the previous month's day count (a
    // 28-day February yields four week bars, any longer month yields five);
    // yearly runs from the same day last year through yesterday.
    val (startDate, inclusiveEndDate) = when (mode) {
        TokenActivityViewMode.DAILY -> anchorDate to anchorDate
        TokenActivityViewMode.WEEKLY -> {
            val start = anchorDate.minusDays(7L)
            start to anchorDate.minusDays(1L)
        }
        TokenActivityViewMode.MONTHLY -> {
            // minusMonths clamps month-end dates (e.g. Mar 31 -> Feb 28/29).
            val start = anchorDate.minusMonths(1L)
            start to anchorDate.minusDays(1L)
        }
        TokenActivityViewMode.YEARLY -> {
            // Rolling year mirroring weekly/monthly: same day last year through
            // yesterday. minusYears clamps leap days (e.g. Feb 29 -> Feb 28).
            val start = anchorDate.minusYears(1L)
            start to anchorDate.minusDays(1L)
        }
        TokenActivityViewMode.CUMULATIVE -> {
            val start = historyStartDate ?: return null
            start to anchorDate
        }
    }
    if (startDate.isAfter(inclusiveEndDate)) return null
    return TokenStatsTimeRanges.customRange(
        startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        inclusiveEndDate.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
