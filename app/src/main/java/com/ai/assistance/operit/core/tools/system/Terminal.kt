package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.SessionDirectoryEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.data.TerminalState
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * 终端管理器
 * 提供应用程序级别的终端服务管理和访问
 */
@RequiresApi(Build.VERSION_CODES.O)
class Terminal private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: Terminal? = null

        fun getInstance(context: Context): Terminal {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Terminal(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val TAG = "Terminal"

        /**
         * 会话进入"等待交互式输入"状态后的宽限期。
         *
         * PTY 模式判定会有瞬时抖动，所以确认到该状态后再等一小段，仍然处于该状态才认定挂起。
         */
        private const val INTERACTIVE_PROMPT_GRACE_MS = 2_000L

        /** 看门狗轮询间隔。 */
        private const val INTERACTIVE_PROMPT_POLL_MS = 500L
    }

    private val terminalManager = TerminalManager.getInstance(context)

    // 事件收集只做流订阅与字符串拼接，但它是被 runBlocking 边界等待的一方。
    // 放在 Main 上会引入"主线程被阻塞 → 收集器无法推进 → 调用方永远等不到"的隐患，
    // 因此固定到 IO。
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 监视 [sessionId] 是否卡在交互式提示上。
     *
     * 命令一旦停在密码/确认提示上，PTY 不会产生完成事件，调用方只能等满自己的 timeout
     * （`execute_in_terminal_session` 工具默认是 30 分钟）。这里在确认挂起后结束等待，
     * 把"卡死"变成一次带解释的失败返回。
     *
     * @param onWedged 确认挂起时执行，用于结束本次等待
     */
    private fun CoroutineScope.watchInteractivePrompt(
        sessionId: String,
        onWedged: suspend (prompt: String) -> Unit
    ): Job = launch {
        while (isActive) {
            delay(INTERACTIVE_PROMPT_POLL_MS)
            val session = terminalState.value.sessions.find { it.id == sessionId } ?: break
            if (!session.isWaitingForInteractiveInput) continue

            // 二次确认，避免把瞬时状态误判为挂起。
            delay(INTERACTIVE_PROMPT_GRACE_MS)
            val stillWaiting =
                terminalState.value.sessions.find { it.id == sessionId }?.isWaitingForInteractiveInput
                    ?: false
            if (!stillWaiting) continue

            AppLogger.w(
                TAG,
                "会话 $sessionId 停留在交互式提示上（${session.interactivePrompt}），停止等待"
            )
            onWedged(session.interactivePrompt)
            break
        }
    }

    /** 交互式挂起时返回给调用方的说明，指引用户改成非交互式写法。 */
    private fun interactivePromptMessage(prompt: String): String =
        "\n[operit] 命令仍在等待交互式输入（提示: $prompt），已停止等待。" +
            "若这是 sudo 密码提示，请改用 `sudo -n`，或为该用户配置 NOPASSWD。\n"

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
    suspend fun createSession(title: String? = null): String {
        AppLogger.d(TAG, "Creating new terminal session and waiting for initialization")
        val newSession = terminalManager.createNewSession(title)
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
     *
     * 注意：[deferred] 只会由匹配的 [CommandExecutionEvent.isCompleted] 事件完成。如果会话的
     * PTY 已经异常退出、命令未能写入、或输出事件因任何原因不再产生，则永远不会有完成事件。
     * 因此这里必须自带超时，否则调用方（例如 MCP 桥接器的会话部署与启动流程）会永久挂起。
     *
     * @return 命令输出；超时或会话失效时返回 null
     */
    suspend fun executeCommand(
        sessionId: String,
        command: String,
        timeoutMs: Long = 120_000L
    ): String? {
        val deferred = CompletableDeferred<String>()
        val output = StringBuilder()
        var completionOutput: String? = null
        
        // 生成命令ID
        val commandId = java.util.UUID.randomUUID().toString()
        
        val collectorReady = CompletableDeferred<Unit>()
        
        // 先开始订阅事件流，然后再发送命令
        val job = scope.launch {
            commandEvents
                .filter { it.sessionId == sessionId && it.commandId == commandId }
                .onStart { collectorReady.complete(Unit) } // 发出信号，表示已准备好收集
                .collect { event ->
                    if (event.isCompleted) {
                        completionOutput = event.outputChunk
                    } else {
                        output.append(event.outputChunk)
                    }
                    if (event.isCompleted) {
                        deferred.complete(completionOutput?.takeIf { it.isNotEmpty() } ?: output.toString())
                    }
                }
        }

        // 等待收集器准备就绪
        collectorReady.await()

        // 命令卡在交互式提示上时不会有完成事件，这里主动结束等待，避免调用方
        // （MCP 桥接器的部署/启动流程，此时还持着互斥锁）白等整个 timeout。
        var wedgedPrompt: String? = null
        val watchdogJob =
            scope.watchInteractivePrompt(sessionId) { prompt ->
                wedgedPrompt = prompt
                deferred.complete("")
            }

        // 直接向指定会话发送命令，不切换当前会话
        terminalManager.sendCommandToSession(sessionId, command, commandId)

        val result =
            try {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } finally {
                job.cancel()
                watchdogJob.cancel()
            }

        if (wedgedPrompt != null) {
            AppLogger.w(
                TAG,
                "命令在会话 $sessionId 上停在交互式提示（${wedgedPrompt}），已中止等待。命令: $command"
            )
            return null
        }

        if (result == null) {
            AppLogger.w(
                TAG,
                "命令在会话 $sessionId 上等待 ${timeoutMs}ms 后仍未收到完成事件，" +
                    "放弃等待（会话可能已失效或 PTY 已关闭）。命令: $command"
            )
        }

        return result
    }

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
     *
     * 注意：完成事件只由 PTY 输出产生。命令停在交互式提示上（例如等待密码的 sudo）时
     * 永远不会有完成事件，因此这里挂一个看门狗，确认挂起后主动结束该 Flow，
     * 避免调用方等满整个 timeout。
     */
    fun executeCommandFlow(sessionId: String, command: String): Flow<CommandExecutionEvent> {
        return channelFlow {
            val commandId = UUID.randomUUID().toString()
            val collectorReady = CompletableDeferred<Unit>()

            val collectorJob = launch {
                commandEvents
                    .filter { it.sessionId == sessionId && it.commandId == commandId }
                    .onStart { collectorReady.complete(Unit) }
                    .transformWhile { event ->
                        emit(event)
                        !event.isCompleted
                    }
                    .collect { sentEvent ->
                        send(sentEvent)
                    }
            }

            val watchdogJob = watchInteractivePrompt(sessionId) { prompt ->
                send(
                    CommandExecutionEvent(
                        commandId = commandId,
                        sessionId = sessionId,
                        outputChunk = interactivePromptMessage(prompt),
                        isCompleted = false
                    )
                )
                // 结束收集，让 channelFlow 正常关闭；调用方会以"未完成"收尾。
                collectorJob.cancel()
            }

            // 先确保事件收集器就绪，再发送命令，避免快命令输出在订阅前丢失。
            collectorReady.await()
            terminalManager.sendCommandToSession(sessionId, command, commandId)
            collectorJob.join()
            watchdogJob.cancel()
        }
    }
    
    /**
     * 发送输入到当前会话
     */
    fun sendInput(sessionId: String, input: String) {
        terminalManager.switchToSession(sessionId)
        terminalManager.sendInput(input)
    }

    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterruptSignal(sessionId: String) {
        terminalManager.switchToSession(sessionId)
        terminalManager.sendInterruptSignal()
    }

    /**
     * 检查服务是否已连接 (现在总是返回 true)
     */
    fun isConnected(): Boolean {
        return true
    }
}
