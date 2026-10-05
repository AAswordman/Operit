package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.IOException
import kotlinx.coroutines.isActive

/** 基于Shizuku的Shell命令执行器 作为管理员模式的 Shizuku 后端 */
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

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ADMIN

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

    /**
     * 封装重试逻辑的函数
     * @param maxRetries 最大重试次数
     * @param delayMs 每次重试前的延迟时间（毫秒）
     * @param operation 要执行的操作
     * @return 操作结果
     */
    private suspend fun <T> retryOperation(
            maxRetries: Int = 3,
            delayMs: Long = 500,
            operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 0 until maxRetries) {
            try {
                return operation()
            } catch (e: Exception) {
                // 检查是否是 read interrupted 异常
                val isInterruptedRead =
                        e is InterruptedIOException &&
                                e.message?.contains("read interrupted") == true

                if (isInterruptedRead) {
                    lastException = e
                    AppLogger.w(
                            TAG,
                            "Read interrupted on attempt ${attempt + 1}/$maxRetries, retrying in $delayMs ms",
                            e
                    )
                    delay(delayMs)
                    continue
                } else {
                    // 对于其他异常，直接抛出
                    throw e
                }
            }
        }
        // 如果达到最大重试次数，抛出最后一个异常
        throw lastException ?: IllegalStateException("Unknown error in retry operation")
    }

    /** 直接执行不包含特殊操作符的普通命令 */
    private suspend fun executeCommandDirect(command: String): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                var process: Any? = null

                try {
                    val service =
                            getShizukuService()
                                    ?: return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            "Shizuku service not available"
                                    )

                    // 拆分命令行参数 - 使用更智能的解析方法
                    val commandParts = parseCommand(command)

                    // 创建进程
                    process = service.newProcess(commandParts, null, null)

                    if (process == null) {
                        return@withContext ShellExecutor.CommandResult(
                                false,
                                "",
                                "Failed to create process"
                        )
                    }

                    // 将ParcelFileDescriptor转换为InputStream
                    val processClass = process::class.java
                    val inputStream =
                            processClass.getMethod("getInputStream").invoke(process) as
                                    ParcelFileDescriptor?
                    val errorStream =
                            processClass.getMethod("getErrorStream").invoke(process) as
                                    ParcelFileDescriptor?

                    // 使用重试逻辑读取标准输出和错误输出
                    val stdout =
                            if (inputStream != null) {
                                retryOperation {
                                    val stdoutStream = FileInputStream(inputStream.fileDescriptor)
                                    BufferedReader(InputStreamReader(stdoutStream)).use {
                                        it.readText()
                                    }
                                }
                            } else ""

                    val stderr =
                            if (errorStream != null) {
                                retryOperation {
                                    val stderrStream = FileInputStream(errorStream.fileDescriptor)
                                    BufferedReader(InputStreamReader(stderrStream)).use {
                                        it.readText()
                                    }
                                }
                            } else ""

                    val exitCode = processClass.getMethod("waitFor").invoke(process) as Int

                    // 返回结果
                    return@withContext ShellExecutor.CommandResult(
                            exitCode == 0,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: RemoteException) {
                    AppLogger.e(TAG, "Remote exception while executing command", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            "Remote exception: ${e.message}"
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing command", e)
                    return@withContext ShellExecutor.CommandResult(false, "", "Error: ${e.message}")
                } finally {
                    // 安全关闭文件描述符
                    try {
                        if (process != null) {
                            val processClass = process::class.java
                            try {
                                val inputStream =
                                        processClass.getMethod("getInputStream").invoke(process) as
                                                ParcelFileDescriptor?
                                inputStream?.close()
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Error closing input stream", e)
                            }

                            try {
                                val errorStream =
                                        processClass.getMethod("getErrorStream").invoke(process) as
                                                ParcelFileDescriptor?
                                errorStream?.close()
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Error closing error stream", e)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in cleanup", e)
                    }
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

                    // 如果命令以单个'&'结尾（后台运行），我们只负责启动，不阻塞等待
                    val trimmedForBg = enhancedCommand.trimEnd()
                    val isBackground =
                            trimmedForBg.endsWith("&") && !trimmedForBg.endsWith("&&")

                    val shellArgs = arrayOf("sh", "-e", "-c", enhancedCommand)

                    // 创建进程
                    val process =
                            service.newProcess(shellArgs, null, null)
                                    ?: return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            "Failed to create process"
                                    )
                    // 处理输入输出流
                    val processClass = process::class.java
                    val inputStream =
                            processClass.getMethod("getInputStream").invoke(process) as
                                    ParcelFileDescriptor?
                    val errorStream =
                            processClass.getMethod("getErrorStream").invoke(process) as
                                    ParcelFileDescriptor?

                    if (isBackground) {
                        AppLogger.d(TAG, "Detected background shell command (ending with '&'), not waiting for process")
                        // 对于后台命令，我们不读取输出，也不等待退出，只要进程创建成功就视为成功
                        try {
                            inputStream?.close()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error closing input stream for background shell command", e)
                        }

                        try {
                            errorStream?.close()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error closing error stream for background shell command", e)
                        }

                        return@withContext ShellExecutor.CommandResult(
                                true,
                                "",
                                "",
                                0
                        )
                    }

                    // 使用重试逻辑读取标准输出和错误输出
                    val stdout =
                            if (inputStream != null) {
                                retryOperation {
                                    val stdoutStream = FileInputStream(inputStream.fileDescriptor)
                                    BufferedReader(InputStreamReader(stdoutStream)).use {
                                        it.readText()
                                    }
                                }
                            } else ""

                    val stderr =
                            if (errorStream != null) {
                                retryOperation {
                                    val stderrStream = FileInputStream(errorStream.fileDescriptor)
                                    BufferedReader(InputStreamReader(stderrStream)).use {
                                        it.readText()
                                    }
                                }
                            } else ""

                    val exitCode = processClass.getMethod("waitFor").invoke(process) as Int

                    // 关闭文件描述符
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error closing input stream in shell execution", e)
                    }

                    try {
                        errorStream?.close()
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error closing error stream in shell execution", e)
                    }

                    // 确定命令是否成功
                    val success =
                            when {
                                // 如果命令包含grep，即使没有找到匹配也认为成功
                                command.contains("grep") -> exitCode == 0 || exitCode == 1

                                // 对其他命令，只有exitCode=0才算成功
                                else -> exitCode == 0
                            }

                    return@withContext ShellExecutor.CommandResult(
                            success,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing shell command", e)
                    return@withContext ShellExecutor.CommandResult(false, "", "Error: ${e.message}")
                }
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
        val service = getShizukuService() ?: throw IOException("Shizuku service not available")
        return ShizukuShellProcess(service, command)
    }
}

