package com.ai.assistance.operit.ui.features.tokenstats

import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenStatsActivityRangePolicyTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `daily mode selects the anchor date`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.DAILY,
            anchorDate = LocalDate.of(2026, 8, 22),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 22),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `weekly mode selects the existing Sunday-first calendar week`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.WEEKLY,
            anchorDate = LocalDate.of(2026, 8, 22),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2026, 8, 16),
                LocalDate.of(2026, 8, 22),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `cumulative mode starts at the filtered history start and ends at anchor`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.CUMULATIVE,
            anchorDate = LocalDate.of(2026, 8, 22),
            historyStartDate = LocalDate.of(2026, 8, 1),
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 22),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `cumulative mode has no range before the first recorded date`() {
        assertNull(
            activityRangeForMode(
                mode = TokenActivityViewMode.CUMULATIVE,
                anchorDate = LocalDate.of(2026, 8, 1),
                historyStartDate = LocalDate.of(2026, 8, 2),
                zone = zone,
            )
        )
    }
}
