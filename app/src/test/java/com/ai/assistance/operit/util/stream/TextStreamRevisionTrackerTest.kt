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
        tracker.savepoint("retry")
        tracker.append(" discarded")

        val rolledBack = tracker.rollback("retry")

        assertEquals("before", rolledBack?.toString())
        assertSame(tracker.currentContent(), rolledBack)
    }

    @Test
    fun rollbackInvalidatesSavepointsInDiscardedSuffix() {
        val tracker = TextStreamRevisionTracker()
        tracker.append("a")
        tracker.savepoint("outer")
        tracker.append("b")
        tracker.savepoint("inner")
        tracker.append("c")

        tracker.rollback("outer")

        assertNull(tracker.rollback("inner"))
    }
}
