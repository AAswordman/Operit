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
    fun `weekly mode covers the last seven complete days ending yesterday`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.WEEKLY,
            anchorDate = LocalDate.of(2026, 8, 22),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 21),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `monthly mode mirrors weekly with the same day last month through yesterday`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.MONTHLY,
            anchorDate = LocalDate.of(2026, 3, 31),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 30),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `monthly mode clamps month-end dates to february 29 in a leap year`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.MONTHLY,
            anchorDate = LocalDate.of(2024, 3, 31),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 30),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `monthly mode spans exactly twenty eight days for a february anchor`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.MONTHLY,
            anchorDate = LocalDate.of(2026, 3, 5),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2026, 2, 5),
                LocalDate.of(2026, 3, 4),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `yearly mode mirrors weekly with the same day last year through yesterday`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.YEARLY,
            anchorDate = LocalDate.of(2026, 9, 5),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2025, 9, 5),
                LocalDate.of(2026, 9, 4),
                zone,
            ),
            range,
        )
    }

    @Test
    fun `yearly mode clamps leap day to february 28 in a non-leap year`() {
        val range = activityRangeForMode(
            mode = TokenActivityViewMode.YEARLY,
            anchorDate = LocalDate.of(2024, 2, 29),
            historyStartDate = null,
            zone = zone,
        )

        assertEquals(
            customRangeInclusiveEnd(
                LocalDate.of(2023, 2, 28),
                LocalDate.of(2024, 2, 28),
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
