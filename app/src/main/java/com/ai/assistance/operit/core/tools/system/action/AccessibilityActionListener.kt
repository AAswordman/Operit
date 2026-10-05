package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import android.view.accessibility.AccessibilityEvent
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import com.ai.assistance.operit.R

/**
 * 基于无障碍服务的UI操作监听器 作为管理员模式的无障碍后端
 * 通过UIHierarchyManager与系统的无障碍服务进行通信，监听系统级的UI事件和用户操作
 */
class AccessibilityActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "AccessibilityActionListener"
    }

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ADMIN

    override suspend fun isAvailable(): Boolean {
        // 使用UIHierarchyManager检查无障碍服务是否启用并连接
        return UIHierarchyManager.isAccessibilityServiceEnabled(context)
    }

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        return if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            ActionListener.PermissionStatus.granted()
        } else {
            ActionListener.PermissionStatus.denied(context.getString(R.string.a11y_service_not_enabled))
        }
    }

    override fun initialize() {
        AppLogger.d(TAG, "无障碍UI操作监听器已初始化")
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        if (isAvailable()) {
            onResult(true)
            return
        }

        // 引导用户打开无障碍服务设置
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)

            // 由于无法知道用户是否启用了服务，返回false，让调用者自行处理后续检查
            onResult(false)
        } catch (e: Exception) {
            AppLogger.e(TAG, "打开无障碍设置失败", e)
            onResult(false)
        }
    }

    override fun isListening(): Boolean = isListening.get()

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ActionListener.ListeningResult.failure(permStatus.reason)
                }

                if (!isListening.compareAndSet(false, true)) {
                    AppLogger.w(TAG, "启动监听失败：已在监听中")
                    return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_already_listening))
                }

                actionCallback = onAction

                // 直接启动监听，不需要注册回调
                isListening.set(true)
                AppLogger.d(TAG, "无障碍UI操作监听已启动")
                ActionListener.ListeningResult.success(context.getString(R.string.a11y_ui_listener_started))
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动无障碍UI操作监听失败", e)
                isListening.set(false)
                actionCallback = null
                ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message))
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.compareAndSet(true, false)) {
                AppLogger.d(TAG, "监听器未在运行，无需停止")
                return@withContext true
            }

            actionCallback = null
            AppLogger.d(TAG, "无障碍UI操作监听已停止")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止无障碍UI操作监听失败", e)
            // Even if unregistering fails, we consider the listener stopped from our side.
            actionCallback = null
            false
        }
    }

    /**
     * 处理从远程无障碍服务通过AIDL回 调传来的事件
     * @param event 无障碍事件
     */
    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        if (!isListening.get()) return

        val callback = actionCallback ?: return

        // 过滤掉不需要的事件类型，避免产生噪音
        // 2048 = TYPE_TOUCH_INTERACTION_START - 触摸交互开始事件，频繁触发
        if (event.eventType == 2048) {
            return
        }

        try {
            val actionType = when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> ActionListener.ActionType.CLICK
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> ActionListener.ActionType.LONG_CLICK
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> ActionListener.ActionType.TEXT_INPUT
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> ActionListener.ActionType.SCROLL
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ActionListener.ActionType.SCREEN_CHANGE
                else -> ActionListener.ActionType.SYSTEM_EVENT
            }

            val elementInfo = ActionListener.ElementInfo(
                className = event.className?.toString(),
                text = event.text?.joinToString(" "),
                contentDescription = event.contentDescription?.toString(),
                packageName = event.packageName?.toString()
            )

            val actionEvent = ActionListener.ActionEvent(
                timestamp = event.eventTime,
                actionType = actionType,
                elementInfo = elementInfo,
                additionalData = mapOf(
                    "eventType" to event.eventType,
                    "source" to "accessibility_service"
                )
            )

            callback.invoke(actionEvent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "处理无障碍事件失败", e)
        }
    }
} 