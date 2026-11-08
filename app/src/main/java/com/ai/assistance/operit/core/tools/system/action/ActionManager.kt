package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UI操作监听管理器
 * 统一管理所有权限级别的UI操作监听器，提供简化的使用接口
 */
class ActionManager(private val context: Context) {
    companion object {
        private const val TAG = "ActionManager"
        
        // 单例实例
        @Volatile
        private var INSTANCE: ActionManager? = null
        
        fun getInstance(context: Context): ActionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 协程作用域
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 当前活跃的监听器
    private var activeListener: ActionListener? = null
    
    // 是否正在监听状态
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    // 当前使用的权限级别
    private val _currentPermissionLevel = MutableStateFlow<AndroidPermissionLevel?>(null)
    val currentPermissionLevel: StateFlow<AndroidPermissionLevel?> = _currentPermissionLevel.asStateFlow()
    
    // 事件回调集合
    private val eventCallbacks = ConcurrentHashMap<String, (ActionListener.ActionEvent) -> Unit>()
    
    // 监听状态变化回调
    private val stateChangeCallbacks = mutableListOf<(Boolean, AndroidPermissionLevel?) -> Unit>()

    /**
     * 开始使用最高可用权限级别进行UI操作监听
     * @param callback 接收UI操作事件的回调
     * @return 监听启动结果
     */
    suspend fun startListeningWithHighestPermission(
        callback: (ActionListener.ActionEvent) -> Unit
    ): ActionListener.ListeningResult {
        try {
            AppLogger.d(TAG, "尝试使用最高可用权限启动UI操作监听")
            
            val (listener, permissionStatus) = ActionListenerFactory.getHighestAvailableListener(context)
            
            if (!permissionStatus.granted) {
                AppLogger.w(TAG, "最高可用权限监听器权限不足: ${permissionStatus.reason}")
                return ActionListener.ListeningResult.failure(context.getString(R.string.action_insufficient_permission, permissionStatus.reason))
            }
            
            return startListeningWithListener(listener, callback)
        } catch (e: Exception) {
            AppLogger.e(TAG, "使用最高权限启动监听失败", e)
            return ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message ?: ""))
        }
    }

    /**
     * 使用指定权限级别开始UI操作监听
     * @param permissionLevel 指定的权限级别
     * @param callback 接收UI操作事件的回调
     * @return 监听启动结果
     */
    suspend fun startListeningWithPermissionLevel(
        permissionLevel: AndroidPermissionLevel,
        callback: (ActionListener.ActionEvent) -> Unit
    ): ActionListener.ListeningResult {
        try {
            AppLogger.d(TAG, "使用指定权限级别启动UI操作监听: $permissionLevel")
            
            val listener = ActionListenerFactory.getListener(context, permissionLevel)
            return startListeningWithListener(listener, callback)
        } catch (e: Exception) {
            AppLogger.e(TAG, "使用指定权限启动监听失败", e)
            return ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message ?: ""))
        }
    }

    /**
     * 使用指定监听器开始监听
     * @param listener 要使用的监听器
     * @param callback 事件回调
     * @return 监听启动结果
     */
    private suspend fun startListeningWithListener(
        listener: ActionListener,
        callback: (ActionListener.ActionEvent) -> Unit
    ): ActionListener.ListeningResult {
        // 如果已在监听，先停止
        if (_isListening.value) {
            stopListening()
        }
        
        val callbackId = "primary_callback"
        eventCallbacks[callbackId] = callback
        
        val result = listener.startListening { event ->
            // 广播事件到所有注册的回调
            eventCallbacks.values.forEach { it(event) }
        }
        
        if (result.success) {
            activeListener = listener
            _isListening.value = true
            _currentPermissionLevel.value = listener.getPermissionLevel()
            
            // 通知状态变化
            notifyStateChange(true, listener.getPermissionLevel())
            
            AppLogger.d(TAG, "UI操作监听已启动，权限级别: ${listener.getPermissionLevel()}")
        } else {
            eventCallbacks.remove(callbackId)
            AppLogger.w(TAG, "UI操作监听启动失败: ${result.message}")
        }
        
        return result
    }

    /**
     * 停止UI操作监听
     * @return 是否成功停止
     */
    suspend fun stopListening(): Boolean {
        try {
            val listener = activeListener
            if (listener == null || !_isListening.value) {
                AppLogger.d(TAG, "当前没有活跃的监听器")
                return true
            }
            
            val success = listener.stopListening()
            
            if (success) {
                activeListener = null
                _isListening.value = false
                _currentPermissionLevel.value = null
                eventCallbacks.clear()
                
                // 通知状态变化
                notifyStateChange(false, null)
                
                AppLogger.d(TAG, "UI操作监听已停止")
            } else {
                AppLogger.w(TAG, "停止UI操作监听失败")
            }
            
            return success
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止UI操作监听时出错", e)
            return false
        }
    }

    /**
     * 注册额外的事件回调
     * @param callbackId 回调标识
     * @param callback 事件回调函数
     */
    fun registerEventCallback(callbackId: String, callback: (ActionListener.ActionEvent) -> Unit) {
        eventCallbacks[callbackId] = callback
        AppLogger.d(TAG, "注册事件回调: $callbackId")
    }

    /**
     * 移除事件回调
     * @param callbackId 回调标识
     */
    fun unregisterEventCallback(callbackId: String) {
        eventCallbacks.remove(callbackId)
        AppLogger.d(TAG, "移除事件回调: $callbackId")
    }

    /**
     * 注册监听状态变化回调
     * @param callback 状态变化回调
     */
    fun registerStateChangeCallback(callback: (Boolean, AndroidPermissionLevel?) -> Unit) {
        stateChangeCallbacks.add(callback)
    }

    /**
     * 移除监听状态变化回调
     * @param callback 要移除的回调
     */
    fun removeStateChangeCallback(callback: (Boolean, AndroidPermissionLevel?) -> Unit) {
        stateChangeCallbacks.remove(callback)
    }

    /**
     * 通知状态变化
     * @param isListening 是否正在监听
     * @param permissionLevel 当前权限级别
     */
    private fun notifyStateChange(isListening: Boolean, permissionLevel: AndroidPermissionLevel?) {
        stateChangeCallbacks.forEach { callback ->
            try {
                callback(isListening, permissionLevel)
            } catch (e: Exception) {
                AppLogger.e(TAG, "状态变化回调执行失败", e)
            }
        }
    }

    /**
     * 获取所有可用监听器的状态信息
     * @return 权限级别到监听器状态的映射
     */
    suspend fun getAvailableListenersStatus(): Map<AndroidPermissionLevel, Pair<Boolean, ActionListener.PermissionStatus>> {
        val result = mutableMapOf<AndroidPermissionLevel, Pair<Boolean, ActionListener.PermissionStatus>>()
        
        for (level in AndroidPermissionLevel.values()) {
            try {
                val listener = ActionListenerFactory.getListener(context, level)
                val available = listener.isAvailable()
                val permissionStatus = listener.hasPermission()
                
                result[level] = Pair(available, permissionStatus)
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取监听器状态失败: $level", e)
                result[level] = Pair(false, ActionListener.PermissionStatus.denied(context.getString(R.string.action_get_status_failed, e.message ?: "")))
            }
        }
        
        return result
    }

    /**
     * 请求指定权限级别的权限
     * @param permissionLevel 要请求的权限级别
     * @param onResult 结果回调
     */
    fun requestPermission(permissionLevel: AndroidPermissionLevel, onResult: (Boolean) -> Unit) {
        managerScope.launch {
            try {
                val listener = ActionListenerFactory.getListener(context, permissionLevel)
                listener.requestPermission(onResult)
            } catch (e: Exception) {
                AppLogger.e(TAG, "请求权限失败: $permissionLevel", e)
                onResult(false)
            }
        }
    }

    /**
     * 获取当前监听器信息
     * @return 当前监听器的信息，如果没有活跃监听器则返回null
     */
    suspend fun getCurrentListenerInfo(): ListenerInfo? {
        val listener = activeListener ?: return null
        
        return ListenerInfo(
            permissionLevel = listener.getPermissionLevel(),
            isListening = listener.isListening(),
            isAvailable = listener.isAvailable(),
            permissionStatus = listener.hasPermission()
        )
    }

    /**
     * 销毁管理器，清理资源
     */
    fun destroy() {
        managerScope.launch {
            try {
                stopListening()
                managerScope.cancel()
                eventCallbacks.clear()
                stateChangeCallbacks.clear()
                AppLogger.d(TAG, "ActionManager已销毁")
            } catch (e: Exception) {
                AppLogger.e(TAG, "销毁ActionManager时出错", e)
            }
        }
    }

    /** 监听器信息数据类 */
    data class ListenerInfo(
        val permissionLevel: AndroidPermissionLevel,
        val isListening: Boolean,
        val isAvailable: Boolean,
        val permissionStatus: ActionListener.PermissionStatus
    )
} 