package com.ai.assistance.operit.core.chat

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class TurnInputInboxTest {
    @Test fun summarySealPreservesTheReasonWhenTheTurnIsCleanedUp() {
        val inbox = TurnInputInbox()
        inbox.offer(TurnInputInbox.Input("follow-up"))
        inbox.sealForSummary()
        inbox.seal()
        assertTrue(inbox.sealedForSummary)
        assertEquals(listOf("follow-up"), inbox.close().map { it.text })
        assertFalse(TurnInputInbox().apply { seal() }.sealedForSummary)
    }

    @Test fun inputAcceptedAtEofCannotBeLostByFinishing() {
        repeat(100) {
            val inbox = TurnInputInbox()
            val start = CountDownLatch(1)
            var accepted = false
            var finished = false
            val sender = thread { start.await(); accepted = inbox.offer(TurnInputInbox.Input("correction")) }
            val finisher = thread { start.await(); finished = inbox.finishIfEmpty() }
            start.countDown()
            sender.join()
            finisher.join()
            assertNotEquals(accepted, finished)
            assertEquals(if (accepted) listOf("correction") else emptyList<String>(),
                inbox.close().map { it.text })
        }
    }

    @Test fun inputDuringDeliveryWaitsForAnotherModelRequest() {
        val inbox = TurnInputInbox()
        val consumed = mutableListOf<String>()
        inbox.offer(TurnInputInbox.Input("first", consumed = { consumed += "first" }))
        assertEquals(listOf("first"), inbox.drain().map { it.text })
        inbox.offer(TurnInputInbox.Input("second"))
        inbox.acknowledge()
        assertEquals(listOf("first"), consumed)
        assertFalse(inbox.finishIfEmpty())
        assertEquals(listOf("second"), inbox.drain().map { it.text })
    }

    @Test fun interruptedDeliveryAndPendingInputAreReturnedInOrder() {
        val inbox = TurnInputInbox()
        inbox.offer(TurnInputInbox.Input("first"))
        inbox.drain()
        inbox.offer(TurnInputInbox.Input("second"))
        inbox.seal()
        assertFalse(inbox.offer(TurnInputInbox.Input("late")))
        assertEquals(listOf("first", "second"), inbox.close().map { it.text })
        assertTrue(inbox.close().isEmpty())
    }

    @Test fun consumedInputIsNotReturnedOnCancellation() {
        val inbox = TurnInputInbox()
        inbox.offer(TurnInputInbox.Input("first"))
        inbox.drain()
        inbox.acknowledge()
        assertTrue(inbox.close().isEmpty())
        assertFalse(inbox.offer(TurnInputInbox.Input("late")))
    }
}
