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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers as CoroutineDispatchers

/** 基于Root权限的Shell命令执行器 实现ROOT权限级别的命令执行 */
class RootShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "RootShellExecutor"
        private var rootAvailable: Boolean? = null

        /**
         * 单条 root 命令的最长等待时间。
         *
         * libsu 的 `Job.exec()` 没有超时，共享 shell 一旦被卡住就会永久阻塞后续所有命令，
         * 所以这里在调用侧补一个上限，保证工具调用总能返回。
         */
        private const val LIBSU_COMMAND_TIMEOUT_MS = 60_000L

        /** 探测被怀疑挂起的共享 shell 是否恢复时的等待上限。 */
        private const val WEDGED_PROBE_TIMEOUT_MS = 3_000L

        /**
         * 超时后拆除 su 会话，等待执行线程退出的上限。
         *
         * `process.destroy()` 之后管道会 EOF，阻塞的读通常立刻返回；这里留一点余量，
         * 超过就说明线程卡在真正不可中断的读上，只能放弃等待并如实记录。
         */
        private const val SHELL_TEARDOWN_JOIN_MS = 5_000L

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

    /**
     * 上一次 libsu 命令是否超时未返回。
     *
     * libsu 的 "main shell" 是进程级单例，所有 `Shell.cmd()` 都串行跑在同一个交互式 su 进程上，
     * 且 `Shell.Builder.setTimeout()` 只约束 shell 建立/校验阶段，**不约束 `Job.exec()`**。
     * 因此一旦某条命令永不返回（交互式提示、等待 stdin、子进程挂死），共享 shell 就被卡住，
     * 之后所有 root 命令都会排在它后面无限期等待。
     *
     * 这里用一个标记把"shell 可能已挂起"暴露出来，让后续调用快速失败而不是继续堆积阻塞，
     * 同时也给用户一个明确的原因，而不是表现为整机卡死。
     */
    @Volatile
    private var shellSuspectedWedged = false

    init {
        AppLogger.d(TAG, "RootShellExecutor实例初始化")
    }

    /**
     * 读取一个流直到 EOF。
     *
     * 必须能在独立线程上并发调用：如果先读完 stdout 再读 stderr，当子进程向 stderr 写入超过
     * 管道缓冲区（Android 上通常 64KB）时，子进程会阻塞在写 stderr 上，而我们在阻塞读 stdout，
     * 双方互等形成死锁。
     */
    private fun drainStream(stream: InputStream): String {
        val builder = StringBuilder()
        try {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    builder.append(line).append("\n")
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取命令输出流失败: ${e.message}")
        }
        return builder.toString()
    }

    private fun drainStreamAsync(stream: InputStream): CompletableFuture<String> =
        CompletableFuture.supplyAsync { drainStream(stream) }

    /**
     * `Thread.join(timeout)`，并在被中断时恢复中断标志后返回 false。
     *
     * `join` 抛 `InterruptedException` 时不能直接往上抛：那样 su 会话会留在挂起状态，
     * 下一次调用又会撞上同一个卡住的 shell。
     */
    private fun joinQuietly(thread: Thread, timeoutMs: Long): Boolean =
        try {
            thread.join(timeoutMs)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    /**
     * 执行 libsu 命令并施加超时。
     *
     * 命令跑在**独立的具名守护线程**上，而不是 `ForkJoinPool.commonPool()`：`Job.exec()`
     * 阻塞在 su 进程的管道读上时是不可中断的，如果放在公共池上，慢设备每超时一次就永久
     * 占用一个公共池线程，累积下来会拖垮整个应用里所有用到公共池的地方。
     *
     * 超时后不只是放弃等待，而是**主动拆掉被卡住的 su 会话**（见 [tearDownWedgedShell]）。
     * `Future.cancel(true)` 做不到这件事——它只能设置中断标志，打断不了阻塞在管道读上的
     * 线程；能真正释放线程的是让 su 进程死掉、管道 EOF。
     *
     * @return 命令结果；超时返回 null（此时共享 shell 已被拆除或标记为挂起）
     */
    private fun execWithTimeout(
        command: String,
        timeoutMs: Long = LIBSU_COMMAND_TIMEOUT_MS
    ): Shell.Result? {
        val resultRef = AtomicReference<Shell.Result?>(null)
        val errorRef = AtomicReference<Throwable?>(null)

        val worker = Thread({
            try {
                resultRef.set(Shell.cmd(command).exec())
            } catch (t: Throwable) {
                errorRef.set(t)
            }
        }, "operit-root-exec").apply { isDaemon = true }

        worker.start()
        // join() 建立 happens-before，线程结束后读 resultRef/errorRef 是安全的。
        if (!joinQuietly(worker, timeoutMs)) {
            // 调用方线程被中断：按超时同样处理，别把 su 会话留在挂起状态。
            worker.interrupt()
            tearDownWedgedShell()
            return null
        }

        if (!worker.isAlive) {
            errorRef.get()?.let { throw it }
            return resultRef.get()
        }

        AppLogger.e(
            TAG,
            "Root 命令在 ${timeoutMs}ms 内没有返回，判定共享 su shell 已挂起。命令: $command"
        )

        // 尽力而为：若线程恰好停在可中断的阻塞点上，这一步就能让它退出。
        worker.interrupt()
        // 真正有效的一步：拆掉 su 会话，进程死亡 → 管道 EOF → 阻塞的读返回。
        tearDownWedgedShell()

        joinQuietly(worker, SHELL_TEARDOWN_JOIN_MS)
        if (worker.isAlive) {
            // 会话已拆除，但线程仍卡在不可中断的读上。如实记录，并且不再无限期等它。
            shellSuspectedWedged = true
            AppLogger.w(
                TAG,
                "执行线程在 ${SHELL_TEARDOWN_JOIN_MS}ms 后仍未退出（阻塞在不可中断的管道读上）。" +
                    "已放弃等待，后续 root 命令将被快速拒绝，直到 su 会话重建。命令: $command"
            )
        }
        return null
    }

    /**
     * 拆除被卡住的共享 su 会话，让阻塞在管道读上的执行线程能够退出。
     *
     * libsu 的 `Shell.close()` 最终走 `ShellImpl.release()`：关闭 STDIN/STDOUT/STDERR 并
     * `process.destroy()`。su 进程结束后管道对端关闭，卡在 `Job.exec()` 里的读操作读到
     * EOF/异常而返回，线程随之释放。关闭后 `ShellImpl.status` 变为 `UNKNOWN`（-1），
     * `MainShell.getCached()` 因此返回 null，下一条 root 命令会自动重建一个新会话。
     */
    private fun tearDownWedgedShell() {
        val cached = try {
            Shell.getCachedShell()
        } catch (e: Exception) {
            AppLogger.w(TAG, "获取缓存的 su shell 失败: ${e.message}")
            null
        }

        if (cached == null) {
            // 没有缓存会话，下一条命令本来就会新建一个，无需拒绝后续命令。
            shellSuspectedWedged = false
            rootAvailable = null
            return
        }

        try {
            cached.close()
            AppLogger.w(TAG, "已强制拆除被卡住的共享 su 会话，下一条 root 命令将重建会话")
            shellSuspectedWedged = false
        } catch (e: Exception) {
            shellSuspectedWedged = true
            AppLogger.w(TAG, "拆除共享 su 会话失败，后续 root 命令将被拒绝: ${e.message}")
        }
        // 会话已变化，root 可用性需要重新判定。
        rootAvailable = null
    }

    private fun wedgedShellMessage(): String =
        "Root shell is unresponsive: a previous command never returned and the shared su " +
            "session is still blocked. libsu provides no per-command timeout, so new root " +
            "commands are rejected instead of queueing forever. Restart the app (or the root " +
            "manager) to rebuild the shell, and avoid commands that wait on stdin."

    /**
     * 检查被怀疑挂起的共享 shell 是否已经恢复。
     *
     * `waitAndClose(timeout)` 在超时后返回 false 且不会终止 shell，因此可以用它做一次
     * 非破坏性的"是否还忙"探测：返回 true 说明之前的命令终于结束了。
     */
    private fun clearWedgedFlagIfRecovered(): Boolean {
        if (!shellSuspectedWedged) return true
        val cached = try {
            Shell.getCachedShell()
        } catch (e: Exception) {
            null
        }
        if (cached == null) {
            // 没有缓存 shell，下一次 Shell.getShell() 会重新构建一个。
            shellSuspectedWedged = false
            rootAvailable = null
            return true
        }
        val recovered = try {
            cached.waitAndClose(WEDGED_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            false
        }
        if (recovered) {
            AppLogger.i(TAG, "共享 su shell 已恢复，清除挂起标记")
            shellSuspectedWedged = false
            rootAvailable = null
        } else {
            AppLogger.w(TAG, "共享 su shell 仍然忙碌/挂起，继续拒绝 root 命令")
        }
        return recovered
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
            
            // 如果已经检查过，直接返回缓存结果，但不每次都输出日志
            if (rootAvailable != null) {
                // 使用更低级别的日志，减少输出量
                AppLogger.v(TAG, "使用缓存的Root检查结果: $rootAvailable")
                return rootAvailable!!
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

                // 并发读取 stdout / stderr。顺序读取会在子进程向 stderr 写入超过管道缓冲区
                // （约 64KB）时死锁：子进程阻塞在写 stderr，而我们阻塞在读 stdout。
                val stderrFuture = drainStreamAsync(process.errorStream)
                val stdoutStr = drainStream(process.inputStream).trimEnd()

                // 等待进程完成并获取退出码
                val exitCode = process.waitFor()

                val stderrStr =
                    try {
                        stderrFuture.get(5, TimeUnit.SECONDS).trimEnd()
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "读取 stderr 超时或失败: ${e.message}")
                        ""
                    }
                
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

                    // 快速失败：共享 su shell 上次超时未返回且仍未恢复时，继续提交命令只会
                    // 让更多调用堆在同一个被卡住的 shell 后面（表现为整个应用卡死）。
                    if (!useExecMode && !clearWedgedFlagIfRecovered()) {
                        return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            wedgedShellMessage(),
                            -1
                        )
                    }

                    val permStatus = hasPermission()
                    if (!permStatus.granted) {
                        return@withContext ShellExecutor.CommandResult(false, "", permStatus.reason)
                    }

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

                                    // 并发读取两个流，避免管道缓冲区写满导致的死锁（见 drainStream 注释）
                                    val stderrFuture = drainStreamAsync(process.errorStream)
                                    val stdoutStr = drainStream(process.inputStream).trimEnd()

                                    val exitCode = process.waitFor()

                                    val stderrStr =
                                        try {
                                            stderrFuture.get(5, TimeUnit.SECONDS).trimEnd()
                                        } catch (e: Exception) {
                                            AppLogger.w(TAG, "读取 stderr 超时或失败: ${e.message}")
                                            ""
                                        }

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
                                    val shellResult = execWithTimeout(shellCommand)
                                        ?: return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            wedgedShellMessage(),
                                            -1
                                        )

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
                                val shellResult = execWithTimeout(actualCommand)
                                    ?: return@withContext ShellExecutor.CommandResult(
                                        false,
                                        "",
                                        wedgedShellMessage(),
                                        -1
                                    )

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

        if (!hasPermission().granted) {
            throw SecurityException("Root permission not granted.")
        }
        
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
