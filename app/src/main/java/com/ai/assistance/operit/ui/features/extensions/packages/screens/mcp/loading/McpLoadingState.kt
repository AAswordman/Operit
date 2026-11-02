package com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.loading

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 表示模型上下文协议服务的加载状态。 */
enum class McpLoadingStatus {
    WAITING, // 等待加载
    LOADING, // 正在加载
    SUCCESS, // 加载成功
    FAILED // 加载失败
}

/** 单个模型上下文协议服务的加载信息。 */
data class McpLoadingItem(
        val id: String,
        val displayName: String,
        var status: McpLoadingStatus = McpLoadingStatus.WAITING,
        var message: String = "",
        var serviceName: String = ""
) {
    val shortName: String
        get() = id.split("/").lastOrNull() ?: id
}

/**
 * 模型上下文协议服务加载状态管理器
 *
 * 管理后台加载与主动重启的运行状态、结果和日志，不持有加载界面。
 */
class McpLoadingState {
    // 进度值 (0.0f - 1.0f)
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    // 当前状态消息
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    // 已启动的模型上下文协议服务数量
    private val _pluginsStarted = MutableStateFlow(0)
    val pluginsStarted: StateFlow<Int> = _pluginsStarted

    // 总模型上下文协议服务数量
    private val _pluginsTotal = MutableStateFlow(0)
    val pluginsTotal: StateFlow<Int> = _pluginsTotal

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 模型上下文协议服务列表及其状态
    private val _plugins = MutableStateFlow<List<McpLoadingItem>>(emptyList())
    val plugins: StateFlow<List<McpLoadingItem>> = _plugins

    private val _pluginLogs = MutableStateFlow<Map<String, String>>(emptyMap())
    val pluginLogs: StateFlow<Map<String, String>> = _pluginLogs

    // 应用上下文，用于获取模型上下文协议相关服务
    private var appContext: Context? = null

    private val mcpInitInProgress = AtomicBoolean(false)
    private var mcpInitJob: Job? = null

    // 保护模型上下文协议服务状态与日志的并发更新，避免回调互相覆盖
    private val pluginStateLock = Any()

    // 设置应用上下文
    fun setAppContext(context: Context) {
        this.appContext = context.applicationContext
    }

    /** 更新进度信息 */
    fun updateProgress(progress: Float) {
        _progress.value = progress
    }

    /** 更新状态消息 */
    fun updateMessage(message: String) {
        _message.value = message
    }

    /** 更新模型上下文协议服务统计 */
    fun updatePluginStats(started: Int, total: Int) {
        synchronized(pluginStateLock) {
            _pluginsStarted.value = started
            _pluginsTotal.value = total
        }
    }

    /** 设置模型上下文协议服务列表 */
    fun setPlugins(pluginIds: List<String>) {
        val context = appContext
        val plugins =
                pluginIds.map { id ->
                    // 尝试从metadata获取模型上下文协议服务名称
                    var displayName = id.split("/").lastOrNull() ?: id

                    // 如果上下文可用，尝试从元数据获取名称
                    if (context != null) {
                        try {
                            val mcpLocalServer = MCPLocalServer.getInstance(context)
                            val pluginInfo = mcpLocalServer.getPluginMetadata(id)
                            if (pluginInfo != null) {
                                displayName = pluginInfo.name
                            }
                        } catch (e: Exception) {
                            // 获取元数据失败，使用默认名称
                        }
                    }

                    McpLoadingItem(id = id, displayName = displayName)
                }
        synchronized(pluginStateLock) {
            _plugins.value = plugins
            _pluginsTotal.value = plugins.size
            _pluginsStarted.value = plugins.count { it.status == McpLoadingStatus.SUCCESS }
        }
    }

    /** 更新模型上下文协议服务状态 */
    fun updateMcpLoadingStatus(pluginId: String, status: McpLoadingStatus, message: String = "") {
        synchronized(pluginStateLock) {
            val currentPlugins = _plugins.value.toMutableList()
            val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

            if (pluginIndex >= 0) {
                val plugin = currentPlugins[pluginIndex].copy(status = status, message = message)
                currentPlugins[pluginIndex] = plugin
                _plugins.value = currentPlugins
                _pluginsStarted.value = currentPlugins.count { it.status == McpLoadingStatus.SUCCESS }
            }
        }
    }

