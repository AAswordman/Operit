package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import com.ai.assistance.operit.terminal.view.domain.ansi.TerminalChar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/** 终端命令执行工具 - 非流式输出版本 执行终端命令并一次性收集全部输出后返回 */
class StandardTerminalCommandExecutor(private val context: Context) {

    private val TAG = "TerminalCommandExecutor"

    companion object {
        // 用于将会话名称映射到会话ID
        private val sessionNameToIdMap = ConcurrentHashMap<String, String>()
        private const val COMMAND_CANCEL_SETTLE_TIMEOUT_MS = 3_000L
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
                val existingSession = terminal.terminalState.value.sessions.find { it.title == sessionName }
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
                val newSessionId = terminal.createSession(sessionName)
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
    fun executeCommandInSession(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val command = tool.parameters.find { param -> param.name == "command" }?.value ?: ""
                val sessionId = tool.parameters.find { param -> param.name == "session_id" }?.value

                if (sessionId.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                }

                val timeout =
                        tool.parameters
                                .find { param -> param.name == "timeout_ms" }
                                ?.value
                                ?.toLongOrNull()
                                ?: 1800000L // 30 分钟

                val terminal = Terminal.getInstance(context)

                // 检查会话是否存在
                if (terminal.terminalState.value.sessions.none { it.id == sessionId }) {
                    // 如果会话不存在，也从我们的映射中移除
                    sessionNameToIdMap.entries.removeIf { it.value == sessionId }
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_session_not_exist, sessionId)
                    )
                }

                val outputFlow = terminal.executeCommandFlow(sessionId, command)

                if (outputFlow != null) {
                    val events = mutableListOf<String>()
                    var completionOutput: String? = null
                    var exitCode: Int? = null
                    var hasCompleted = false
                    var didTimeout = false

                    try {
                            withTimeout(timeout) {
                                outputFlow.collect { event ->
                                    if (event.isCompleted) {
                                        completionOutput = event.outputChunk
                                        exitCode = event.exitCode ?: 0
                                        hasCompleted = true
                                    } else if (event.outputChunk.isNotEmpty()) {
                                        events.add(event.outputChunk)
                                    }
                                }
                            }
                    } catch (e: TimeoutCancellationException) {
                        AppLogger.w(TAG, "Command execution timed out after ${timeout}ms")
                        cancelTimedOutCommand(terminal, sessionId)
                        hasCompleted = true
                        exitCode = -1
                        didTimeout = true
                    }

                    val fullOutput = completionOutput?.takeIf { it.isNotEmpty() } ?: events.joinToString("")
                    val finalExitCode = exitCode ?: if (didTimeout) -1 else 0
                    AppLogger.d(TAG, "Command output collected: '$fullOutput', exitCode: $finalExitCode")
                    val errorMessage =
                            when {
                                didTimeout -> "Command execution timed out after ${timeout}ms"
                                !hasCompleted -> context.getString(R.string.terminal_error_command_failed)
                                finalExitCode != 0 ->
                                    if (finalExitCode == -1 && fullOutput.isNotBlank()) {
                                        "Command failed: ${fullOutput.takeLast(1000)}"
                                    } else {
                                        "Command exited with code $finalExitCode"
                                    }
                                else -> null
                            }

                    ToolResult(
                            toolName = tool.name,
                            success = errorMessage == null,
                            result = TerminalCommandResultData(
                                    command = command,
                                    output = fullOutput,
                                    exitCode = finalExitCode,
                                    sessionId = sessionId,
                                    timedOut = didTimeout
                            ),
                            error = errorMessage
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_command_failed)
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "执行终端命令时出错", e)
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_execute_command, e.message ?: "")
                )
            }
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
            var exitCode: Int? = null
            var hasCompleted = false
            var didTimeout = false
            var chunkIndex = 0
            var receivedChars = 0

            try {
                withTimeout(timeout) {
                    outputFlow.collect { event ->
                        if (event.isCompleted) {
                            completionOutput = event.outputChunk
                            exitCode = event.exitCode ?: 0
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
                cancelTimedOutCommand(terminal, sessionId)
                hasCompleted = true
                exitCode = -1
                didTimeout = true
            }

            val fullOutput = completionOutput?.takeIf { it.isNotEmpty() } ?: events.joinToString("")
            val finalExitCode = exitCode ?: if (didTimeout) -1 else 0
            val errorMessage =
                when {
                    didTimeout -> "Command execution timed out after ${timeout}ms"
                    !hasCompleted -> context.getString(R.string.terminal_error_command_failed)
                    finalExitCode != 0 ->
                        if (finalExitCode == -1 && fullOutput.isNotBlank()) {
                            "Command failed: ${fullOutput.takeLast(1000)}"
                        } else {
                            "Command exited with code $finalExitCode"
                        }
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
                            exitCode = finalExitCode,
                            sessionId = sessionId,
                            timedOut = didTimeout
                        ),
                    error = errorMessage
                )
            )
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
                        didTimeout -> null
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

    private suspend fun cancelTimedOutCommand(terminal: Terminal, sessionId: String) {
        terminal.sendInterruptSignal(sessionId)
        val settled =
            withTimeoutOrNull(COMMAND_CANCEL_SETTLE_TIMEOUT_MS) {
                terminal.terminalState.first { state ->
                    val session = state.sessions.find { it.id == sessionId }
                    session?.currentExecutingCommand?.isExecuting != true
                }
            }
        if (settled == null) {
            AppLogger.w(TAG, "Timed-out command cancellation did not settle within ${COMMAND_CANCEL_SETTLE_TIMEOUT_MS}ms")
        }
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
