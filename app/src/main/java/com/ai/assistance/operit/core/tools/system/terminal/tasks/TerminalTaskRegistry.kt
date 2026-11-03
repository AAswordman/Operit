package com.ai.assistance.operit.core.tools.system.terminal.tasks

import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.command.CommandCancellationOutcome
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** 独立持有命令收集器，查询请求结束不会取消命令。 */
internal class TerminalTaskRegistry(
    private val scope: CoroutineScope,
    private val capacity: Int = 32,
    private val outputLimit: Int = 64_000,
) {
    init { require(capacity > 0 && outputLimit > 0) }

    internal enum class Status(val wire: String, val terminal: Boolean) {
        QUEUED("queued", false), RUNNING("running", false), CANCELLING("cancelling", false),
        COMPLETED("completed", true), FAILED("failed", true), CANCELLED("cancelled", true),
        TIMED_OUT("timed_out", true),
    }

    internal data class Snapshot(
        val runId: String,
        val sessionId: String,
        val command: String,
        val status: Status,
        val output: String,
        val outputTruncated: Boolean,
        val exitCode: Int?,
        val sessionExitCode: Int?,
        val terminationReason: String?,
        val screen: String?,
        val cancellationOutcome: CommandCancellationOutcome?,
    )

    private class Run(
        val id: String,
        val sessionId: String,
        val command: String,
    ) {
        val done = CompletableDeferred<Unit>()
        val cancellationMutex = Mutex()
        lateinit var job: Job
        var status = Status.QUEUED
        var output = ""
        var truncated = false
        var exitCode: Int? = null
        var sessionExitCode: Int? = null
        var reason: String? = null
        var screen: String? = null
        var cancellationOutcome: CommandCancellationOutcome? = null
    }

    private val runs = linkedMapOf<String, Run>()

    @Synchronized
    fun start(
        sessionId: String,
        command: String,
        timeoutMs: Long,
        collect: (String, (CommandCancellationOutcome) -> Unit) -> Flow<CommandExecutionEvent>,
    ): String {
        require(timeoutMs >= 3_000L) { "timeout_ms must be at least 3000" }
        check(scope.isActive) { "Terminal task service is closed" }
        while (runs.size >= capacity) {
            val expired = runs.entries.firstOrNull { it.value.done.isCompleted }
                ?: error("Too many active terminal tasks; cancel or wait for an existing task")
            runs.remove(expired.key)
        }
        val id = UUID.randomUUID().toString()
        val run = Run(id, sessionId, command)
        runs[id] = run
        run.job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(timeoutMs) {
                    collect(id) { outcome -> recordCancellation(run, outcome) }
                        .collect { event -> accept(run, event) }
                }
                finishIfActive(run, Status.FAILED, "missing_completion")
            } catch (error: TimeoutCancellationException) {
                // Flow 的 finally 可能已收到取消完成事件，执行期限仍要记为超时。
                finishCancellation(run, Status.TIMED_OUT)
            } catch (error: CancellationException) {
                finishCancellation(run, Status.CANCELLED)
                throw error
            } catch (error: Exception) {
                finishIfActive(run, Status.FAILED, error.message ?: error.javaClass.simpleName)
            } finally {
                run.done.complete(Unit)
            }
        }
        run.job.invokeOnCompletion { cause ->
            // LAZY 任务可能在进入协程体之前就被取消，仍需完成查询等待方。
            if (!run.done.isCompleted) {
                finishIfActive(run, if (cause is CancellationException) Status.CANCELLED else Status.FAILED,
                    if (cause is CancellationException) "cancelled" else "missing_completion")
                run.done.complete(Unit)
            }
        }
        run.job.start()
        return id
    }

    @Synchronized
    private fun recordCancellation(run: Run, outcome: CommandCancellationOutcome) {
        run.cancellationOutcome = outcome
    }

    @Synchronized
    private fun finishCancellation(run: Run, requested: Status) {
        if (run.status == Status.COMPLETED || run.reason == "non_zero_exit") return
        run.status = if (requested == Status.CANCELLED && run.cancellationOutcome in setOf(
            CommandCancellationOutcome.CLEANUP_INCOMPLETE, CommandCancellationOutcome.ALREADY_FINISHED,
        )) Status.FAILED else requested
        run.reason = when (run.cancellationOutcome) {
            CommandCancellationOutcome.CLEANUP_INCOMPLETE -> "cleanup_incomplete"
            CommandCancellationOutcome.ALREADY_FINISHED -> if (requested == Status.TIMED_OUT)
                "execution_timeout" else "missing_completion"
            CommandCancellationOutcome.SESSION_CLOSED -> if (requested == Status.TIMED_OUT)
                "execution_timeout_session_closed" else "cancelled_session_closed"
            else -> if (requested == Status.TIMED_OUT) "execution_timeout" else "cancelled"
        }
    }

    @Synchronized
    private fun accept(run: Run, event: CommandExecutionEvent) {
        if (run.status.terminal) return
        val text = if (event.isCompleted) event.outputChunk else if (event.outputChunk.isEmpty()) run.output
            else if (run.output.isEmpty()) event.outputChunk else run.output + "\n" + event.outputChunk
        run.truncated = run.truncated || text.length > outputLimit
        run.output = text.takeLast(outputLimit)
        event.screen?.let { run.screen = it.takeLast(12_000) }
        if (!event.isCompleted) {
            if (run.status != Status.CANCELLING) run.status = Status.RUNNING
            return
        }
        run.sessionExitCode = event.sessionExitCode
        run.reason = event.terminationReason
        // 会话退出码与命令退出码分别保存，未知状态不能当作命令成功。
        run.exitCode = event.exitCode.takeIf { event.terminationReason != "session_closed" }
        run.status = when (event.terminationReason) {
            "cancelled" -> Status.CANCELLED
            null -> when (event.exitCode) {
                0 -> Status.COMPLETED
                null -> { run.reason = "missing_exit_code"; Status.FAILED }
                else -> { run.reason = "non_zero_exit"; Status.FAILED }
            }
            else -> Status.FAILED
        }
    }

    @Synchronized
    private fun finishIfActive(run: Run, status: Status, reason: String) {
        if (!run.status.terminal) {
            run.status = status
            run.reason = reason
        }
    }

    @Synchronized
    private fun get(id: String): Run = runs[id]
        ?: error("Terminal task not found or expired; do not rerun the command automatically")

    suspend fun poll(id: String, yieldMs: Long): Snapshot {
        require(yieldMs in 0L..30_000L) { "yield_ms must be between 0 and 30000" }
        val run = get(id)
        if (yieldMs > 0L) withTimeoutOrNull(yieldMs) { run.done.await() }
        return snapshot(run)
    }

    suspend fun cancel(id: String): Snapshot = withContext(NonCancellable) {
        val run = get(id)
        run.cancellationMutex.withLock {
            val finished = synchronized(this@TerminalTaskRegistry) {
                if (run.status.terminal) true else { run.status = Status.CANCELLING; false }
            }
            if (!finished) {
                // 停止收集后由 Flow 的 finally 精确取消，覆盖取消早于提交的竞态。
                run.job.cancel()
                withTimeoutOrNull(10_000L) { run.done.await() }
            }
            snapshot(run)
        }
    }

    @Synchronized
    private fun snapshot(run: Run): Snapshot = Snapshot(
        run.id, run.sessionId, run.command, run.status, run.output.takeLast(12_000),
        run.truncated || run.output.length > 12_000, run.exitCode, run.sessionExitCode, run.reason, run.screen, run.cancellationOutcome,
    )
}