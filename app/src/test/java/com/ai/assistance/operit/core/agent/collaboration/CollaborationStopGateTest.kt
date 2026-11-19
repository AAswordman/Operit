package com.ai.assistance.operit.core.agent.collaboration

import org.junit.Assert.*
import org.junit.Test

class CollaborationStopGateTest {
    @Test fun stopInvalidatesPreparedWorkEvenAfterItFinishes() {
        val gate = CollaborationStopGate()
        val before = gate.generation("root")
        assertTrue(gate.begin("root"))
        assertThrows(IllegalStateException::class.java) { gate.checkCurrent("root", before) }
        gate.end("root")
        assertThrows(IllegalStateException::class.java) { gate.checkCurrent("root", before) }
        gate.checkCurrent("root", gate.generation("root"))
    }

    @Test fun stoppingOneTreeDoesNotBlockAnotherAndCannotStartTwice() {
        val gate = CollaborationStopGate()
        val other = gate.generation("other")
        assertTrue(gate.begin("root"))
        assertFalse(gate.begin("root"))
        assertThrows(IllegalStateException::class.java) { gate.generation("root") }
        gate.checkCurrent("other", other)
        gate.end("root")
        assertFalse(gate.isStopping("root"))
    }
}