/**
 * 使用 Shizuku 实现的 ShellProcess。
 */
private class ShizukuShellProcess(
    private val service: IShizukuService,
    private val command: String
) : ShellProcess {
    private val process: Any
    private val processClass: Class<*>

    init {
        val shellArgs = arrayOf("sh", "-c", command)
        process = service.newProcess(shellArgs, null, null)
            ?: throw IOException("Failed to create Shizuku process")
        processClass = process.javaClass
    }

    private val inputStream: ParcelFileDescriptor by lazy {
        processClass.getMethod("getInputStream").invoke(process) as ParcelFileDescriptor
    }

    private val errorStream: ParcelFileDescriptor by lazy {
        processClass.getMethod("getErrorStream").invoke(process) as ParcelFileDescriptor
    }

    override val stdout: Flow<String> by lazy {
        flowFromStream(FileInputStream(inputStream.fileDescriptor))
    }

    override val stderr: Flow<String> by lazy {
        flowFromStream(FileInputStream(errorStream.fileDescriptor))
    }

    override val isAlive: Boolean
        get() = try {
            processClass.getMethod("exitValue").invoke(process)
            false
        } catch (e: Exception) {
            // IllegalThreadStateException means it's still running
            true
        }

    override fun destroy() {
        try {
            processClass.getMethod("destroy").invoke(process)
        } finally {
            inputStream.close()
            errorStream.close()
        }
    }

    override suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        processClass.getMethod("waitFor").invoke(process) as Int
    }
}

private fun flowFromStream(inputStream: InputStream): Flow<String> = callbackFlow {
    val job = CoroutineScope(Dispatchers.IO).launch {
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (isActive) {
                        trySend(line)
                    }
                }
            }
        } catch (e: IOException) {
            // This is expected when the process is destroyed
        } finally {
            close()
        }
    }
    awaitClose { job.cancel() }
}
