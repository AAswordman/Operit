package com.ai.assistance.operit.integrations.http

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for issue #1375.
 *
 * NanoHTTPD hands the chat endpoint a buffered stream whose remaining bytes are exactly the request
 * body. The body length is defined in bytes by `Content-Length`, and HTTP clients are free to split
 * it into any TCP segments. These tests feed the reader with pathological segmentations, including
 * one byte at a time and chunks that cut multi-byte UTF-8 sequences in half.
 */
class ExternalChatRequestBodyReaderTest {

    @Test
    fun readsWholeBodyWhenDeliveredAsOneBlock() {
        val body = """{"message":"TESTLINE-完整"}"""
        assertRead(body, listOf(body.toByteArray(StandardCharsets.UTF_8).size))
    }

    @Test
    fun readsWholeBodyWhenDeliveredOneByteAtATime() {
        val body = """{"message":"TESTLINE-完整","request_id":"abc"}"""
        val byteCount = body.toByteArray(StandardCharsets.UTF_8).size
        assertRead(body, List(byteCount) { 1 })
    }

    @Test
    fun readsWholeBodyWhenChunksSplitMultiByteCharacters() {
        val body = """{"message":"<tool name=\"x\">完整消息</tool>"}"""
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        // A split inside every CJK code point exercises decoding over segment boundaries.
        val segments = mutableListOf<Int>()
        var index = 0
        while (index < bytes.size) {
            val size = if (index % 3 == 0) 1 else 2
            segments += size
            index += size
        }
        assertRead(body, segments)
    }

    @Test
    fun readsBodyLargerThanNanoHttpdHeaderBuffer() {
        val padding = "填".repeat(4_000)
        val body = """{"message":"HEAD-$padding-TAIL"}"""
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        // The default NanoHTTPD header buffer is 8192 bytes; a body above that size must not be
        // silently clipped by the reader.
        assertRead(body, listOf(8_192, 4_096, 1_024, bytes.size))
    }

    @Test
    fun readsNormalAsciiBodyWithoutTrailingBytes() {
        val body = """{"message":"OK"}"""
        assertRead(body, listOf(3, 2, 1, 100))
    }

    @Test
    fun emptyBodyStaysEmpty() {
        val body = ExternalChatRequestBodyReader.read(
            ByteArrayInputStream(ByteArray(0)),
            contentLength = 0,
            charset = StandardCharsets.UTF_8,
        )
        assertEquals("", body)
    }

    private fun assertRead(expected: String, segmentSizes: List<Int>) {
        val bytes = expected.toByteArray(StandardCharsets.UTF_8)
        val stream = SegmentedInputStream(bytes, segmentSizes)
        val actual = ExternalChatRequestBodyReader.read(
            inputStream = stream,
            contentLength = bytes.size.toLong(),
            charset = StandardCharsets.UTF_8,
        )
        assertEquals(expected, actual)
        assertEquals(expected.length, actual.length)
    }

    /** Serves at most one configured segment size per read call, mimicking arbitrary TCP splits. */
    private class SegmentedInputStream(
        private val bytes: ByteArray,
        private val segmentSizes: List<Int>,
    ) : InputStream() {
        private var position = 0
        private var segmentIndex = 0

        override fun read(): Int {
            if (position >= bytes.size) return -1
            return bytes[position++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= bytes.size) return -1
            if (len == 0) return 0
            val requested = segmentSizes.getOrElse(segmentIndex++) { len }
            val count = minOf(requested, len, bytes.size - position)
            System.arraycopy(bytes, position, b, off, count)
            position += count
            return count
        }
    }
}
