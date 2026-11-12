package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.core.chat.TurnInputInbox
import org.junit.Assert.*
import org.junit.Test

class CollaborationWaitResultTest {
    @Test fun userInputIsDistinctFromMailboxAndClampingIsReported() {
        val inbox = TurnInputInbox()
        inbox.offer(TurnInputInbox.Input("agent", agentPath = "/root/child"))
        assertTrue(inbox.hasPending())
        assertFalse(inbox.hasPendingUserInput())
        inbox.offer(TurnInputInbox.Input("user"))
        assertTrue(inbox.hasPendingUserInput())
        val result = CollaborationWaitResult(CollaborationWaitOutcome.STEERED, 5)
        assertFalse(result.timedOut)
        assertTrue(result.message.startsWith("Wait interrupted by new input."))
        assertTrue(result.message.contains("clamped to the minimum of 10000ms"))
        assertEquals("Wait completed.", CollaborationWaitResult(CollaborationWaitOutcome.MAILBOX, 30_000).message)
        assertTrue(CollaborationWaitResult(CollaborationWaitOutcome.TIMED_OUT, 30_000).timedOut)
        val idle = CollaborationWaitResult(CollaborationWaitOutcome.IDLE, 30_000)
        assertFalse(idle.timedOut)
        assertTrue(idle.message.contains("no other agent is running"))
        assertTrue(idle.message.contains("call wait_agent again"))
    }

    @Test fun limitsRejectImpossibleCapacityAndWaitSettings() {
        assertTrue(runCatching { CollaborationLimits(maxActive = 65).validate() }.isFailure)
        assertTrue(runCatching { CollaborationLimits(minWaitMs = 40_000).validate() }.isFailure)
        assertEquals(3, CollaborationLimits().validate().maxActive)
    }
}
