package com.ai.assistance.operit.core.tools.permissions.authorization

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.shell.RootShellExecutor
import com.ai.assistance.operit.core.tools.system.shell.ShellIdentity
import com.ai.assistance.operit.data.preferences.RootCommandExecutionMode
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Root权限授权管理器 提供检查设备是否root、请求root权限等功能 */
object RootAuthorizer {
    private const val TAG = "RootAuthorizer"

    // Root执行器实例
    private var rootShellExecutor: RootShellExecutor? = null

    // 状态监听器列表
    private val stateChangeListeners = CopyOnWriteArrayList<() -> Unit>()

    // Root状态流
    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

    // Root访问权限状态流
    private val _hasRootAccess = MutableStateFlow(false)
    val hasRootAccess: StateFlow<Boolean> = _hasRootAccess.asStateFlow()
    
    // 是否使用exec执行命令而不是libsu (适用于KernelSu等情况)
    private var useExecForCommands = false

    private fun normalizeSuCommand(command: String?): String {
        val normalized = command?.trim().orEmpty()
        return normalized.ifEmpty { "su" }
    }

    private fun parseSuCommandTokens(command: String): List<String> {
        return normalizeSuCommand(command).split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun getConfiguredSuCommand(): String {
        return try {
            normalizeSuCommand(androidPermissionPreferences.getCustomSuCommand())
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取自定义su命令失败，回退默认su", e)
            "su"
        }
    }

    private fun buildSuExecCommand(command: String): Array<String> {
        val tokens = parseSuCommandTokens(getConfiguredSuCommand())
        return (tokens + listOf("-c", command)).toTypedArray()
    }

    private fun buildSuVersionCommand(): Array<String> {
        val tokens = parseSuCommandTokens(getConfiguredSuCommand())
        return (tokens + "--version").toTypedArray()
    }

    private fun getRootExecutionModePreference(): RootCommandExecutionMode {
        return try {
            androidPermissionPreferences.getRootExecutionMode()
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取Root执行模式偏好失败，回退自动模式", e)
            RootCommandExecutionMode.AUTO
        }
    }

    private fun applyRootExecutionPreferences() {
        val rootExecutionMode = getRootExecutionModePreference()
        val customSuCommand = if (rootExecutionMode == RootCommandExecutionMode.FORCE_EXEC) {
            getConfiguredSuCommand()
        } else {
            "su"
        }
        rootShellExecutor?.setExecSuCommand(customSuCommand)
    }
    
    // 静态初始化libsu
    init {
        // 确保libsu全局设置已配置
        try {
            // 配置Shell
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
            )
            AppLogger.d(TAG, "libsu Shell全局配置已初始化")
        } catch (e: Exception) {
            AppLogger.e(TAG, "libsu Shell全局配置初始化失败", e)
        }
    }

    /** 初始化Root授权器 */
    fun initialize(context: Context) {
        try {
            AppLogger.d(TAG, "初始化RootAuthorizer...")
            
            // 初始化Root执行器
            if (rootShellExecutor == null) {
                rootShellExecutor = RootShellExecutor(context)
            }
            rootShellExecutor?.initialize()

            // 检查Root状态
            checkRootStatus(context)
            
            AppLogger.d(TAG, "RootAuthorizer初始化完成")
        } catch (e: Exception) {
            AppLogger.e(TAG, "RootAuthorizer初始化失败", e)
            // 设置默认状态
            _isRooted.value = false
            _hasRootAccess.value = false
        }
    }

