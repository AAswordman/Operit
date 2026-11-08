package com.ai.assistance.operit.data.stats

import org.json.JSONArray
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** 可配置的高峰时间段，使用当天的分钟数表示。 */
data class TokenPeakTimeRange(
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "start minute is out of range" }
        require(endMinute in 1..MINUTES_PER_DAY) { "end minute is out of range" }
        require(startMinute < endMinute) { "peak time range must be non-empty" }
    }

    fun contains(minuteOfDay: Int): Boolean =
        minuteOfDay in startMinute until endMinute

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

val DEFAULT_TOKEN_PEAK_TIME_RANGES = listOf(
    TokenPeakTimeRange(startMinute = 9 * 60, endMinute = 12 * 60),
    TokenPeakTimeRange(startMinute = 14 * 60, endMinute = 18 * 60),
)

/** 峰谷规则使用中国时区，统计页面展示时区不影响计费判断。 */
object TokenPricingRules {
    const val PEAK_PRICING_START_YEAR = 2026
    val PRICING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    // 2026 年官方放假区间按整段低谷处理，调休上班日不单独建模。
    private val HOLIDAY_RANGES_2026 = listOf(
        LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 1, 3),
        LocalDate.of(2026, 2, 15) to LocalDate.of(2026, 2, 23),
        LocalDate.of(2026, 4, 4) to LocalDate.of(2026, 4, 6),
        LocalDate.of(2026, 5, 1) to LocalDate.of(2026, 5, 5),
        LocalDate.of(2026, 6, 19) to LocalDate.of(2026, 6, 21),
        LocalDate.of(2026, 9, 25) to LocalDate.of(2026, 9, 27),
        LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 7),
    )

    private val HOLIDAYS_2026: Set<LocalDate> = buildSet {
        HOLIDAY_RANGES_2026.forEach { (start, end) ->
            var date = start
            while (!date.isAfter(end)) {
                add(date)
                date = date.plusDays(1)
            }
        }
    }

    fun isPeakTime(
        occurredAtMs: Long?,
        periods: List<TokenPeakTimeRange>,
        weekendOffPeakPricingEnabled: Boolean = true,
        holidayOffPeakPricingEnabled: Boolean = true,
    ): Boolean {
        if (occurredAtMs == null || periods.isEmpty()) return false
        val local = Instant.ofEpochMilli(occurredAtMs).atZone(PRICING_ZONE)
        if (local.year < PEAK_PRICING_START_YEAR) return false
        if (
            weekendOffPeakPricingEnabled &&
            (local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY)
        ) {
            return false
        }
        if (holidayOffPeakPricingEnabled && local.toLocalDate() in HOLIDAYS_2026) {
            return false
        }
        val minuteOfDay = local.hour * 60 + local.minute
        return periods.any { it.contains(minuteOfDay) }
    }

    fun encodePeakTimeRanges(periods: List<TokenPeakTimeRange>): String =
        JSONArray().apply {
            periods.forEach { period ->
                put(JSONArray().put(period.startMinute).put(period.endMinute))
            }
        }.toString()

    fun decodePeakTimeRanges(encoded: String?): List<TokenPeakTimeRange> {
        if (encoded.isNullOrBlank()) return DEFAULT_TOKEN_PEAK_TIME_RANGES
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val range = array.optJSONArray(index) ?: continue
                    val start = range.optInt(0, -1)
                    val end = range.optInt(1, -1)
                    if (start >= 0 && end >= 0 && start < end) {
                        runCatching { TokenPeakTimeRange(start, end) }
                            .getOrNull()
                            ?.let(::add)
                    }
                }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_TOKEN_PEAK_TIME_RANGES
    }

    fun parseTimeMinutes(raw: String): Int? {
        val parts = raw.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun formatTimeMinutes(minutes: Int): String =
        "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60)
}
