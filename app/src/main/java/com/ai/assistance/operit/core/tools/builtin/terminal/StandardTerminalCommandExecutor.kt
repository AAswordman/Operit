package com.ai.assistance.operit.core.tools.builtin.terminal

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.tools.system.terminal.Terminal
import com.ai.assistance.operit.core.tools.system.terminal.tasks.TerminalTaskRegistry
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import com.ai.assistance.operit.terminal.view.domain.ansi.TerminalChar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/** 终端命令执行工具 - 非流式输出版本 执行终端命令并一次性收集全部输出后返回 */
class StandardTerminalCommandExecutor(private val context: Context) {

    private val TAG = "TerminalCommandExecutor"

    companion object {
        // 用于将会话名称映射到会话ID
        private val sessionNameToIdMap = ConcurrentHashMap<String, String>()
        private val taskRegistry = TerminalTaskRegistry(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }


    /** 创建或获取一个终端会话 */
    fun createOrGetSession(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val sessionName = tool.parameters.find { it.name == "session_name" }?.value
                if (sessionName.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_name)
                    )
                }

                val terminal = Terminal.getInstance(context)

                // 修正：直接检查 Terminal 单例中是否已存在同名会话，而不是依赖本地缓存
                val existingSession = terminal.terminalState.value.sessions.find { it.title == sessionName && !it.commandLifecycle.closed }
                if (existingSession != null) {
                    // 如果存在，更新本地缓存并返回该会话
                    sessionNameToIdMap[sessionName] = existingSession.id
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = TerminalSessionCreationResultData(
                            sessionId = existingSession.id,
                            sessionName = sessionName,
                            isNewSession = false
                        )
                    )
                }

                // 如果 Terminal 中不存在，则创建新会话
                val newSessionId = terminal.createSession(sessionName, automation = true)
                sessionNameToIdMap[sessionName] = newSessionId

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = TerminalSessionCreationResultData(
                        sessionId = newSessionId,
                        sessionName = sessionName,
                        isNewSession = true
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "创建或获取终端会话时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_create_session, e.message ?: "")
                )
            }
        }
    }

    /** 在指定的终端会话中执行命令 */
    fun executeCommandInSession(tool: AITool): ToolResult = runBlocking {
        executeCommandInSessionStream(tool).last()
    }

    fun executeCommandInSessionResult(tool: AITool): Flow<ToolResult> =
        executeCommandInSessionStream(tool).filter { it.result !is TerminalStreamEventData }

    /** 启动、查询与取消托管任务，保留旧 exec 的阻塞返回契约。 */
    suspend fun terminalTask(tool: AITool, action: String): ToolResult {
        var createdId: String? = null
        try {
            fun parameter(name: String): String? = tool.parameters.find { it.name == name }?.value
            val terminal = Terminal.getInstance(context)
            val yieldMs = parameter("yield_ms")?.toLong() ?: 10_000L
            require(yieldMs in 0L..30_000L) { "yield_ms must be between 0 and 30000" }
            val id = when (action) {
                "start" -> {
                    val sessionId = requireNotNull(parameter("session_id")) { "session_id is required" }
                    val command = requireNotNull(parameter("command")) { "command is required" }
                    require(command.isNotBlank()) { "command must not be blank" }
                    val timeoutMs = parameter("timeout_ms")?.toLong() ?: 1_800_000L
                    taskRegistry.start(sessionId, command, timeoutMs,
                        collect = { commandId, onCancellation ->
                            terminal.executeCommandFlow(sessionId, command, commandId, onCancellation)
                        },
                    ).also { createdId = it }
                }
                "poll", "cancel" -> requireNotNull(parameter("run_id")) { "run_id is required" }
                else -> error("Unknown terminal task action: $action")
            }
            val snapshot = if (action == "cancel") taskRegistry.cancel(id) else taskRegistry.poll(id, yieldMs)
            val data = TerminalTaskResultData(
                runId = snapshot.runId, sessionId = snapshot.sessionId, command = snapshot.command,
                status = snapshot.status.wire, output = snapshot.output, outputTruncated = snapshot.outputTruncated,
                exitCode = snapshot.exitCode, sessionExitCode = snapshot.sessionExitCode,
                terminationReason = snapshot.terminationReason, screen = snapshot.screen,
                cancellationOutcome = snapshot.cancellationOutcome?.name?.lowercase(java.util.Locale.ROOT),
                timedOut = snapshot.status == TerminalTaskRegistry.Status.TIMED_OUT, outputMode = "tail_snapshot",
            )
            return ToolResult(tool.name, true, data)
        } catch (cancelled: CancellationException) {
            // 未把新任务 ID 返回给调用方时取消启动，避免留下调用方无法查询的孤立任务。
            createdId?.let { id -> withContext(NonCancellable) { taskRegistry.cancel(id) } }
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e(TAG, "Terminal task action failed: $action", error)
            return ToolResult(tool.name, false, StringResultData(""), error.message)
        }
    }

    /** 在指定的终端会话中执行命令并流式返回输出 */
    fun executeCommandInSessionStream(tool: AITool): Flow<ToolResult> = flow {
        try {
            val command = tool.parameters.find { param -> param.name == "command" }?.value ?: ""
            val sessionId = tool.parameters.find { param -> param.name == "session_id" }?.value

            if (sessionId.isNullOrBlank()) {
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                )
                return@flow
            }

            val timeout =
                tool.parameters
                    .find { param -> param.name == "timeout_ms" }
                    ?.value
                    ?.toLongOrNull()
                    ?: 1800000L

            val terminal = Terminal.getInstance(context)

            if (terminal.terminalState.value.sessions.none { it.id == sessionId }) {
                sessionNameToIdMap.entries.removeIf { it.value == sessionId }
                emit(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_session_not_exist, sessionId)
                    )
                )
                return@flow
            }

            emit(
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                        TerminalStreamEventData(
                            type = "start",
                            command = command,
                            sessionId = sessionId,
                            chunkIndex = 0,
                            receivedChars = 0
                        ),
                    error = ""
                )
            )

            val outputFlow = terminal.executeCommandFlow(sessionId, command)
            val events = mutableListOf<String>()
            var completionOutput: String? = null
            var exitCode = 0
            var hasCompleted = false
            var didTimeout = false
            var chunkIndex = 0
            var receivedChars = 0

            try {
                withTimeout(timeout) {
                    outputFlow.collect { event ->
                        if (event.isCompleted) {
                            completionOutput = event.outputChunk
                            // 完成事件携带真实退出码，普通执行和流式执行统一上报。
                            exitCode = if (event.terminationReason != null) -1 else checkNotNull(event.exitCode) {
                                context.getString(R.string.terminal_error_command_failed)
                            }
                            hasCompleted = true
                            return@collect
                        }

                        val chunk = event.outputChunk
                        if (chunk.isEmpty()) {
                            return@collect
                        }

                        events.add(chunk)
                        receivedChars += chunk.length
                        emit(
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                    TerminalStreamEventData(
                                        type = "chunk",
                                        command = command,
                                        sessionId = sessionId,
                                        chunk = chunk,
                                        chunkIndex = chunkIndex,
                                        receivedChars = receivedChars
                                    ),
                                error = ""
                            )
                        )
                        chunkIndex += 1
                    }
                }
            } catch (e: TimeoutCancellationException) {
                AppLogger.w(TAG, "Command execution timed out after ${timeout}ms")
                hasCompleted = true
                exitCode = -1
                didTimeout = true
            }

            val fullOutput = completionOutput?.takeIf { it.isNotEmpty() } ?: events.joinToString("")
            // 流式执行的最终状态与普通执行一致，非零退出码不能显示成功。
            val errorMessage =
                when {
                    didTimeout -> context.getString(R.string.terminal_error_command_timeout)
                    !hasCompleted -> context.getString(R.string.terminal_error_command_failed)
                    exitCode != 0 -> context.getString(R.string.terminal_error_command_non_zero_exit, exitCode)
                    else -> null
                }

            emit(
                ToolResult(
                    toolName = tool.name,
                    success = errorMessage == null,
                    result =
                        TerminalCommandResultData(
                            command = command,
                            output = fullOutput,
                            exitCode = exitCode,
                            sessionId = sessionId,
                            timedOut = didTimeout,
                            // 取消清理已经返回，按原会话实际存活状态报告，不能由超时标志推断。
                            contextPreserved = terminal.terminalState.value.sessions.any {
                                it.id == sessionId && !it.commandLifecycle.closed
                            }
                        ),
                    error = errorMessage
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "流式执行终端命令时出错", e)
            emit(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_execute_command, e.message ?: "")
                )
            )
        }
    }

    /** 在隐藏终端执行器中执行命令 */
    fun executeHiddenCommand(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                if (command.isBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_command)
                    )
                }

                val executorKey =
                    tool.parameters
                        .find { it.name == "executor_key" }
                        ?.value
                        ?.trim()
                        ?.ifEmpty { "default" }
                        ?: "default"
                val timeoutMs =
                    tool.parameters
                        .find { it.name == "timeout_ms" }
                        ?.value
                        ?.toLongOrNull()
                        ?: 120000L

                val terminal = Terminal.getInstance(context)
                val hiddenResult =
                    terminal.executeHiddenCommand(
                        command = command,
                        executorKey = executorKey,
                        timeoutMs = timeoutMs
                    )
                val output = extractHiddenExecOutput(hiddenResult)
                val didTimeout = hiddenResult.state == HiddenExecResult.State.TIMEOUT
                val errorMessage =
                    when {
                        didTimeout -> context.getString(R.string.terminal_error_command_timeout)
                        !hiddenResult.isOk ->
                            context.getString(
                                R.string.terminal_error_execute_hidden_command,
                                buildHiddenExecFailureDetail(hiddenResult)
                            )
                        else -> null
                    }

                ToolResult(
                    toolName = tool.name,
                    success = errorMessage == null,
                    result =
                        HiddenTerminalCommandResultData(
                            command = command,
                            output = output,
                            exitCode = hiddenResult.exitCode,
                            executorKey = executorKey,
                            timedOut = didTimeout
                        ),
                    error = errorMessage
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "执行隐藏终端命令时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                        context.getString(
                            R.string.terminal_error_execute_hidden_command,
                            e.message ?: ""
                        )
                )
            }
        }
    }

    /** 向指定的终端会话写入输入 */
    fun inputInSession(tool: AITool): ToolResult {
        return runBlocking {
            val sessionId = tool.parameters.find { it.name == "session_id" }?.value
            try {
                if (sessionId.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                }

                val inputParam = tool.parameters.find { it.name == "input" }
                val hasInput = inputParam != null
                val input = inputParam?.value ?: ""
                val control = normalizeControl(tool.parameters.find { it.name == "control" }?.value)

                if (!hasInput && control == null) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_input_or_control)
                    )
                }

                val terminal = Terminal.getInstance(context)

                // 检查会话是否存在
                if (terminal.terminalState.value.sessions.none { it.id == sessionId }) {
                    sessionNameToIdMap.entries.removeIf { it.value == sessionId }
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_session_not_exist, sessionId)
                    )
                }

                val acceptedChars = applyTerminalInput(
                    terminal = terminal,
                    sessionId = sessionId,
                    hasInput = hasInput,
                    input = input,
                    control = control
                )

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(
                        context.getString(
                            R.string.terminal_input_sent,
                            sessionId,
                            acceptedChars
                        )
                    )
                )
            } catch (e: IllegalArgumentException) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = e.message ?: context.getString(R.string.terminal_error_input)
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "向终端会话写入输入时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_input_with_reason, e.message ?: "")
                )
            }
        }
    }

    /** 关闭一个终端会话 */
    fun closeSession(tool: AITool): ToolResult {
        return runBlocking {
            val sessionId = tool.parameters.find { it.name == "session_id" }?.value
            try {
                if (sessionId.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                }

                val terminal = Terminal.getInstance(context)
                terminal.closeSession(sessionId)

                // 从名称映射中移除
                sessionNameToIdMap.entries.removeIf { it.value == sessionId }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = TerminalSessionCloseResultData(
                        sessionId = sessionId,
                        success = true,
                        message = context.getString(R.string.terminal_session_closed, sessionId)
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "关闭终端会话时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_close_session, sessionId, e.message ?: "")
                )
            }
        }
    }

    /** 获取终端会话当前屏幕内容（不包含历史滚动缓冲） */
    fun getSessionScreen(tool: AITool): ToolResult {
        return runBlocking {
            val sessionId = tool.parameters.find { it.name == "session_id" }?.value
            try {
                if (sessionId.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                }

                val terminal = Terminal.getInstance(context)
                val session = terminal.terminalState.value.sessions.find { it.id == sessionId }
                if (session == null) {
                    sessionNameToIdMap.entries.removeIf { it.value == sessionId }
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_session_not_exist, sessionId)
                    )
                }

                val screen = session.ansiParser.getScreenContent()
                val content = renderSingleScreen(screen)
                val rows = screen.size
                val cols = if (rows > 0) screen[0].size else 0

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = TerminalSessionScreenResultData(
                        sessionId = sessionId,
                        rows = rows,
                        cols = cols,
                        content = content
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取终端会话屏幕内容时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_get_screen, e.message ?: "")
                )
            }
        }
    }

    private fun renderSingleScreen(screen: Array<Array<TerminalChar>>): String {
        val lines = screen.map { row ->
            buildString {
                row.forEach { cell -> append(cell.char) }
            }.trimEnd()
        }.toMutableList()

        while (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }

        return lines.joinToString("\n")
    }

    private fun extractHiddenExecOutput(result: HiddenExecResult): String {
        return result.output.ifBlank { result.rawOutputPreview }
    }

    private fun buildHiddenExecFailureDetail(result: HiddenExecResult): String {
        val summary =
            buildString {
                append("state=")
                append(result.state.name)
                val error = result.error.trim()
                if (error.isNotEmpty()) {
                    append(", error=")
                    append(error)
                }
            }
        val preview = result.rawOutputPreview.trim()
        return if (preview.isNotEmpty()) {
            "$summary\n$preview"
        } else {
            summary
        }
    }

    private fun normalizeControl(rawControl: String?): String? {
        val value = rawControl?.trim()?.lowercase()
        if (value.isNullOrEmpty()) return null
        return when (value) {
            "return" -> "enter"
            "escape" -> "esc"
            "arrowup" -> "up"
            "arrowdown" -> "down"
            "arrowleft" -> "left"
            "arrowright" -> "right"
            "pgup", "page_up" -> "pageup"
            "pgdn", "page_down" -> "pagedown"
            "del" -> "delete"
            else -> value
        }
    }

    private fun applyTerminalInput(
        terminal: Terminal,
        sessionId: String,
        hasInput: Boolean,
        input: String,
        control: String?
    ): Int {
        if (control == null) {
            if (hasInput && input.isNotEmpty()) {
                terminal.sendInput(sessionId, input)
            }
            return if (hasInput) input.length else 0
        }

        if (isModifierControl(control)) {
            return applyModifierControl(terminal, sessionId, control, hasInput, input)
        }

        val controlSequence = controlToSequence(control)
            ?: throw IllegalArgumentException(context.getString(R.string.terminal_error_unsupported_control, control))

        if (hasInput && input.isNotEmpty()) {
            terminal.sendInput(sessionId, input)
        }
        terminal.sendInput(sessionId, controlSequence)
        return (if (hasInput) input.length else 0) + controlSequence.length
    }

    private fun isModifierControl(control: String): Boolean {
        return control == "ctrl" || control == "control" || control == "alt" || control == "shift" || control == "meta" || control == "cmd"
    }

    private fun applyModifierControl(
        terminal: Terminal,
        sessionId: String,
        control: String,
        hasInput: Boolean,
        input: String
    ): Int {
        if (!hasInput) {
            throw IllegalArgumentException(context.getString(R.string.terminal_error_control_requires_input, control))
        }

        return when (control) {
            "ctrl", "control" -> applyCtrlCombination(terminal, sessionId, input)
            "alt", "meta", "cmd" -> {
                val payload = "\u001b$input"
                terminal.sendInput(sessionId, payload)
                payload.length
            }
            "shift" -> {
                val payload = input.uppercase()
                terminal.sendInput(sessionId, payload)
                payload.length
            }
            else -> throw IllegalArgumentException(context.getString(R.string.terminal_error_unsupported_control, control))
        }
    }

    private fun applyCtrlCombination(terminal: Terminal, sessionId: String, input: String): Int {
        if (input.length != 1) {
            throw IllegalArgumentException(context.getString(R.string.terminal_error_ctrl_input_single_char))
        }

        val value = input[0]
        if (value.equals('c', ignoreCase = true)) {
            terminal.sendInterruptSignal(sessionId)
            return 1
        }

        val code =
            when (val upper = value.uppercaseChar()) {
                in 'A'..'Z' -> upper.code - 'A'.code + 1
                '@' -> 0
                '[' -> 27
                '\\' -> 28
                ']' -> 29
                '^' -> 30
                '_' -> 31
                '?' -> 127
                else ->
                    throw IllegalArgumentException(
                        context.getString(
                            R.string.terminal_error_ctrl_input_unsupported,
                            input
                        )
                    )
            }

        terminal.sendInput(sessionId, code.toChar().toString())
        return 1
    }

    private fun controlToSequence(control: String): String? {
        return when (control) {
            "enter" -> "\r"
            "tab" -> "\t"
            "esc" -> "\u001b"
            "up" -> "\u001b[A"
            "down" -> "\u001b[B"
            "left" -> "\u001b[D"
            "right" -> "\u001b[C"
            "home" -> "\u001b[H"
            "end" -> "\u001b[F"
            "pageup" -> "\u001b[5~"
            "pagedown" -> "\u001b[6~"
            "backspace" -> "\u007f"
            "delete" -> "\u001b[3~"
            else -> null
        }
    }
}
