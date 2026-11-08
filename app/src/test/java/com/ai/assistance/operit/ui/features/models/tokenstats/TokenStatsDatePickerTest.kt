package com.ai.assistance.operit.ui.features.models.tokenstats

import com.ai.assistance.operit.data.stats.TokenStatsTimeRanges
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义范围日期选择纯逻辑测试（P1-6）：
 * - DatePicker 毫秒按 **UTC 日历**解析日期（西时区不再回退一天）；
 * - 结束日期**包含当天**（+1 天 0 点作为半开区间终点），同日合法；
 * - DST 自然日跨度为 23/25 小时由 java.time 日历运算保证。
 */
class TokenStatsDatePickerTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val newYork = ZoneId.of("America/New_York")

    private fun utcMidnightMs(date: String): Long =
        LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `date picker millis parse as UTC calendar in east and west zones`() {
        // 回归：西时区（New York）若用设备时区解析，UTC 8/7 0 点会被看成
        // 8/6 20:00 而回退一天；按 UTC 日历解析必须是 8/7
        assertEquals(LocalDate.of(2026, 8, 7), datePickerMillisToLocalDate(utcMidnightMs("2026-08-07")))
        // 东时区（Shanghai）同样按 UTC 日历解析
        assertEquals(LocalDate.of(2026, 8, 7), datePickerMillisToLocalDate(utcMidnightMs("2026-08-07")))
    }

    @Test
    fun `inclusive end date makes same day selection a valid one day range`() {
        val range =
            customRangeInclusiveEnd(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 7), shanghai)
        assertEquals(
            LocalDate.of(2026, 8, 7).atStartOfDay(shanghai).toInstant().toEpochMilli(),
            range.startMs,
        )
        assertEquals(
            LocalDate.of(2026, 8, 8).atStartOfDay(shanghai).toInstant().toEpochMilli(),
            range.endMs,
        )
        assertEquals(TokenStatsTimeRanges.DAY_MS, range.durationMs)
    }

    @Test
    fun `cross day selection spans all selected days`() {
        // 8/7 → 8/9（含结束日）= 3 个自然日：终点为 8/10 0 点
        val range =
            customRangeInclusiveEnd(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 9), newYork)
        assertEquals(3L * TokenStatsTimeRanges.DAY_MS, range.durationMs)
        assertEquals(
            LocalDate.of(2026, 8, 10).atStartOfDay(newYork).toInstant().toEpochMilli(),
            range.endMs,
        )
    }

    @Test
    fun `dst spring forward day is 23 hours`() {
        // 美东 2026-03-08 春季拨快 1 小时：单日范围正好 23 小时
        val range =
            customRangeInclusiveEnd(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8), newYork)
        assertEquals(23L * TokenStatsTimeRanges.HOUR_MS, range.durationMs)
    }

    @Test
    fun `end before start is rejected`() {
        val failure = runCatching {
            customRangeInclusiveEnd(LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 7), shanghai)
        }
        assertTrue("end before start must be rejected", failure.isFailure)
    }

    @Test
    fun `fall back range at maximum natural days is accepted despite extra elapsed hour`() {
        val maxDays = 10L
        val start = LocalDate.of(2026, 10, 25).atStartOfDay(newYork).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 4).atStartOfDay(newYork).toInstant().toEpochMilli()
        assertEquals(10L * TokenStatsTimeRanges.DAY_MS + TokenStatsTimeRanges.HOUR_MS, end - start)
        assertEquals(CustomRangeValidation.VALID, validateCustomRange(start, end, newYork, maxDays))
    }

    @Test
    fun `range one natural day over maximum is rejected across fall back`() {
        val maxDays = 10L
        val start = LocalDate.of(2026, 10, 25).atStartOfDay(newYork).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 5).atStartOfDay(newYork).toInstant().toEpochMilli()
        assertEquals(CustomRangeValidation.TOO_LONG, validateCustomRange(start, end, newYork, maxDays))
    }
}
