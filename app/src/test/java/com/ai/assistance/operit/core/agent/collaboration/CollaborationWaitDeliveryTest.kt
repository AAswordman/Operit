package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.core.chat.TurnInputInbox
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CollaborationWaitDeliveryTest {
    @Test(timeout = 5_000) fun eventSubscriptionCannotMissInputArrivingAfterReadinessCheck() = runBlocking {
        val inbox = TurnInputInbox()
        var acknowledged = false
        val result = awaitCollaborationInput(
            timeoutMs = 2_000,
            deliverToCurrentTurn = {},
            pendingInput = { pending(inbox) },
            awaitActivity = {
                inbox.offer(TurnInputInbox.Input("late input", consumed = { acknowledged = true }))
                inbox.awaitInput()
            },
        )
        assertEquals(CollaborationWaitOutcome.STEERED, result)
        assertFalse(acknowledged)
        assertEquals("late input", inbox.drain().single().text)
    }
    private fun pending(inbox: TurnInputInbox) = when (inbox.pendingInputKind()) {
        TurnInputInbox.PendingInputKind.USER -> CollaborationWaitOutcome.STEERED
        TurnInputInbox.PendingInputKind.AGENT -> CollaborationWaitOutcome.MAILBOX
        null -> null
    }

    @Test(timeout = 5_000) fun storageArrivalCannotWakeBeforeBodyReachesExecutionInbox() = runBlocking {
        val inbox = TurnInputInbox()
        val reconciliationStarted = CompletableDeferred<Unit>()
        val allowDelivery = CompletableDeferred<Unit>()
        var acknowledged = 0
        val stored = TurnInputInbox.Input(
            "actual message body", agentPath = "/root/child", consumed = { acknowledged++ },
        )
        val waiter = async {
            awaitCollaborationInput(2_000, {
                reconciliationStarted.complete(Unit)
                allowDelivery.await()
                inbox.offer(stored)
            }, { pending(inbox) })
        }
        reconciliationStarted.await()
        // The stored message already exists, but history reconciliation is still blocked.
        assertFalse(waiter.isCompleted)
        assertFalse(inbox.hasPending())
        allowDelivery.complete(Unit)
        assertEquals(CollaborationWaitOutcome.MAILBOX, waiter.await())
        assertEquals(0, acknowledged)
        assertEquals(listOf(stored), inbox.drain())
        inbox.acknowledge()
        assertEquals(1, acknowledged)
    }

    @Test(timeout = 5_000) fun reconciledOrRejectedMessagesDoNotProduceFalseWakeups() = runBlocking {
        val inbox = TurnInputInbox()
        // Recovery found the stored message was already committed: no input is offered.
        assertEquals(CollaborationWaitOutcome.TIMED_OUT,
            awaitCollaborationInput(20, {}, { pending(inbox) }))
        inbox.seal()
        var acknowledged = false
        val stored = TurnInputInbox.Input("still queued", agentPath = "/root/child",
            consumed = { acknowledged = true })
        assertEquals(CollaborationWaitOutcome.TIMED_OUT,
            awaitCollaborationInput(20, { assertFalse(inbox.offer(stored)) }, { pending(inbox) }))
        assertFalse(acknowledged)
    }

    @Test(timeout = 5_000) fun userInputHasPriorityBeforeAndAfterDeliveryWithoutConsumingEither() = runBlocking {
        for (alreadyPending in listOf(true, false)) {
            val inbox = TurnInputInbox()
            fun offerBoth() {
                inbox.offer(TurnInputInbox.Input("peer body", agentPath = "/root/peer"))
                inbox.offer(TurnInputInbox.Input("user correction"))
            }
            if (alreadyPending) offerBoth()
            var flushed = false
            val result = awaitCollaborationInput(2_000, {
                flushed = true
                offerBoth()
            }, { pending(inbox) })
            assertEquals(CollaborationWaitOutcome.STEERED, result)
            assertEquals(!alreadyPending, flushed)
            assertEquals(listOf("peer body", "user correction"), inbox.drain().map { it.text })
        }
    }

    @Test(timeout = 5_000) fun cancellationPropagatesRatherThanReportingAnArrival() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val waiter = async {
            awaitCollaborationInput(2_000, {
                started.complete(Unit)
                awaitCancellation()
            }, { null })
        }
        started.await()
        waiter.cancelAndJoin()
        assertTrue(waiter.isCancelled)
    }

    @Test(timeout = 5_000) fun anEmptyTreeReturnsIdleInsteadOfWaitingOutTheTimeout() = runBlocking {
        var deliveries = 0
        val startedAt = System.currentTimeMillis()
        val outcome = awaitCollaborationInput(5_000, { deliveries++ }, { null }, { false })
        assertEquals(CollaborationWaitOutcome.IDLE, outcome)
        // The queued-mailbox step still runs once before the wait can give up.
        assertEquals(1, deliveries)
        assertTrue(System.currentTimeMillis() - startedAt < 2_000)
    }

    @Test(timeout = 10_000) fun historyReconciliationIsThrottledWhileInputKeepsPolling() = runBlocking {
        var deliveries = 0
        val outcome = awaitCollaborationInput(1_500, { deliveries++ }, { null }, { true })
        assertEquals(CollaborationWaitOutcome.TIMED_OUT, outcome)
        // Immediate pass plus at most one per second; the unthrottled loop reaches ~15 here.
        assertTrue("deliveries=$deliveries", deliveries in 1..3)
    }
}
