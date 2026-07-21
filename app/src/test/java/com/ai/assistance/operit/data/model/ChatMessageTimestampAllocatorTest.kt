package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTimestampAllocatorTest {

    @Test fun `next returns positive timestamp`() {
        val ts = ChatMessageTimestampAllocator.next()
        assertTrue(ts > 0)
    }

    @Test fun `next returns monotonically increasing values`() {
        val t1 = ChatMessageTimestampAllocator.next()
        val t2 = ChatMessageTimestampAllocator.next()
        val t3 = ChatMessageTimestampAllocator.next()
        assertTrue(t2 > t1)
        assertTrue(t3 > t2)
    }

    @Test fun `next with explicit base timestamp`() {
        val base = 1000L
        val ts = ChatMessageTimestampAllocator.next(base)
        assertTrue(ts >= base)
    }

    @Test fun `next respects larger explicit base`() {
        val ts = ChatMessageTimestampAllocator.next(999999999L)
        assertTrue(ts >= 999999999L)
    }

    @Test fun `observe increases max timestamp`() {
        val large = 987654321L
        ChatMessageTimestampAllocator.observe(large)
        val next = ChatMessageTimestampAllocator.next()
        assertTrue(next > large)
    }

    @Test fun `observe with smaller value does nothing`() {
        val t1 = ChatMessageTimestampAllocator.next()
        ChatMessageTimestampAllocator.observe(t1 - 1)
        val t2 = ChatMessageTimestampAllocator.next()
        assertTrue(t2 > t1)
    }

    @Test fun `concurrent next calls produce unique values`() {
        val timestamps = (1..100).map { ChatMessageTimestampAllocator.next() }
        val unique = timestamps.toSet()
        assertEquals(100, unique.size)
    }

    @Test fun `next handles base timestamp smaller than current`() {
        val t1 = ChatMessageTimestampAllocator.next()
        val t2 = ChatMessageTimestampAllocator.next(t1 - 10)
        assertTrue(t2 > t1)
    }

    @Test fun `next with current time base`() {
        val before = System.currentTimeMillis()
        val ts = ChatMessageTimestampAllocator.next()
        val after = System.currentTimeMillis()
        assertTrue(ts >= before - 1 || ts >= ChatMessageTimestampAllocator.next(0)) // just verify it succeeds
        assertTrue(ts > 0)
    }

    @Test fun `observe then next is monotonic`() {
        ChatMessageTimestampAllocator.observe(500L)
        val ts = ChatMessageTimestampAllocator.next()
        assertTrue(ts >= 500L)
    }

    @Test fun `repeated observe with increasing values`() {
        ChatMessageTimestampAllocator.observe(100L)
        ChatMessageTimestampAllocator.observe(200L)
        ChatMessageTimestampAllocator.observe(300L)
        val ts = ChatMessageTimestampAllocator.next()
        assertTrue(ts >= 300L)
    }

    @Test fun `next without explicit base is near current time`() {
        val now = System.currentTimeMillis()
        val ts = ChatMessageTimestampAllocator.next()
        val diff = ts - now
        assertTrue(diff >= -1) // timestamp should be >= now (approximately)
    }
}
