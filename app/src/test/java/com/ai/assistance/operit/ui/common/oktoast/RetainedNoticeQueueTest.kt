package com.ai.assistance.operit.ui.common.oktoast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedNoticeQueueTest {
    private data class Entry(val id: Int, val request: Boolean)
    private fun queue() = RetainedNoticeQueue<Entry> { it.request }
    private fun drain(queue: RetainedNoticeQueue<Entry>): List<Int> = buildList {
        while (!queue.isEmpty) add(queue.removeFirst().id)
    }

    @Test
    fun backgroundRequestErrorsSurviveHostLossInOrder() {
        val queue = queue()
        queue.add(Entry(1, true))
        queue.add(Entry(2, false))
        queue.add(Entry(3, true))
        queue.retainForHostChange()
        assertEquals(listOf(1, 3), drain(queue))
    }

    @Test
    fun temporaryNoticesCannotEvictRequestErrors() {
        val queue = queue()
        queue.add(Entry(1, true))
        repeat(20) { queue.add(Entry(it + 2, false)) }
        assertEquals(listOf(1, 17, 18, 19, 20, 21), drain(queue))
    }

    @Test
    fun anInterruptedRequestNoticeIsRestoredBeforeLaterErrors() {
        val queue = queue()
        queue.add(Entry(2, true))
        queue.restore(Entry(1, true))
        assertEquals(listOf(1, 2), drain(queue))
    }

    @Test
    fun repeatedHostChangesDoNotDiscardOrDuplicatePendingRequests() {
        val queue = queue()
        queue.add(Entry(1, true))
        repeat(5) { queue.retainForHostChange() }
        assertEquals(listOf(1), drain(queue))
    }

    @Test
    fun ordinaryNoticesAreNotReplayedAfterReturningToTheApp() {
        val queue = queue()
        queue.add(Entry(1, false))
        queue.restore(Entry(2, false))
        queue.retainForHostChange()
        assertTrue(queue.isEmpty)
    }
    @Test
    fun cancellingQueuedErrorLeavesOtherErrorsInOrder() {
        val queue = queue()
        val cancelled = Entry(2, true)
        queue.add(Entry(1, true))
        queue.add(cancelled)
        queue.add(Entry(3, true))
        queue.remove(cancelled)
        queue.retainForHostChange()
        assertEquals(listOf(1, 3), drain(queue))
    }
    @Test
    fun repeatedCancellationDoesNotRemoveTheNextError() {
        val queue = queue()
        val cancelled = Entry(1, true)
        queue.add(cancelled)
        queue.add(Entry(2, true))
        queue.remove(cancelled)
        queue.remove(cancelled)
        assertEquals(listOf(2), drain(queue))
    }
}
