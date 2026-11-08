package com.ai.assistance.operit.ui.features.extensions.packages.market.transfer

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferFormattingTest {
    @Test
    fun byteUnitsCoverSmallFilesAndLargeArchives() {
        assertEquals("0 B", formatTransferBytes(0.0, Locale.US))
        assertEquals("512 B", formatTransferBytes(512.0, Locale.US))
        assertEquals("1.5 KiB", formatTransferBytes(1_536.0, Locale.US))
        assertEquals("2 GiB", formatTransferBytes(2.0 * 1024 * 1024 * 1024, Locale.US))
    }

    @Test
    fun byteCountsUseLocaleDecimalSeparators() {
        assertEquals("1,5 KiB", formatTransferBytes(1_536.0, Locale.GERMANY))
    }

    @Test
    fun durationsKeepSecondsMinutesAndLongDownloadsReadable() {
        assertEquals("0:00", formatTransferDuration(0L))
        assertEquals("0:59", formatTransferDuration(59L))
        assertEquals("1:00", formatTransferDuration(60L))
        assertEquals("1:01:01", formatTransferDuration(3_661L))
    }
}
