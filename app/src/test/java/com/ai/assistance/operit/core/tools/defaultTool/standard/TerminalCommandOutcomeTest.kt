package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.terminal.CommandExecutionEvent
import org.junit.Assert.*
import org.junit.Test

class TerminalCommandOutcomeTest {
    @Test
    fun normalNonzeroExitRemainsAFailedExecution() {
        val outcome = TerminalCommandOutcome()
        outcome.complete(CommandExecutionEvent("run", "session", "before", true, exitCode = 7))
        assertEquals(7, outcome.exitCode)
        assertEquals("Command exited with code 7", outcome.error(3000))
        assertFalse(outcome.sessionExited)
    }

    @Test
    fun shellExitRetainsItsExitCodeAndTerminationState() {
        val outcome = TerminalCommandOutcome()
        outcome.complete(CommandExecutionEvent("run", "session", "logout", true, exitCode = 3, sessionExited = true))
        assertEquals(3, outcome.exitCode)
        assertEquals("Terminal session exited with code 3", outcome.error(3000))
        assertTrue(outcome.sessionExited)
    }

    @Test
    fun completionWithoutAnExitStatusIsNeverSuccess() {
        val outcome = TerminalCommandOutcome()
        outcome.complete(CommandExecutionEvent("run", "session", "prompt", true))
        assertEquals(-1, outcome.exitCode)
        assertNotNull(outcome.error(3000))
    }

    @Test
    fun anExplicitZeroExitCanSucceedEvenWhenTheShellCloses() {
        val outcome = TerminalCommandOutcome()
        outcome.complete(CommandExecutionEvent("run", "session", "", true, exitCode = 0, sessionExited = true))
        assertNull(outcome.error(3000))
        assertTrue(outcome.sessionExited)
    }

    @Test
    fun timeoutCannotBeOverwrittenByALaterCompletion() {
        val outcome = TerminalCommandOutcome()
        outcome.timeout()
        outcome.complete(CommandExecutionEvent("run", "session", "", true, exitCode = 0))
        assertEquals(-1, outcome.exitCode)
        assertTrue(outcome.timedOut)
        assertEquals("Command timed out after 3000ms", outcome.error(3000))
    }
}
