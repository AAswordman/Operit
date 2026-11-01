package com.ai.assistance.operit.ui.features.support.diagnostics.uidebugger

import android.content.Context
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.core.tools.system.action.ActionListenerFactory
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** UI调试工具的ViewModel，负责处理与AITool的交互 */
class UIDebuggerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UIDebuggerState())
    val uiState: StateFlow<UIDebuggerState> = _uiState.asStateFlow()

    private lateinit var toolHandler: AIToolHandler
    private var statusBarHeight: Int = 0
    private val TAG = "UIDebuggerViewModel"
    private var windowInteractionController: (suspend (Boolean) -> Unit)? = null
    private lateinit var context: Context
    
    // Activity监听相关
    private var currentActionListener: ActionListener? = null
    private var lastEventTimestamp: Long = 0
    private var connectionCheckJob: kotlinx.coroutines.Job? = null

    companion object {
        @Volatile
        private var INSTANCE: UIDebuggerViewModel? = null
        
        /**
         * 获取单例实例，确保主应用和悬浮窗使用同一个ViewModel
         */
        fun getInstance(): UIDebuggerViewModel {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UIDebuggerViewModel().also { INSTANCE = it }
            }
        }
        
        /**
         * 清除单例实例
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }

    /**
     * 设置窗口交互控制器
     */
    fun setWindowInteractionController(controller: (suspend (Boolean) -> Unit)?) {
        this.windowInteractionController = controller
    }

    /** 初始化ViewModel */
    fun initialize(context: Context) {
        this.context = context
        toolHandler = AIToolHandler.getInstance(context)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
    }







    /** 选择UI元素 */
    fun selectElement(elementId: String?) {
        _uiState.update { it.copy(selectedElementId = elementId) }
    }

    /** 执行元素操作 */
    fun performElementAction(element: UIElement, action: UIElementAction) {
        viewModelScope.launch {
            try {
                when (action) {
                    UIElementAction.CLICK -> {
                        val clickTool = AITool(
                            name = "click_element",
                            parameters = listOf(
                                ToolParameter(
                                    name = "selector_type",
                                    value = if (element.resourceId != null) "ByResourceId" else "ByText"
                                ),
                                ToolParameter(
                                    name = "selector_value",
                                    value = element.resourceId ?: element.text
                                )
                            )
                        )
                        val result = withContext(Dispatchers.IO) {
                            toolHandler.executeTool(clickTool)
                        }
                        showActionFeedback(
                            if (result.success) context.getString(R.string.uidebugger_action_click_success)
                            else context.getString(R.string.uidebugger_action_click_failed)
                        )
                    }
                    UIElementAction.HIGHLIGHT -> {
                        selectElement(element.id)
                        showActionFeedback(context.getString(R.string.uidebugger_action_highlighted))
                    }
                    UIElementAction.INSPECT -> {
                        selectElement(element.id)
                        showActionFeedback(context.getString(R.string.uidebugger_action_selected))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "执行元素操作失败", e)
                showActionFeedback(context.getString(R.string.uidebugger_action_failed))
            }
        }
    }

    /** 显示操作反馈 */
    private fun showActionFeedback(message: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    showActionFeedback = true, 
                    actionFeedbackMessage = message
                ) 
            }
            delay(3000)
            _uiState.update { it.copy(showActionFeedback = false) }
        }
    }

    /** 刷新UI元素 */
    fun refreshUI() {
        viewModelScope.launch {
            try {
                windowInteractionController?.invoke(false)
                delay(300)

                val (elements, activityInfo) = withContext(Dispatchers.IO) {
                    val pageInfoTool = AITool(name = "get_page_info", parameters = listOf())
                    val result = toolHandler.executeTool(pageInfoTool)
                    if (result.success) {
                        val resultData = result.result
                        if (resultData is UIPageResultData) {
                            val currentActivityName = resultData.activityName
                            val currentPackageName = resultData.packageName
                            val elements = convertToUIElements(resultData.uiElements, currentActivityName, currentPackageName)
                            Pair(elements, Pair(currentActivityName, currentPackageName))
                        } else {
                            throw Exception(context.getString(R.string.uidebugger_error_wrong_result_type))
                        }
                    } else {
                        throw IllegalStateException("${context.getString(R.string.uidebugger_error_fetch_ui_failed)}: ${result.error}")
                    }
                }

                _uiState.update { 
                    it.copy(
                        elements = elements, 
                        errorMessage = null,
                        currentAnalyzedActivityName = activityInfo.first,
                        currentAnalyzedPackageName = activityInfo.second
                    ) 
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "刷新UI元素失败", e)
                _uiState.update { it.copy(errorMessage = "${context.getString(R.string.uidebugger_error_refresh_failed)}: $e") }
            } finally {
                windowInteractionController?.invoke(true)
            }
        }
    }






    private fun convertToUIElements(
        node: com.ai.assistance.operit.core.tools.SimplifiedUINode,
        activityName: String? = null,
        packageName: String? = null
    ): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        processNode(node, elements, activityName, packageName)
        return elements
    }

    private fun processNode(
        node: com.ai.assistance.operit.core.tools.SimplifiedUINode,
        elements: MutableList<UIElement>,
        activityName: String? = null,
        packageName: String? = null
    ) {
        val element = createUiElement(node, activityName, packageName)
        elements.add(element)
        node.children.forEach { childNode -> processNode(childNode, elements, activityName, packageName) }
    }

    private fun createUiElement(
        node: com.ai.assistance.operit.core.tools.SimplifiedUINode,
        activityName: String? = null,
        packageName: String? = null
    ): UIElement {
        val bounds = node.bounds?.let {
            try {
                val coordsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
                val matchResult = coordsPattern.find(it)
                if (matchResult != null) {
                    val (left, top, right, bottom) = matchResult.destructured
                    Rect(left.toInt(), top.toInt() - statusBarHeight, right.toInt(), bottom.toInt() - statusBarHeight)
                } else {
                    null
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析边界失败", e)
                null
            }
        }

        return UIElement(
            id = UUID.randomUUID().toString(),
            className = node.className ?: "Unknown",
            resourceId = node.resourceId,
            contentDesc = node.contentDesc,
            text = node.text ?: "",
            bounds = bounds,
            isClickable = node.isClickable,
            activityName = activityName,
            packageName = packageName
        )
    }

    // Activity监听相关方法

    /**
     * 启动Activity监听
     */
    fun startActivityListening() {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "开始启动Activity监听...")
                
                // 如果已经在监听，直接返回
                if (currentActionListener?.isListening() == true) {
                    AppLogger.d(TAG, "监听器已在运行，同步UI状态")
                    _uiState.update { it.copy(isActivityListening = true) } // 同步UI状态
                    showActionFeedback(context.getString(R.string.uidebugger_activity_monitor_already_running))
                    return@launch
                }

                // 先停止现有的监听器，避免重复监听
                currentActionListener?.let { existingListener ->
                    AppLogger.d(TAG, "停止现有监听器")
                    existingListener.stopListening()
                    currentActionListener = null
                }

                AppLogger.d(TAG, "获取最高权限的监听器...")
                val (listener, status) = ActionListenerFactory.getHighestAvailableListener(context)
                AppLogger.d(TAG, "获取到监听器类型: ${listener::class.simpleName}, 权限状态: ${status.granted}")
                
                if (!status.granted) {
                    AppLogger.w(TAG, "权限不足: ${status.reason}")
                    showActionFeedback(
                        context.getString(
                            R.string.uidebugger_activity_monitor_permission_insufficient,
                            status.reason
                        )
                    )
                    return@launch
                }

                currentActionListener = listener
                AppLogger.d(TAG, "开始启动监听器...")
                val result = listener.startListening { event ->
                    AppLogger.v(TAG, "监听器回调触发: ${event.actionType} - ${event.elementInfo?.packageName}")
                    // 处理监听到的事件
                    handleActionEvent(event)
                }

                if (result.success) {
                    AppLogger.d(TAG, "监听器启动成功")
                    _uiState.update { 
                        it.copy(
                            isActivityListening = true,
                            showActivityMonitor = true,
                            activityEvents = emptyList() // 清空之前的事件
                        ) 
                    }
                    showActionFeedback(context.getString(R.string.uidebugger_activity_monitor_started))
                } else {
                    AppLogger.e(TAG, "监听器启动失败: ${result.message}")
                    showActionFeedback(
                        context.getString(
                            R.string.uidebugger_activity_monitor_start_failed_with_reason,
                            result.message
                        )
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动Activity监听失败", e)
                showActionFeedback(
                    context.getString(
                        R.string.uidebugger_activity_monitor_start_failed_with_reason,
                        e.message ?: ""
                    )
                )
            }
        }
    }

    /**
     * 停止Activity监听
     */
    fun stopActivityListening() {
        viewModelScope.launch {
            try {
                val stopped = currentActionListener?.stopListening() ?: false
                if (stopped) {
                    _uiState.update { 
                        it.copy(
                            isActivityListening = false
                        ) 
                    }
                    showActionFeedback(context.getString(R.string.uidebugger_activity_monitor_stopped))
                } else {
                    showActionFeedback(context.getString(R.string.uidebugger_activity_monitor_stop_failed))
                }
                currentActionListener = null
            } catch (e: Exception) {
                AppLogger.e(TAG, "停止Activity监听失败", e)
                showActionFeedback(
                    context.getString(
                        R.string.uidebugger_activity_monitor_stop_failed_with_reason,
                        e.message ?: ""
                    )
                )
            }
        }
    }


    /**
     * 切换Activity监听显示状态
     */
    fun toggleActivityMonitor() {
        AppLogger.d(TAG, "切换Activity监听面板显示状态")
        _uiState.update { currentState ->
            val newShowState = !currentState.showActivityMonitor
            AppLogger.d(TAG, "面板显示状态从 ${currentState.showActivityMonitor} 变更为 $newShowState")
            
            // 如果要显示面板，同步检查实际的监听状态
            if (newShowState) {
                val actualListeningState = currentActionListener?.isListening() == true
                val eventsCount = currentState.activityEvents.size
                AppLogger.d(TAG, "显示面板时同步状态: currentActionListener=${currentActionListener != null}, isListening=$actualListeningState, eventsCount=$eventsCount")
                
                // 检查AIDL连接状态
                if (currentActionListener != null && !actualListeningState) {
                    AppLogger.w(TAG, "检测到监听器存在但未监听，可能AIDL连接断开")
                }
                
                currentState.copy(
                    showActivityMonitor = newShowState,
                    isActivityListening = actualListeningState
                )
            } else {
                AppLogger.d(TAG, "隐藏面板，保持监听状态不变")
                currentState.copy(showActivityMonitor = newShowState)
            }
        }
    }

    /**
     * 清除Activity事件记录
     */
    fun clearActivityEvents() {
        _uiState.update { 
            it.copy(
                activityEvents = emptyList(),
                currentActivityName = null
            ) 
        }
    }

    /**
     * 处理监听到的Action事件
     */
    private fun handleActionEvent(event: ActionListener.ActionEvent) {
        viewModelScope.launch {
            // 过滤掉自己软件的事件
            val currentPackageName = context.packageName
            if (event.elementInfo?.packageName == currentPackageName) {
                return@launch
            }
            _uiState.update { state ->
                val newEvents = (state.activityEvents + event).takeLast(100) // 保留最近100个事件
                
                // 更新当前活动名称
                val currentActivity = event.elementInfo?.let { elementInfo ->
                    if (elementInfo.packageName != null && elementInfo.className != null) {
                        "${elementInfo.packageName}/${elementInfo.className}"
                    } else {
                        elementInfo.packageName
                    }
                }
                
                state.copy(
                    activityEvents = newEvents,
                    currentActivityName = currentActivity ?: state.currentActivityName
                )
            }
        }
    }






    /**
     * ViewModel清理时停止监听
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            currentActionListener?.stopListening()
            currentActionListener = null
        }
        // 注意：不要在这里清除单例实例，因为可能有其他地方还在使用
    }
}