    private fun updatePluginMessage(pluginId: String, message: String) {
        synchronized(pluginStateLock) {
            val currentPlugins = _plugins.value.toMutableList()
            val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

            if (pluginIndex >= 0) {
                val plugin = currentPlugins[pluginIndex]
                currentPlugins[pluginIndex] = plugin.copy(message = message)
                _plugins.value = currentPlugins
            }
        }
    }

    private fun appendPluginLog(pluginId: String, message: String) {
        if (message.isBlank()) return

        synchronized(pluginStateLock) {
            val maxCharsPerPlugin = 2_000_000
            val existing = _pluginLogs.value[pluginId].orEmpty()
            val combined = if (existing.isBlank()) message else "$existing\n$message"
            val trimmed =
                if (combined.length > maxCharsPerPlugin) combined.takeLast(maxCharsPerPlugin) else combined

            _pluginLogs.value = _pluginLogs.value.toMutableMap().apply {
                put(pluginId, trimmed)
            }
        }
    }

    /** 更新模型上下文协议服务注册状态 */
    fun updatePluginRegistration(pluginId: String, serviceName: String, success: Boolean) {
        synchronized(pluginStateLock) {
            val currentPlugins = _plugins.value.toMutableList()
            val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

            if (pluginIndex >= 0) {
                val plugin = currentPlugins[pluginIndex]
                val message = if (success) {
                    appContext?.getString(R.string.plugin_registered) ?: "Registered"
                } else {
                    appContext?.getString(R.string.plugin_registration_failed) ?: "Registration Failed"
                }
                currentPlugins[pluginIndex] = plugin.copy(
                    serviceName = serviceName,
                    // 不要改变主状态，只更新消息
                    message = message
                )
                _plugins.value = currentPlugins
            }
        }
    }

    /** 开始加载指定模型上下文协议服务 */
    fun startLoadingPlugin(pluginId: String) {
        updateMcpLoadingStatus(
                pluginId,
                McpLoadingStatus.LOADING,
                appContext?.getString(R.string.plugin_loading) ?: "Loading..."
        )
    }

    /** 标记模型上下文协议服务加载成功 */
    fun setPluginSuccess(pluginId: String, message: String = "") {
        updateMcpLoadingStatus(
                pluginId,
                McpLoadingStatus.SUCCESS,
                message.ifEmpty { appContext?.getString(R.string.plugin_loading_success) ?: "Loading successful" }
        )
    }

    /** 标记模型上下文协议服务加载失败 */
    fun setPluginFailed(pluginId: String, message: String = "") {
        updateMcpLoadingStatus(
                pluginId,
                McpLoadingStatus.FAILED,
                message.ifEmpty { appContext?.getString(R.string.plugin_loading_failed) ?: "Loading failed" }
        )
    }

    /** 新批次开始时清理结果；并发启动请求不会重置正在更新的日志。 */
    private fun resetProgress() {
        _progress.value = 0f
        _message.value = ""
        synchronized(pluginStateLock) {
            _pluginsStarted.value = 0
            _pluginsTotal.value = 0
            _plugins.value = emptyList()
            _pluginLogs.value = emptyMap()
        }
    }

    fun cancel() {
        mcpInitJob?.cancel()
    }

