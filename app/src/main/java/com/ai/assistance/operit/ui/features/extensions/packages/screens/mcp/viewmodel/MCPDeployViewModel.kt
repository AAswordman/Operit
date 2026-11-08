package com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MCP部署ViewModel
 *
 * 负责处理插件部署业务逻辑
 */
class MCPDeployViewModel(private val context: Context, private val mcpRepository: MCPRepository) :
        ViewModel() {

    private val mcpDeployer = MCPDeployer(context)

    // 部署状态
    private val _deploymentStatus =
            MutableStateFlow<MCPDeployer.DeploymentStatus>(MCPDeployer.DeploymentStatus.NotStarted)
    val deploymentStatus: StateFlow<MCPDeployer.DeploymentStatus> = _deploymentStatus.asStateFlow()

    // 当前正在部署的插件ID
    private val _currentDeployingPlugin = MutableStateFlow<String?>(null)
    val currentDeployingPlugin: StateFlow<String?> = _currentDeployingPlugin.asStateFlow()

    // 部署输出消息
    private val _outputMessages = MutableStateFlow<List<String>>(emptyList())
    val outputMessages: StateFlow<List<String>> = _outputMessages.asStateFlow()

    // 生成的部署命令
    private val _generatedCommands = MutableStateFlow<List<String>>(emptyList())
    val generatedCommands: StateFlow<List<String>> = _generatedCommands.asStateFlow()

    // 环境变量
    private val _environmentVariables = MutableStateFlow<Map<String, String>>(emptyMap())
    val environmentVariables: StateFlow<Map<String, String>> = _environmentVariables.asStateFlow()

    /**
     * 获取部署命令 - 在部署前调用，用于显示和编辑
     *
     * @param pluginId 插件ID
     * @return 是否成功获取命令
     */
    fun getDeployCommands(pluginId: String): Boolean {
        // 获取插件路径
        val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)
        if (pluginPath == null) {
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_cannot_get_path, pluginId))
            return false
        }

        // 对于虚拟路径（npx/uvx 插件），直接返回空命令列表
        if (pluginPath.startsWith("virtual://")) {
            _generatedCommands.value = emptyList()
            return true
        }

        // 在协程中调用挂起函数
        viewModelScope.launch {
            try {
                // 使用MCPDeployer的getDeployCommands方法
                val deployCommands = mcpDeployer.getDeployCommands(pluginId, pluginPath)

                if (deployCommands.isEmpty()) {
                    _deploymentStatus.value =
                            MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_cannot_determine))
                } else {
                    // 保存生成的命令
                    _generatedCommands.value = deployCommands
                }
            } catch (e: Exception) {
                _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_analysis_failed, e.message ?: ""))
            }
        }

        return true
    }

    /**
     * 设置环境变量
     *
     * @param envVars 环境变量键值对
     */
    fun setEnvironmentVariables(envVars: Map<String, String>) {
        _environmentVariables.value = envVars
    }

    /**
     * 添加单个环境变量
     *
     * @param key 变量名
     * @param value 变量值
     */
    fun addEnvironmentVariable(key: String, value: String) {
        val currentVars = _environmentVariables.value.toMutableMap()
        currentVars[key] = value
        _environmentVariables.value = currentVars
    }

    /**
     * 移除环境变量
     *
     * @param key 要移除的环境变量名
     */
    fun removeEnvironmentVariable(key: String) {
        val currentVars = _environmentVariables.value.toMutableMap()
        currentVars.remove(key)
        _environmentVariables.value = currentVars
    }

    /** 清除环境变量 */
    fun clearEnvironmentVariables() {
        _environmentVariables.value = emptyMap()
    }

    /**
     * 部署插件（使用默认生成的命令）
     *
     * @param pluginId 要部署的插件ID
     */
    fun deployPlugin(pluginId: String) {
        viewModelScope.launch {
            val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)
            if (pluginPath == null) {
                _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_cannot_get_path, pluginId))
                return@launch
            }

            // 对于 npx/uvx 类型的插件（虚拟路径），使用空命令列表直接部署
            if (pluginPath.startsWith("virtual://")) {
                deployPluginWithCommands(pluginId, emptyList())
                return@launch
            }

            // 确保命令已生成（普通插件）
            if (_generatedCommands.value.isEmpty()) {
                // 先获取命令
                val deployCommands = try {
                    mcpDeployer.getDeployCommands(pluginId, pluginPath)
                } catch (e: Exception) {
                    _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_analysis_failed, e.message ?: ""))
                    return@launch
                }

                if (deployCommands.isEmpty()) {
                    _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_cannot_determine))
                    return@launch
                }

                // 保存生成的命令
                _generatedCommands.value = deployCommands
            }

            // 使用生成的命令部署
            deployPluginWithCommands(pluginId, _generatedCommands.value)
        }
    }

    /**
     * 使用自定义命令部署插件
     *
     * @param pluginId 要部署的插件ID
     * @param customCommands 自定义命令列表
     */
    fun deployPluginWithCommands(pluginId: String, customCommands: List<String>) {
        // 获取插件路径
        val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)
        if (pluginPath == null) {
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_cannot_get_path, pluginId))
            return
        }

        // 清除之前的输出
        _outputMessages.value = emptyList()

        // 设置当前部署的插件
        _currentDeployingPlugin.value = pluginId

        // 开始部署
        viewModelScope.launch {
            // 更新状态
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.InProgress(context.getString(R.string.mcp_deploy_status_preparing))

            // 执行部署，包含环境变量
            mcpDeployer.deployPluginWithCommands(
                    pluginId = pluginId,
                    pluginPath = pluginPath,
                    customCommands = customCommands,
                    environmentVariables = _environmentVariables.value,
                    statusCallback = { status ->
                        var consumedAsOutput = false

                        // 收集输出消息
                        if (status is MCPDeployer.DeploymentStatus.InProgress) {
                            val outputPrefix = context.getString(R.string.mcp_deployment_output_prefix).trimEnd()
                            val legacyPrefix = context.getString(R.string.mcp_deploy_output_prefix).trimEnd()
                            val rawMessage = status.message

                            val outputContent = when {
                                rawMessage.startsWith(outputPrefix) ->
                                    rawMessage.removePrefix(outputPrefix).trimStart()
                                rawMessage.startsWith(legacyPrefix) ->
                                    rawMessage.removePrefix(legacyPrefix).trimStart()
                                else -> null
                            }

                            if (!outputContent.isNullOrEmpty()) {
                                val newLines = outputContent
                                    .replace("\r\n", "\n")
                                    .split('\n')
                                    .filter { it.isNotBlank() }
                                if (newLines.isNotEmpty()) {
                                    _outputMessages.value = _outputMessages.value + newLines
                                    consumedAsOutput = true
                                }
                            }
                        }

                        if (!consumedAsOutput) {
                            _deploymentStatus.value = status
                        }
                    }
            )
        }
    }

    /** 重置部署状态 */
    fun resetDeploymentState() {
        _deploymentStatus.value = MCPDeployer.DeploymentStatus.NotStarted
        _currentDeployingPlugin.value = null
        _outputMessages.value = emptyList()
        _generatedCommands.value = emptyList()
        // 不重置环境变量，因为可能需要在多次部署中重用
    }

    /** 工厂类 */
    class Factory(private val context: Context, private val mcpRepository: MCPRepository) :
            ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPDeployViewModel::class.java)) {
                return MCPDeployViewModel(context, mcpRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
