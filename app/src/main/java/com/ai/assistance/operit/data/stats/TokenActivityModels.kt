package com.ai.assistance.operit.data.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
enum class TokenActivityViewMode { DAILY, WEEKLY, MONTHLY, YEARLY, CUMULATIVE }

internal data class TokenActivitySnapshot(
    val zone: ZoneId,
    val dayTotals: Map<LocalDate, Long>,
    val hourTotals: Map<LocalDateTime, Long> = emptyMap(),
)

data class TokenActivityDay(val date: LocalDate, val tokens: Long, val level: Int)

data class TokenActivityWeek(
    val startDate: LocalDate,
    val tokens: Long,
    val level: Int,
    val barHeight: Int,
)

data class TokenActivityMonth(
    val startDate: LocalDate,
    val tokens: Long,
    val level: Int,
    val barHeight: Int,
)

data class TokenActivityYear(
    val startDate: LocalDate,
    val tokens: Long,
    val level: Int,
    val barHeight: Int,
)

data class TokenActivityHour(
    val startDate: LocalDate,
    val hour: Int,
    val tokens: Long,
    val level: Int,
    val barHeight: Int,
)

data class TokenActivityStats(
    val totalTokens: Long = 0L,
    val peakTokens: Long = 0L,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

data class TokenActivityRangeData(
    val daily: List<TokenActivityDay>,
    val weekly: List<TokenActivityWeek>,
    val monthly: List<TokenActivityMonth>,
    val yearly: List<TokenActivityYear>,
    val hourly: List<TokenActivityHour>,
    val cumulative: List<TokenActivityDay>,
    val stats: TokenActivityStats,
)

object TokenActivityAggregator {
    /** Builds all activity views from the same explicit calendar range. */
    internal fun rangeData(
        snapshot: TokenActivitySnapshot,
        range: TokenStatsTimeRange,
    ): TokenActivityRangeData {
        val start = java.time.Instant.ofEpochMilli(range.startMs).atZone(snapshot.zone).toLocalDate()
        val end = java.time.Instant.ofEpochMilli(range.endMs - 1L).atZone(snapshot.zone).toLocalDate()
        return rangeData(snapshot.dayTotals, snapshot.hourTotals, start, end)
    }

    private fun rangeData(
        dayTotals: Map<LocalDate, Long>,
        hourTotals: Map<LocalDateTime, Long>,
        start: LocalDate,
        end: LocalDate,
    ): TokenActivityRangeData {
        val dayCount = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val raw = List(dayCount) { index ->
            val date = start.plusDays(index.toLong())
            TokenActivityDay(date, dayTotals[date] ?: 0L, 0)
        }
        val dailyLevels = QuantileLevels.from(raw.map(TokenActivityDay::tokens))
        val daily = raw.map { it.copy(level = dailyLevels.level(it.tokens)) }

        var cumulativeTotal = 0L
        val cumulativeRaw = raw.map {
            cumulativeTotal = TokenCostCalculator.saturatedAdd(cumulativeTotal, it.tokens)
            it.copy(tokens = cumulativeTotal)
        }
        val cumulativeLevels = QuantileLevels.from(cumulativeRaw.map(TokenActivityDay::tokens))
        val cumulative = cumulativeRaw.map { it.copy(level = cumulativeLevels.level(it.tokens)) }

        // Rolling 7-day buckets aligned to the range start: a week runs from the
        // range's first day, matching the mode policy that ends the window today
        // and starts seven days earlier. The final bucket may be shorter when the
        // range is not an exact multiple of seven days.
        val weekCount = ((dayCount + 6) / 7).coerceAtLeast(1)
        val weekTotals = LongArray(weekCount)
        raw.forEach { day ->
            val index = (ChronoUnit.DAYS.between(start, day.date).toInt() / 7)
                .coerceIn(0, weekCount - 1)
            weekTotals[index] = TokenCostCalculator.saturatedAdd(weekTotals[index], day.tokens)
        }
        val weekLevels = QuantileLevels.from(weekTotals.toList())
        val heights = barHeights(weekTotals.toList())
        val weekly = List(weekCount) { index ->
            TokenActivityWeek(
                startDate = start.plusDays(index.toLong() * 7L),
                tokens = weekTotals[index],
                level = weekLevels.level(weekTotals[index]),
                barHeight = heights[index],
            )
        }

        val monthTotals = linkedMapOf<YearMonth, Long>()
        raw.forEach { day ->
            val key = YearMonth.from(day.date)
            monthTotals[key] = TokenCostCalculator.saturatedAdd(monthTotals[key] ?: 0L, day.tokens)
        }
        val monthLevels = QuantileLevels.from(monthTotals.values.toList())
        val monthHeights = barHeights(monthTotals.values.toList())
        val monthly = monthTotals.entries.mapIndexed { index, (yearMonth, tokens) ->
            TokenActivityMonth(
                startDate = yearMonth.atDay(1),
                tokens = tokens,
                level = monthLevels.level(tokens),
                barHeight = monthHeights[index],
            )
        }

        val yearTotals = linkedMapOf<Int, Long>()
        raw.forEach { day ->
            val key = day.date.year
            yearTotals[key] = TokenCostCalculator.saturatedAdd(yearTotals[key] ?: 0L, day.tokens)
        }
        val yearLevels = QuantileLevels.from(yearTotals.values.toList())
        val yearHeights = barHeights(yearTotals.values.toList())
        val yearly = yearTotals.entries.mapIndexed { index, (year, tokens) ->
            TokenActivityYear(
                startDate = LocalDate.of(year, 1, 1),
                tokens = tokens,
                level = yearLevels.level(tokens),
                barHeight = yearHeights[index],
            )
        }

        // Hourly buckets are only meaningful for single-day views (24 bars). Keep the
        // range short to avoid wasteful computation for weekly/monthly/yearly ranges.
        val hourly = if (dayCount <= 2 && hourTotals.isNotEmpty()) {
            val hourEntries = buildList {
                var current = start.atStartOfDay()
                val endExclusive = end.plusDays(1L).atStartOfDay()
                while (current < endExclusive) {
                    add(
                        TokenActivityHour(
                            startDate = current.toLocalDate(),
                            hour = current.hour,
                            tokens = hourTotals[current] ?: 0L,
                            level = 0,
                            barHeight = 0,
                        )
                    )
                    current = current.plusHours(1L)
                }
            }
            val hourLevels = QuantileLevels.from(hourEntries.map(TokenActivityHour::tokens))
            val hourHeights = barHeights(hourEntries.map(TokenActivityHour::tokens))
            hourEntries.mapIndexed { index, entry ->
                entry.copy(
                    level = hourLevels.level(entry.tokens),
                    barHeight = hourHeights[index],
                )
            }
        } else {
            emptyList()
        }

        return TokenActivityRangeData(daily, weekly, monthly, yearly, hourly, cumulative, stats(raw))
    }

    private fun stats(days: List<TokenActivityDay>): TokenActivityStats {
        var total = 0L
        var peak = 0L
        var run = 0
        var longest = 0
        days.forEach { day ->
            total = TokenCostCalculator.saturatedAdd(total, day.tokens)
            peak = maxOf(peak, day.tokens)
            run = if (day.tokens > 0L) run + 1 else 0
            longest = maxOf(longest, run)
        }
        var current = 0
        var index = days.lastIndex
        while (index >= 0 && days[index].tokens > 0L) {
            current++
            index--
        }
        return TokenActivityStats(total, peak, current, longest)
    }

    private fun barHeights(values: List<Long>): IntArray {
        val distinct = values.filter { it > 0L }.distinct().sorted()
        return IntArray(values.size) { index ->
            when {
                values[index] <= 0L -> 1
                distinct.size == 1 -> 7
                else -> 2 + distinct.indexOf(values[index]) * 5 / (distinct.size - 1)
            }
        }
    }
}

private class QuantileLevels(private val thresholds: LongArray) {
    fun level(value: Long): Int {
        if (value <= 0L) return 0
        for (level in 1..5) if (value <= thresholds[level]) return level
        return 5
    }

    companion object {
        fun from(values: List<Long>): QuantileLevels {
            val nonZero = values.filter { it > 0L }.sorted()
            if (nonZero.size < 2 || nonZero.firstOrNull() == nonZero.lastOrNull()) {
                return QuantileLevels(LongArray(6).also { it[3] = Long.MAX_VALUE })
            }
            fun nearest(percentile: Double): Long {
                val index = (ceil(nonZero.size * percentile).toInt() - 1)
                    .coerceIn(0, nonZero.lastIndex)
                return nonZero[index]
            }
            return QuantileLevels(
                longArrayOf(0L, nearest(0.25), nearest(0.50), nearest(0.75), nearest(0.95), Long.MAX_VALUE)
            )
        }
    }
}