    /** 检查配置后静默加载；没有启用的服务时不初始化启动器和桥接。 */
    fun startConfiguredServers(context: Context, lifecycleScope: CoroutineScope) {
        if (!mcpInitInProgress.compareAndSet(false, true)) {
            AppLogger.d("McpLoadingState", "模型上下文协议服务正在加载，复用当前批次")
            return
        }

        resetProgress()
        _isLoading.value = true
        mcpInitJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                updateMessage(context.getString(R.string.plugin_loading_list))
                updateProgress(0.05f)
                val mcpLocalServer = MCPLocalServer.getInstance(context)
                val mcpRepository = MCPRepository(context)
                mcpRepository.refreshPluginList()
                val installedPlugins = mcpRepository.installedPluginIds.first()
                val (pluginsToStart, disabledPlugins) = installedPlugins.partition(mcpLocalServer::isServerEnabled)
                setPlugins(pluginsToStart)

                if (pluginsToStart.isEmpty()) {
                    mcpRepository.unregisterToolsForPlugins(disabledPlugins)
                    AppLogger.d("McpLoadingState", "没有可启动的模型上下文协议服务")
                    updateMessage(context.getString(R.string.plugin_no_plugins_to_start))
                    updateProgress(1f)
                    return@launch
                }

                updateMessage(context.getString(R.string.plugin_preparing, pluginsToStart.size))
                updateProgress(0.35f)
                val mcpStarter = MCPStarter(context)
                val listener = createPluginStartProgressListener(mcpLocalServer, context)
                val startupJob = mcpStarter.startAllDeployedPlugins(listener)
                try {
                    startupJob.join()
                } catch (cancelled: CancellationException) {
                    startupJob.cancel()
                    throw cancelled
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("McpLoadingState", "加载模型上下文协议服务失败", error)
                updateMessage(error.message ?: context.getString(R.string.plugin_loading_failed))
                updateProgress(1f)
            } finally {
                _isLoading.value = false
                mcpInitInProgress.set(false)
            }
        }
    }

    // 创建模型上下文协议服务启动进度监听器
    private fun createPluginStartProgressListener(
            mcpLocalServer: MCPLocalServer,
            context: Context
    ): MCPStarter.PluginStartProgressListener {
        return object : MCPStarter.PluginStartProgressListener {
            override fun onPluginStarting(pluginId: String, index: Int, total: Int) {
                // 在这里检查模型上下文协议服务是否被启用
                val isEnabled = mcpLocalServer.isServerEnabled(pluginId) // 从配置读取

                // 更新总体状态
                val disabledSuffix =
                        if (!isEnabled) context.getString(R.string.plugin_disabled_suffix) else ""
                updateMessage(
                        context.getString(
                                R.string.plugin_starting_number,
                                index,
                                total,
                                disabledSuffix
                        )
                )
                updateProgress(0.4f + 0.1f * (index.toFloat() / total)) // 注册占10% (0.4 -> 0.5)

                // 更新特定模型上下文协议服务状态
                startLoadingPlugin(pluginId)
                appendPluginLog(pluginId, "START")
            }

            override fun onPluginRegistered(pluginId: String, serviceName: String, success: Boolean) {
                updatePluginRegistration(pluginId, serviceName, success)
                if (!success) {
                    val lastLogLine = _pluginLogs.value[pluginId]?.lineSequence()?.lastOrNull().orEmpty()
                    val message = lastLogLine.ifBlank { context.getString(R.string.plugin_registration_failed) }
                    setPluginFailed(pluginId, message)
                }
            }

            override fun onPluginStarted(
                    pluginId: String,
                    success: Boolean,
                    index: Int,
                    total: Int
            ) {
                // 记录模型上下文协议服务加载结果
                if (success) {
                    setPluginSuccess(pluginId)
                } else {
                    setPluginFailed(pluginId, context.getString(R.string.plugin_verification_failed))
                }

                // 更新总体进度
                updateProgress(0.5f + 0.5f * (index.toFloat() / total)) // 验证和处理占50% (0.5 -> 1.0)
            }

            override fun onPluginLog(pluginId: String, message: String) {
                appendPluginLog(pluginId, message)
                val brief = message.lineSequence().firstOrNull().orEmpty().take(160)
                updatePluginMessage(pluginId, brief)
            }

            override fun onAllPluginsStarted(
                    successCount: Int,
                    totalCount: Int,
                    status: MCPStarter.PluginInitStatus
            ) {
                // 汇总状态供管理页和工具读取，失败不触发弹窗或操作引导
                when (status) {
                    MCPStarter.PluginInitStatus.NODEJS_MISSING -> {
                        updateMessage(context.getString(R.string.plugin_nodejs_missing))
                    }
                    MCPStarter.PluginInitStatus.BRIDGE_FAILED -> {
                        updateMessage(context.getString(R.string.plugin_bridge_failed))
                    }
                    MCPStarter.PluginInitStatus.OTHER_ERROR -> {
                        updateMessage(context.getString(R.string.plugin_other_error))
                    }
                    else -> {
                        // 所有模型上下文协议服务加载完成
                        val successRate =
                                if (totalCount > 0) {
                                    (successCount * 100) / totalCount
                                } else {
                                    0 // 当没有部署的模型上下文协议服务时，成功率为0
                                }

                        // 工具注册将在验证阶段自动进行，无需在此处触发

                        // 保留各服务的加载结果和汇总消息
                        if (successCount < totalCount && totalCount > 0) {
                            updateMessage(
                                    context.getString(
                                            R.string.plugin_complete_with_failures,
                                            successRate
                                    )
                            )
                        } else if (totalCount > 0) {
                            updateMessage(
                                    context.getString(R.string.plugin_complete_success, successRate)
                            )
                        } else {
                            updateMessage(context.getString(R.string.plugin_no_plugins_to_start))
                        }
                    }
                }

                updateProgress(1.0f)
            }
        }
    }
}
