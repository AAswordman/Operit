package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenHitRateFormatTest {
    @Test
    fun formatsRealWorldRate() {
        // 657280 / 685487 = 95.8849...% -> 95.88%
        assertEquals("95.88%", formatCacheHitRate(657_280L, 685_487L))
    }

    @Test
    fun truncatesInsteadOfRounding() {
        // 99.999% 向下截断为 99.99%，不得四舍五入为 100.00%
        assertEquals("99.99%", formatCacheHitRate(999_990L, 1_000_000L))
        assertEquals("99.99%", formatCacheHitRate(999_999L, 1_000_000L))
        // 99.5% 保留为 99.50%，不得进位为 100.00%
        assertEquals("99.50%", formatCacheHitRate(995L, 1_000L))
        // 66.666...% -> 66.66%
        assertEquals("66.66%", formatCacheHitRate(2L, 3L))
    }

    @Test
    fun keepsExactHundredPercent() {
        assertEquals("100.00%", formatCacheHitRate(1_000L, 1_000L))
    }

    @Test
    fun handlesZeroInput() {
        assertEquals("-", formatCacheHitRate(0L, 0L))
        assertEquals("-", formatCacheHitRate(10L, 0L))
    }

    @Test
    fun handlesZeroCache() {
        assertEquals("0.00%", formatCacheHitRate(0L, 100L))
    }
}