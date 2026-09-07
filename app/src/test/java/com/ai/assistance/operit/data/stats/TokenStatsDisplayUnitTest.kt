package com.ai.assistance.operit.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenStatsDisplayUnitTest {

    @Test
    fun `million display keeps compact values below one million`() {
        assertEquals("8.8K", formatTokenCount(8_800L, TokenStatsDisplayUnit.MILLIONS))
        assertEquals("999.9K", formatTokenCount(999_900L, TokenStatsDisplayUnit.MILLIONS))
    }

    @Test
    fun `million display formats large values in millions`() {
        assertEquals("1.0M", formatTokenCount(1_000_000L, TokenStatsDisplayUnit.MILLIONS))
        assertEquals("917.4M", formatTokenCount(917_400_000L, TokenStatsDisplayUnit.MILLIONS))
    }

    @Test
    fun `billion display formats large values in billions`() {
        assertEquals("0.917B", formatTokenCount(917_400_000L, TokenStatsDisplayUnit.BILLIONS))
        assertEquals("1.000B", formatTokenCount(1_000_000_000L, TokenStatsDisplayUnit.BILLIONS))
    }

    @Test
    fun `display unit toggles between millions and billions`() {
        assertEquals(
            TokenStatsDisplayUnit.BILLIONS,
            TokenStatsDisplayUnit.MILLIONS.toggled(),
        )
        assertEquals(
            TokenStatsDisplayUnit.MILLIONS,
            TokenStatsDisplayUnit.BILLIONS.toggled(),
        )
    }
}
