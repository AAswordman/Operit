package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class SubagentConcurrencyPolicyTest {
    @Test
    fun localNativeProvidersAlwaysRunOneAtATime() {
        assertEquals(1, SubagentConcurrencyPolicy.modelLimit(ApiProviderType.MNN, 0))
        assertEquals(1, SubagentConcurrencyPolicy.modelLimit(ApiProviderType.LLAMA_CPP, 8))
    }

    @Test
    fun remoteProviderUsesConfiguredLimitAndZeroMeansUnlimited() {
        assertEquals(3, SubagentConcurrencyPolicy.modelLimit(ApiProviderType.OPENAI, 3))
        assertEquals(0, SubagentConcurrencyPolicy.modelLimit(ApiProviderType.OPENAI, 0))
    }

    @Test
    fun loweringLimitKeepsOneStableGateUntilOlderHoldersDrain() = runBlocking {
        val gate = AdjustableConcurrencyGate(initialLimit = 2)
        gate.acquire()
        gate.acquire()
        gate.updateLimit(1)

        val waiting = async { gate.acquire() }
        yield()
        assertFalse(waiting.isCompleted)

        gate.release()
        yield()
        assertFalse(waiting.isCompleted)

        gate.release()
        waiting.await()
        gate.release()
    }

    @Test
    fun cancelledWaiterDoesNotConsumeOrLeakAPermit() = runBlocking {
        val gate = AdjustableConcurrencyGate(initialLimit = 1)
        gate.acquire()
        val waiting =
            async(start = CoroutineStart.UNDISPATCHED) {
                gate.acquire()
            }

        waiting.cancelAndJoin()
        gate.release()

        assertTrue(gate.tryAcquire())
        gate.release()
    }

    @Test
    fun queuedWaitersArePromotedInFifoOrder() = runBlocking {
        val gate = AdjustableConcurrencyGate(initialLimit = 1)
        gate.acquire()
        val first = async(start = CoroutineStart.UNDISPATCHED) { gate.acquire() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { gate.acquire() }

        gate.release()
        first.await()
        assertFalse(second.isCompleted)

        gate.release()
        second.await()
        gate.release()
    }
}
