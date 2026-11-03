package com.ai.assistance.operit.core.tools.system.terminal

import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.command.CommandCancellationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 订阅实际建立后才提交命令，收集器退出时按同一个命令 ID 收尾。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun terminalCommandFlow(
    events: SharedFlow<CommandExecutionEvent>,
    sessionId: String,
    commandId: String,
    submit: suspend () -> Unit,
    cancel: suspend () -> CommandCancellationOutcome,
    onCancellationOutcome: (CommandCancellationOutcome) -> Unit = {},
): Flow<CommandExecutionEvent> = channelFlow {
    val ready = CompletableDeferred<Unit>()
    var completed = false
    var dispatchAttempted = false
    val collector = launch {
        events.onSubscription { ready.complete(Unit) }
            .filter { it.sessionId == sessionId && it.commandId == commandId }
            .transformWhile { event -> emit(event); !event.isCompleted }
            .collect { event ->
                if (event.isCompleted) completed = true
                send(event)
            }
    }
    try {
        ready.await()
        currentCoroutineContext().ensureActive()
        dispatchAttempted = true
        submit()
        collector.join()
    } finally {
        collector.cancel()
        if (!completed && dispatchAttempted) withContext(NonCancellable) { onCancellationOutcome(cancel()) }
    }
}
