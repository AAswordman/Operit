package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService

/** 基于Shizuku的Shell命令执行器 实现DEBUGGER权限级别的命令执行 */
class DebuggerShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "DebuggerShellExecutor"
        private val serviceCache = ConcurrentHashMap<Int, IShizukuService>()

        /** 添加状态变更监听器 */
        fun addStateChangeListener(listener: () -> Unit) {
            ShizukuAuthorizer.addStateChangeListener(listener)
        }

        /** 移除状态变更监听器 */
        fun removeStateChangeListener(listener: () -> Unit) {
            ShizukuAuthorizer.removeStateChangeListener(listener)
        }

        /** 获取Shizuku启动说明 */
        fun getShizukuStartupInstructions(context: Context): String {
            return ShizukuAuthorizer.getShizukuStartupInstructions(context)
        }
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.DEBUGGER

    override fun isAvailable(): Boolean {
        return ShizukuAuthorizer.isShizukuServiceRunning()
    }

    override fun hasPermission(): ShellExecutor.PermissionStatus {
        val hasPermission = ShizukuAuthorizer.hasShizukuPermission()
        return if (hasPermission) {
            ShellExecutor.PermissionStatus.granted()
        } else {
            ShellExecutor.PermissionStatus.denied(ShizukuAuthorizer.getPermissionErrorMessage())
        }
    }

    override fun initialize() {
        ShizukuAuthorizer.initialize()
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        ShizukuAuthorizer.requestShizukuPermission(onResult)
    }

    /**
     * 检查Shizuku是否已安装
     * @return 是否已安装Shizuku
     */
    fun isShizukuInstalled(): Boolean {
        return ShizukuAuthorizer.isShizukuInstalled(context)
    }

    override suspend fun executeCommand(
        command: String,
        identity: ShellIdentity
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ShellExecutor.CommandResult(false, "", permStatus.reason)
                }

                // 使用更精确的方法检测shell操作符
                if (containsShellOperators(command)) {
                    AppLogger.d(TAG, "Executing command via shell: $command")
                    return@withContext executeWithShell(command)
                }

                AppLogger.d(TAG, "Executing command: $command")

                // 普通命令执行
                return@withContext executeCommandDirect(command)
            }

    /**
     * 检测命令是否包含需要shell解释的特殊操作符
     * @param command 要检查的命令
     * @return 是否包含shell操作符
     */
    private fun containsShellOperators(command: String): Boolean {
        // 预处理：标记引号内的内容，避免检测引号内的操作符
        var inSingleQuotes = false
        var inDoubleQuotes = false
        var escaped = false
        var i = 0

        while (i < command.length) {
            val c = command[i]

            // 处理转义字符
            if (c == '\\' && !escaped) {
                escaped = true
                i++
                continue
            }

            // 处理引号
            if (c == '\'' && !escaped && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
            } else if (c == '"' && !escaped && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
            }
            // 只在不在引号内时检测操作符
            else if (!inSingleQuotes && !inDoubleQuotes && !escaped) {
                // 检测管道
                if (c == '|') {
                    // 检查是不是 || 操作符
                    if (i + 1 < command.length && command[i + 1] == '|') {
                        return true
                    }
                    // 单个 | 管道符
                    return true
                }

                // 检测 && 操作符
                if (c == '&') {
                    // 检查是不是 && 操作符
                    if (i + 1 < command.length && command[i + 1] == '&') {
                        return true
                    }
                    // 后台运行符号 &
                    return true
                }

                // 检测重定向
                if (c == '>' || c == '<') {
                    return true
                }

                // 检测分号
                if (c == ';') {
                    return true
                }
            }

            escaped = false
            i++
        }

        return false
    }

    /** 直接执行不包含特殊操作符的普通命令 */
    private suspend fun executeCommandDirect(command: String): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    val service =
                            getShizukuService()
                                    ?: return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            "Shizuku service not available"
                                    )

                    // 直接执行时仍然通过 AIDL 的远程进程对象管理 stdin/stdout/stderr。
                    val process = service.newProcess(parseCommand(command), null, null)
                            ?: return@withContext ShellExecutor.CommandResult(
                                    false,
                                    "",
                                    "Failed to create process"
                            )

                    executeRemoteProcess(
                            process = process,
                            command = command,
                            allowGrepExitCode = false,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RemoteException) {
                    AppLogger.e(TAG, "Remote exception while executing command", e)
                    ShellExecutor.CommandResult(
                            false,
                            "",
                            "Remote exception: ${e.message}"
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing command", e)
                    ShellExecutor.CommandResult(false, "", "Error: ${e.message}")
                }
            }

    /** 通过shell解释器执行包含特殊操作符的命令 */
    private suspend fun executeWithShell(command: String): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    val service =
                            getShizukuService()
                                    ?: return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            "Shizuku service not available"
                                    )

                    // 检测是否包含重定向操作符进行写入操作
                    val containsRedirection = command.contains(">")

                    // 处理命令，确保使用完整路径
                    val processedCommand =
                            if (command.contains("|") && command.contains("grep")) {
                                // 替换 'grep' 为 '/system/bin/grep'，确保使用系统grep命令
                                command.replace(" grep ", " /system/bin/grep ")
                            } else {
                                command
                            }

                    // 构建增强的shell环境和命令
                    val enhancedCommand =
                            if (containsRedirection) {
                                // 为重定向操作添加更多环境支持
                                "umask 0022 && PATH=\$PATH:/system/bin:/system/xbin:/vendor/bin:/vendor/xbin && $processedCommand"
                            } else {
                                processedCommand
                            }

                    val isBackground = hasTrailingBackgroundOperator(enhancedCommand)
                    // 后台命令不会把输出交给调用方；显式断开 stdin/stdout/stderr，避免
                    // 后台子进程继承 Shizuku 的管道后让调用方永远等不到 EOF。
                    val commandForExecution =
                            if (isBackground) {
                                detachBackgroundShellCommand(enhancedCommand)
                            } else {
                                enhancedCommand
                            }
                    val shellArgs = arrayOf("sh", "-e", "-c", commandForExecution)

                    val process =
                            service.newProcess(shellArgs, null, null)
                                    ?: return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            "Failed to create process"
                                    )

                    executeRemoteProcess(
                            process = process,
                            command = command,
                            allowGrepExitCode = command.contains("grep"),
                            detach = isBackground,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RemoteException) {
                    AppLogger.e(TAG, "Remote exception while executing shell command", e)
                    ShellExecutor.CommandResult(
                            false,
                            "",
                            "Remote exception: ${e.message}"
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing shell command", e)
                    ShellExecutor.CommandResult(false, "", "Error: ${e.message}")
                }
            }

    /**
     * 判断命令末尾是否有未转义、未处于引号中的单个后台操作符。
     */
    private fun hasTrailingBackgroundOperator(command: String): Boolean {
        var inSingleQuotes = false
        var inDoubleQuotes = false
        var escaped = false
        var lastSignificantIndex = -1

        command.forEachIndexed { index, character ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            if (character == '\\') {
                escaped = true
                return@forEachIndexed
            }
            if (character == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
                return@forEachIndexed
            }
            if (character == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
                return@forEachIndexed
            }
            if (!inSingleQuotes && !inDoubleQuotes && !character.isWhitespace()) {
                lastSignificantIndex = index
            }
        }

        if (lastSignificantIndex < 0 || command[lastSignificantIndex] != '&') return false
        return lastSignificantIndex == 0 || command[lastSignificantIndex - 1] != '&'
    }

    /** 将 fire-and-forget 命令与调用方的管道彻底解耦。 */
    private fun detachBackgroundShellCommand(command: String): String {
        val trimmed = command.trimEnd()
        val body = trimmed.dropLast(1).trimEnd().ifBlank { ":" }
        return "$body </dev/null >/dev/null 2>&1 &"
    }

    /** 获取Shizuku服务 */
    private fun getShizukuService(): IShizukuService? {
        try {
            val connection = ShizukuAuthorizer.getOrResolveShizukuConnection() ?: return null

            // 检查缓存的服务是否可用
            val cached = serviceCache[connection.uid]
            if (cached != null) {
                val isCachedAlive =
                        try {
                            cached.asBinder().pingBinder()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error pinging cached binder", e)
                            false
                        }

                if (isCachedAlive) {
                    return cached
                } else {
                    AppLogger.d(TAG, "Cached Shizuku service is dead, removing from cache")
                    serviceCache.remove(connection.uid)
                }
            }

            val service = IShizukuService.Stub.asInterface(connection.binder)
            if (service == null) {
                AppLogger.d(TAG, "Failed to create Shizuku service interface")
                return null
            }

            AppLogger.d(TAG, "Creating new Shizuku service interface")
            serviceCache[connection.uid] = service
            return service
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting Shizuku service", e)
            return null
        }
    }

    /**
     * 智能解析命令行，正确处理引号
     * @param command 完整命令行
     * @return 解析后的参数数组
     */
    private fun parseCommand(command: String): Array<String> {
        val result = mutableListOf<String>()
        val currentArg = StringBuilder()
        var i = 0
        var inSingleQuotes = false
        var inDoubleQuotes = false

        while (i < command.length) {
            val c = command[i]

            // 处理转义字符
            if (i < command.length - 1 && c == '\\') {
                val nextChar = command[i + 1]
                if (nextChar == '\'' || nextChar == '"') {
                    // 处理转义的引号
                    currentArg.append(nextChar)
                    i += 2
                    continue
                }
            }

            // 处理单引号 (只有当不在双引号中时才处理单引号的开始和结束)
            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
                i++
                continue
            }

            // 处理双引号 (只有当不在单引号中时才处理双引号的开始和结束)
            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
                i++
                continue
            }

            // 处理空格 (只有当不在任何引号中时才分割参数)
            if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (currentArg.isNotEmpty()) {
                    result.add(currentArg.toString())
                    currentArg.clear()
                }
                i++
                continue
            }

            // 正常字符
            currentArg.append(c)
            i++
        }

        // 添加最后一个参数
        if (currentArg.isNotEmpty()) {
            result.add(currentArg.toString())
        }

        // 检查未闭合的引号
        if (inSingleQuotes || inDoubleQuotes) {
            AppLogger.w(TAG, "Warning: Unclosed quotes in command: $command")
        }

        return result.toTypedArray()
    }

    override suspend fun startProcess(command: String): ShellProcess {
        if (!hasPermission().granted) {
            throw SecurityException("Shizuku permission not granted.")
        }
        val service = getShizukuService() ?: throw IOException("Shizuku service not available")
        return ShizukuShellProcess(service, command)
    }
}

