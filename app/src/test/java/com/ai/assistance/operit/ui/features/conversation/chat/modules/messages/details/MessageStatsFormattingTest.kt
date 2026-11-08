package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.details

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStatsFormattingTest {
    @Test
    fun fullNumbersKeepAllDigitsWithCommaGrouping() {
        assertEquals("0", formatMessageNumber(0L))
        assertEquals("999", formatMessageNumber(999L))
        assertEquals("1,000", formatMessageNumber(1_000L))
        assertEquals("417,762", formatMessageNumber(417_762L))
        assertEquals("3,001,412", formatMessageNumber(3_001_412L))
        assertEquals("9,223,372,036,854,775,807", formatMessageNumber(Long.MAX_VALUE))
    }

    @Test
    fun compactTotalsRoundAndPromoteAtUnitBoundaries() {
        val cases = listOf(
            0L to "0",
            999L to "999",
            1_000L to "1K",
            1_499L to "1K",
            1_500L to "2K",
            417_762L to "418K",
            999_499L to "999K",
            999_500L to "1M",
            3_001_412L to "3M",
            999_499_999L to "999M",
            999_500_000L to "1B",
            1_000_000_000L to "1B",
            Long.MAX_VALUE to "9223372037B",
        )
        cases.forEach { (value, expected) ->
            assertEquals("value=$value", expected, formatCompactMessageTokens(value))
        }
    }

    @Test
    fun durationAndSpeedKeepTheAgreedPrecision() {
        assertEquals("0 ms", formatMessageDuration(0L))
        assertEquals("999 ms", formatMessageDuration(999L))
        assertEquals("4.00 s", formatMessageDuration(4_000L))
        assertEquals("419.18 s", formatMessageDuration(419_177L))
        assertEquals("1,133.33 s", formatMessageDuration(1_133_333L))
        assertEquals("26.8", formatMessageTokenSpeed(calculateMessageTokenSpeed(11_248L, 419_177L)!!))
    }

    @Test
    fun statsKeepCommaGroupingWhenDeviceLocaleUsesDecimalComma() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("406,514", formatMessageNumber(406_514L))
            assertEquals("419.18 s", formatMessageDuration(419_177L))
            assertEquals("1,234.5", formatMessageTokenSpeed(1_234.5))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
