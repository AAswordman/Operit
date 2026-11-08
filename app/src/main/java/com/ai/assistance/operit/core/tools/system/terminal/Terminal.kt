package com.ai.assistance.operit.core.tools.system.terminal

import android.content.Context
import android.os.Build
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.SessionDirectoryEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.data.TerminalState
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.last
import java.util.UUID

/**
 * 终端管理器
 * 提供应用程序级别的终端服务管理和访问
 */
@RequiresApi(Build.VERSION_CODES.O)
class Terminal internal constructor(private val terminalManager: TerminalManager) {

    companion object {
        @Volatile
        private var INSTANCE: Terminal? = null

        fun getInstance(context: Context): Terminal {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Terminal(TerminalManager.getInstance(context.applicationContext)).also { INSTANCE = it }
            }
        }

        private const val TAG = "Terminal"
    }


    // 从 TerminalManager 暴露状态和事件流
    val commandEvents: SharedFlow<CommandExecutionEvent> = terminalManager.commandExecutionEvents
    val directoryEvents: SharedFlow<SessionDirectoryEvent> = terminalManager.directoryChangeEvents
    val terminalState: StateFlow<TerminalState> = terminalManager.terminalState
    val sessions = terminalManager.sessions
    val currentSessionId = terminalManager.currentSessionId
    val currentDirectory = terminalManager.currentDirectory
    val isInteractiveMode = terminalManager.isInteractiveMode
    val interactivePrompt = terminalManager.interactivePrompt
    val isFullscreen = terminalManager.isFullscreen

    /**
     * 初始化终端管理器
     */
    suspend fun initialize(): Boolean {
        return terminalManager.initializeEnvironment()
    }

    /**
     * 销毁终端管理器
     */
    fun destroy() {
        terminalManager.cleanup()
    }

    /**
     * 创建新的终端会话 - 同步等待初始化完成
     */
    suspend fun createSession(title: String? = null, automation: Boolean = false): String {
        AppLogger.d(TAG, "Creating new terminal session and waiting for initialization")
        val newSession = terminalManager.createNewSession(title, automation)
        AppLogger.d(TAG, "Session ${newSession.id} initialized successfully")
        return newSession.id
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String) {
        terminalManager.switchToSession(sessionId)
    }

    /**
     * 关闭终端会话
     */
    fun closeSession(sessionId: String) {
        terminalManager.closeSession(sessionId)
    }

    /**
     * 执行命令并等待其完成（不切换当前会话）
     */
    suspend fun executeCommand(sessionId: String, command: String): String? =
        executeCommandFlow(sessionId, command).last().outputChunk

    suspend fun executeHiddenCommand(
        command: String,
        executorKey: String = "default",
        timeoutMs: Long = 120000L
    ): HiddenExecResult {
        return terminalManager.executeHiddenCommand(
            command = command,
            executorKey = executorKey,
            timeoutMs = timeoutMs
        )
    }

    /**
     * 执行命令 - Flow版本
     * 返回命令执行过程中的所有事件，直到命令完成
     */
    fun executeCommandFlow(
        sessionId: String,
        command: String,
        commandId: String = UUID.randomUUID().toString(),
        onCancellationOutcome: (com.ai.assistance.operit.terminal.command.CommandCancellationOutcome) -> Unit = {},
    ): Flow<CommandExecutionEvent> = terminalCommandFlow(
        events = commandEvents, sessionId = sessionId, commandId = commandId,
        submit = { terminalManager.sendCommandToSession(sessionId, command, commandId) },
        cancel = { terminalManager.cancelCommand(sessionId, commandId) },
        onCancellationOutcome = onCancellationOutcome,
    )

    suspend fun cancelCommand(sessionId: String, commandId: String) =
        terminalManager.cancelCommand(sessionId, commandId)

    /**
     * 发送输入到当前会话
     */
    fun sendInput(sessionId: String, input: String) {
        terminalManager.sendInput(input, sessionId)
    }

    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterruptSignal(sessionId: String) {
        terminalManager.sendInterruptSignal(sessionId)
    }

    /**
     * 检查服务是否已连接 (现在总是返回 true)
     */
    fun isConnected(): Boolean {
        return true
    }
}
