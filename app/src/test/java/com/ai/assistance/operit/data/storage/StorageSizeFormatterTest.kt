package com.ai.assistance.operit.data.storage

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSizeFormatterTest {
    @Test
    fun formatStorageSizeUsesBinaryUnitBoundaries() {
        assertEquals("0 B", formatStorageSize(0L, Locale.US))
        assertEquals("1023 B", formatStorageSize(1023L, Locale.US))
        assertEquals("1.00 KB", formatStorageSize(1024L, Locale.US))
        assertEquals("10.0 MB", formatStorageSize(10L * 1024L * 1024L, Locale.US))
        assertEquals("2.00 GB", formatStorageSize(2L * 1024L * 1024L * 1024L, Locale.US))
        assertEquals("1.00 TB", formatStorageSize(1024L * 1024L * 1024L * 1024L, Locale.US))
    }

    @Test
    fun formatStorageSizeClampsNegativeValues() {
        assertEquals("0 B", formatStorageSize(-1L, Locale.US))
    }
}
