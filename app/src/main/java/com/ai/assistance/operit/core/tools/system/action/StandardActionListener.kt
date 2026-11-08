package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.R
import android.view.accessibility.AccessibilityEvent
import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** 基于标准Android权限的UI操作监听器 实现STANDARD权限级别的操作监听 */
class StandardActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "StandardActionListener"
    }

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.STANDARD

    override suspend fun isAvailable(): Boolean = true // 标准监听器始终可用

    override suspend fun hasPermission(): ActionListener.PermissionStatus =
        ActionListener.PermissionStatus.granted() // 标准监听器不需要额外权限

    override fun initialize() {
        AppLogger.d(TAG, "标准UI操作监听器初始化完成")
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        // 标准监听器不需要额外权限
        onResult(true)
    }

    override fun isListening(): Boolean = isListening.get()

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                if (isListening.get()) {
                    return@withContext ActionListener.ListeningResult.failure("Already listening")
                }

                actionCallback = onAction
                isListening.set(true)

                AppLogger.d(TAG, "开始标准权限级别的UI操作监听")

                // 标准权限只能监听应用内的基本事件
                startBasicEventMonitoring()

                return@withContext ActionListener.ListeningResult.success(context.getString(R.string.standard_action_listener_started))
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动标准UI操作监听失败", e)
                isListening.set(false)
                return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.standard_action_listener_start_failed, e.message ?: ""))
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.get()) {
                AppLogger.d(TAG, "监听器未在运行")
                return@withContext true
            }

            isListening.set(false)
            actionCallback = null

            stopBasicEventMonitoring()

            AppLogger.d(TAG, "标准UI操作监听已停止")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止标准UI操作监听失败", e)
            return@withContext false
        }
    }

    /**
     * 开始基本事件监控
     * 在标准权限下，只能监听应用内的基本触摸和按键事件
     */
    private fun startBasicEventMonitoring() {
        // 在标准权限下，监听能力有限
        // 可以监听应用内的View触摸事件、Activity生命周期变化等
        AppLogger.d(TAG, "开始基本事件监控 - 监听应用内触摸和按键事件")
        
        // 注意：标准权限无法监听系统级事件或其他应用的操作
        // 只能监听当前应用内的用户交互
    }

    /**
     * 停止基本事件监控
     */
    private fun stopBasicEventMonitoring() {
        AppLogger.d(TAG, "停止基本事件监控")
    }

    /**
     * 处理应用内的触摸事件
     * @param x 触摸X坐标
     * @param y 触摸Y坐标
     */
    fun handleTouchEvent(x: Int, y: Int) {
        if (isListening.get() && actionCallback != null) {
            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.CLICK,
                coordinates = Pair(x, y),
                additionalData = mapOf("source" to "app_internal")
            )
            actionCallback?.invoke(event)
        }
    }

    /**
     * 处理应用内的按键事件
     * @param keyCode 按键代码
     */
    fun handleKeyEvent(keyCode: Int) {
        if (isListening.get() && actionCallback != null) {
            val event = ActionListener.ActionEvent(
                timestamp = System.currentTimeMillis(),
                actionType = ActionListener.ActionType.KEY_PRESS,
                additionalData = mapOf(
                    "keyCode" to keyCode,
                    "source" to "app_internal"
                )
            )
            actionCallback?.invoke(event)
        }
    }
}