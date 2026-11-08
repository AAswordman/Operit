package com.ai.assistance.operit.ui.features.management.resources.hub

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ResourceTransferDisplayTest {
    @Test fun readableSizeUsesTheSameDecimalUnitsAtEveryBoundary() {
        val cases = mapOf(0L to "0 B", 1L to "1 B", 999L to "999 B", 1000L to "1.0 KB",
            1500L to "1.5 KB", 999999L to "1.0 MB", 1000000L to "1.0 MB", 1000000000L to "1.0 GB")
        cases.forEach { (bytes, expected) -> assertEquals(expected, resourceSize(bytes, Locale.US)) }
    }

    @Test fun existingCachedBytesDoNotBecomeNetworkSpeed() {
        val meter = DownloadRateMeter()
        assertEquals(0.0, meter.sample(0, 500000), 0.0)
        assertEquals(5000.0, meter.sample(1000, 505000), 0.01)
    }

    @Test fun stalledTransferReachesZeroAndResumes() {
        val meter = DownloadRateMeter()
        meter.sample(0, 0)
        assertEquals(1000.0, meter.sample(1000, 1000), 0.01)
        for (second in 2L..7L) meter.sample(second * 1000, 1000)
        assertEquals(0.0, meter.sample(8000, 1000), 0.0)
        assertTrue(meter.sample(9000, 2000) > 0.0)
    }

    @Test fun newTransferOrCounterRestartDoesNotReuseOldRate() {
        val meter = DownloadRateMeter()
        meter.sample(0, 0)
        meter.sample(1000, 1000)
        meter.reset()
        assertEquals(0.0, meter.sample(2000, 1000000), 0.0)
        assertEquals(0.0, meter.sample(3000, 0), 0.0)
        assertEquals(1000.0, meter.sample(4000, 1000), 0.01)
    }
}
