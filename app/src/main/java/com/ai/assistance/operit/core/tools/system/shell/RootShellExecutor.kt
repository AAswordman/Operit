package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.RootCommandExecutionMode
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers as CoroutineDispatchers

/** 基于Root权限的Shell命令执行器 实现ROOT权限级别的命令执行 */
class RootShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "RootShellExecutor"
        private var rootAvailable: Boolean? = null
        
        // 静态初始化，确保Shell配置只被设置一次
        init {
            // 配置 libsu 库的全局设置
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
            )
            
            AppLogger.d(TAG, "libsu Shell静态初始化完成")
        }
    }

    // 是否使用exec模式执行命令
    private var useExecMode = false
    private var suCommand: String = AndroidPermissionPreferences.DEFAULT_SU_COMMAND

    init {
        AppLogger.d(TAG, "RootShellExecutor实例初始化")
    }

    /**
     * 设置是否使用exec模式执行命令
     * @param useExec 是否使用exec模式
     */
    fun setUseExecMode(useExec: Boolean) {
        useExecMode = useExec
        refreshExecSuCommandFromPreferences()
        AppLogger.d(TAG, "Root命令执行模式设置为: ${if(useExec) "exec模式" else "libsu模式"}")
    }

    fun setExecSuCommand(command: String?) {
        suCommand = normalizeSuCommand(command)
        AppLogger.d(TAG, "Root exec su命令设置为: $suCommand")
    }

    private fun normalizeSuCommand(command: String?): String {
        val normalized = command?.trim().orEmpty()
        return normalized.ifEmpty { AndroidPermissionPreferences.DEFAULT_SU_COMMAND }
    }

    private fun parseCommandTokens(command: String): List<String> {
        val normalized = normalizeSuCommand(command)
        return normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun buildSuExecCommand(command: String): Array<String> {
        val tokens = parseCommandTokens(suCommand)
        return (tokens + listOf("-c", command)).toTypedArray()
    }

    private fun buildSuInteractiveCommand(): Array<String> {
        return parseCommandTokens(suCommand).toTypedArray()
    }

    private fun refreshExecSuCommandFromPreferences() {
        try {
            val mode = androidPermissionPreferences.getRootExecutionMode()
            suCommand = if (mode == RootCommandExecutionMode.FORCE_EXEC) {
                normalizeSuCommand(androidPermissionPreferences.getCustomSuCommand())
            } else {
                AndroidPermissionPreferences.DEFAULT_SU_COMMAND
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取自定义su命令失败，回退默认su", e)
            suCommand = AndroidPermissionPreferences.DEFAULT_SU_COMMAND
        }
    }

    private fun applyExecutionModePreferenceOverride() {
        try {
            when (androidPermissionPreferences.getRootExecutionMode()) {
                RootCommandExecutionMode.FORCE_EXEC -> useExecMode = true
                RootCommandExecutionMode.FORCE_LIBSU -> useExecMode = false
                RootCommandExecutionMode.AUTO -> Unit
            }
            if (useExecMode) {
                refreshExecSuCommandFromPreferences()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取Root执行模式偏好失败，保留当前模式", e)
        }
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ROOT

    override fun isAvailable(): Boolean {
        try {
            applyExecutionModePreferenceOverride()

            // 如果使用exec模式，检查su命令是否可用
            if (useExecMode) {
                return checkExecSuAvailable()
            }
            
            // 使用 libsu 检查 root 权限
            val hasRoot = Shell.getShell().isRoot
            val previousValue = rootAvailable
            rootAvailable = hasRoot
            
            // 只在首次检查或值发生变化时输出日志
            if (previousValue != hasRoot) {
                AppLogger.d(TAG, "Root访问检查: $hasRoot")
            }
            return hasRoot
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查Root权限时出错", e)
            rootAvailable = false
            return false
        }
    }
    
    /**
     * 检查通过exec方式执行su命令是否可用
     * @return su命令是否可用
     */
    private fun checkExecSuAvailable(): Boolean {
        try {
            val process = Runtime.getRuntime().exec(buildSuExecCommand("id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line)
            }
            
            val exitCode = process.waitFor()
            val result = output.toString().trim()
            
            val available = exitCode == 0 && result.contains("uid=0")
            AppLogger.d(TAG, "exec su可用性检查: $available (结果: $result, 退出码: $exitCode)")
            return available
        } catch (e: Exception) {
            AppLogger.e(TAG, "exec su可用性检查失败", e)
            return false
        }
    }

    override fun hasPermission(): ShellExecutor.PermissionStatus {
        try {
            val available = isAvailable()
            return if (available) {
                ShellExecutor.PermissionStatus.granted()
            } else {
                ShellExecutor.PermissionStatus.denied("Root access not available on this device")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查Root权限状态时出错", e)
            return ShellExecutor.PermissionStatus.denied("Error checking root permission: ${e.message}")
        }
    }

    override fun initialize() {
        try {
            applyExecutionModePreferenceOverride()

            // 如果使用exec模式，检查su命令是否可用
            if (useExecMode) {
                refreshExecSuCommandFromPreferences()
                rootAvailable = checkExecSuAvailable()
                AppLogger.d(TAG, "使用exec模式初始化, Root可用: $rootAvailable")
                return
            }
            
            // 初始化 libsu 主 Shell 实例
            Shell.getShell { shell ->
                AppLogger.d(TAG, "Shell初始化完成, root: ${shell.isRoot}")
                rootAvailable = shell.isRoot
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化Shell时出错", e)
            rootAvailable = false
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        try {
            // Root权限无法通过代码请求，只能提示用户
            val hasRoot = isAvailable()
            onResult(hasRoot)

            if (!hasRoot) {
                AppLogger.d(TAG, "无法以编程方式请求Root权限")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求Root权限时出错", e)
            onResult(false)
        }
    }

    /**
     * 检查并提取run-as包装中的实际命令
     * @param command 可能包含run-as的命令
     * @return 提取后的实际命令
     */
    private fun extractActualCommand(command: String): String {
        // 检查命令是否是run-as格式
        val runAsPattern = """run-as\s+(\S+)\s+sh\s+-c\s+['"](.+)['"]""".toRegex()
        val match = runAsPattern.find(command)
        
        return if (match != null) {
            // 提取内部命令
            val innerCommand = match.groupValues[2]
            // 使用更低级别的日志，减少输出量
            AppLogger.v(TAG, "提取run-as内部命令: $innerCommand")
            innerCommand
        } else {
            // 没有匹配到run-as格式，直接返回原命令
            command
        }
    }
    
    /**
     * 确保用于shell身份执行的本地launcher二进制已从assets复制到可执行路径
     * @return 可执行文件的绝对路径，如果复制失败则返回空字符串
     */
    private fun ensureShellLauncherInstalled(): String {
        return try {
            val launcherName = "operit_shell_exec"
            val baseDir = File(context.filesDir, "bin")
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            val outFile = File(baseDir, launcherName)

            context.assets.open(launcherName).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            // 确保文件具有可执行权限
            outFile.setExecutable(true, false)
            AppLogger.d(TAG, "shell launcher已复制到: ${outFile.absolutePath}")
            outFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e(TAG, "复制shell launcher到本地目录失败", e)
            ""
        }
    }
    
    /**
     * 使用exec方式执行Root命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    private suspend fun executeCommandWithExec(command: String): ShellExecutor.CommandResult {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "使用exec执行Root命令: $command")

                // 执行su -c命令
                val process = Runtime.getRuntime().exec(buildSuExecCommand(command))
                
                // 读取标准输出
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stdout = StringBuilder()
                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    stdout.append(line).append("\n")
                }
                
                // 读取标准错误
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
                val stderr = StringBuilder()
                while (stderrReader.readLine().also { line = it } != null) {
                    stderr.append(line).append("\n")
                }
                
                // 等待进程完成并获取退出码
                val exitCode = process.waitFor()
                
                val stdoutStr = stdout.toString().trimEnd()
                val stderrStr = stderr.toString().trimEnd()
                
                AppLogger.d(TAG, "exec执行完成，退出码: $exitCode")
                if (stdoutStr.isNotEmpty()) {
                    AppLogger.v(TAG, "标准输出: $stdoutStr")
                }
                if (stderrStr.isNotEmpty()) {
                    AppLogger.v(TAG, "标准错误: $stderrStr")
                }
                
                return@withContext ShellExecutor.CommandResult(
                    exitCode == 0,
                    stdoutStr,
                    stderrStr,
                    exitCode
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "使用exec执行Root命令时出错", e)
                return@withContext ShellExecutor.CommandResult(
                    false,
                    "",
                    context.getString(R.string.root_shell_error, e.message ?: ""),
                    -1
                )
            }
        }
    }

    override suspend fun executeCommand(
        command: String,
        identity: ShellIdentity
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    applyExecutionModePreferenceOverride()

                    val actualCommand = extractActualCommand(command)

                    return@withContext when (identity) {
                        ShellIdentity.SHELL -> {
                            AppLogger.d(TAG, "使用shell身份执行命令: $actualCommand (原始命令: $command)")

                            val launcherPath = ensureShellLauncherInstalled()
                            if (launcherPath.isEmpty()) {
                                ShellExecutor.CommandResult(
                                    false,
                                    "",
                                    "Shell launcher binary not available",
                                    -1
                                )
                            } else {
                                if (useExecMode) {
                                    val fullCmd = "$launcherPath $actualCommand"
                                    val process = Runtime.getRuntime().exec(buildSuExecCommand(fullCmd))

                                    val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                                    val stdout = StringBuilder()
                                    var line: String?
                                    while (stdoutReader.readLine().also { line = it } != null) {
                                        stdout.append(line).append("\n")
                                    }

                                    val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
                                    val stderr = StringBuilder()
                                    while (stderrReader.readLine().also { line = it } != null) {
                                        stderr.append(line).append("\n")
                                    }

                                    val exitCode = process.waitFor()

                                    val stdoutStr = stdout.toString().trimEnd()
                                    val stderrStr = stderr.toString().trimEnd()

                                    AppLogger.d(TAG, "shell launcher命令(exec)执行完成，退出码: $exitCode")
                                    if (stdoutStr.isNotEmpty()) {
                                        AppLogger.v(TAG, "标准输出: $stdoutStr")
                                    }
                                    if (stderrStr.isNotEmpty()) {
                                        AppLogger.v(TAG, "标准错误: $stderrStr")
                                    }

                                    ShellExecutor.CommandResult(
                                        exitCode == 0,
                                        stdoutStr,
                                        stderrStr,
                                        exitCode
                                    )
                                } else {
                                    val shellCommand = "$launcherPath $actualCommand"
                                    val shellResult = Shell.cmd(shellCommand).exec()

                                    val stdout = shellResult.out.joinToString("\n")
                                    val stderr = shellResult.err.joinToString("\n")
                                    val exitCode = shellResult.code

                                    AppLogger.d(TAG, "shell launcher命令(libsu)执行完成，退出码: $exitCode")
                                    if (stdout.isNotEmpty()) {
                                        AppLogger.v(TAG, "标准输出: $stdout")
                                    }
                                    if (stderr.isNotEmpty()) {
                                        AppLogger.v(TAG, "标准错误: $stderr")
                                    }

                                    ShellExecutor.CommandResult(
                                        exitCode == 0,
                                        stdout,
                                        stderr,
                                        exitCode
                                    )
                                }
                            }
                        }
                        ShellIdentity.ROOT, ShellIdentity.DEFAULT, ShellIdentity.APP -> {
                            // 保持原有 Root 命令执行逻辑
                            if (useExecMode) {
                                executeCommandWithExec(actualCommand)
                            } else {
                                AppLogger.d(TAG, "执行Root命令: $actualCommand (原始命令: $command)")
                                val shellResult = Shell.cmd(actualCommand).exec()

                                val stdout = shellResult.out.joinToString("\n")
                                val stderr = shellResult.err.joinToString("\n")
                                val exitCode = shellResult.code

                                ShellExecutor.CommandResult(
                                    exitCode == 0,
                                    stdout,
                                    stderr,
                                    exitCode
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "执行Root命令时出错", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            context.getString(R.string.root_shell_error, e.message ?: ""),
                            -1
                    )
                }
            }

    override suspend fun startProcess(command: String): ShellProcess {
        applyExecutionModePreferenceOverride()

        return if (useExecMode) {
            ExecRootShellProcess(command, buildSuInteractiveCommand())
        } else {
            LibSuShellProcess(command)
        }
    }
}

/**
 * 使用 libsu 实现的 ShellProcess。
 */
private class LibSuShellProcess(command: String) : ShellProcess {
    private val stdoutChannel = Channel<String>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val stderrChannel = Channel<String>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val stdoutCallbackList = object : CallbackList<String>() {
        override fun onAddElement(s: String) {
            stdoutChannel.trySend(s)
            if (size > 2048) clear()
        }
    }
    private val stderrCallbackList = object : CallbackList<String>() {
        override fun onAddElement(s: String) {
            stderrChannel.trySend(s)
            if (size > 2048) clear()
        }
    }

    // Execute the job asynchronously - enqueue() returns a Future in v6.0.0
    private val future: java.util.concurrent.Future<Shell.Result> =
        Shell.cmd(command).to(stdoutCallbackList, stderrCallbackList).enqueue()

    private val closeJob: Job = CoroutineScope(Dispatchers.IO).launch {
        try {
            future.get()
        } catch (e: Exception) {
            AppLogger.e("RootShellExecutor", "Error waiting for shell result", e)
        } finally {
            stdoutChannel.close()
            stderrChannel.close()
        }
    }

    override val stdout: Flow<String> = stdoutChannel.receiveAsFlow()
    override val stderr: Flow<String> = stderrChannel.receiveAsFlow()

    override val isAlive: Boolean
        get() = !future.isDone

    override fun destroy() {
        // Cancel the future if it's still running
        future.cancel(true)
        closeJob.cancel()
        stdoutChannel.close()
        stderrChannel.close()
    }

    override suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        try {
            val result = future.get()
            result.code
        } catch (e: Exception) {
            AppLogger.e("RootShellExecutor", "Error waiting for shell result", e)
            -1
        }
    }
}

/**
 * 使用传统 `Runtime.exec("su")` 实现的 ShellProcess。
 */
private class ExecRootShellProcess(command: String, suCommand: Array<String>) : ShellProcess {
    private val process: Process = Runtime.getRuntime().exec(suCommand)

    init {
        process.outputStream.bufferedWriter().use {
            it.write(command)
            it.newLine()
            it.flush()
            it.write("exit")
            it.newLine()
            it.flush()
        }
    }

    override val stdout: Flow<String> = flowFromStream(process.inputStream)
    override val stderr: Flow<String> = flowFromStream(process.errorStream)

    override val isAlive: Boolean
        get() = process.isAlive

    override fun destroy() {
        process.destroy()
    }

    override suspend fun waitFor(): Int = withContext(CoroutineDispatchers.IO) {
        process.waitFor()
    }
}


private fun flowFromStream(inputStream: InputStream): Flow<String> = callbackFlow {
    val job = CoroutineScope(CoroutineDispatchers.IO).launch {
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    send(line)
                }
            }
        } catch (e: IOException) {
            AppLogger.w("ShellProcess", "Stream reading failed", e)
        } finally {
            close()
        }
    }
    awaitClose { job.cancel() }
}
