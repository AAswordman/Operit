package com.ai.assistance.operit.core.tools.system.action

import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel

/** 通用UI操作监听接口 定义了不同权限级别UI操作监听的统一接口 */
interface ActionListener {
    /**
     * 开始监听用户UI操作
     * @param onAction 当用户执行操作时的回调
     * @return 监听是否成功启动
     */
    suspend fun startListening(onAction: (ActionEvent) -> Unit): ListeningResult

    /**
     * 停止监听用户UI操作
     * @return 停止监听是否成功
     */
    suspend fun stopListening(): Boolean

    /**
     * 获取当前监听器的权限级别
     * @return 当前监听器的权限级别
     */
    fun getPermissionLevel(): AndroidPermissionLevel

    /**
     * 检查监听器是否可用
     * @return 监听器是否可用
     */
    suspend fun isAvailable(): Boolean

    /**
     * 请求监听器所需的权限
     * @param onResult 权限请求结果回调
     */
    suspend fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * 检查是否已有监听器所需的权限
     * @return 权限状态，包含是否有权限及详细的错误原因
     */
    suspend fun hasPermission(): PermissionStatus

    /** 初始化监听器 */
    fun initialize()

    /**
     * 检查监听器是否正在运行
     * @return 是否正在监听
     */
    fun isListening(): Boolean

    /** 用户UI操作事件数据类 */
    data class ActionEvent(
        val timestamp: Long,
        val actionType: ActionType,
        val coordinates: Pair<Int, Int>? = null,
        val elementInfo: ElementInfo? = null,
        val inputText: String? = null,
        val additionalData: Map<String, Any> = emptyMap()
    )

    /** UI操作类型枚举 */
    enum class ActionType {
        CLICK,
        LONG_CLICK,
        SWIPE,
        TEXT_INPUT,
        KEY_PRESS,
        SCROLL,
        GESTURE,
        APP_SWITCH,
        SCREEN_CHANGE,
        SYSTEM_EVENT
    }

    /** UI元素信息 */
    data class ElementInfo(
        val resourceId: String? = null,
        val className: String? = null,
        val text: String? = null,
        val contentDescription: String? = null,
        val bounds: String? = null,
        val packageName: String? = null
    )

    /** 监听结果数据类 */
    data class ListeningResult(
        val success: Boolean,
        val message: String = if (success) "Listening started" else "Failed to start listening"
    ) {
        companion object {
            fun success(message: String = "Listening started") = ListeningResult(true, message)
            fun failure(message: String) = ListeningResult(false, message)
        }
    }

    /** 权限状态数据类 包含权限检查结果和失败原因描述 */
    data class PermissionStatus(
        val granted: Boolean,
        val reason: String = if (granted) "Permission granted" else "Permission denied"
    ) {
        companion object {
            fun granted() = PermissionStatus(true)
            fun denied(reason: String) = PermissionStatus(false, reason)
        }
    }
} 