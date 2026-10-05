package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.permissions.PermissionBackend
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import com.ai.assistance.operit.core.tools.system.shell.ShellProcess
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** 基于Shizuku的UI操作监听器 作为管理员模式的 Shizuku 后端 */
class DebuggerActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "DebuggerActionListener"

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

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null
    private var monitoringJob: Job? = null
    private var windowMonitorProcess: ShellProcess? = null
    private var activityMonitorProcess: ShellProcess? = null
    private var lastFocusedWindow: String? = null
    private var lastActivityStack: String? = null
    private val shellExecutor by lazy {
        com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
            .getBackendExecutor(context, PermissionBackend.SHIZUKU)
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ADMIN

    override suspend fun isAvailable(): Boolean {
        return ShizukuAuthorizer.isShizukuServiceRunning()
    }

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        val hasPermission = ShizukuAuthorizer.hasShizukuPermission()
        return if (hasPermission) {
            ActionListener.PermissionStatus.granted()
        } else {
            ActionListener.PermissionStatus.denied(ShizukuAuthorizer.getPermissionErrorMessage())
        }
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        ShizukuAuthorizer.requestShizukuPermission(onResult)
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

                AppLogger.d(TAG, "开始调试器权限级别的UI操作监听")

                // 启动系统级事件监控
                startSystemEventMonitoring()

                return@withContext ActionListener.ListeningResult.success(context.getString(R.string.debugger_ui_listener_started))
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动调试器UI操作监听失败", e)
                isListening.set(false)
                return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message ?: "Unknown error"))
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

            stopSystemEventMonitoring()

            AppLogger.d(TAG, "调试器UI操作监听已停止")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止调试器UI操作监听失败", e)
            return@withContext false
        }
    }

    /**
     * 检查Shizuku是否已安装
     * @return 是否已安装Shizuku
     */
    fun isShizukuInstalled(): Boolean {
        return ShizukuAuthorizer.isShizukuInstalled(context)
    }

    /**
     * 开始系统级事件监控
     * 使用Shizuku权限通过startProcess启动持续监控进程
     */
    private fun startSystemEventMonitoring() {
        AppLogger.d(TAG, "开始系统级事件监控 - 使用startProcess启动持续监控进程")
        
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 启动窗口焦点监控进程
                startWindowFocusMonitoring()
                
                // 启动Activity栈监控进程
                startActivityStackMonitoring()
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动系统事件监控进程失败", e)
            }
        }
    }

    /**
     * 停止系统级事件监控
     */
    private fun stopSystemEventMonitoring() {
        AppLogger.d(TAG, "停止系统级事件监控")
        
        // 停止监控进程
        windowMonitorProcess?.destroy()
        windowMonitorProcess = null
        
        activityMonitorProcess?.destroy()
        activityMonitorProcess = null
        
        monitoringJob?.cancel()
        monitoringJob = null
        lastFocusedWindow = null
        lastActivityStack = null
    }

    /**
     * 启动窗口焦点监控进程
     */
    private suspend fun startWindowFocusMonitoring() {
        try {
            // 使用watch命令每秒检查窗口焦点变化
            val command = "while true; do dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -2; sleep 1; done"
            windowMonitorProcess = shellExecutor.startProcess(command)
            
            // 监听输出流
            windowMonitorProcess?.stdout?.onEach { output ->
                if (output.isNotEmpty() && output != lastFocusedWindow) {
                    lastFocusedWindow = output
                    parseWindowFocusEvents(output)
                }
            }?.launchIn(CoroutineScope(Dispatchers.IO))
            
            AppLogger.d(TAG, "窗口焦点监控进程已启动")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动窗口焦点监控进程失败", e)
        }
    }

    /**
     * 启动Activity栈监控进程
     */
    private suspend fun startActivityStackMonitoring() {
        try {
            // 使用watch命令每秒检查Activity栈变化
            val command = "while true; do dumpsys activity activities | grep -E 'Running activities|TaskRecord' | head -5; sleep 1; done"
            activityMonitorProcess = shellExecutor.startProcess(command)
            
            // 监听输出流
            activityMonitorProcess?.stdout?.onEach { output ->
                if (output.isNotEmpty() && output != lastActivityStack) {
                    lastActivityStack = output
                    parseActivityStackEvents(output)
                }
            }?.launchIn(CoroutineScope(Dispatchers.IO))
            
            AppLogger.d(TAG, "Activity栈监控进程已启动")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动Activity栈监控进程失败", e)
        }
    }



    /**
     * 解析窗口焦点事件
     * @param windowInfo 窗口信息输出
     */
    private fun parseWindowFocusEvents(windowInfo: String) {
        if (windowInfo.contains("mCurrentFocus") || windowInfo.contains("mFocusedApp")) {
            AppLogger.v(TAG, "检测到窗口焦点变化: ${windowInfo.take(100)}")
            
            // 尝试从窗口信息中提取应用包名
            val packageName = extractPackageNameFromWindowInfo(windowInfo)
            
            actionCallback?.let { callback ->
                val event = ActionListener.ActionEvent(
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionListener.ActionType.SCREEN_CHANGE,
                    additionalData = mapOf(
                        "source" to "window_focus_monitor",
                        "windowInfo" to windowInfo.take(200),
                        "packageName" to (packageName ?: "unknown")
                    )
                )
                callback(event)
            }
        }
    }



    /**
     * 解析Activity栈事件
     * @param activityStack Activity栈信息
     */
    private fun parseActivityStackEvents(activityStack: String) {
        AppLogger.v(TAG, "检测到Activity栈变化: ${activityStack.take(100)}")
        
        // 从Activity栈信息中提取当前前台Activity
        val currentActivity = extractCurrentActivityFromStack(activityStack)
        
        actionCallback?.let { callback ->
            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.APP_SWITCH,
                additionalData = mapOf(
                    "source" to "activity_stack_monitor",
                    "activityStack" to activityStack.take(200),
                    "currentActivity" to (currentActivity ?: "unknown")
                )
            )
            callback(event)
        }
    }

    /**
     * 处理检测到的触摸事件
     * @param x 触摸X坐标
     * @param y 触摸Y坐标
     * @param action 触摸动作类型
     */
    fun handleDetectedTouchEvent(x: Int, y: Int, action: String) {
        if (isListening.get() && actionCallback != null) {
            val actionType = when (action) {
                "DOWN", "UP" -> ActionListener.ActionType.CLICK
                "MOVE" -> ActionListener.ActionType.SWIPE
                else -> ActionListener.ActionType.GESTURE
            }

            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = actionType,
                coordinates = Pair(x, y),
                additionalData = mapOf(
                    "rawAction" to action,
                    "source" to "system_input_monitor"
                )
            )
            actionCallback?.invoke(event)
        }
    }

    /**
     * 处理检测到的应用切换事件
     * @param fromPackage 切换前的应用包名
     * @param toPackage 切换后的应用包名
     */
    fun handleAppSwitchEvent(fromPackage: String?, toPackage: String?) {
        if (isListening.get() && actionCallback != null) {
            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.APP_SWITCH,
                additionalData = mapOf(
                    "fromPackage" to (fromPackage ?: "unknown"),
                    "toPackage" to (toPackage ?: "unknown"),
                    "source" to "window_manager_monitor"
                )
            )
            actionCallback?.invoke(event)
        }
    }

    /**
     * 从窗口信息中提取应用包名
     * @param windowInfo 窗口信息字符串
     * @return 提取的包名，如果无法提取则返回null
     */
    private fun extractPackageNameFromWindowInfo(windowInfo: String): String? {
        // 尝试从窗口信息中提取包名
        // 示例格式: mCurrentFocus=Window{abc123 u0 com.example.app/com.example.app.MainActivity}
        val packagePattern = Regex("""([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+)/""")
        return packagePattern.find(windowInfo)?.groupValues?.get(1)
    }

    /**
     * 从Activity栈信息中提取当前前台Activity
     * @param activityStack Activity栈信息字符串
     * @return 提取的Activity信息，如果无法提取则返回null
     */
    private fun extractCurrentActivityFromStack(activityStack: String): String? {
        // 尝试从Activity栈信息中提取当前Activity
        // 示例格式: Running activities (most recent first): ActivityRecord{abc123 u0 com.example.app/.MainActivity t123}
        val activityPattern = Regex("""ActivityRecord\{[^}]*\s+([^/]+/[^}]+)""")
        return activityPattern.find(activityStack)?.groupValues?.get(1)
    }
} 