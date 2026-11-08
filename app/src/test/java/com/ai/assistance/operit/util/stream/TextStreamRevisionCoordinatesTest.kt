package com.ai.assistance.operit.util.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class TextStreamRevisionCoordinatesTest {
    @Test fun overlappingRangesAndFutureChunksKeepOffsetsConsistent() {
        val tracker = TextStreamRevisionTracker()
        tracker.savepoint("a", 2)
        tracker.append("abcdefgh")
        tracker.rollback("a", 2, 3)
        tracker.savepoint("b", 4)
        tracker.rollback("b", 4, 3)
        assertEquals("abh", tracker.currentContent().toString())
        assertEquals(2, tracker.visibleOffsetAt(6))
        tracker.savepoint("future", 8)
        tracker.rollback("future", 8, 3)
        tracker.append("ij")
        tracker.append("klm")
        assertEquals("abhlm", tracker.currentContent().toString())
    }

    @Test fun applyingHistoryReplacesExistingDiscardRanges() {
        val tracker = TextStreamRevisionTracker()
        tracker.append("abcdef")
        tracker.savepoint("old", 1)
        tracker.rollback("old", 1, 3)
        assertEquals("aef", tracker.currentContent().toString())
        val history = listOf(TextStreamEvent(TextStreamEventType.SAVEPOINT, "new", 2),
            TextStreamEvent(TextStreamEventType.ROLLBACK, "new", 2, 2))
        assertEquals("abef", tracker.applyEventHistory(history).toString())
        tracker.append("g")
        assertEquals("abefg", tracker.currentContent().toString())
    }
}