    /**
     * 检查设备是否已Root以及应用是否有Root访问权限
     * @param context 应用上下文
     * @return 是否已获取Root权限
     */
    fun checkRootStatus(context: Context): Boolean {
        try {
            AppLogger.d(TAG, "检查Root状态...")
            
            // 确保Root执行器已初始化
            if (rootShellExecutor == null) {
                rootShellExecutor = RootShellExecutor(context)
                rootShellExecutor?.initialize()
            }

            applyRootExecutionPreferences()
            
            // 检查设备是否已Root（基于文件系统检查，不依赖于Shell访问）
            val deviceRooted = isDeviceRooted()
            _isRooted.value = deviceRooted
            AppLogger.d(TAG, "设备Root状态: $deviceRooted")

            // 如果设备没有Root，则应用肯定没有Root访问权限
            if (!deviceRooted) {
                _hasRootAccess.value = false
                notifyStateChanged()
                return false
            }

            val rootExecutionMode = getRootExecutionModePreference()
            useExecForCommands = when (rootExecutionMode) {
                RootCommandExecutionMode.FORCE_EXEC -> true
                RootCommandExecutionMode.FORCE_LIBSU -> false
                RootCommandExecutionMode.AUTO -> useExecForCommands
            }

            rootShellExecutor?.setUseExecMode(useExecForCommands)

            // 检查应用是否有Root访问权限
            val hasAccess = rootShellExecutor?.isAvailable() ?: false
            
            _hasRootAccess.value = hasAccess
            AppLogger.d(TAG, "应用Root访问权限: $hasAccess，使用exec模式: $useExecForCommands")

            // 通知状态变更
            notifyStateChanged()

            return hasAccess
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查Root状态时出错", e)
            _isRooted.value = false
            _hasRootAccess.value = false
            notifyStateChanged()
            return false
        }
    }

