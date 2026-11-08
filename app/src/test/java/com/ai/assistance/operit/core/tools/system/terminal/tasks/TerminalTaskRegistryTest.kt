package com.ai.assistance.operit.core.tools.system.terminal.tasks

import com.ai.assistance.operit.core.tools.system.terminal.terminalCommandFlow
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.command.CommandCancellationOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalTaskRegistryTest {
    @Test fun pollDeadlineAndCancelledWaiter_leaveCommandRunning() = runTest {
        val events = MutableSharedFlow<CommandExecutionEvent>()
        val registry = TerminalTaskRegistry(backgroundScope)
        var cancellations = 0
        val id = registry.start("session", "sleep", 30_000L) { commandId, report ->
            terminalCommandFlow(events, "session", commandId, submit = {},
                cancel = { cancellations++; CommandCancellationOutcome.INTERRUPTED }, onCancellationOutcome = report)
        }
        runCurrent()
        val waiting = async { registry.poll(id, 100L) }
        advanceTimeBy(100L)
        runCurrent()
        assertFalse(waiting.await().status.terminal)
        val abandoned = async { registry.poll(id, 1_000L) }
        runCurrent()
        abandoned.cancel()
        abandoned.join()
        assertEquals(0, cancellations)
        events.emit(CommandExecutionEvent(id, "session", "done", true, 0))
        runCurrent()
        assertEquals(TerminalTaskRegistry.Status.COMPLETED, registry.poll(id, 0).status)
    }

    @Test fun executionDeadline_isTimedOutAndRecordsCleanup() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope)
        val events = MutableSharedFlow<CommandExecutionEvent>()
        var cancellations = 0
        val id = registry.start("session", "sleep", 3_000L) { commandId, report ->
            terminalCommandFlow(events, "session", commandId, submit = {},
                cancel = { cancellations++; CommandCancellationOutcome.INTERRUPTED }, onCancellationOutcome = report)
        }
        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()
        val result = registry.poll(id, 0)
        assertEquals(TerminalTaskRegistry.Status.TIMED_OUT, result.status)
        assertEquals("execution_timeout", result.terminationReason)
        assertEquals(CommandCancellationOutcome.INTERRUPTED, result.cancellationOutcome)
        assertEquals(1, cancellations)
    }

    @Test fun explicitCancel_isIdempotentAndDoesNotClaimUnknownCleanupSucceeded() = runTest {
        for ((outcome, reason) in listOf(
            CommandCancellationOutcome.CLEANUP_INCOMPLETE to "cleanup_incomplete",
            CommandCancellationOutcome.ALREADY_FINISHED to "missing_completion",
        )) {
            val registry = TerminalTaskRegistry(backgroundScope)
            val events = MutableSharedFlow<CommandExecutionEvent>()
            var cancellations = 0
            val id = registry.start("session", "sleep", 30_000L) { commandId, report ->
                terminalCommandFlow(events, "session", commandId, submit = {},
                    cancel = { cancellations++; outcome }, onCancellationOutcome = report)
            }
            runCurrent()
            val result = registry.cancel(id)
            assertEquals(TerminalTaskRegistry.Status.FAILED, result.status)
            assertEquals(reason, result.terminationReason)
            registry.cancel(id)
            assertEquals(1, cancellations)
        }
    }

    @Test fun cancelledBeforeDispatch_doesNotSubmitLater() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope)
        var submissions = 0
        val id = registry.start("session", "queued", 30_000L) { _, _ -> flow {
            submissions++
            awaitCancellation()
        } }
        assertEquals(TerminalTaskRegistry.Status.CANCELLED, registry.cancel(id).status)
        runCurrent()
        assertEquals(0, submissions)
    }

    @Test fun completion_preservesNonZeroAndUnknownExitCodes() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope)
        for ((code, reason) in listOf(7 to "non_zero_exit", null to "missing_exit_code")) {
            val id = registry.start("session", "command", 30_000L) { commandId, _ -> flow {
                emit(CommandExecutionEvent(commandId, "session", "output", true, code))
            } }
            runCurrent()
            val result = registry.poll(id, 0)
            assertEquals(TerminalTaskRegistry.Status.FAILED, result.status)
            assertEquals(code, result.exitCode)
            assertEquals(reason, result.terminationReason)
        }
    }

    @Test fun sessionExit_doesNotBecomeSuccessfulCommandExit() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope)
        val id = registry.start("session", "exit", 30_000L) { commandId, _ -> flow {
            emit(CommandExecutionEvent(commandId, "session", "closed", true,
                terminationReason = "session_closed", sessionExitCode = 0))
        } }
        runCurrent()
        val result = registry.poll(id, 0)
        assertEquals(TerminalTaskRegistry.Status.FAILED, result.status)
        assertNull(result.exitCode)
        assertEquals(0, result.sessionExitCode)
    }

    @Test fun screenReplacesSnapshotAndOutputIsBounded() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope, outputLimit = 5)
        val id = registry.start("session", "progress", 30_000L) { commandId, _ -> flow {
            emit(CommandExecutionEvent(commandId, "session", "abcdef", false, screen = "10%"))
            emit(CommandExecutionEvent(commandId, "session", "", false, screen = "90%"))
            awaitCancellation()
        } }
        runCurrent()
        val result = registry.poll(id, 0)
        assertEquals("bcdef", result.output)
        assertEquals("90%", result.screen)
        assertTrue(result.outputTruncated)
        registry.cancel(id)
    }

    @Test fun capacityEvictsOnlyCompletedRecordsAndInvalidIdsFail() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope, capacity = 1)
        val oldId = registry.start("session", "true", 30_000L) { commandId, _ -> flow {
            emit(CommandExecutionEvent(commandId, "session", "", true, 0))
        } }
        runCurrent()
        val activeId = registry.start("session", "sleep", 30_000L) { _, _ -> flow { awaitCancellation() } }
        try {
            registry.poll(oldId, 0)
            fail("已淘汰 ID 必须明确报错")
        } catch (_: IllegalStateException) { }
        try {
            registry.start("session", "extra", 30_000L) { _, _ -> flow { awaitCancellation() } }
            fail("容量满时不能淘汰活动任务")
        } catch (_: IllegalStateException) { }
        registry.cancel(activeId)
    }

    @Test fun missingCompletion_isFailure() = runTest {
        val registry = TerminalTaskRegistry(backgroundScope)
        val id = registry.start("session", "command", 30_000L) { _, _ -> flow { } }
        runCurrent()
        assertEquals("missing_completion", registry.poll(id, 0).terminationReason)
    }
}
