package com.ai.assistance.operit.core.tools.system.terminal

import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.command.CommandCancellationOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalCommandFlowTest {
    @Test fun immediateCompletion_isSubscribedBeforeSubmission() = runTest {
        val events = MutableSharedFlow<CommandExecutionEvent>()
        var cancellations = 0
        val result = terminalCommandFlow(events, "session", "command",
            submit = {
                assertEquals(1, events.subscriptionCount.value)
                events.emit(CommandExecutionEvent("other", "session", "ignored", true, 0))
                events.emit(CommandExecutionEvent("command", "session", "done", true, 0))
            },
            cancel = { cancellations++; CommandCancellationOutcome.INTERRUPTED },
        ).toList()
        assertEquals(listOf("done"), result.map { it.outputChunk })
        assertEquals(0, cancellations)
        assertEquals(0, events.subscriptionCount.value)
    }

    @Test fun cancellingCollector_cleansOnlyItsCommandAndUnsubscribes() = runTest {
        val events = MutableSharedFlow<CommandExecutionEvent>()
        val cancelled = mutableListOf<String>()
        var outcome: CommandCancellationOutcome? = null
        val job = async {
            terminalCommandFlow(events, "session", "queued",
                submit = {},
                cancel = { cancelled += "queued"; CommandCancellationOutcome.REMOVED_FROM_QUEUE },
                onCancellationOutcome = { outcome = it },
            ).toList()
        }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(listOf("queued"), cancelled)
        assertEquals(CommandCancellationOutcome.REMOVED_FROM_QUEUE, outcome)
        assertEquals(0, events.subscriptionCount.value)
    }
}