    /**
     * 判断设备是否已Root（不一定意味着应用有Root权限）
     * 使用多种方法检测设备是否Root
     * @return 设备是否已Root
     */
    fun isDeviceRooted(): Boolean {
        try {
            AppLogger.d(TAG, "检查设备是否已Root...")
            val rootExecutionMode = getRootExecutionModePreference()

            if (rootExecutionMode == RootCommandExecutionMode.FORCE_EXEC) {
                AppLogger.d(TAG, "Root执行模式已手动设为exec，跳过libsu自动检测")
                useExecForCommands = true
                if (checkExecSuAccess()) {
                    return true
                }
            }

            // 方法1: 使用libsu检测
            if (rootExecutionMode != RootCommandExecutionMode.FORCE_EXEC) {
                try {
                    val isRoot = Shell.isAppGrantedRoot() ?: false
                    if (isRoot) {
                        AppLogger.d(TAG, "libsu检测到设备已Root并授予应用权限")
                        useExecForCommands = false
                        return true
                    }
                } catch (e: Exception) {
                    AppLogger.d(TAG, "libsu检测Root失败: ${e.message}")
                }
            }

            // 方法2: 检查KernelSU
            if (rootExecutionMode != RootCommandExecutionMode.FORCE_LIBSU && checkKernelSu()) {
                AppLogger.d(TAG, "检测到KernelSU，设备已Root")
                useExecForCommands = true
                return true
            }
            
            // 方法3: 检查常见的su路径
            val suPaths = arrayOf(
                "/system/bin/su", 
                "/system/xbin/su", 
                "/sbin/su", 
                "/system/app/Superuser.apk", 
                "/system/app/SuperSU.apk"
            )
            
            for (path in suPaths) {
                if (File(path).exists()) {
                    AppLogger.d(TAG, "发现su文件: $path")
                    return true
                }
            }
            
            // 方法4: 检查是否可以执行su命令
            try {
                val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    AppLogger.d(TAG, "su命令可用，设备已Root")
                    return true
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "检查su命令失败: ${e.message}")
            }
            
            // 如果所有方法都失败，则认为设备未Root
            AppLogger.d(TAG, "设备未检测到Root")
            useExecForCommands = false
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查设备Root状态时出错", e)
            useExecForCommands = false
            return false
        }
    }
    
    /**
     * 检查是否是KernelSU
     * @return 是否检测到KernelSU
     */
    private fun checkKernelSu(): Boolean {
        try {
            AppLogger.d(TAG, "检查KernelSU...")

            // 执行su --version命令
            val process = Runtime.getRuntime().exec(buildSuVersionCommand())
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            val result = output.toString().trim()
            
            AppLogger.d(TAG, "su --version输出: $result (退出码: $exitCode)")
            
            // 检查输出是否包含KernelSU
            val isKernelSu = result.contains("KernelSU", ignoreCase = true)
            if (isKernelSu) {
                AppLogger.d(TAG, "检测到KernelSU")
                useExecForCommands = true
            }
            
            return isKernelSu || exitCode == 0
        } catch (e: Exception) {
            AppLogger.d(TAG, "检查KernelSU失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 检查通过exec方式执行su命令是否可行
     * @return 是否可以使用exec执行su命令
     */
    private fun checkExecSuAccess(): Boolean {
        try {
            AppLogger.d(TAG, "检查exec su访问权限...")

            // 执行一个简单的测试命令
            val process = Runtime.getRuntime().exec(buildSuExecCommand("echo success"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line)
            }
            
            val exitCode = process.waitFor()
            val result = output.toString().trim()
            
            AppLogger.d(TAG, "exec su测试结果: $result (退出码: $exitCode)")
            
            return result == "success" && exitCode == 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查exec su访问权限失败", e)
            return false
        }
    }

    /**
     * 请求Root权限（尝试获取su权限）
     * 注意：这将触发Root管理应用的权限授予弹窗（如Magisk、SuperSU等）
     * @param onResult 请求结果回调
     */
    fun requestRootPermission(onResult: (Boolean) -> Unit) {
        try {
            AppLogger.d(TAG, "正在请求Root权限...")

            // 如果使用exec模式，尝试通过exec请求权限
            if (useExecForCommands) {
                try {
                    val process = Runtime.getRuntime().exec(buildSuExecCommand("echo granted"))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val result = reader.readLine()
                    val exitCode = process.waitFor()
                    
                    val granted = result == "granted" && exitCode == 0
                    AppLogger.d(TAG, "通过exec请求Root权限结果: ${if (granted) "已授予" else "已拒绝"}")
                    
                    _hasRootAccess.value = granted
                    if (granted) {
                        _isRooted.value = true
                    }
                    
                    notifyStateChanged()
                    onResult(granted)
                    return
                } catch (e: Exception) {
                    AppLogger.e(TAG, "通过exec请求Root权限失败", e)
                }
            }

            // 使用libsu直接请求root权限
            Shell.getShell { shell ->
                val granted = shell.isRoot
                AppLogger.d(TAG, "Root权限请求结果: ${if (granted) "已授予" else "已拒绝"}")
                
                // 更新状态
                _hasRootAccess.value = granted
                if (granted) {
                    _isRooted.value = true
                }
                
                // 通知状态变更
                notifyStateChanged()
                
                // 回调结果
                onResult(granted)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求Root权限时出错", e)
            onResult(false)
        }
    }

    /**
     * 执行Root命令
     * @param command 要执行的命令
     * @param context 应用上下文
     * @return 命令执行结果
     */
    suspend fun executeRootCommand(command: String, context: Context): Pair<Boolean, String> {
        try {
            AppLogger.d(TAG, "执行Root命令: $command")

            // 检查Root执行器是否可用
            if (rootShellExecutor == null || !_hasRootAccess.value) {
                return Pair(false, context.getString(R.string.root_authorizer_not_initialized))
            }

            // 使用Root执行器执行命令（以ROOT身份）
            val result = rootShellExecutor!!.executeCommand(command, ShellIdentity.ROOT)
            return Pair(result.success, if (result.success) result.stdout else result.stderr)
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行Root命令时出错", e)
            return Pair(false, context.getString(R.string.root_authorizer_execute_error, e.message ?: ""))
        }
    }

    /**
     * 添加状态变更监听器
     * @param listener 状态变更监听器
     */
    fun addStateChangeListener(listener: () -> Unit) {
        stateChangeListeners.add(listener)
    }

    /**
     * 移除状态变更监听器
     * @param listener 状态变更监听器
     */
    fun removeStateChangeListener(listener: () -> Unit) {
        stateChangeListeners.remove(listener)
    }

    /** 通知所有监听器状态已变更 */
    private fun notifyStateChanged() {
        stateChangeListeners.forEach { it.invoke() }
    }
}