/**
 * 通过 AIDL 管理一次性 Shizuku 远程进程的输出和生命周期。
 *
 * stdout 与 stderr 必须同时消费，否则任一管道写满都会阻塞远程进程；stdin
 * 也必须主动关闭，否则需要 EOF 的命令会一直等待输入。
 */
private const val DEBUGGER_SHELL_TAG = "DebuggerShellExecutor"
private const val REMOTE_PROCESS_POLL_INTERVAL_MS = 100L
private const val POST_EXIT_OUTPUT_DRAIN_GRACE_MS = 1_000L
private const val OUTPUT_TRUNCATION_NOTICE =
        "Remote process output was truncated after the process exited."

private suspend fun executeRemoteProcess(
        process: IRemoteProcess,
        command: String,
        allowGrepExitCode: Boolean,
        detach: Boolean = false,
): ShellExecutor.CommandResult = withContext(Dispatchers.IO) {
    var stdoutDescriptor: ParcelFileDescriptor? = null
    var stderrDescriptor: ParcelFileDescriptor? = null
    var processFinished = false

    try {
        // One-shot commands have no input API at this layer. EOF prevents interactive
        // commands and child scripts from waiting on an input pipe forever.
        closeRemoteStdin(process)
        stdoutDescriptor = process.getInputStream()
        stderrDescriptor = process.getErrorStream()

        if (detach) {
            closeDescriptor(stdoutDescriptor, "detached stdout")
            closeDescriptor(stderrDescriptor, "detached stderr")
            stdoutDescriptor = null
            stderrDescriptor = null
            // The shell command was deliberately detached; do not destroy it in cleanup.
            processFinished = true
            return@withContext ShellExecutor.CommandResult(true, "", "", 0)
        }

        val result = coroutineScope {
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()
            val stdoutJob = async(Dispatchers.IO) {
                drainDescriptor(stdoutDescriptor, stdoutBuilder, "stdout")
            }
            val stderrJob = async(Dispatchers.IO) {
                drainDescriptor(stderrDescriptor, stderrBuilder, "stderr")
            }

            try {
                // Polling keeps cancellation responsive and avoids an uninterruptible Binder
                // wait. The two drain jobs continue concurrently while the process runs.
                val exitCode = awaitRemoteProcessExit(process)
                processFinished = true

                val outputDrained =
                        withTimeoutOrNull(POST_EXIT_OUTPUT_DRAIN_GRACE_MS) {
                            stdoutJob.await()
                            stderrJob.await()
                            true
                        } ?: false

                if (!outputDrained) {
                    // A descendant may have inherited one of the pipe write ends. Close the
                    // descriptors so the reader jobs cannot hold the tool call forever.
                    closeDescriptor(stdoutDescriptor, "stdout after process exit")
                    closeDescriptor(stderrDescriptor, "stderr after process exit")
                    stdoutJob.cancelAndJoin()
                    stderrJob.cancelAndJoin()
                }

                val stderr = buildString {
                    append(stderrBuilder)
                    if (!outputDrained) {
                        if (isNotEmpty()) append('\n')
                        append(OUTPUT_TRUNCATION_NOTICE)
                    }
                }
                val success =
                        if (allowGrepExitCode) {
                            exitCode == 0 || exitCode == 1
                        } else {
                            exitCode == 0
                        }
                ShellExecutor.CommandResult(
                        success = success,
                        stdout = stdoutBuilder.toString(),
                        stderr = stderr,
                        exitCode = exitCode,
                )
            } finally {
                withContext(NonCancellable) {
                    if (!processFinished) {
                        destroyRemoteProcess(process)
                    }
                    closeDescriptor(stdoutDescriptor, "stdout cleanup")
                    closeDescriptor(stderrDescriptor, "stderr cleanup")
                    stdoutJob.cancel()
                    stderrJob.cancel()
                    stdoutJob.join()
                    stderrJob.join()
                }
            }
        }
        return@withContext result
    } catch (e: CancellationException) {
        // The finally block above destroys the remote process and closes both pipes before
        // cancellation is allowed to leave this function.
        throw e
    } catch (e: RemoteException) {
        AppLogger.e(DEBUGGER_SHELL_TAG, "Remote process operation failed: $command", e)
        ShellExecutor.CommandResult(false, "", "Remote exception: ${e.message}")
    } catch (e: Exception) {
        AppLogger.e(DEBUGGER_SHELL_TAG, "Remote process execution failed: $command", e)
        ShellExecutor.CommandResult(false, "", "Error: ${e.message}")
    } finally {
        // Covers failures before the structured reader scope was created.
        withContext(NonCancellable) {
            if (!processFinished && !detach) {
                destroyRemoteProcess(process)
            }
            closeDescriptor(stdoutDescriptor, "stdout outer cleanup")
            closeDescriptor(stderrDescriptor, "stderr outer cleanup")
        }
    }
}

