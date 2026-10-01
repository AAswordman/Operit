package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.permissions.PermissionBackend
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import com.ai.assistance.operit.core.tools.system.shell.ShellProcess
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** 基于Root权限的UI操作监听器 实现ROOT权限级别的操作监听 */
class RootActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "RootActionListener"
    }

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null
    private var monitoringJob: Job? = null
    private var process: ShellProcess? = null
    private val shellExecutor by lazy { 
        ShellExecutorFactory.getBackendExecutor(context, PermissionBackend.ROOT)
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ROOT

    override suspend fun isAvailable(): Boolean {
        try {
            // 检查Root权限
            val hasRoot = shellExecutor.isAvailable()
            
            AppLogger.d(TAG, "Root权限检查: $hasRoot")
            return hasRoot
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查Root权限时出错", e)
            return false
        }
    }

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        try {
            val available = isAvailable()
            return if (available) {
                ActionListener.PermissionStatus.granted()
            } else {
                ActionListener.PermissionStatus.denied(context.getString(R.string.root_no_root_permission))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查Root权限状态时出错", e)
            return ActionListener.PermissionStatus.denied(context.getString(R.string.root_check_permission_error, e.message ?: ""))
        }
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
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

    override fun isListening(): Boolean = isListening.get()

    override fun initialize() {
        // No-op
    }

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ActionListener.ListeningResult.failure(permStatus.reason)
                }

                if (isListening.get()) {
                    return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_already_listening))
                }

                actionCallback = onAction
                isListening.set(true)

                AppLogger.d(TAG, "开始Root权限级别的UI操作监听")

                // 启动底层系统事件监控
                startRootLevelMonitoring()

                return@withContext ActionListener.ListeningResult.success(context.getString(R.string.root_ui_listener_started))
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动Root UI操作监听失败", e)
                isListening.set(false)
                return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message ?: ""))
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.get()) {
                return@withContext true
            }

            isListening.set(false)
            actionCallback = null

            // 停止监控任务
            monitoringJob?.cancel()
            monitoringJob = null
            
            stopRootLevelMonitoring()

            AppLogger.d(TAG, "Root UI操作监听已停止")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止Root UI操作监听失败", e)
            return@withContext false
        }
    }

    /**
     * 开始Root级别的系统监控
     * 可以监听最底层的输入设备事件、内核事件等
     */
    private fun startRootLevelMonitoring() {
        AppLogger.d(TAG, "开始Root级别系统监控 - 直接监听内核输入设备和系统事件")
        
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                process = shellExecutor.startProcess("getevent -l")
                
                process?.stdout?.onEach { line ->
                    parseTouchEvent(line)
                }?.launchIn(this)
                
                process?.stderr?.onEach { line ->
                    AppLogger.w(TAG, "getevent stderr: $line")
                }?.launchIn(this)
                
                val exitCode = process?.waitFor()
                AppLogger.d(TAG, "getevent process exited with code $exitCode")

            } catch (e: Exception) {
                AppLogger.e(TAG, "Root级别监控任务出错", e)
            } finally {
                if (isActive) {
                    AppLogger.d(TAG, "Restarting root monitoring process...")
                    startRootLevelMonitoring()
                }
            }
        }
    }

    /**
     * 停止Root级别的系统监控
     */
    private fun stopRootLevelMonitoring() {
        AppLogger.d(TAG, "停止Root级别系统监控")
        monitoringJob?.cancel()
        process?.destroy()
        process = null
    }

    private fun parseTouchEvent(line: String) {
        // A simple parser for `getevent -l` output.
        // This is a placeholder and needs to be implemented properly.
        AppLogger.v(TAG, "Input event: $line")
        if (line.contains("ABS_MT_POSITION_X")) {
            // Handle X coordinate
        } else if (line.contains("ABS_MT_POSITION_Y")) {
            // Handle Y coordinate
        } else if (line.contains("BTN_TOUCH") && line.contains("DOWN")) {
            // Handle touch down
        } else if (line.contains("BTN_TOUCH") && line.contains("UP")) {
            // Handle touch up
            actionCallback?.invoke(
                ActionListener.ActionEvent(
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionListener.ActionType.CLICK,
                    additionalData = mapOf("source" to "getevent")
                )
            )
        }
    }

    /**
     * 监听原始输入设备事件
     * 直接读取 /dev/input/event* 设备文件
     */
    private suspend fun monitorRawInputEvents() {
        try {
            // 使用Root权限直接读取输入设备
            val result = shellExecutor.executeCommand("cat /proc/bus/input/devices | grep -E 'Name|Handlers'")
            if (result.success) {
                parseInputDeviceInfo(result.stdout)
            }
            
            // 监听实时触摸事件 - 这里只是示例，实际需要解析二进制事件数据
            // val touchResult = shellExecutor.executeCommand("timeout 0.1 getevent")
            // if (touchResult.success) {
            //     parseTouchEvents(touchResult.stdout)
            // }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "监听原始输入事件失败", e)
        }
    }

    /**
     * 监听内核事件
     * 通过dmesg或/proc/kmsg监听内核级别的事件
     */
    private suspend fun monitorKernelEvents() {
        try {
            // 读取最新的内核消息
            val result = shellExecutor.executeCommand("dmesg -T | tail -5")
            if (result.success && result.stdout.isNotEmpty()) {
                parseKernelEvents(result.stdout)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "监听内核事件失败", e)
        }
    }

    /**
     * 监听进程状态变化
     * 监听应用启动、关闭等进程级事件
     */
    private suspend fun monitorProcessEvents() {
        try {
            // 获取当前运行的应用进程
            val result = shellExecutor.executeCommand("ps -A | grep -v '\\[' | tail -10")
            if (result.success) {
                parseProcessEvents(result.stdout)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "监听进程事件失败", e)
        }
    }

    /**
     * 解析输入设备信息
     * @param deviceInfo 设备信息输出
     */
    private fun parseInputDeviceInfo(deviceInfo: String) {
        // 解析输入设备信息，识别触摸屏等设备
        AppLogger.v(TAG, "解析输入设备信息: $deviceInfo")
    }

    /**
     * 解析触摸事件数据
     * @param eventData getevent命令输出的原始事件数据
     */
    private fun parseTouchEvents(eventData: String) {
        // 解析getevent输出的二进制触摸事件数据
        // 转换为ActionEvent并回调
        AppLogger.v(TAG, "解析触摸事件: $eventData")
    }

    /**
     * 解析内核事件
     * @param kernelLog 内核日志输出
     */
    private fun parseKernelEvents(kernelLog: String) {
        // 解析内核日志中的相关事件
        if (kernelLog.contains("input") || kernelLog.contains("touch")) {
            AppLogger.v(TAG, "检测到输入相关内核事件")
            
            actionCallback?.let { callback ->
                val event = ActionListener.ActionEvent(
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionListener.ActionType.SYSTEM_EVENT,
                    additionalData = mapOf(
                        "source" to "kernel_log",
                        "event" to "input_device_activity"
                    )
                )
                callback(event)
            }
        }
    }

    /**
     * 解析进程事件
     * @param processInfo 进程信息输出
     */
    private fun parseProcessEvents(processInfo: String) {
        // 解析进程状态变化，检测应用启动/关闭
        AppLogger.v(TAG, "解析进程事件: ${processInfo.take(100)}...")
    }

    /**
     * 处理检测到的原始触摸事件
     * @param devicePath 输入设备路径
     * @param x 触摸X坐标
     * @param y 触摸Y坐标
     * @param pressure 压力值
     */
    fun handleRawTouchEvent(devicePath: String, x: Int, y: Int, pressure: Int) {
        if (isListening.get() && actionCallback != null) {
            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.CLICK,
                coordinates = Pair(x, y),
                additionalData = mapOf(
                    "devicePath" to devicePath,
                    "pressure" to pressure,
                    "source" to "raw_input_device"
                )
            )
            actionCallback?.invoke(event)
        }
    }
} 