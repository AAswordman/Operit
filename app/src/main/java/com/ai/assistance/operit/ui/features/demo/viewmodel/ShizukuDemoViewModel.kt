package com.ai.assistance.operit.ui.features.demo.viewmodel

import android.app.Application
import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.demo.state.DemoStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ViewModel for the ShizukuDemoScreen Delegates most state management to DemoStateManager */
class ShizukuDemoViewModel(application: Application) : AndroidViewModel(application) {
    // 初始化时直接创建stateManager
    private val stateManager: DemoStateManager = DemoStateManager(application, viewModelScope)

    // AIToolHandler instance
    private val toolHandler: AIToolHandler = AIToolHandler.getInstance(application)

    // Expose state from the manager
    val uiState: StateFlow<com.ai.assistance.operit.ui.features.demo.state.DemoScreenState> =
            stateManager.uiState

    // Expose NodeJS and Python environment properties
    val isPnpmInstalled
        get() = stateManager.isPnpmInstalled
    val isPythonInstalled
        get() = stateManager.isPythonInstalled
    val isNodejsPythonEnvironmentReady
        get() = stateManager.isNodejsPythonEnvironmentReady

    /** Initialize the ViewModel with context data */
    fun initialize(context: Context) {
        // 初始化Root授权器
        RootAuthorizer.initialize(context)
        // 只需要调用stateManager的initialize方法
        stateManager.initialize()
    }

    /** Set loading state */
    fun setLoading(isLoading: Boolean) {
        stateManager.setLoading(isLoading)
    }

    /** Initialize the ViewModel with context data (Async version) */
    fun initializeAsync(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 初始化Root授权器 - 在IO线程进行初始化
                RootAuthorizer.initialize(context)

                // 直接从RootAuthorizer获取当前状态
                val isDeviceRooted = RootAuthorizer.isRooted.value
                val hasRootAccess = RootAuthorizer.hasRootAccess.value

                // 更新状态
                withContext(Dispatchers.Main) {
                    stateManager.updateRootStatus(isDeviceRooted, hasRootAccess)
                }

                // 调用stateManager的异步初始化方法
                stateManager.initializeAsync()
            } catch (e: Exception) {
                AppLogger.e("ShizukuDemoViewModel", "初始化时出错: ${e.message}", e)
            } finally {
                // 完成后关闭加载指示器
                withContext(Dispatchers.Main) { setLoading(false) }
            }
        }
    }

    /** Refresh app status */
    fun refreshStatus(context: Context) {
        // 检查Root状态
        checkRootStatus(context)
        stateManager.refreshStatus()
    }
    fun refreshSystemPermissions() {
        stateManager.refreshSystemPermissions()
    }

    /** Check root status */
    fun checkRootStatus(context: Context) {
        viewModelScope.launch {
            val isDeviceRooted = RootAuthorizer.isDeviceRooted()
            val hasRootAccess = RootAuthorizer.checkRootStatus(context)
            stateManager.updateRootStatus(isDeviceRooted, hasRootAccess)
            AppLogger.d(
                    "ShizukuDemoViewModel",
                    "Root状态更新: 设备已Root=$isDeviceRooted, 应用有Root权限=$hasRootAccess"
            )
        }
    }

    /** Request root permission */
    fun requestRootPermission(context: Context) {
        viewModelScope.launch {
            // 如果已有Root权限，则直接执行测试命令
            if (RootAuthorizer.hasRootAccess.value) {
                executeRootCommand("id", context)
                return@launch
            }

            // 如果没有Root权限，则先请求权限
            Toast.makeText(context, context.getString(R.string.requesting_root_permission), Toast.LENGTH_SHORT).show()

            RootAuthorizer.requestRootPermission { granted ->
                viewModelScope.launch {
                    if (granted) {
                        Toast.makeText(context, context.getString(R.string.root_permission_granted), Toast.LENGTH_SHORT).show()
                        // 权限授予后执行一个简单的测试命令
                        executeRootCommand("id", context)
                    } else {
                        Toast.makeText(context, context.getString(R.string.root_permission_denied), Toast.LENGTH_SHORT).show()
                    }
                    // 刷新状态
                    checkRootStatus(context)
                }
            }
        }
    }

    /** Execute root command */
    fun executeRootCommand(command: String, context: Context) {
        viewModelScope.launch {
            val result = RootAuthorizer.executeRootCommand(command, context)
            if (result.first) {
                Toast.makeText(context, context.getString(R.string.command_execution_success), Toast.LENGTH_SHORT).show()
                stateManager.updateResultText("${context.getString(R.string.command_execution_success)}:\n${result.second}")
            } else {
                Toast.makeText(context, context.getString(R.string.command_execution_failed), Toast.LENGTH_SHORT).show()
                stateManager.updateResultText("${context.getString(R.string.command_execution_failed)}:\n${result.second}")
            }
        }
    }

    /** Dialog management */
    fun showResultDialog(title: String, content: String) {
        stateManager.showResultDialog(title, content)
    }

    fun hideResultDialog() {
        stateManager.hideResultDialog()
    }

    /** UI visibility toggles */
    fun toggleShizukuWizard() {
        stateManager.toggleShizukuWizard()
    }

    fun toggleOperitTerminalWizard() {
        stateManager.toggleOperitTerminalWizard()
    }

    fun toggleRootWizard() {
        stateManager.toggleRootWizard()
    }

    fun toggleAccessibilityWizard() {
        stateManager.toggleAccessibilityWizard()
    }

    fun toggleAdbCommandExecutor() {
        stateManager.toggleAdbCommandExecutor()
    }

    fun toggleSampleCommands() {
        stateManager.toggleSampleCommands()
    }

    /** Command handling */
    fun updateCommandText(text: String) {
        stateManager.updateCommandText(text)
    }

    /** Refresh all registered tools */
    fun refreshTools(context: Context) {
        AppLogger.d("ShizukuDemoViewModel", "Refreshing all registered tools")
        // First clear the current tool execution state
        toolHandler.reset()

        // Re-register all default tools
        toolHandler.registerDefaultTools()

        // Show a toast notification for feedback
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.all_tools_reregistered), Toast.LENGTH_SHORT).show()
        }
    }

    /** Cleanup when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        stateManager.cleanup()
    }

    /** ViewModelFactory for creating ShizukuDemoViewModel with dependencies */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShizukuDemoViewModel::class.java)) {
                return ShizukuDemoViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