private suspend fun awaitRemoteProcessExit(process: IRemoteProcess): Int {
    while (true) {
        currentCoroutineContext().ensureActive()
        if (!process.alive()) {
            return process.exitValue()
        }
        delay(REMOTE_PROCESS_POLL_INTERVAL_MS)
    }
}

private fun closeRemoteStdin(process: IRemoteProcess) {
    try {
        process.getOutputStream()?.close()
    } catch (e: Exception) {
        // A command may still be usable when the optional stdin descriptor is unavailable.
        AppLogger.v(DEBUGGER_SHELL_TAG, "Unable to close remote stdin", e)
    }
}

private fun destroyRemoteProcess(process: IRemoteProcess?) {
    if (process == null) return
    try {
        process.destroy()
    } catch (e: Exception) {
        AppLogger.v(DEBUGGER_SHELL_TAG, "Unable to destroy remote process", e)
    }
}

private fun closeDescriptor(descriptor: ParcelFileDescriptor?, label: String) {
    if (descriptor == null) return
    try {
        descriptor.close()
    } catch (e: Exception) {
        AppLogger.v(DEBUGGER_SHELL_TAG, "Unable to close $label", e)
    }
}

private fun drainDescriptor(
        descriptor: ParcelFileDescriptor?,
        output: StringBuilder,
        label: String,
) {
    if (descriptor == null) return
    try {
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            InputStreamReader(input).use { reader ->
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (count > 0) output.append(buffer, 0, count)
                }
            }
        }
    } catch (e: IOException) {
        // Closing a descriptor during cancellation or post-exit cleanup is expected.
        AppLogger.v(DEBUGGER_SHELL_TAG, "Reading remote $label stopped", e)
    } catch (e: Exception) {
        AppLogger.w(DEBUGGER_SHELL_TAG, "Reading remote $label failed", e)
    }
}

