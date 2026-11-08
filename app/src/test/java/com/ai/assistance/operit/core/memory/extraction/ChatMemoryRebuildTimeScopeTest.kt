package com.ai.assistance.operit.core.memory.extraction

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryRebuildTimeScopeTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `utc midnight millis parse as UTC calendar in east and west zones`() {
        val utcMidnightMs =
            LocalDate.of(2026, 8, 7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(
            LocalDate.of(2026, 8, 7),
            ChatMemoryRebuildTimeScope.utcMidnightMillisToLocalDate(utcMidnightMs)
        )
        assertEquals(
            LocalDate.of(2026, 8, 6),
            java.time.Instant.ofEpochMilli(utcMidnightMs).atZone(newYork).toLocalDate()
        )
        assertEquals(
            LocalDate.of(2026, 8, 7),
            java.time.Instant.ofEpochMilli(utcMidnightMs).atZone(shanghai).toLocalDate()
        )
    }

    @Test
    fun `inclusive dates include the selected end day in local time`() {
        val range =
            ChatMemoryRebuildTimeScope.inclusiveDates(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 2),
                shanghai
            )
        val start = LocalDate.of(2026, 3, 2).atStartOfDay(shanghai).toInstant().toEpochMilli()
        val nextDay = LocalDate.of(2026, 3, 3).atStartOfDay(shanghai).toInstant().toEpochMilli()

        assertEquals(start, range.startInclusiveMs)
        assertEquals(nextDay, range.endExclusiveMs)
        assertTrue(range.contains(start))
        assertTrue(range.contains(nextDay - 1))
        assertFalse(range.contains(start - 1))
        assertFalse(range.contains(nextDay))
    }

    @Test
    fun `inclusive dates span every selected local day`() {
        val range =
            ChatMemoryRebuildTimeScope.inclusiveDates(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 4),
                newYork
            )
        assertEquals(
            LocalDate.of(2026, 3, 2).atStartOfDay(newYork).toInstant().toEpochMilli(),
            range.startInclusiveMs
        )
        assertEquals(
            LocalDate.of(2026, 3, 5).atStartOfDay(newYork).toInstant().toEpochMilli(),
            range.endExclusiveMs
        )
    }

    @Test
    fun `inclusive dates reject end before start`() {
        val failure =
            runCatching {
                ChatMemoryRebuildTimeScope.inclusiveDates(
                    LocalDate.of(2026, 3, 4),
                    LocalDate.of(2026, 3, 2),
                    shanghai
                )
            }
        assertTrue(failure.isFailure)
    }
}
