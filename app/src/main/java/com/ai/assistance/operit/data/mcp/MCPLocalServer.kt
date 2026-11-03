package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.OperitPaths
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * 统一的MCP配置管理中心
 * 
 * 负责管理所有MCP相关的配置，包括：
 * - 官方MCP配置格式的读写
 * - 插件配置管理
 * - 服务器状态管理
 * - 统一存储在下载/Operit/mcp_plugins目录
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PATH = "server_path"
        
        // 配置文件名称
        private const val MCP_CONFIG_FILE = "mcp_config.json"
        private const val SERVER_STATUS_FILE = "server_status.json"

        @Volatile private var INSTANCE: MCPLocalServer? = null

        fun getInstance(context: Context): MCPLocalServer {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: MCPLocalServer(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 持久化配置
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 配置目录路径
    private val configBaseDir by lazy {
        OperitPaths.mcpPluginsDir()
    }

    // 配置文件路径
    private val mcpConfigFile get() = File(configBaseDir, MCP_CONFIG_FILE)
    private val serverStatusFile get() = File(configBaseDir, SERVER_STATUS_FILE)

    // 服务路径
    private val _serverPath = MutableStateFlow(configBaseDir.absolutePath)
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()

    // 配置状态
    private val _mcpConfig = MutableStateFlow(MCPConfig())
    val mcpConfig: StateFlow<MCPConfig> = _mcpConfig.asStateFlow()

    // 插件元数据 - 现在从MCPConfig派生
    val pluginMetadata: StateFlow<Map<String, PluginMetadata>> = _mcpConfig
        .map { it.pluginMetadata.toMap() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    // 服务器状态
    private val _serverStatus = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val serverStatus: StateFlow<Map<String, ServerStatus>> = _serverStatus.asStateFlow()

    // Gson实例 - 使用格式化输出
    private val gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

    init {
        // 初始化时加载所有配置
        loadAllConfigurations()
    }

    // ==================== 官方MCP配置格式支持 ====================

    /**
     * 官方MCP配置格式数据结构
     */
    @Serializable
    data class MCPConfig(
        @SerializedName("mcpServers")
        val mcpServers: MutableMap<String, ServerConfig> = mutableMapOf(),
        @SerializedName("pluginMetadata")
        val pluginMetadata: MutableMap<String, PluginMetadata> = mutableMapOf()
    ) {
        @Serializable
        data class ServerConfig(
            @SerializedName("command")
            val command: String,
            @SerializedName("args")
            val args: List<String>? = emptyList(),
            @SerializedName("disabled")
            val disabled: Boolean = false,
            @SerializedName("autoApprove")
            val autoApprove: List<String>? = emptyList(),
            @SerializedName("env")
            val env: Map<String, String>? = emptyMap(),
            // Standard MCP stdio field. Keeping it in the model is what allows a server that
            // lives outside ~/mcp_plugins to be started with its own working directory.
            @SerializedName("cwd")
            val cwd: String? = null
        )
    }

    /**
     * 插件元数据
     */
    @Serializable
    data class PluginMetadata(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String,
        @SerializedName("logoUrl")
        val logoUrl: String? = null,
        @SerializedName("author")
        val author: String = "Unknown",
        @SerializedName("isInstalled")
        val isInstalled: Boolean = false,
        @SerializedName("version")
        val version: String = "",
        @SerializedName("updatedAt")
        val updatedAt: String = "",
        @SerializedName("longDescription")
        val longDescription: String = "",
        @SerializedName("repoUrl")
        val repoUrl: String = "",
        // 新增字段以支持远程服务
        @SerializedName("type")
        val type: String = "local", // "local" or "remote"
        @SerializedName("endpoint")
        val endpoint: String? = null,
        @SerializedName("connectionType")
        val connectionType: String? = "httpStream",
        @SerializedName("disabled")
        val disabled: Boolean = false,
        // 认证相关字段（用于远程服务）
        @SerializedName("bearerToken")
        val bearerToken: String? = null,
        @SerializedName("headers")
        val headers: Map<String, String>? = null,
        // 本地安装相关字段
        @SerializedName("installedPath")
        val installedPath: String? = null,
        @SerializedName("installedTime")
        val installedTime: Long = System.currentTimeMillis(),
        // 市场配置（来自 GitHub Issue）
        @SerializedName("marketConfig")
        val marketConfig: String? = null
    )

    /**
     * 服务器运行状态
     * 注意：启用/禁用状态已移至ServerConfig.disabled字段
     */
    @Serializable
    data class ServerStatus(
        @SerializedName("serverId")
        val serverId: String,
        @SerializedName("lastStartTime")
        val lastStartTime: Long = 0L,
        @SerializedName("lastStopTime")
        val lastStopTime: Long = 0L,
        @SerializedName("errorMessage")
        val errorMessage: String? = null,
        @SerializedName("cachedTools")
        val cachedTools: List<CachedToolInfo>? = null,
        @SerializedName("toolsCachedTime")
        val toolsCachedTime: Long = 0L
    )

    /**
     * 缓存的工具信息
     */
    @Serializable
    data class CachedToolInfo(
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String = "",
        @SerializedName("inputSchema")
        val inputSchema: String = "{}", // JSON字符串形式的schema
        @SerializedName("cachedAt")
        val cachedAt: Long = System.currentTimeMillis()
    )

    // ==================== 配置文件操作 ====================
    
    /**
     * 重新加载配置文件（用于用户手动编辑配置后刷新）
     */
    suspend fun reloadConfigurations() {
        withContext(Dispatchers.IO) {
            loadAllConfigurations()
            AppLogger.d(TAG, "配置已重新加载")
        }
    }

    /**
     * 加载所有配置文件
     */
    private fun loadAllConfigurations() {
        try {
            // 加载MCP配置
            if (mcpConfigFile.exists()) {
                val configJson = mcpConfigFile.readText()
                val rawConfig = gson.fromJson(configJson, MCPConfig::class.java) ?: MCPConfig()
                val sanitizedConfig = sanitizeMCPConfig(rawConfig, "loadAllConfigurations")
                
                // 自动为 mcpServers 中存在但 pluginMetadata 中缺失的服务器创建默认元数据
                val updatedConfig = autoFillMissingMetadata(sanitizedConfig.config)
                _mcpConfig.value = updatedConfig
                
                if (updatedConfig != rawConfig) {
                    coroutineScope.launch {
                        saveMCPConfig()
                        val createdMetadataCount =
                            (updatedConfig.pluginMetadata.size - sanitizedConfig.config.pluginMetadata.size)
                                .coerceAtLeast(0)
                        if (createdMetadataCount > 0) {
                            AppLogger.d(TAG, "自动创建了 $createdMetadataCount 个缺失的插件元数据")
                        }
                        if (sanitizedConfig.removedServerIds.isNotEmpty() || sanitizedConfig.removedMetadataIds.isNotEmpty()) {
                            AppLogger.d(TAG, "已持久化清理后的MCP配置")
                        }
                    }
                }
            }

            // 加载服务器状态
            if (serverStatusFile.exists()) {
                val statusJson = serverStatusFile.readText()
                val hasLegacyActiveField = statusJson.contains("\"active\"")
                val typeToken = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val status = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken) ?: emptyMap()
                _serverStatus.value = status
                if (hasLegacyActiveField) {
                    coroutineScope.launch {
                        saveServerStatus()
                        AppLogger.d(TAG, "已迁移 server_status.json：移除 legacy active 字段")
                    }
                }
            }
            
            // 为新配置的服务器初始化状态
            initializeMissingServerStatus()

            AppLogger.d(TAG, "配置加载完成 - MCP服务器: ${_mcpConfig.value.mcpServers.size}, 插件元数据: ${_mcpConfig.value.pluginMetadata.size}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载配置时出错", e)
        }
    }
    
    /**
     * 自动为缺失的服务器创建默认元数据
     */
    private fun autoFillMissingMetadata(config: MCPConfig): MCPConfig {
        val newMetadata = config.pluginMetadata.toMutableMap()
        var hasNewMetadata = false
        
        config.mcpServers.forEach { (serverId, serverConfig) ->
            if (!newMetadata.containsKey(serverId)) {
                // 从 serverId 生成友好的显示名称
                val displayName = displayNameForServerId(serverId)
                
                // 创建默认元数据
                val metadata = PluginMetadata(
                    id = serverId,
                    name = displayName,
                    description = "",
                    logoUrl = null,
                    author = "Unknown",
                    isInstalled = true,
                    version = "1.0.0",
                    updatedAt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    longDescription = context.getString(R.string.mcp_local_auto_detected_server),
                    repoUrl = "",
                    type = "local",
                    endpoint = null,
                    connectionType = "httpStream"
                )
                
                newMetadata[serverId] = metadata
                hasNewMetadata = true
                AppLogger.d(TAG, "自动创建元数据: $serverId -> $displayName")
            }
        }
        
        return if (hasNewMetadata) {
            config.copy(pluginMetadata = newMetadata)
        } else {
            config
        }
    }

    private fun displayNameForServerId(serverId: String): String {
        return serverId
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { character -> character.uppercase() } }
    }

    private fun createRemoteMetadata(
        server: RemoteMcpImportedServer,
        existingMetadata: PluginMetadata?
    ): PluginMetadata {
        val baseMetadata = existingMetadata?.takeIf { it.type == "remote" }
            ?: PluginMetadata(
                id = server.id,
                name = displayNameForServerId(server.id),
                description = "",
                isInstalled = true,
                version = "1.0.0",
                type = "remote"
            )

        return baseMetadata.copy(
            type = "remote",
            isInstalled = true,
            endpoint = server.endpoint,
            connectionType = server.connectionType,
            disabled = server.disabled,
            bearerToken = null,
            headers = server.headers,
            installedTime = System.currentTimeMillis()
        )
    }

    private data class SanitizedConfigResult(
        val config: MCPConfig,
        val removedServerIds: List<String>,
        val removedMetadataIds: List<String>
    )

    private fun sanitizeServerConfig(
        serverId: String,
        serverConfig: MCPConfig.ServerConfig,
        source: String
    ): MCPConfig.ServerConfig? {
        val command = serverConfig.command?.trim()
        if (command.isNullOrEmpty()) {
            AppLogger.w(TAG, "忽略无效MCP服务器配置: $serverId, source=$source, command为空")
            return null
        }

        val args = serverConfig.args?.mapNotNull { it } ?: emptyList()
        val autoApprove = serverConfig.autoApprove?.mapNotNull { it } ?: emptyList()
        val env = serverConfig.env?.entries?.mapNotNull { entry ->
            val key = entry.key?.takeIf { it.isNotBlank() }
            val value = entry.value
            if (key == null || value == null) null else key to value
        }?.toMap() ?: emptyMap()

        return MCPConfig.ServerConfig(
            command = command,
            args = args,
            disabled = serverConfig.disabled,
            autoApprove = autoApprove,
            env = env,
            cwd = serverConfig.cwd?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private suspend fun resetRuntimeState(serverId: String) = withContext(Dispatchers.IO) {
        val toolHandler = AIToolHandler.getInstance(context)
        toolHandler.getAllToolNames()
            .filter { it.startsWith("$serverId:") }
            .forEach(toolHandler::unregisterTool)
        MCPManager.getInstance(context).unregisterServer(serverId)
        removeServerStatus(serverId)
    }

    private fun hasLocalRuntimeConfigurationChanged(
        existing: MCPConfig.ServerConfig,
        replacement: MCPConfig.ServerConfig
    ): Boolean {
        return existing.command != replacement.command ||
            existing.args.orEmpty() != replacement.args.orEmpty() ||
            existing.env.orEmpty() != replacement.env.orEmpty() ||
            // Changing cwd must reset the runtime, otherwise a running process keeps the old
            // working directory and the next start silently uses stale state.
            existing.cwd != replacement.cwd
    }

    private fun hasRemoteRuntimeConfigurationChanged(
        existing: PluginMetadata,
        replacement: PluginMetadata
    ): Boolean {
        return existing.type != "remote" ||
            existing.endpoint != replacement.endpoint ||
            existing.connectionType != replacement.connectionType ||
            existing.bearerToken != replacement.bearerToken ||
            existing.headers.orEmpty() != replacement.headers.orEmpty()
    }

    private fun sanitizeMCPConfig(config: MCPConfig, source: String): SanitizedConfigResult {
        val sanitizedServers = mutableMapOf<String, MCPConfig.ServerConfig>()
        val removedServerIds = mutableListOf<String>()

        config.mcpServers.forEach { (serverId, serverConfig) ->
            val sanitizedServer = sanitizeServerConfig(serverId, serverConfig, source)
            if (sanitizedServer != null) {
                sanitizedServers[serverId] = sanitizedServer
            } else {
                removedServerIds.add(serverId)
            }
        }

        val sanitizedMetadata = config.pluginMetadata.toMutableMap()
        val removedMetadataIds = removedServerIds.filter { serverId ->
            sanitizedMetadata[serverId]?.type != "remote"
        }
        removedMetadataIds.forEach { serverId ->
            sanitizedMetadata.remove(serverId)
        }

        if (removedServerIds.isNotEmpty()) {
            AppLogger.w(
                TAG,
                "已清理无效MCP服务器配置: ${removedServerIds.joinToString()}" +
                    if (removedMetadataIds.isNotEmpty()) {
                        ", 同步移除本地元数据: ${removedMetadataIds.joinToString()}"
                    } else {
                        ""
                    }
            )
        }

        return SanitizedConfigResult(
            config = config.copy(
                mcpServers = sanitizedServers,
                pluginMetadata = sanitizedMetadata
            ),
            removedServerIds = removedServerIds,
            removedMetadataIds = removedMetadataIds
        )
    }
    
    /**
     * 为新配置的服务器初始化状态
     */
    private fun initializeMissingServerStatus() {
        val currentStatus = _serverStatus.value.toMutableMap()
        var hasNewStatus = false
        
        val configuredServerIds = _mcpConfig.value.mcpServers.keys + _mcpConfig.value.pluginMetadata.keys
        configuredServerIds.forEach { serverId ->
            if (!currentStatus.containsKey(serverId)) {
                currentStatus[serverId] = ServerStatus(
                    serverId = serverId,
                    lastStartTime = 0L,
                    lastStopTime = 0L,
                    errorMessage = null
                )
                hasNewStatus = true
                AppLogger.d(TAG, "初始化服务器状态: $serverId")
            }
        }
        
        if (hasNewStatus) {
            _serverStatus.value = currentStatus
            coroutineScope.launch {
                saveServerStatus()
            }
        }
    }

    /**
     * 保存MCP配置
     */
    suspend fun saveMCPConfig() {
        try {
            val configJson = gson.toJson(_mcpConfig.value)
            mcpConfigFile.writeText(configJson)
            AppLogger.d(TAG, "MCP配置已保存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存MCP配置时出错", e)
        }
    }

    /**
     * 保存服务器状态
     */
    suspend fun saveServerStatus() {
        try {
            val statusJson = gson.toJson(_serverStatus.value)
            serverStatusFile.writeText(statusJson)
            AppLogger.d(TAG, "服务器状态已保存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存服务器状态时出错", e)
        }
    }

    // ==================== MCP服务器管理 ====================

    /**
     * 添加或更新MCP服务器配置
     */
    suspend fun addOrUpdateMCPServer(
        serverId: String,
        command: String,
        args: List<String>? = emptyList(),
        env: Map<String, String>? = emptyMap(),
        disabled: Boolean = false,
        autoApprove: List<String>? = emptyList(),
        cwd: String? = null
    ) {
        val normalizedCommand = command?.trim()
        require(!normalizedCommand.isNullOrEmpty()) { "MCP服务器 $serverId 的 command 不能为空" }

        val updatedServerConfig = MCPConfig.ServerConfig(
            command = normalizedCommand,
            args = args?.mapNotNull { it } ?: emptyList(),
            disabled = disabled,
            autoApprove = autoApprove?.mapNotNull { it } ?: emptyList(),
            env = env?.entries?.mapNotNull { entry ->
                val key = entry.key?.takeIf { it.isNotBlank() }
                val value = entry.value
                if (key == null || value == null) null else key to value
            }?.toMap() ?: emptyMap(),
            cwd = cwd?.trim()?.takeIf { it.isNotEmpty() }
        )
        var shouldResetRuntime = false
        _mcpConfig.update { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            val previousServerConfig = newServers[serverId]
            val previousMetadata = newMetadata[serverId]
            shouldResetRuntime =
                (previousServerConfig != null &&
                    hasLocalRuntimeConfigurationChanged(previousServerConfig, updatedServerConfig)) ||
                    previousMetadata?.type == "remote"
            newServers[serverId] = updatedServerConfig
            if (newMetadata[serverId]?.type == "remote") {
                newMetadata.remove(serverId)
            }
            autoFillMissingMetadata(
                currentConfig.copy(mcpServers = newServers, pluginMetadata = newMetadata)
            )
        }
        saveMCPConfig()
        if (shouldResetRuntime) {
            resetRuntimeState(serverId)
        }
        AppLogger.d(TAG, "MCP服务器配置已更新: $serverId")
    }

    /**
     * 删除MCP服务器配置
     */
    suspend fun removeMCPServer(serverId: String) {
        _mcpConfig.update { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            newServers.remove(serverId)
            currentConfig.copy(mcpServers = newServers)
        }
        saveMCPConfig()

        // 同时清理相关的元数据和状态
        removePluginMetadata(serverId)
        removeServerStatus(serverId)
        
        AppLogger.d(TAG, "MCP服务器配置已删除: $serverId")
    }

    /**
     * 合并JSON配置到现有配置
     */
    suspend fun mergeConfigFromJson(jsonConfig: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "开始合并配置，输入长度: ${jsonConfig.length}")
                val parsedConfig = try {
                    McpConfigImportParser.parse(jsonConfig)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "标准 MCP 配置解析失败", e)
                    return@withContext Result.failure(
                        Exception(
                            context.getString(
                                R.string.mcp_local_json_format_error,
                                e.message ?: "配置字段无效"
                            )
                        )
                    )
                }

                val runtimeResetIds = mutableSetOf<String>()
                _mcpConfig.update { currentConfig ->
                    val newServers = currentConfig.mcpServers.toMutableMap()
                    val newMetadata = currentConfig.pluginMetadata.toMutableMap()

                    parsedConfig.servers.forEach { importedServer ->
                        when (importedServer) {
                            is StdioMcpImportedServer -> {
                                val replacement = MCPConfig.ServerConfig(
                                    command = importedServer.command,
                                    args = importedServer.args,
                                    disabled = importedServer.disabled,
                                    autoApprove = importedServer.autoApprove,
                                    env = importedServer.env,
                                    cwd = importedServer.cwd
                                )
                                val existingServer = newServers[importedServer.id]
                                val existingMetadata = newMetadata[importedServer.id]
                                if ((existingServer != null &&
                                        hasLocalRuntimeConfigurationChanged(existingServer, replacement)) ||
                                    existingMetadata?.type == "remote") {
                                    runtimeResetIds.add(importedServer.id)
                                }
                                newServers[importedServer.id] = replacement
                                if (newMetadata[importedServer.id]?.type == "remote") {
                                    newMetadata.remove(importedServer.id)
                                }
                            }

                            is RemoteMcpImportedServer -> {
                                val replacement = createRemoteMetadata(
                                    importedServer,
                                    newMetadata[importedServer.id]
                                )
                                val existingServer = newServers[importedServer.id]
                                val existingMetadata = newMetadata[importedServer.id]
                                if (existingServer != null ||
                                    (existingMetadata != null &&
                                        hasRemoteRuntimeConfigurationChanged(
                                            existingMetadata,
                                            replacement
                                        ))) {
                                    runtimeResetIds.add(importedServer.id)
                                }
                                newServers.remove(importedServer.id)
                                newMetadata[importedServer.id] = replacement
                            }
                        }
                    }

                    autoFillMissingMetadata(
                        currentConfig.copy(
                            mcpServers = newServers,
                            pluginMetadata = newMetadata
                        )
                    )
                }

                runtimeResetIds.forEach { serverId -> resetRuntimeState(serverId) }
                saveMCPConfig()

                val localCount = parsedConfig.servers.count { it is StdioMcpImportedServer }
                val remoteCount = parsedConfig.servers.size - localCount
                AppLogger.i(
                    TAG,
                    "成功合并 ${parsedConfig.servers.size} 个服务器配置（stdio=$localCount, remote=$remoteCount）"
                )
                Result.success(parsedConfig.servers.size)
            } catch (e: Exception) {
                AppLogger.e(TAG, "合并配置失败: ${e.message}", e)
                Result.failure(Exception(context.getString(R.string.mcp_local_merge_config_failed, e.message)))
            }
        }
    }

    /**
     * 获取配置文件路径
     */
    fun getConfigFilePath(): String = mcpConfigFile.absolutePath

    /**
     * 获取MCP服务器配置
     */
    fun getMCPServer(serverId: String): MCPConfig.ServerConfig? {
        return _mcpConfig.value.mcpServers[serverId]
    }

    /**
     * 获取所有MCP服务器配置
     */
    fun getAllMCPServers(): Map<String, MCPConfig.ServerConfig> {
        return _mcpConfig.value.mcpServers.toMap()
    }

    // ==================== 插件元数据管理 ====================

    /**
     * 添加或更新插件元数据
     */
    suspend fun addOrUpdatePluginMetadata(metadata: PluginMetadata) {
        var shouldResetRuntime = false
        _mcpConfig.update { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            if (metadata.type == "remote") {
                val existingMetadata = newMetadata[metadata.id]
                shouldResetRuntime =
                    newServers.containsKey(metadata.id) ||
                        (existingMetadata != null &&
                            hasRemoteRuntimeConfigurationChanged(existingMetadata, metadata))
                newServers.remove(metadata.id)
            }
            newMetadata[metadata.id] = metadata
            currentConfig.copy(mcpServers = newServers, pluginMetadata = newMetadata)
        }
        saveMCPConfig()
        if (metadata.type == "remote" && shouldResetRuntime) {
            resetRuntimeState(metadata.id)
        }
        AppLogger.d(TAG, "插件元数据已更新: ${metadata.id} - ${metadata.name}")
    }

    /**
     * 删除插件元数据
     */
    suspend fun removePluginMetadata(pluginId: String) {
        _mcpConfig.update { currentConfig ->
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            newMetadata.remove(pluginId)
            currentConfig.copy(pluginMetadata = newMetadata)
        }
        saveMCPConfig()
        AppLogger.d(TAG, "插件元数据已删除: $pluginId")
    }

    /**
     * 获取插件元数据
     */
    fun getPluginMetadata(pluginId: String): PluginMetadata? {
        return _mcpConfig.value.pluginMetadata[pluginId]
    }

    /**
     * 获取所有插件元数据
     */
    fun getAllPluginMetadata(): Map<String, PluginMetadata> {
        return _mcpConfig.value.pluginMetadata.toMap()
    }

    // ==================== 服务器状态管理 ====================

    /**
     * 更新服务器状态
     * 注意：启用/禁用状态请使用 setServerEnabled() 方法
     */
    suspend fun updateServerStatus(
        serverId: String,
        errorMessage: String? = null,
        cachedTools: List<CachedToolInfo>? = null,
        lastStartTime: Long? = null,
        lastStopTime: Long? = null
    ) {
        val currentStatus = _serverStatus.value.toMutableMap()
        val existingStatus = currentStatus[serverId] ?: ServerStatus(serverId)
        
        val updatedStatus = existingStatus.copy(
            errorMessage = errorMessage ?: existingStatus.errorMessage,
            cachedTools = cachedTools ?: existingStatus.cachedTools,
            toolsCachedTime = if (cachedTools != null) System.currentTimeMillis() else existingStatus.toolsCachedTime,
            lastStartTime = lastStartTime ?: existingStatus.lastStartTime,
            lastStopTime = lastStopTime ?: existingStatus.lastStopTime
        )
        
        currentStatus[serverId] = updatedStatus
        _serverStatus.value = currentStatus
        saveServerStatus()
        AppLogger.d(TAG, "服务器状态已更新: $serverId")
    }

    /**
     * 缓存服务器的工具列表
     */
    suspend fun cacheServerTools(serverId: String, tools: List<CachedToolInfo>) {
        updateServerStatus(serverId = serverId, cachedTools = tools)
        AppLogger.d(TAG, "已缓存服务器 $serverId 的 ${tools.size} 个工具")
    }

    /**
     * 获取缓存的工具列表
     */
    fun getCachedTools(serverId: String): List<CachedToolInfo>? {
        return _serverStatus.value[serverId]?.cachedTools
    }

    /**
     * 检查工具缓存是否有效 (有效期1天)
     */
    fun hasValidToolCache(serverId: String): Boolean {
        val status = _serverStatus.value[serverId] ?: return false
        
        val cachedTools = status.cachedTools
        val cacheTime = status.toolsCachedTime
        
        if (cachedTools.isNullOrEmpty() || cacheTime <= 0) {
            return false
        }
        
        // 缓存有效期为1天
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - cacheTime) < oneDayInMillis
    }

    /**
     * 删除服务器状态
     */
    suspend fun removeServerStatus(serverId: String) {
        val currentStatus = _serverStatus.value.toMutableMap()
        currentStatus.remove(serverId)
        _serverStatus.value = currentStatus
        saveServerStatus()
        AppLogger.d(TAG, "服务器状态已删除: $serverId")
    }

    /**
     * 获取服务器状态
     */
    fun getServerStatus(serverId: String): ServerStatus? {
        return _serverStatus.value[serverId]
    }

    /**
     * 获取所有服务器状态
     */
    fun getAllServerStatus(): Map<String, ServerStatus> {
        return _serverStatus.value.toMap()
    }

    /**
     * 基于时间戳推断服务是否处于运行态（近似状态，不是实时状态）
     */
    fun isServerLikelyRunning(serverId: String): Boolean {
        val status = _serverStatus.value[serverId] ?: return false
        return status.lastStartTime > 0L && status.lastStartTime >= status.lastStopTime
    }

    /**
     * 检查服务器是否启用
     * 本地插件从 mcpServers.disabled 读取；远程插件从 pluginMetadata.disabled 读取
     */
    fun isServerEnabled(serverId: String): Boolean {
        val serverConfig = getMCPServer(serverId)
        if (serverConfig != null) {
            return serverConfig.disabled != true // disabled=true 表示禁用
        }

        val metadata = getPluginMetadata(serverId)
        if (metadata?.type == "remote") {
            return metadata.disabled != true // disabled=true 表示禁用
        }

        return true
    }

    /**
     * 设置服务器启用状态
     * 本地插件写入 mcpServers.disabled；远程插件写入 pluginMetadata.disabled
     */
    suspend fun setServerEnabled(serverId: String, enabled: Boolean) {
        val serverConfig = getMCPServer(serverId)
        if (serverConfig != null) {
            val command = serverConfig.command?.trim()
            if (command.isNullOrEmpty()) {
                AppLogger.w(TAG, "服务器配置无效，已移除本地 server 记录: $serverId")
                val shouldRemoveMetadata = getPluginMetadata(serverId)?.type != "remote"
                _mcpConfig.update { currentConfig ->
                    val newServers = currentConfig.mcpServers.toMutableMap()
                    val newMetadata = currentConfig.pluginMetadata.toMutableMap()
                    newServers.remove(serverId)
                    if (shouldRemoveMetadata) {
                        newMetadata.remove(serverId)
                    }
                    currentConfig.copy(
                        mcpServers = newServers,
                        pluginMetadata = newMetadata
                    )
                }
                saveMCPConfig()
                if (shouldRemoveMetadata) {
                    removeServerStatus(serverId)
                }
            } else {
                addOrUpdateMCPServer(
                    serverId = serverId,
                    command = command,
                    args = serverConfig.args ?: emptyList(),
                    env = serverConfig.env ?: emptyMap(),
                    disabled = !enabled,
                    autoApprove = serverConfig.autoApprove ?: emptyList(),
                    // Preserve the configured working directory; dropping it would silently
                    // change how the process resolves relative arguments on the next start.
                    cwd = serverConfig.cwd
                )
                AppLogger.d(TAG, "服务器启用状态已更新(本地): $serverId, enabled=$enabled")
                return
            }
        }

        val metadata = getPluginMetadata(serverId)
        if (metadata?.type == "remote") {
            addOrUpdatePluginMetadata(metadata.copy(disabled = !enabled))
            AppLogger.d(TAG, "服务器启用状态已更新(远程): $serverId, enabled=$enabled")
            return
        }

        AppLogger.w(TAG, "设置启用状态失败，未找到服务器配置或远程元数据: $serverId")
    }

    fun getPluginRuntimeDirectory(pluginId: String): String {
        val serverConfig = getMCPServer(pluginId)
        return if (serverConfig != null) {
            McpStdioConfigResolver.resolveWorkingDirectory(
                configuredCwd = serverConfig.cwd,
                pluginId = pluginId,
                command = serverConfig.command,
                args = serverConfig.args ?: emptyList()
            )
        } else {
            val pluginHomeDir = "~/mcp_plugins"
            "$pluginHomeDir/${pluginId.split("/").last()}"
        }
    }

    /** True when the service is launched from the app-managed ~/mcp_plugins/<id> directory. */
    private fun pluginRuntimeUsesAppManagedDirectory(pluginId: String): Boolean {
        val serverConfig = getMCPServer(pluginId) ?: return true
        return McpStdioConfigResolver.usesAppManagedRuntimeDirectory(
            command = serverConfig.command,
            args = serverConfig.args ?: emptyList(),
            cwd = serverConfig.cwd
        )
    }

    /** npx/uvx/uv 由包管理器下载自身载荷，因此运行目录允许为空。 */
    private fun pluginRuntimeAllowsEmptyDirectory(pluginId: String): Boolean {
        val serverConfig = getMCPServer(pluginId) ?: return false
        return McpStdioConfigResolver.commandName(serverConfig.command) in
            setOf("npx", "uvx", "uv")
    }

    /**
     * 检查插件运行目录是否已就绪。
     *
     * 配置了绝对命令、绝对入口脚本或显式 cwd 的 stdio 服务不依赖 Operit 复制插件目录，
     * 因此直接视为就绪；否则按既有规则检查 ~/mcp_plugins/<id>。
     * 对于 npx/uvx/uv 类型：目录存在即可，允许为空目录
     * 对于普通本地插件：目录存在且至少包含一个文件
     */
    suspend fun isPluginRuntimeReady(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = getPluginMetadata(pluginId)

            if (metadata?.type == "remote") {
                AppLogger.d(TAG, "插件 $pluginId 是远程服务，运行目录视为已就绪")
                return@withContext true
            }

            val serverConfig = getMCPServer(pluginId)
            if (serverConfig == null) {
                AppLogger.w(TAG, "插件 $pluginId 缺少 mcpServers 配置，无法判断运行目录")
                return@withContext false
            }

            if (!pluginRuntimeUsesAppManagedDirectory(pluginId)) {
                AppLogger.d(
                    TAG,
                    "插件 $pluginId 使用终端中已有的命令/目录，无需应用侧插件目录: " +
                        "command=${serverConfig.command}, cwd=${serverConfig.cwd}"
                )
                return@withContext true
            }

            val pluginDir = getPluginRuntimeDirectory(pluginId)
            val toolHandler = AIToolHandler.getInstance(context)
            val checkExistsTool = AITool(
                name = "file_exists",
                parameters = listOf(
                    ToolParameter("path", pluginDir),
                    ToolParameter("environment", "linux")
                )
            )
            
            val existsResult = toolHandler.executeTool(checkExistsTool)
            val dirExists = existsResult.success && existsResult.result is FileExistsData && 
                            (existsResult.result as FileExistsData).exists
            
            if (!dirExists) {
                AppLogger.d(TAG, "插件 $pluginId 运行目录不存在: $pluginDir")
                return@withContext false
            }

            if (pluginRuntimeAllowsEmptyDirectory(pluginId)) {
                AppLogger.d(TAG, "插件 $pluginId 运行目录已就绪: $pluginDir (允许空目录)")
                return@withContext true
            }

            val listFilesTool = AITool(
                name = "list_files",
                parameters = listOf(
                    ToolParameter("path", pluginDir),
                    ToolParameter("environment", "linux")
                )
            )
            
            val listResult = toolHandler.executeTool(listFilesTool)
            val hasFiles = if (listResult.success && listResult.result is DirectoryListingData) {
                val listing = listResult.result as DirectoryListingData
                listing.entries.isNotEmpty()
            } else {
                false
            }

            AppLogger.d(TAG, "插件 $pluginId 运行目录检查: $hasFiles (路径: $pluginDir, 包含${if (hasFiles) "有" else "无"}文件)")
            return@withContext hasFiles
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查插件运行目录状态时出错: $pluginId", e)
            return@withContext false
        }
    }

    // ==================== 兼容性方法 ====================

    /**
     * 获取插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @return 配置内容JSON字符串，如果不存在返回空对象
     */
    fun getPluginConfig(pluginId: String): String {
        val serverConfig = getMCPServer(pluginId)
        return if (serverConfig != null) {
            val configForOnePlugin = MCPConfig(
                mcpServers = mutableMapOf(pluginId to serverConfig)
            )
            gson.toJson(configForOnePlugin)
        } else {
            gson.toJson(MCPConfig())
        }
    }

    /**
     * 保存插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @param config 配置内容JSON字符串，可以是完整的MCPConfig或单个ServerConfig
     * @return 是否保存成功
     */
    suspend fun savePluginConfig(pluginId: String, config: String): Boolean {
        return try {
            // 先尝试解析为完整的MCPConfig（getPluginConfig返回的格式）
            val parsedServerConfig = try {
                val fullConfig = gson.fromJson(config, MCPConfig::class.java)
                // 如果包含mcpServers且有对应的pluginId，使用该配置
                fullConfig.mcpServers[pluginId] ?: throw Exception("No server config found for $pluginId")
            } catch (e: Exception) {
                // 如果失败，尝试直接解析为ServerConfig
                gson.fromJson(config, MCPConfig.ServerConfig::class.java) ?: return false
            }
            val serverConfig = sanitizeServerConfig(pluginId, parsedServerConfig, "savePluginConfig")
                ?: return false
            
            _mcpConfig.update { currentConfig ->
                val newServers = currentConfig.mcpServers.toMutableMap()
                newServers[pluginId] = serverConfig
                currentConfig.copy(mcpServers = newServers)
            }
            saveMCPConfig()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存插件配置失败: $pluginId", e)
            false
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 导出配置为JSON字符串
     */
    fun exportConfigAsJson(): String {
        val exportData = mapOf(
            "mcpConfig" to _mcpConfig.value,
            "serverStatus" to _serverStatus.value,
            "exportTime" to System.currentTimeMillis(),
            "version" to "1.0"
        )
        return gson.toJson(exportData)
    }

    /**
     * 从JSON字符串导入配置
     */
    suspend fun importConfigFromJson(json: String): Boolean {
        return try {
            val typeToken = object : TypeToken<Map<String, Any>>() {}.type
            val importData = gson.fromJson<Map<String, Any>>(json, typeToken)
            
            importData["mcpConfig"]?.let { config ->
                val configJson = gson.toJson(config)
                val rawMcpConfig = gson.fromJson(configJson, MCPConfig::class.java) ?: MCPConfig()
                val sanitizedConfig = sanitizeMCPConfig(rawMcpConfig, "importConfigFromJson")
                _mcpConfig.value = autoFillMissingMetadata(sanitizedConfig.config)
                saveMCPConfig()
            }
            
            importData["serverStatus"]?.let { status ->
                val statusJson = gson.toJson(status)
                val typeToken3 = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val serverStatus = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken3)
                _serverStatus.value = serverStatus
                saveServerStatus()
            }
            
            AppLogger.d(TAG, "配置导入成功")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入配置失败", e)
            false
        }
    }

    /**
     * 获取配置目录路径
     */
    fun getConfigDirectory(): String = configBaseDir.absolutePath

    /**
     * 清理无效配置
     */
    suspend fun cleanupInvalidConfigurations() {
        try {
            // 清理不存在的插件配置
            val validPluginIds = _mcpConfig.value.pluginMetadata.keys
            val mcpConfig = _mcpConfig.value
            val serversToRemove = mcpConfig.mcpServers.keys.filter { it !in validPluginIds }
            
            serversToRemove.forEach { serverId ->
                mcpConfig.mcpServers.remove(serverId)
            }
            
            if (serversToRemove.isNotEmpty()) {
                _mcpConfig.value = mcpConfig
                saveMCPConfig()
                AppLogger.d(TAG, "清理了 ${serversToRemove.size} 个无效的MCP服务器配置")
            }
            
            // 清理无效的服务器状态
            val statusToRemove = _serverStatus.value.keys.filter { it !in validPluginIds }
            if (statusToRemove.isNotEmpty()) {
                val currentStatus = _serverStatus.value.toMutableMap()
                statusToRemove.forEach { serverId ->
                    currentStatus.remove(serverId)
                }
                _serverStatus.value = currentStatus
                saveServerStatus()
                AppLogger.d(TAG, "清理了 ${statusToRemove.size} 个无效的服务器状态")
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理配置时出错", e)
        }
    }
}
