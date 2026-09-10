package com.ai.assistance.operit.ui.common.composedsl

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeDslTextInputDispatchQueueTest {

    private class Harness {
        val started = mutableListOf<Pair<String, String>>()
        val settleCallbacks = mutableListOf<() -> Unit>()
        var drainedCount = 0

        val queue = ComposeDslTextInputDispatchQueue(onAllSettled = { drainedCount++ })

        val dispatch: (ComposeDslTextInputDispatchQueue.Entry, () -> Unit) -> Unit =
            { entry, onSettled ->
                started.add(entry.actionId to entry.text)
                settleCallbacks.add(onSettled)
            }

        fun enqueue(actionId: String, text: String) {
            queue.enqueue(actionId, text, dispatch)
        }

        fun settleNext() {
            settleCallbacks.removeAt(0).invoke()
        }
    }

    @Test
    fun `edits dispatch sequentially in keystroke order`() {
        val harness = Harness()
        harness.enqueue("edit", "a")
        harness.enqueue("edit", "ab")
        harness.enqueue("edit", "abc")

        // Only the first edit is in flight; later keystrokes wait for it to settle.
        assertEquals(listOf("edit" to "a"), harness.started)

        harness.settleNext()
        assertEquals(listOf("edit" to "a", "edit" to "ab"), harness.started)

        harness.settleNext()
        assertEquals(listOf("edit" to "a", "edit" to "ab", "edit" to "abc"), harness.started)
        assertEquals(0, harness.drainedCount)

        harness.settleNext()
        assertEquals(1, harness.drainedCount)
    }

    @Test
    fun `late settle after completeAll does not restart dispatching`() {
        val harness = Harness()
        harness.enqueue("edit", "a")
        harness.enqueue("edit", "ab")
        assertTrue(harness.queue.hasPending())

        harness.queue.completeAll()
        assertFalse(harness.queue.hasPending())

        // The in-flight dispatch settles after the queue was cleared (e.g. teardown):
        // it must neither fire the drain callback nor dispatch the cleared entry.
        harness.settleNext()
        assertEquals(listOf("edit" to "a"), harness.started)
        assertEquals(0, harness.drainedCount)
    }

    @Test
    fun `enqueue after completeAll dispatches immediately`() {
        val harness = Harness()
        harness.enqueue("edit", "a")
        harness.queue.completeAll()
        harness.settleNext()

        harness.enqueue("edit", "b")
        assertEquals(listOf("edit" to "a", "edit" to "b"), harness.started)
    }

    @Test
    fun `awaitAll waits for every queued entry`() = runTest {
        val harness = Harness()
        harness.enqueue("edit", "a")
        harness.enqueue("edit", "ab")

        var awaited = false
        val job =
            backgroundScope.launch {
                harness.queue.awaitAll()
                awaited = true
            }
        testScheduler.runCurrent()
        assertFalse(awaited)

        harness.settleNext()
        testScheduler.runCurrent()
        assertFalse(awaited)

        harness.settleNext()
        testScheduler.runCurrent()
        job.join()
        assertTrue(awaited)
    }
}
