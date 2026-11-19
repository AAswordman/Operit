package com.ai.assistance.operit.core.chat

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SamplingInterruptTest {
    @Test fun inputAlreadyQueuedSkipsNetworkWithoutConsumingIt() = runBlocking {
        val inbox = TurnInputInbox()
        inbox.offer(TurnInputInbox.Input("change direction"))
        assertTrue(runSteerableSampling(inbox) { error("must not start") })
        assertEquals("change direction", inbox.drain().single().text)
        inbox.acknowledge()
    }

    @Test fun userInputWakesBlockedRequestAndLeavesTurnAlive() = runBlocking {
        val inbox = TurnInputInbox()
        val started = CompletableDeferred<Unit>()
        val aborted = AtomicBoolean()
        val result = async {
            runSteerableSampling(inbox) { control ->
                control!!.bindAbort { aborted.set(true) }
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        inbox.offer(TurnInputInbox.Input("steer"))
        assertTrue(withTimeout(1_000) { result.await() })
        assertTrue(aborted.get())
        assertTrue(isActive)
        assertTrue(inbox.offer(TurnInputInbox.Input("another")))
    }

    @Test fun mailboxWaitsForCompleteItemAndPreservesReplayMetadata() = runBlocking {
        val inbox = TurnInputInbox()
        val started = CompletableDeferred<Unit>()
        val boundary = CompletableDeferred<Unit>()
        var replay = ""
        val result = async {
            runSteerableSampling(inbox, { replay = it }) { control ->
                started.complete(Unit)
                boundary.await()
                control!!.replayMetadata = { "signed completed reasoning" }
                control.checkpoint()
                error("should yield before tools")
            }
        }
        started.await()
        inbox.offer(TurnInputInbox.Input("audit ready", agentPath = "/root/reviewer"))
        yield()
        assertFalse(result.isCompleted)
        boundary.complete(Unit)
        assertTrue(withTimeout(1_000) { result.await() })
        assertEquals("signed completed reasoning", replay)
    }

    @Test fun cancellingParentIsNotSwallowedAsSteering() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val request = async {
            runSteerableSampling(TurnInputInbox()) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        request.cancel()
        try {
            request.await()
            fail("Stop must cancel")
        } catch (_: CancellationException) { }
    }

    @Test fun requestsUseIsolatedAbortHandles() = runBlocking {
        val a = SamplingInterrupt(TurnInputInbox())
        val b = SamplingInterrupt(TurnInputInbox())
        var cancelledA = false
        var cancelledB = false
        a.bindAbort { cancelledA = true }
        b.bindAbort { cancelledB = true }
        a.interrupt()
        assertTrue(cancelledA)
        assertFalse(cancelledB)
    }

    @Test fun cancellationBeforeCallRegistrationStillAbortsCall() {
        val control = SamplingInterrupt(TurnInputInbox())
        control.interrupt()
        var cancelled = false
        try {
            control.bindAbort { cancelled = true }
            fail("late call must not run")
        } catch (_: SamplingInterrupted) { }
        assertTrue(cancelled)
    }
}
