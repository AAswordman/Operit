package com.ai.assistance.operit.util.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TextStreamRevisionTrackerTest {
    @Test
    fun appendReturnsSharedSmartString() {
        val tracker = TextStreamRevisionTracker("a")

        val first = tracker.append("b")
        val second = tracker.append("c")

        assertSame(first, second)
        assertEquals("abc", second.toString())
    }

    @Test
    fun rollbackTruncatesToSavedLength() {
        val tracker = TextStreamRevisionTracker()
        tracker.append("before")
        tracker.savepoint("retry", 6)
        tracker.append(" discarded")

        val rolledBack = tracker.rollback("retry", contentLength = 6, discardedLength = 10)

        assertEquals("before", rolledBack?.toString())
        assertSame(tracker.currentContent(), rolledBack)
    }

    @Test
    fun rollbackInvalidatesSavepointsInDiscardedSuffix() {
        val tracker = TextStreamRevisionTracker()
        tracker.append("a")
        tracker.savepoint("outer", 1)
        tracker.append("b")
        tracker.savepoint("inner", 2)
        tracker.append("c")

        tracker.rollback("outer", contentLength = 1, discardedLength = 2)

        assertNull(tracker.rollback("inner", contentLength = 2, discardedLength = 0))
    }

    /**
     * Regression for the HTTP/API truncation of issue #1375.
     *
     * The event channel and the text channel are collected by separate coroutines. When the text
     * consumer is scheduled first it has already appended the aborted attempt before it dequeues
     * the SAVEPOINT event. Deriving the savepoint offset from the consumer buffer truncated the
     * message to that stale offset and leaked a prefix such as `<tool_L6Gi` into the final message.
     * The producer-recorded offset is authoritative and must be used verbatim.
     */
    @Test
    fun rollbackUsesProducerOffsetWhenConsumerAlreadyAppendedDiscardedChunks() {
        val tracker = TextStreamRevisionTracker()
        // Text channel ran ahead of the event channel.
        tracker.append("<tool_L6Gi name=\"missing")
        tracker.savepoint("attempt", 0)

        val rolledBack =
            tracker.rollback(
                "attempt",
                contentLength = 0,
                discardedLength = "<tool_L6Gi name=\"missing".length
            )

        assertEquals("", rolledBack?.toString())
        assertEquals("", tracker.currentContent().toString())
    }

    @Test
    fun rollbackKeepsContentThatPrecedesTheProducerOffset() {
        val tracker = TextStreamRevisionTracker()
        tracker.append("kept prefix")
        tracker.savepoint("attempt", 11)
        // A retry can only roll back what it produced; earlier turns stay untouched.
        tracker.append(" discarded attempt")

        val rolledBack =
            tracker.rollback(
                "attempt",
                contentLength = 11,
                discardedLength = " discarded attempt".length
            )

        assertEquals("kept prefix", rolledBack?.toString())
    }

    @Test
    fun rollbackBeforeDiscardedChunksArriveDropsThemAsTheyArrive() {
        val tracker = TextStreamRevisionTracker()
        tracker.savepoint("attempt", 0)

        tracker.rollback("attempt", contentLength = 0, discardedLength = "<tool_L6Gi".length)
        tracker.append("<tool_L6Gi")
        tracker.append("<tool_AB12 name=\"device_info\">\n</tool_AB12>")

        assertEquals("<tool_AB12 name=\"device_info\">\n</tool_AB12>", tracker.currentContent().toString())
    }

    @Test
    fun rollbackAfterRetriedChunksArriveKeepsOnlyTheDiscardedRange() {
        val tracker = TextStreamRevisionTracker()
        tracker.savepoint("attempt", 0)
        // The text channel ran ahead of the event channel: both the discarded attempt and the
        // beginning of the retried answer are already appended.
        tracker.append("<tool_L6Gi<tool_AB12 name=\"device_info\">")

        tracker.rollback("attempt", contentLength = 0, discardedLength = "<tool_L6Gi".length)
        tracker.append("\n</tool_AB12>")

        assertEquals("<tool_AB12 name=\"device_info\">\n</tool_AB12>", tracker.currentContent().toString())
    }
}
