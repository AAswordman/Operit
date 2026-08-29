package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责管理悬浮窗交互 */
class FloatingWindowDelegate(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val inputProcessingState: StateFlow<InputProcessingState>
) {
    companion object {
        private const val TAG = "FloatingWindowDelegate"
    }

    // 悬浮窗状态
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()
    private val _moveTaskToBackEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val moveTaskToBackEvents: SharedFlow<Unit> = _moveTaskToBackEvents.asSharedFlow()

    // 悬浮窗服务
    private var floatingService: FloatingChatService? = null

    private var floatingBinder: FloatingChatService.LocalBinder? = null

    private var isBoundToService: Boolean = false
    private var moveTaskToBackOnWindowShownPending: Boolean = false

    private val serviceLifecycleReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    FloatingChatService.ACTION_FLOATING_CHAT_SERVICE_STARTED -> {
                        tryBindToRunningService()
                    }
                    FloatingChatService.ACTION_FLOATING_CHAT_SERVICE_STOPPED -> {
                        moveTaskToBackOnWindowShownPending = false
                        disconnectFromService(updateFloatingMode = false)
                    }
                    FloatingChatService.ACTION_FLOATING_CHAT_WINDOW_SHOWN -> {
                        if (moveTaskToBackOnWindowShownPending) {
                            moveTaskToBackOnWindowShownPending = false
                            _moveTaskToBackEvents.tryEmit(Unit)
                        }
                    }
                    FloatingChatService.ACTION_FLOATING_CHAT_WINDOW_SHOW_FAILED -> {
                        moveTaskToBackOnWindowShownPending = false
                    }
                }
            }
        }

    // 服务连接
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as FloatingChatService.LocalBinder
                floatingService = binder.getService()
                floatingBinder = binder
                // 设置回调，允许服务通知委托关闭
                binder.setCloseCallback {
                    closeFloatingWindow()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                try {
                    floatingBinder?.clearCallbacks()
                } catch (_: Exception) {
                }
                floatingBinder = null
                floatingService = null
                isBoundToService = false
            }
        }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(FloatingChatService.ACTION_FLOATING_CHAT_SERVICE_STARTED)
                addAction(FloatingChatService.ACTION_FLOATING_CHAT_SERVICE_STOPPED)
                addAction(FloatingChatService.ACTION_FLOATING_CHAT_WINDOW_SHOWN)
                addAction(FloatingChatService.ACTION_FLOATING_CHAT_WINDOW_SHOW_FAILED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(serviceLifecycleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(serviceLifecycleReceiver, filter)
            }
        } catch (_: Exception) {
        }

        // If the service is already running (started by wake/workflow/widget), bind to it.
        tryBindToRunningService()
        setupInputStateCollection()
    }

    private fun tryBindToRunningService() {
        if (isBoundToService) return
        if (FloatingChatService.getInstance() == null) return
        try {
            val intent = Intent(context, FloatingChatService::class.java)
            // Bind without auto-create: only succeed if service is already running.
            val ok = context.bindService(intent, serviceConnection, 0)
            if (ok) {
                isBoundToService = true
                AppLogger.d(TAG, "已绑定到已运行的悬浮窗服务")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "绑定到已运行的悬浮窗服务失败", e)
        }
    }

    /** 切换悬浮窗模式 */
    fun toggleFloatingMode() {
        val newMode = !_isFloatingMode.value

        if (newMode) {
            _isFloatingMode.value = true
            moveTaskToBackOnWindowShownPending = false

            // 先启动并绑定服务
            val intent = Intent(context, FloatingChatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            moveTaskToBackOnWindowShownPending = false
            // 统一调用关闭逻辑，确保服务被正确关闭
            floatingService?.onClose()
        }
    }

    /**
     * 启动悬浮窗并指定一个初始模式
     */
    fun launchInMode(
        mode: FloatingMode,
        moveTaskToBackOnReady: Boolean = false
    ) {
        if (_isFloatingMode.value && floatingService != null) {
            // 如果服务已在运行，直接切换模式
            floatingService?.switchToMode(mode)
            if (moveTaskToBackOnReady) {
                _moveTaskToBackEvents.tryEmit(Unit)
            }
            AppLogger.d(TAG, "悬浮窗已在运行，直接切换到模式: $mode")
            return
        }

        _isFloatingMode.value = true
        moveTaskToBackOnWindowShownPending = moveTaskToBackOnReady

        // 先启动并绑定服务
        val intent = Intent(context, FloatingChatService::class.java)
        // 添加初始模式参数
        intent.putExtra("INITIAL_MODE", mode.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 由服务回调或用户操作调用，用于关闭悬浮窗并更新状态
     */
    private fun closeFloatingWindow() {
        disconnectFromService(updateFloatingMode = true)
    }

    private fun disconnectFromService(updateFloatingMode: Boolean) {
        if (updateFloatingMode && _isFloatingMode.value) {
            _isFloatingMode.value = false
        }
        moveTaskToBackOnWindowShownPending = false
        if (isBoundToService) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {
            }
        }
        try {
            floatingBinder?.clearCallbacks()
        } catch (_: Exception) {
        }
        floatingBinder = null
        floatingService = null
        isBoundToService = false
    }

    private fun setupInputStateCollection() {
        coroutineScope.launch {
            inputProcessingState.collect { state ->
                val isUiToolExecuting = state is InputProcessingState.ExecutingTool

                // Update UI busy state directly on the window state
                // floatingService?.windowState?.isUiBusy?.value = isUiToolExecuting
            }
        }
    }

    /** 清理资源 */
    fun cleanup() {
        try {
            context.unregisterReceiver(serviceLifecycleReceiver)
        } catch (_: Exception) {
        }
        // 解绑服务
        disconnectFromService(updateFloatingMode = false)
    }
}
