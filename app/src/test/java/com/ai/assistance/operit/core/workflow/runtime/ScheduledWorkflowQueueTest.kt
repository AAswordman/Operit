package com.ai.assistance.operit.core.workflow.runtime

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ScheduledWorkflowQueueTest {
    @Test fun simultaneousSchedulesRunInOrderWithoutFailing() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            ScheduledWorkflowQueue.run("one") { events += "one-start"; release.await(); events += "one-end"; true }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            ScheduledWorkflowQueue.run("two") { events += "two-start"; true }
        }
        assertEquals(listOf("one-start"), events)
        assertTrue("two" in ScheduledWorkflowQueue.waitingWorkflowIds.value)
        release.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf("one-start", "one-end", "two-start"), events)
        assertTrue(ScheduledWorkflowQueue.waitingWorkflowIds.value.isEmpty())
    }
    @Test fun cancelledWaiterIsRemovedAndDoesNotBlockLaterSchedules() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { ScheduledWorkflowQueue.run("one") { release.await() } }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { ScheduledWorkflowQueue.run("two") { fail("取消后的任务不应执行") } }
        second.cancelAndJoin()
        assertFalse("two" in ScheduledWorkflowQueue.waitingWorkflowIds.value)
        release.complete(Unit)
        first.join()
        assertEquals("ok", ScheduledWorkflowQueue.run("three") { "ok" })
    }
    @Test fun failedRunReleasesTheExecutionSlot() = runBlocking {
        try { ScheduledWorkflowQueue.run("one") { error("执行异常") } } catch (_: IllegalStateException) { }
        assertEquals(7, ScheduledWorkflowQueue.run("two") { 7 })
    }
}
