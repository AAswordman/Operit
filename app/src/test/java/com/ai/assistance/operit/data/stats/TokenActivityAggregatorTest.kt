package com.ai.assistance.operit.data.stats

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenActivityAggregatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `range data contains every selected calendar day and excludes surrounding activity`() {
        val range = dateRange("2026-08-02", "2026-08-04")
        val snapshot = TokenActivitySnapshot(
            zone = zone,
            dayTotals = mapOf(
                LocalDate.of(2026, 8, 1) to 40L,
                LocalDate.of(2026, 8, 2) to 10L,
                LocalDate.of(2026, 8, 4) to 30L,
                LocalDate.of(2026, 8, 5) to 50L,
            ),
        )

        val result = TokenActivityAggregator.rangeData(snapshot, range)

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 4),
            ),
            result.daily.map(TokenActivityDay::date),
        )
        assertEquals(listOf(10L, 0L, 30L), result.daily.map(TokenActivityDay::tokens))
        assertEquals(40L, result.stats.totalTokens)
        assertEquals(30L, result.stats.peakTokens)
    }

    @Test
    fun `range data groups monthly totals by calendar month`() {
        val range = dateRange("2026-01-15", "2026-03-10")
        val snapshot = TokenActivitySnapshot(
            zone = zone,
            dayTotals = mapOf(
                LocalDate.of(2026, 1, 20) to 10L,
                LocalDate.of(2026, 2, 5) to 20L,
                LocalDate.of(2026, 3, 1) to 30L,
            ),
        )

        val result = TokenActivityAggregator.rangeData(snapshot, range)

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
            ),
            result.monthly.map(TokenActivityMonth::startDate),
        )
        assertEquals(listOf(10L, 20L, 30L), result.monthly.map(TokenActivityMonth::tokens))
    }

    @Test
    fun `range data groups yearly totals by calendar year`() {
        val range = dateRange("2025-06-01", "2026-06-30")
        val snapshot = TokenActivitySnapshot(
            zone = zone,
            dayTotals = mapOf(
                LocalDate.of(2025, 7, 1) to 15L,
                LocalDate.of(2025, 12, 31) to 25L,
                LocalDate.of(2026, 1, 1) to 35L,
            ),
        )

        val result = TokenActivityAggregator.rangeData(snapshot, range)

        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
            ),
            result.yearly.map(TokenActivityYear::startDate),
        )
        assertEquals(listOf(40L, 35L), result.yearly.map(TokenActivityYear::tokens))
    }

    @Test
    fun `range data builds hourly buckets for a single day range`() {
        val range = dateRange("2026-08-02", "2026-08-02")
        val snapshot = TokenActivitySnapshot(
            zone = zone,
            dayTotals = mapOf(LocalDate.of(2026, 8, 2) to 70L),
            hourTotals = mapOf(
                LocalDate.of(2026, 8, 2).atTime(9, 0) to 10L,
                LocalDate.of(2026, 8, 2).atTime(21, 0) to 60L,
            ),
        )

        val result = TokenActivityAggregator.rangeData(snapshot, range)

        assertEquals(24, result.hourly.size)
        assertEquals(10L, result.hourly.first { it.hour == 9 }.tokens)
        assertEquals(60L, result.hourly.first { it.hour == 21 }.tokens)
        assertEquals(0L, result.hourly.first { it.hour == 3 }.tokens)
    }

    @Test
    fun `range data calculates streaks and cumulative totals inside the selected range`() {
        val range = dateRange("2026-08-01", "2026-08-05")
        val snapshot = TokenActivitySnapshot(
            zone = zone,
            dayTotals = mapOf(
                LocalDate.of(2026, 8, 1) to 10L,
                LocalDate.of(2026, 8, 2) to 20L,
                LocalDate.of(2026, 8, 4) to 30L,
                LocalDate.of(2026, 8, 5) to 40L,
            ),
        )

        val result = TokenActivityAggregator.rangeData(snapshot, range)

        assertEquals(2, result.stats.currentStreak)
        assertEquals(2, result.stats.longestStreak)
        assertEquals(listOf(10L, 30L, 30L, 60L, 100L), result.cumulative.map(TokenActivityDay::tokens))
    }

    private fun dateRange(start: String, inclusiveEnd: String): TokenStatsTimeRange =
        TokenStatsTimeRanges.customRange(
            LocalDate.parse(start).atStartOfDay(zone).toInstant().toEpochMilli(),
            LocalDate.parse(inclusiveEnd).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
}
