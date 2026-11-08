package com.ai.assistance.operit.data.updates.patch

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PatchByteRangeTest {
    @Test
    fun readsTotalFromSingleByteResponse() {
        val range = requirePatchByteRange(206, "bytes 0-0/77584288", 0L, 0L)
        assertEquals(77584288L, range.total)
        assertEquals(1L, range.length)
    }

    @Test
    fun acceptsExactWorkerRangeAndFinalShortProbe() {
        assertEquals(100L, requirePatchByteRange(206, "bytes 100-199/1000", 100L, 199L, 1000L).length)
        assertEquals(12L, requirePatchByteRange(206, "bytes 0-11/12", 0L, 262143L).length)
    }

    @Test
    fun rejectsSuccessfulHtmlOrNonRangeResponse() {
        rejects { requirePatchByteRange(200, null, 0L, 0L) }
        rejects { requirePatchByteRange(302, null, 0L, 0L) }
        rejects { requirePatchByteRange(206, null, 0L, 0L) }
    }

    @Test
    fun rejectsWrongOffsetsOrChangedTotalBeforeWriting() {
        rejects { requirePatchByteRange(206, "bytes 0-99/1000", 100L, 199L) }
        rejects { requirePatchByteRange(206, "bytes 100-198/1000", 100L, 199L) }
        rejects { requirePatchByteRange(206, "bytes 100-199/2000", 100L, 199L, 1000L) }
    }

    @Test
    fun rejectsUnknownEmptyReversedAndOverflowingRanges() {
        rejects { requirePatchByteRange(206, "bytes 0-0/*", 0L, 0L) }
        rejects { requirePatchByteRange(206, "bytes 0-0/0", 0L, 0L) }
        rejects { requirePatchByteRange(206, "bytes 10-9/20", 10L, 9L) }
        rejects { requirePatchByteRange(206, "bytes 0-0/999999999999999999999", 0L, 0L) }
    }

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("无效分段响应应被拒绝")
        } catch (_: IOException) {
        }
    }
}
