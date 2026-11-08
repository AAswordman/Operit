package com.ai.assistance.operit.data.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class DownloadTransferProgressTest {
    @Test
    fun knownLengthUsesActualBytesAndRecentSpeedForRemainingTime() {
        var now = 0L
        val tracker = DownloadTransferTracker(10_000L) { now }
        assertNull(tracker.sample(0L, force = true)!!.bytesPerSecond)
        now = 1_000_000_000L
        val first = tracker.sample(2_500L)!!
        assertEquals(0.25f, first.fraction!!, 0.0001f)
        assertEquals(2_500.0, first.bytesPerSecond!!, 0.001)
        assertEquals(3L, first.remainingSeconds!!)
        assertEquals(1_000L, first.elapsedMillis)
        now = 2_000_000_000L
        val second = tracker.sample(7_500L)!!
        assertEquals(5_000.0, second.bytesPerSecond!!, 0.001)
        assertEquals(1L, second.remainingSeconds!!)
    }

    @Test
    fun unknownLengthKeepsSpeedAndByteCountWithoutInventingPercentOrEta() {
        var now = 0L
        val tracker = DownloadTransferTracker(null) { now }
        now = 500_000_000L
        val snapshot = tracker.sample(1_024L)!!
        assertEquals(1_024L, snapshot.downloadedBytes)
        assertEquals(2_048.0, snapshot.bytesPerSecond!!, 0.001)
        assertNull(snapshot.totalBytes)
        assertNull(snapshot.fraction)
        assertNull(snapshot.remainingSeconds)
    }

    @Test
    fun limitsIntermediateReportsButAlwaysAllowsFirstAndFinalSamples() {
        var now = 0L
        val tracker = DownloadTransferTracker(100L) { now }
        assertEquals(0L, tracker.sample(0L, force = true)!!.downloadedBytes)
        now = 50_000_000L
        assertNull(tracker.sample(25L))
        now = 200_000_000L
        assertEquals(50L, tracker.sample(50L)!!.downloadedBytes)
        now = 210_000_000L
        val last = tracker.sample(100L, force = true)!!
        assertEquals(1f, last.fraction!!, 0f)
        assertEquals(0L, last.remainingSeconds!!)
        assertTrue(last.bytesPerSecond!! > 0.0)
    }

    @Test
    fun zeroClockIntervalAndUnknownHeaderSizesDoNotProduceInvalidEstimates() {
        listOf(null, 0L, -1L).forEach { length ->
            val snapshot = DownloadTransferTracker(length) { 0L }.sample(0L, force = true)!!
            assertNull(snapshot.totalBytes)
            assertNull(snapshot.bytesPerSecond)
            assertNull(snapshot.remainingSeconds)
            assertEquals(0L, snapshot.elapsedMillis)
        }
        assertNull(DownloadTransferProgress(1L, 10L, 0.0, 0L).remainingSeconds)
        assertNull(DownloadTransferProgress(1L, 10L, Double.POSITIVE_INFINITY, 0L).remainingSeconds)
    }

    @Test
    fun copyingReportsExactInitialAndFinalByteCounts() {
        val content = ByteArray(3_079) { (it % 127).toByte() }
        val output = ByteArrayOutputStream()
        val snapshots = mutableListOf<DownloadTransferProgress>()
        var now = 0L
        val copied = ByteArrayInputStream(content).copyWithTransferProgress(
            output = output,
            totalBytes = content.size.toLong(),
            onProgress = { snapshots += it },
            bufferSize = 512,
            clockNanos = { now.also { now += 250_000_000L } },
        )
        assertArrayEquals(content, output.toByteArray())
        assertEquals(content.size.toLong(), copied)
        assertEquals(0L, snapshots.first().downloadedBytes)
        assertEquals(copied, snapshots.last().downloadedBytes)
        assertEquals(1f, snapshots.last().fraction!!, 0f)
        assertEquals(0L, snapshots.last().remainingSeconds!!)
    }

    @Test
    fun copyingChunkedStreamDoesNotTreatFinalByteCountAsAdvertisedLength() {
        val snapshots = mutableListOf<DownloadTransferProgress>()
        ByteArrayInputStream(byteArrayOf(1, 2, 3)).copyWithTransferProgress(
            ByteArrayOutputStream(), null, { snapshots += it }, clockNanos = { 0L },
        )
        assertEquals(3L, snapshots.last().downloadedBytes)
        assertTrue(snapshots.all { it.totalBytes == null && it.fraction == null && it.remainingSeconds == null })
    }

    @Test
    fun rejectsTruncatedOrOversizedKnownLengthResponses() {
        listOf(2L, 4L).forEach { expected ->
            try {
                ByteArrayInputStream(byteArrayOf(1, 2, 3)).copyWithTransferProgress(
                    ByteArrayOutputStream(), expected, {}, clockNanos = { 0L },
                )
                fail("响应长度不符时必须失败")
            } catch (error: EOFException) {
                assertTrue(error.message!!.contains("received 3 bytes"))
            }
        }
    }

    @Test
    fun propagatesReadFailureWithoutReportingSuccessfulCompletion() {
        val input = object : InputStream() {
            var reads = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (reads++ > 0) throw IOException("connection lost")
                buffer[offset] = 42
                return 1
            }
        }
        val snapshots = mutableListOf<DownloadTransferProgress>()
        try {
            input.copyWithTransferProgress(ByteArrayOutputStream(), 10L, { snapshots += it }, clockNanos = { 0L })
            fail("读取失败必须传递给安装流程")
        } catch (error: IOException) {
            assertEquals("connection lost", error.message)
        }
        assertTrue(snapshots.none { it.fraction == 1f })
    }
}
