package com.ai.assistance.operit.data.stats

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenPricingRulesTest {

    @Test
    fun `weekday peak interval uses peak pricing`() {
        assertTrue(TokenPricingRules.isPeakTime(at("2026-08-03T10:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
        assertTrue(TokenPricingRules.isPeakTime(at("2026-08-03T14:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
        assertFalse(TokenPricingRules.isPeakTime(at("2026-08-03T12:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
        assertFalse(TokenPricingRules.isPeakTime(at("2026-08-03T13:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
    }

    @Test
    fun `weekends and 2026 holidays use off peak pricing`() {
        assertFalse(TokenPricingRules.isPeakTime(at("2026-08-08T10:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
        assertFalse(TokenPricingRules.isPeakTime(at("2026-10-02T10:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
        assertFalse(TokenPricingRules.isPeakTime(at("2026-02-23T10:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
    }

    @Test
    fun `all configured 2026 holiday boundaries use off peak pricing`() {
        listOf(
            "2026-01-01T10:00", "2026-01-03T10:00",
            "2026-02-15T10:00", "2026-02-23T10:00",
            "2026-04-04T10:00", "2026-04-06T10:00",
            "2026-05-01T10:00", "2026-05-05T10:00",
            "2026-06-19T10:00", "2026-06-21T10:00",
            "2026-09-25T10:00", "2026-09-27T10:00",
            "2026-10-01T10:00", "2026-10-07T10:00",
        ).forEach { value ->
            assertFalse(TokenPricingRules.isPeakTime(at(value), DEFAULT_TOKEN_PEAK_TIME_RANGES))
        }
        assertTrue(TokenPricingRules.isPeakTime(at("2026-01-05T10:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
    }

    @Test
    fun `peak pricing is not inferred for records before 2026`() {
        assertFalse(TokenPricingRules.isPeakTime(at("2025-08-04T10:00"), DEFAULT_TOKEN_PEAK_TIME_RANGES))
    }

    @Test
    fun `weekend off peak can be disabled independently`() {
        assertFalse(
            TokenPricingRules.isPeakTime(
                at("2026-08-08T10:00"),
                DEFAULT_TOKEN_PEAK_TIME_RANGES,
            )
        )
        assertTrue(
            TokenPricingRules.isPeakTime(
                at("2026-08-08T10:00"),
                DEFAULT_TOKEN_PEAK_TIME_RANGES,
                weekendOffPeakPricingEnabled = false,
            )
        )
    }

    @Test
    fun `holiday off peak can be disabled independently`() {
        assertFalse(
            TokenPricingRules.isPeakTime(
                at("2026-10-02T10:00"),
                DEFAULT_TOKEN_PEAK_TIME_RANGES,
            )
        )
        assertTrue(
            TokenPricingRules.isPeakTime(
                at("2026-10-02T10:00"),
                DEFAULT_TOKEN_PEAK_TIME_RANGES,
                holidayOffPeakPricingEnabled = false,
            )
        )
    }

    @Test
    fun `peak time uses the fixed Shanghai billing zone`() {
        val localTime = LocalDateTime.of(2026, 8, 3, 10, 0)
        val occurredAtMs = localTime.atZone(TokenPricingRules.PRICING_ZONE).toInstant().toEpochMilli()

        assertTrue(TokenPricingRules.isPeakTime(occurredAtMs, DEFAULT_TOKEN_PEAK_TIME_RANGES))
    }

    @Test
    fun `peak time ranges round trip through storage`() {
        val source = listOf(
            TokenPeakTimeRange(540, 720),
            TokenPeakTimeRange(840, 1080),
        )
        assertTrue(source == TokenPricingRules.decodePeakTimeRanges(TokenPricingRules.encodePeakTimeRanges(source)))
    }

    private fun at(value: String): Long =
        LocalDateTime.parse(value).atZone(TokenPricingRules.PRICING_ZONE).toInstant().toEpochMilli()
}
