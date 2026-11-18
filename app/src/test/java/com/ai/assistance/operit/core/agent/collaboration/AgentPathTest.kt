package com.ai.assistance.operit.core.agent.collaboration

import org.junit.Assert.*
import org.junit.Test

class AgentPathTest {
    @Test fun relativeTargetsStayBeneathCaller() {
        assertEquals("/root/review/check", AgentPath.resolve("/root/review", "check"))
        assertEquals("/root/build", AgentPath.resolve("/root/review", "/root/build"))
        assertFalse(AgentPath.isWithin("/root/reviewer", "/root/review"))
        assertTrue(AgentPath.isWithin("/root/review/check", "/root/review"))
    }

    @Test fun rejectsTraversalAndEmptySegments() {
        listOf("../other", "/root//other", "/other", "/root/").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                AgentPath.resolve("/root/review", it)
            }
        }
    }

    @Test fun parsesForkModesWithoutSilentlyAcceptingInvalidCounts() {
        assertEquals(AgentFork.All, AgentFork.parse(null))
        assertEquals(AgentFork.None, AgentFork.parse("NONE"))
        assertEquals(AgentFork.LastTurns(3), AgentFork.parse("3"))
        listOf("0", "-1", "1.5", "bogus", "999999999999").forEach {
            assertThrows(IllegalArgumentException::class.java) { AgentFork.parse(it) }
        }
    }
}
