package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.google.gson.JsonParser
import java.io.File

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * MCP 插件部署工具类
 *
 * 负责协调项目分析、命令生成和配置生成等组件完成插件部署
 */
class MCPDeployer(private val context: Context) {

    // 创建一个协程作用域，用于处理异步操作
    private val deployerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MCPDeployer"
    }

    /** Get or create shared session */
    private suspend fun getOrCreateSharedSession(): String? {
        return MCPSharedSession.getOrCreateSharedSession(context)
    }

    // 部署状态
    sealed class DeploymentStatus {
        object NotStarted : DeploymentStatus()
        data class InProgress(val message: String) : DeploymentStatus()
        data class Success(val message: String) : DeploymentStatus()
        data class Error(val message: String) : DeploymentStatus()
    }

    private fun emitCommandOutput(output: String, statusCallback: (DeploymentStatus) -> Unit) {
        if (output.isBlank()) return

        val maxChunkSize = 3500
        var start = 0
        val prefix = context.getString(R.string.mcp_deployment_output_prefix)
        while (start < output.length) {
            val end = kotlin.math.min(start + maxChunkSize, output.length)
            val chunk = output.substring(start, end)
            statusCallback(DeploymentStatus.InProgress("$prefix$chunk"))
            start = end
        }
    }

    private suspend fun executeCommandWithStreaming(
        terminal: Terminal,
        sessionId: String,
        command: String,
        statusCallback: (DeploymentStatus) -> Unit
    ): String? {
        return try {
            val outputBuilder = StringBuilder()
            var hasEvent = false

            terminal.executeCommandFlow(sessionId, command).collect { event ->
                hasEvent = true
                if (event.outputChunk.isNotEmpty()) {
                    outputBuilder.append(event.outputChunk)
                    emitCommandOutput(event.outputChunk, statusCallback)
                }
            }

            if (hasEvent) outputBuilder.toString() else null
        } catch (e: Exception) {
            AppLogger.e(TAG, "流式执行命令失败: $command", e)
            null
        }
    }
    /**
     * 部署MCP插件（使用自定义命令）
     *
     * @param pluginId 插件ID
     * @param pluginPath 插件安装路径
     * @param customCommands 自定义部署命令列表
     * @param environmentVariables 环境变量键值对
     * @param statusCallback 部署状态回调
     */
    suspend fun deployPluginWithCommands(
            pluginId: String,
            pluginPath: String,
            customCommands: List<String>,
            environmentVariables: Map<String, String> = emptyMap(),
            statusCallback: (DeploymentStatus) -> Unit
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_start, pluginId)))
                    AppLogger.d(TAG, "开始部署插件(自定义命令): $pluginId, 路径: $pluginPath")

                    val mcpLocalServer = MCPLocalServer.getInstance(context)

                    // 检查是否是虚拟路径（npx/uvx/uv 类型的插件）
                    if (pluginPath.startsWith("virtual://")) {
                        val serverConfig = mcpLocalServer.getMCPServer(pluginId)
                        val command = serverConfig?.command?.lowercase() ?: "npx/uvx/uv"

                        val usesAppManagedDirectory = serverConfig != null &&
                            com.ai.assistance.operit.data.mcp.McpStdioConfigResolver
                                .usesAppManagedRuntimeDirectory(
                                    command = serverConfig.command,
                                    args = serverConfig.args.orEmpty(),
                                    cwd = serverConfig.cwd
                                )
                        if (!usesAppManagedDirectory) {
                            // 绝对命令、绝对入口或显式外部 cwd：文件已在终端中，创建目录反而会
                            // 触碰用户自己的路径，因此这里只确认配置可用。
                            AppLogger.d(TAG, "插件 $pluginId 使用终端中已有的命令/目录，无需部署")
                            statusCallback(DeploymentStatus.Success(context.getString(R.string.mcp_deployment_minimal_complete, command)))
                            return@withContext true
                        }

                        AppLogger.d(TAG, "插件 $pluginId 使用 $command 命令（虚拟路径），开始最小化部署...")
                        statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_virtual_plugin_detected, command)))

                        val terminal = Terminal.getInstance(context)
                        val pluginShortName = pluginId.split("/").last()
                        val sessionId = terminal.createSession("deploy-$pluginShortName")
                        if (sessionId == null) {
                            statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_cannot_create_terminal)))
                            return@withContext false
                        }

                        try {
                            val pluginDir = mcpLocalServer.getPluginRuntimeDirectory(pluginId)
                            statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_creating_directory, pluginDir)))

                            val mkdirExecuted = terminal.executeCommand(sessionId, "mkdir -p $pluginDir")
                            if (mkdirExecuted == null) {
                                statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_create_directory_failed)))
                                return@withContext false
                            }

                            AppLogger.d(TAG, "虚拟路径插件 $pluginId 最小化部署完成")
                            statusCallback(DeploymentStatus.Success(context.getString(R.string.mcp_deployment_minimal_complete, command)))
                            return@withContext true
                        } finally {
                            kotlinx.coroutines.delay(2000L)
                            terminal.closeSession(sessionId)
                            AppLogger.d(TAG, "虚拟插件部署完成, 已关闭会話: $sessionId")
                        }
                    }

                    // 验证插件路径（仅对物理路径）
                    val pluginDir = File(pluginPath)
                    if (!pluginDir.exists() || !pluginDir.isDirectory) {
                        AppLogger.e(TAG, "插件目录不存在: $pluginPath")
                        statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_plugin_not_exist, pluginPath)))
                        return@withContext false
                    }

                    // 检查是否有市场配置
                    val pluginMetadata = mcpLocalServer.getPluginMetadata(pluginId)
                    val marketConfig = pluginMetadata?.marketConfig
                    
                    // 创建配置生成器（用于提取 server name，无论是否有市场配置都需要）
                    val configGenerator = MCPConfigGenerator()
                    
                    val mcpConfig: String
                    
                    if (!marketConfig.isNullOrBlank()) {
                        // 优先使用市场配置
                        AppLogger.d(TAG, "使用市场配置部署插件: $pluginId")
                        statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_using_market_config)))
                        mcpConfig = marketConfig
                    } else {
                        // 没有市场配置，分析项目并生成配置
                        AppLogger.d(TAG, "没有市场配置，分析项目生成配置: $pluginId")
                        
                        // 创建项目分析器（仅用于分析项目类型和生成配置）
                        val projectAnalyzer = MCPProjectAnalyzer()
                        val readmeFile = projectAnalyzer.findReadmeFile(pluginDir)
                        val readmeContent = readmeFile?.readText() ?: ""

                        // 分析项目结构以便生成配置
                        statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_analyzing_structure)))
                        val projectStructure = projectAnalyzer.analyzeProjectStructure(pluginDir, readmeContent)

                        // 定义插件在 proot 环境中的目录路径
                        val pluginDirPath = mcpLocalServer.getPluginRuntimeDirectory(pluginId)

                        // 生成MCP配置，包含环境变量和插件目录路径
                        mcpConfig = configGenerator.generateMcpConfig(
                            pluginId,
                            projectStructure,
                            environmentVariables,
                            pluginDirPath
                        )
                    }

                    // 保存MCP配置
                    statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_saving_config)))

                    // 解析生成的MCP配置并保存为正确的格式
                    val configSaveResult = saveMCPConfigToLocalServer(mcpLocalServer, pluginId, mcpConfig)

                    if (!configSaveResult) {
                        AppLogger.e(TAG, "保存MCP配置失败: $pluginId")
                        statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_save_config_failed)))
                        return@withContext false
                    }

                    AppLogger.d(TAG, "使用自定义命令: $customCommands")

                    // 执行部署命令
                    return@withContext executeDeployCommands(
                            pluginId,
                            pluginPath,
                            customCommands,
                            statusCallback,
                            configGenerator.extractServerNameFromConfig(mcpConfig)
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "使用自定义命令部署插件时出错", e)
                    statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_error, e.message ?: "")))
                    return@withContext false
                }
            }

    /**
     * 获取部署命令 - 在部署前调用，用于分析和生成命令
     *
     * @param pluginId 插件ID
     * @param pluginPath 插件路径
     * @return 生成的命令列表，如果失败则返回空列表
     */
    suspend fun getDeployCommands(pluginId: String, pluginPath: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val pluginDir = File(pluginPath)
            if (!pluginDir.exists() || !pluginDir.isDirectory) {
                AppLogger.e(TAG, "插件目录不存在: $pluginPath")
                return@withContext emptyList()
            }

            // 创建项目分析器
            val projectAnalyzer = MCPProjectAnalyzer()

            // 查找README文件
            val readmeFile = projectAnalyzer.findReadmeFile(pluginDir)
            val readmeContent = readmeFile?.readText() ?: ""

            // 分析项目结构
            val projectStructure = projectAnalyzer.analyzeProjectStructure(pluginDir, readmeContent)

            // 创建命令生成器
            val commandGenerator = MCPCommandGenerator()

            // 生成部署命令
            val deployCommands = commandGenerator.generateDeployCommands(projectStructure, readmeContent)
            if (deployCommands.isEmpty()) {
                AppLogger.e(TAG, "无法确定如何部署此插件: $pluginId")
                return@withContext emptyList()
            }

            return@withContext deployCommands
        } catch (e: Exception) {
            AppLogger.e(TAG, "分析插件时出错: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * 执行部署命令（提取的公共方法）
     */
    private suspend fun executeDeployCommands(
            pluginId: String,
            pluginPath: String,
            deployCommands: List<String>,
            statusCallback: (DeploymentStatus) -> Unit,
            serverName: String?
    ): Boolean = withContext(Dispatchers.IO) {
        var sessionId: String? = null
        var deploySuccess = false
        try {
            // 为每个插件创建独立的终端会话，方便查看部署日志
            val terminal = Terminal.getInstance(context)
            val pluginShortName = pluginId.split("/").last()
            sessionId = terminal.createSession("deploy-$pluginShortName")
            if (sessionId == null) {
                statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_cannot_create_terminal)))
                return@withContext false
            }

            AppLogger.d(TAG, "为插件 $pluginId 创建独立部署会话: $sessionId")

            // 定义插件在 proot 环境中的主目录路径
            val mcpLocalServer = MCPLocalServer.getInstance(context)
            val pluginDir = mcpLocalServer.getPluginRuntimeDirectory(pluginId)

            // 首先创建插件目录
            statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_creating_directory, pluginDir)))

            val mkdirExecuted = terminal.executeCommand(sessionId, "mkdir -p $pluginDir")
            if (mkdirExecuted == null) {
                statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_create_directory_failed)))
                return@withContext false
            }

            // 复制插件文件到目标目录
            statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_copying_files)))

            // 将pluginPath转换为终端可访问的路径
            val terminalPluginPath = if (pluginPath.startsWith("/storage/")) {
                // 如果是Android storage路径，转换为sdcard路径
                pluginPath.replace(Regex("^/storage/emulated/\\d+"), "/sdcard")
            } else if (pluginPath.startsWith("/sdcard")) {
                // 已经是sdcard路径，直接使用
                pluginPath
            } else {
                // 其他情况，假设是相对路径或需要检查的路径
                pluginPath
            }

            // 使用 AIToolHandler 复制目录（跨环境复制：Android -> Linux）
            val toolHandler = AIToolHandler.getInstance(context)
            val copyTool = AITool(
                name = "copy_file",
                parameters = listOf(
                    ToolParameter("source", terminalPluginPath),
                    ToolParameter("destination", pluginDir),
                    ToolParameter("source_environment", "android"),
                    ToolParameter("dest_environment", "linux"),
                    ToolParameter("recursive", "true")
                )
            )
            
            val copyResult = toolHandler.executeTool(copyTool)
            if (!copyResult.success) {
                statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_copy_failed, copyResult.error ?: "")))
                return@withContext false
            }
            AppLogger.d(TAG, "成功复制插件目录: $terminalPluginPath -> $pluginDir")

            // 切换到插件目录
            statusCallback(DeploymentStatus.InProgress(context.getString(R.string.mcp_deployment_switching_directory)))

            val cdExecuted = terminal.executeCommand(sessionId, "cd $pluginDir")
            if (cdExecuted == null) {
                statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_switch_failed)))
                return@withContext false
            }

            // 安装依赖并配置环境
            for ((index, command) in deployCommands.withIndex()) {
                // 跳过启动命令，只执行依赖安装命令
                if (command.contains("python -m") ||
                                (command.contains("node ") &&
                                        !command.contains(
                                                "node ./node_modules/typescript"
                                        )) ||
                                command.contains("npm start") ||
                                command.startsWith("#")
                ) {
                    continue
                }

                val cleanCommand = command.trim()
                if (cleanCommand.isBlank()) continue

                // 判断是否是非关键命令（如npm配置命令）
                val isNonCriticalCommand =
                        cleanCommand.contains("pnpm config set") ||
                                cleanCommand.contains("|| true") ||
                                cleanCommand.contains("pnpm install -g") ||
                                cleanCommand.startsWith("pnpm config")

                statusCallback(
                        DeploymentStatus.InProgress(
                                context.getString(R.string.mcp_deployment_executing_command, index + 1, deployCommands.size, cleanCommand)
                        )
                )
                AppLogger.d(TAG, "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand")

                val commandExecuted = executeCommandWithStreaming(
                    terminal = terminal,
                    sessionId = sessionId,
                    command = cleanCommand,
                    statusCallback = statusCallback
                )

                // 如果命令失败
                if (commandExecuted == null) {
                    if (isNonCriticalCommand) {
                        // 对于非关键命令，即使失败也继续
                        AppLogger.w(TAG, "非关键命令执行失败，但将继续部署: $cleanCommand")
                        statusCallback(
                                DeploymentStatus.InProgress(
                                        context.getString(R.string.mcp_deployment_non_critical_failed, cleanCommand)
                                )
                        )
                    } else {
                        // 关键命令失败，中止部署
                        AppLogger.e(TAG, "命令执行失败: $cleanCommand")
                        statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_command_failed, cleanCommand)))
                        return@withContext false
                    }
                }
            }

            // 构建部署成功消息
            val finalServerName = serverName ?: pluginId.split("/").last().lowercase()
            val currentTime = System.currentTimeMillis()
            val deployTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime))

            val successMessage = context.getString(
                R.string.mcp_deployment_success,
                pluginId,
                pluginDir,
                finalServerName,
                deployTime
            )

            AppLogger.d(TAG, "插件 $pluginId 部署完成")
            statusCallback(DeploymentStatus.Success(successMessage))
            deploySuccess = true
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行部署命令时出错", e)
            statusCallback(DeploymentStatus.Error(context.getString(R.string.mcp_deployment_error, e.message ?: "")))
            return@withContext false
        } finally {
            // 无论成功失败，都关闭部署会话
            sessionId?.let {
                try {
                    // 延迟关闭会话让用户看到结果
                    val delayTime = if (deploySuccess) 2000L else 3000L
                    kotlinx.coroutines.delay(delayTime)
                    Terminal.getInstance(context).closeSession(it)
                    AppLogger.d(TAG, "部署${if (deploySuccess) "完成" else "失败"}，已关闭会话: $it")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "关闭部署会话时出错: ${e.message}")
                }
            }
        }
    }

    /**
     * 将生成的MCP配置正确保存到MCPLocalServer
     */
    private suspend fun saveMCPConfigToLocalServer(
        mcpLocalServer: MCPLocalServer,
        pluginId: String,
        mcpConfigJson: String
    ): Boolean {
        return try {
            // 解析生成的MCP配置JSON
            val jsonObject = JsonParser.parseString(mcpConfigJson).asJsonObject
            val mcpServers = jsonObject.getAsJsonObject("mcpServers")

            if (mcpServers != null && mcpServers.size() > 0) {
                // 获取第一个服务器配置（通常是唯一的）
                val serverName = mcpServers.keySet().first()
                val serverConfig = mcpServers.getAsJsonObject(serverName)

                // 提取配置参数
                val command = serverConfig.get("command")?.asString ?: "python"
                val args = serverConfig.getAsJsonArray("args")?.map { it.asString } ?: emptyList()
                val disabled = serverConfig.get("disabled")?.asBoolean ?: false
                val autoApprove = serverConfig.getAsJsonArray("autoApprove")?.map { it.asString } ?: emptyList()
                // cwd 是标准 MCP stdio 字段；丢失它会让相对入口脚本在错误目录下启动。
                val cwd = serverConfig.get("cwd")?.takeIf { !it.isJsonNull }?.asString

                // 提取环境变量
                val env = mutableMapOf<String, String>()
                serverConfig.getAsJsonObject("env")?.let { envObject ->
                    envObject.keySet().forEach { key ->
                        env[key] = envObject.get(key).asString
                    }
                }

                AppLogger.d(TAG, "解析MCP配置 - 服务器: $serverName, 命令: $command, 参数: $args, cwd: $cwd")

                // 保存到MCPLocalServer
                mcpLocalServer.addOrUpdateMCPServer(
                    serverId = pluginId,
                    command = command,
                    args = args,
                    env = env,
                    disabled = disabled,
                    autoApprove = autoApprove,
                    cwd = cwd
                )

                true
            } else {
                AppLogger.e(TAG, "MCP配置中没有找到服务器配置")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析和保存MCP配置失败", e)
            false
        }
    }
}
