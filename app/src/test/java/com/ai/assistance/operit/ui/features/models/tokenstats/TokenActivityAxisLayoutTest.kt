package com.ai.assistance.operit.ui.features.models.tokenstats

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenActivityAxisLayoutTest {
    @Test
    fun `hourly labels include midnight and the last hour`() {
        val date = LocalDate.of(2026, 10, 20)
        val points = (0..23).map { TokenActivitySeriesPoint(date, date, 0L, hour = it) }

        val labels = tokenActivityAxisLabels(points, TokenActivityAxisMode.HOUR, Locale.ENGLISH)

        assertEquals(24, labels.size)
        assertEquals(listOf("0:00"), labels.first())
        assertEquals(listOf("23:00"), labels.last())
    }

    @Test
    fun `daily labels distinguish days on either side of a month boundary`() {
        val points = listOf(
            TokenActivitySeriesPoint(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30), 0L),
            TokenActivitySeriesPoint(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), 0L),
        )

        assertEquals(
            listOf(listOf("Sep", "30"), listOf("Oct", "1")),
            tokenActivityAxisLabels(points, TokenActivityAxisMode.DAY, Locale.ENGLISH),
        )
    }

    @Test
    fun `weekly labels name the actual beginning and end of each bucket`() {
        val points = listOf(
            TokenActivitySeriesPoint(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 5), 12L),
            TokenActivitySeriesPoint(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8), 5L),
        )

        assertEquals(
            listOf(listOf("Sep 29", "–Oct 5"), listOf("Oct 6", "–Oct 8")),
            tokenActivityAxisLabels(points, TokenActivityAxisMode.WEEK, Locale.ENGLISH),
        )
    }
}