/**
 * 使用 Shizuku 实现的 ShellProcess。
 *
 * 两个输出读取器在进程创建后立即启动，即使调用方只订阅 stdout，也不会因为 stderr
 * 管道无人消费而把远程命令卡住。
 */
private class ShizukuShellProcess(
        private val service: IShizukuService,
        private val command: String,
) : ShellProcess {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val destroyed = AtomicBoolean(false)
    private val stdoutChannel =
            Channel<String>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val stderrChannel =
            Channel<String>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val completion = CompletableDeferred<Int>()

    private lateinit var process: IRemoteProcess
    private lateinit var stdoutDescriptor: ParcelFileDescriptor
    private lateinit var stderrDescriptor: ParcelFileDescriptor
    private lateinit var stdoutReader: kotlinx.coroutines.Job
    private lateinit var stderrReader: kotlinx.coroutines.Job
    private lateinit var completionJob: kotlinx.coroutines.Job

    init {
        var createdProcess: IRemoteProcess? = null
        var createdStdout: ParcelFileDescriptor? = null
        try {
            createdProcess =
                    service.newProcess(arrayOf("sh", "-c", command), null, null)
                            ?: throw IOException("Failed to create Shizuku process")
            closeRemoteStdin(createdProcess)
            createdStdout = createdProcess.getInputStream()
                    ?: throw IOException("Shizuku stdout is unavailable")
            val createdStderr =
                    createdProcess.getErrorStream()
                            ?: throw IOException("Shizuku stderr is unavailable")

            process = createdProcess
            stdoutDescriptor = createdStdout
            stderrDescriptor = createdStderr

            // Start both readers eagerly. This is essential for commands that write diagnostics
            // to stderr while the caller only consumes stdout.
            stdoutReader = scope.launch { drainLines(stdoutDescriptor, stdoutChannel, "stdout") }
            stderrReader = scope.launch { drainLines(stderrDescriptor, stderrChannel, "stderr") }
            completionJob = scope.launch { monitorCompletion() }
        } catch (e: Throwable) {
            closeDescriptor(createdStdout, "constructor stdout")
            destroyRemoteProcess(createdProcess)
            scope.cancel()
            throw e
        }
    }

    override val stdout: Flow<String> = stdoutChannel.receiveAsFlow()
    override val stderr: Flow<String> = stderrChannel.receiveAsFlow()

    override val isAlive: Boolean
        get() {
            if (destroyed.get()) return false
            return try {
                process.alive()
            } catch (e: Exception) {
                false
            }
        }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return

        // Destroy first, then close both descriptors so Binder/read calls unblock promptly.
        destroyRemoteProcess(process)
        closeDescriptor(stdoutDescriptor, "stdout destroy")
        closeDescriptor(stderrDescriptor, "stderr destroy")
        stdoutChannel.close()
        stderrChannel.close()
        completion.complete(-1)
        scope.cancel()
    }

    override suspend fun waitFor(): Int = completion.await()

    private suspend fun monitorCompletion() {
        var processFinished = false
        try {
            val exitCode = awaitRemoteProcessExit(process)
            processFinished = true
            completion.complete(exitCode)

            val outputDrained =
                    withTimeoutOrNull(POST_EXIT_OUTPUT_DRAIN_GRACE_MS) {
                        stdoutReader.join()
                        stderrReader.join()
                        true
                    } ?: false
            if (!outputDrained) {
                closeDescriptor(stdoutDescriptor, "stdout after interactive process exit")
                closeDescriptor(stderrDescriptor, "stderr after interactive process exit")
                stdoutReader.cancelAndJoin()
                stderrReader.cancelAndJoin()
            }
        } catch (e: CancellationException) {
            if (!destroyed.get()) {
                destroyRemoteProcess(process)
            }
            completion.complete(-1)
            throw e
        } catch (e: Exception) {
            AppLogger.e(DEBUGGER_SHELL_TAG, "Interactive Shizuku process failed: $command", e)
            completion.complete(-1)
        } finally {
            if (!processFinished && !destroyed.get()) {
                destroyRemoteProcess(process)
            }
            closeDescriptor(stdoutDescriptor, "interactive stdout cleanup")
            closeDescriptor(stderrDescriptor, "interactive stderr cleanup")
            stdoutChannel.close()
            stderrChannel.close()
        }
    }

    private suspend fun drainLines(
            descriptor: ParcelFileDescriptor,
            channel: Channel<String>,
            label: String,
    ) {
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        channel.trySend(line)
                    }
                }
            }
        } catch (e: IOException) {
            if (!destroyed.get()) {
                AppLogger.v(DEBUGGER_SHELL_TAG, "Reading interactive $label stopped", e)
            }
        } catch (e: Exception) {
            if (!destroyed.get()) {
                AppLogger.w(DEBUGGER_SHELL_TAG, "Reading interactive $label failed", e)
            }
        } finally {
            channel.close()
        }
    }
}
