package com.ai.assistance.operit.data.mcp

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPPackage
import com.ai.assistance.operit.core.tools.mcp.McpRuntimeDescriptor
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor
import com.ai.assistance.operit.data.mcp.plugins.MCPConfigGenerator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统一的MCP仓库管理类
 * 
 * 职责：
 * - 管理MCP服务器的UI状态和数据
 * - 处理插件的安装、卸载
 * - 管理已安装插件的状态跟踪
 * - 处理远程服务器的添加和管理
 * 
 * 配置管理由MCPLocalServer单独处理
 */
class MCPRepository(private val context: Context) {
    private val mcpLocalServer = MCPLocalServer.getInstance(context)

    companion object {
        private const val TAG = "MCPRepository"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 15000
        private const val PLUGINS_DIR_NAME = "mcp_plugins"
        private const val OPERIT_DIR_NAME = "Operit"
    }

    // UI状态管理
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<MCPLocalServer.PluginMetadata>>(emptyList())
    val mcpServers: StateFlow<List<MCPLocalServer.PluginMetadata>> = _mcpServers.asStateFlow()

    // 已安装插件ID管理
    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    // 插件安装目录
    private val pluginsBaseDir by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, OPERIT_DIR_NAME)
        val pluginsDir = File(operitDir, PLUGINS_DIR_NAME)

        if (!operitDir.exists()) operitDir.mkdirs()
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        if (pluginsDir.exists() && pluginsDir.canWrite()) {
            pluginsDir
        } else {
            val fallbackDir = context.getExternalFilesDir(PLUGINS_DIR_NAME)
                ?: File(context.filesDir, PLUGINS_DIR_NAME).also {
                    if (!it.exists()) it.mkdirs()
                }
            AppLogger.w(TAG, "使用应用私有目录: ${fallbackDir.path}")
            fallbackDir
        }
    }

    init {
        loadPluginsFromMCPLocalServer()
        
        // 监听MCPLocalServer的配置变化
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            mcpLocalServer.pluginMetadata.collect {
                // 当插件元数据发生变化时，重新加载插件列表
                loadPluginsFromMCPLocalServer()
            }
        }
    }

    fun refreshInstalledPlugins() {
        loadPluginsFromMCPLocalServer()
    }

    // ==================== 插件状态管理 ====================

    /**
     * 从MCPLocalServer加载插件信息（主要数据源）
     */
    private fun loadPluginsFromMCPLocalServer() {
        try {
            val pluginMetadata = mcpLocalServer.getAllPluginMetadata()

            // 构建插件列表
            val servers = mutableListOf<MCPLocalServer.PluginMetadata>()
            val installedIds = mutableSetOf<String>()

            pluginMetadata.values.forEach { metadata ->
                // 统一检查：根据 command 判断是否需要物理安装
                val isInstalled = if (metadata.type == "remote") {
                    true // 远程服务器
                } else {
                    isPluginPhysicallyInstalled(metadata.id) // 自动处理 npx/uvx/uv
                }

                if (isInstalled) {
                    installedIds.add(metadata.id)
                }

                // 创建更新的metadata，确保isInstalled字段正确
                val updatedMetadata = metadata.copy(isInstalled = isInstalled)
                servers.add(updatedMetadata)
            }

            // 补充扫描 mcp_plugins 目录中的本地插件（即使它们尚未写入 JSON 配置）
            val physicallyInstalledIds = scanPhysicallyInstalledPlugins()
            installedIds.addAll(physicallyInstalledIds)

            val missingMetadataPluginIds = physicallyInstalledIds - pluginMetadata.keys
            if (missingMetadataPluginIds.isNotEmpty()) {
                AppLogger.d(
                    TAG,
                    "发现 ${missingMetadataPluginIds.size} 个仅存在于 mcp_plugins 的插件: ${missingMetadataPluginIds.joinToString()}"
                )
            }

            missingMetadataPluginIds.forEach { pluginId ->
                servers.add(
                    MCPLocalServer.PluginMetadata(
                        id = pluginId,
                        name = pluginId,
                        description = context.getString(R.string.local_installed_plugin),
                        author = context.getString(R.string.local_installation),
                        isInstalled = true,
                        version = context.getString(R.string.local_version),
                        longDescription = context.getString(R.string.local_installed_plugin),
                        type = "local"
                    )
                )
            }

            _mcpServers.value = servers.sortedBy { it.name }
            _installedPluginIds.value = installedIds

        } catch (e: Exception) {
            AppLogger.e(TAG, "从MCPLocalServer加载插件失败", e)
        }
    }

    /**
     * 扫描文件系统中实际安装的插件（辅助验证）
     */
    private fun scanPhysicallyInstalledPlugins(): Set<String> {
        val installedIds = mutableSetOf<String>()
        try {
            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (pluginDir.isDirectory && isPluginPhysicallyInstalled(pluginDir.name)) {
                        installedIds.add(pluginDir.name)
                    }
                }
            }
            AppLogger.d(TAG, "文件系统扫描到已安装插件: ${installedIds.size}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "扫描文件系统插件失败", e)
        }
        return installedIds
    }

    /**
     * 判断插件是否需要物理安装（npx/uvx/uv/remote 类型不需要）
     */
    private fun needsPhysicalInstallation(serverId: String): Boolean {
        val serverConfig = mcpLocalServer.getMCPServer(serverId)
        val command = serverConfig?.command?.lowercase() ?: return true
        
        return commandNeedsPhysicalInstallation(command)
    }
    
    /**
     * 判断命令类型是否需要物理安装
     * @param command 命令字符串（小写）
     * @return true 如果需要物理安装，false 如果是 npx/uvx/uv 等不需要物理安装的命令
     */
    private fun commandNeedsPhysicalInstallation(command: String): Boolean {
        // npx、uvx、uv、remote 类型的插件不需要物理安装
        return when (command) {
            "npx" -> false
            "uvx" -> false
            "uv" -> false
            else -> true
        }
    }
    
    /**
     * 检查标准 MCP 配置中的 stdio 服务器是否需要物理安装。
     * 远程 HTTP/SSE 服务器只写入远程元数据，不需要仓库目录。
     */
    fun checkConfigNeedsPhysicalInstallation(jsonConfig: String): Boolean {
        val parsedConfig = McpConfigImportParser.parse(jsonConfig)
        return parsedConfig.servers
            .filterIsInstance<StdioMcpImportedServer>()
            .any { server ->
                val command = server.command
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .lowercase()
                commandNeedsPhysicalInstallation(command)
            }
    }

    /**
     * 检查插件是否在文件系统中物理存在
     */
    private fun isPluginPhysicallyInstalled(serverId: String): Boolean {
        // 如果不需要物理安装，直接返回 true
        if (!needsPhysicalInstallation(serverId)) {
            return true
        }
        
        val pluginDir = File(pluginsBaseDir, serverId)
        return if (pluginDir.exists() && pluginDir.isDirectory) {
            val hasContent = pluginDir.listFiles()?.isNotEmpty() ?: false
            if (hasContent) checkForRequiredFiles(pluginDir) else false
        } else false
    }

    /**
     * 检查插件是否已安装（优先从MCPLocalServer检查）
     */
    fun isPluginInstalled(serverId: String): Boolean {
        val metadata = mcpLocalServer.getPluginMetadata(serverId)
        return if (metadata == null) {
            false // 没有元数据记录
        } else if (metadata.type == "remote") {
            true // 远程服务器配置后即为已安装
        } else {
            isPluginPhysicallyInstalled(serverId) // 自动处理 npx/uvx/uv
        }
    }

    /**
     * 获取已安装插件的路径
     */
    fun getInstalledPluginPath(serverId: String): String? {
        // 对于 npx/uvx/uv 类型的插件，返回一个虚拟路径标记
        if (!needsPhysicalInstallation(serverId)) {
            return "virtual://$serverId"
        }
        
        val pluginDir = File(pluginsBaseDir, serverId)
        if (!pluginDir.exists() || !pluginDir.isDirectory) return null

        val subdirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        return if (subdirs.isNotEmpty()) {
            val repoDir = subdirs.find { it.name.contains(serverId, ignoreCase = true) }
            repoDir?.path ?: subdirs.first().path
                                } else {
            if (pluginDir.listFiles()?.isNotEmpty() == true) pluginDir.path else null
        }
    }

    // ==================== 插件安装功能 ====================

    /**
     * 安装MCP插件
     */
    suspend fun installMCPServer(
        pluginId: String,
        progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            val metadata = _mcpServers.value.find { it.id == pluginId }
            if (metadata == null) {
                AppLogger.e(TAG, "找不到服务器信息: $pluginId")
                return@withContext InstallResult.Error(context.getString(R.string.mcp_repository_server_not_found))
            }

            val result = installPluginInternal(metadata, progressCallback)
            
            if (result is InstallResult.Success) {
                // 保存插件元数据到MCPLocalServer
                savePluginMetadata(metadata, result.pluginPath)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
            }

            result
        }
    }

    /**
     * 安装MCP插件 - 使用服务器对象
     */
    suspend fun installMCPServerWithObject(
        server: MCPLocalServer.PluginMetadata,
        progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            AppLogger.d(TAG, "安装服务器插件: ${server.name} (ID: ${server.id})")

            val result = installPluginInternal(server, progressCallback)
            
            if (result is InstallResult.Success) {
                // 保存插件元数据到MCPLocalServer
                savePluginMetadata(server, result.pluginPath)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
            }

            result
        }
    }

    /**
     * 从本地ZIP文件安装MCP插件
     */
    suspend fun installMCPServerFromZip(
            serverId: String,
        zipUri: Uri,
            name: String,
            description: String,
            author: String,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                AppLogger.d(TAG, "从本地ZIP安装插件, ID: $serverId, Name: $name")

                val server = MCPLocalServer.PluginMetadata(
                                id = serverId,
                                name = name,
                                description = description,
                                logoUrl = "",
                                author = author,
                                isInstalled = false,
                                version = "1.0.0",
                                updatedAt = "",
                                longDescription = description,
                                repoUrl = "",
                                type = "local"
                        )

                val result = installPluginFromZipInternal(server, zipUri, progressCallback)

                if (result is InstallResult.Success) {
                    savePluginMetadata(server, result.pluginPath)
                    // 重新加载插件状态
                    loadPluginsFromMCPLocalServer()
                }

                result
            } catch (e: Exception) {
                AppLogger.e(TAG, "从本地ZIP安装插件失败", e)
                InstallResult.Error(context.getString(R.string.mcp_repository_install_error, e.message ?: ""))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 卸载MCP插件
     */
    suspend fun uninstallMCPServer(pluginId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val pluginDir = File(pluginsBaseDir, pluginId)
                val result = if (pluginDir.exists()) {
                    pluginDir.deleteRecursively()
                } else {
                    true // 目录不存在，认为卸载成功
                }

                if (result) {
                    // 从MCPLocalServer中移除配置
                    mcpLocalServer.removeMCPServer(pluginId)
                    // 重新加载插件状态
                    loadPluginsFromMCPLocalServer()
                    AppLogger.d(TAG, "插件卸载成功: $pluginId")
                } else {
                    AppLogger.e(TAG, "插件卸载失败: $pluginId")
                }

                result
            } catch (e: Exception) {
                AppLogger.e(TAG, "卸载插件时发生错误: $pluginId", e)
                false
            }
        }
    }

    // ==================== 内部安装实现 ====================

    /**
     * 内部安装插件实现
     */
    private suspend fun installPluginInternal(
        server: MCPLocalServer.PluginMetadata,
        progressCallback: (InstallProgress) -> Unit
    ): InstallResult {
        progressCallback(InstallProgress.Preparing)

        try {
            AppLogger.d(TAG, "安装插件 - 名称: ${server.name}, URL: ${server.repoUrl}")

            val pluginDir = File(pluginsBaseDir, server.id)
            if (pluginDir.exists()) {
                AppLogger.d(TAG, "删除已存在的插件目录: ${pluginDir.path}")
                pluginDir.deleteRecursively()
            }
            pluginDir.mkdirs()

            val repoOwnerAndName = extractOwnerAndRepo(server.repoUrl)
            if (repoOwnerAndName == null) {
                AppLogger.e(TAG, "无法从 URL 提取仓库信息: ${server.repoUrl}")
                return InstallResult.Error(context.getString(R.string.mcp_repository_invalid_github_url))
            }

            val (owner, repoName) = repoOwnerAndName
            AppLogger.d(TAG, "准备下载仓库: $owner/$repoName")

            progressCallback(InstallProgress.Downloading(0))
            val zipFile = downloadRepositoryZip(owner, repoName, server.id, progressCallback)

            if (zipFile == null || !zipFile.exists()) {
                return InstallResult.Error(context.getString(R.string.mcp_repository_download_zip_failed))
            }

            progressCallback(InstallProgress.Extracting(0))
            val extractSuccess = extractZipFile(zipFile, pluginDir, progressCallback)
            zipFile.delete()

            if (!extractSuccess) {
                pluginDir.deleteRecursively()
                return InstallResult.Error(context.getString(R.string.mcp_repository_extract_repo_failed))
            }

            val extractedDirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (extractedDirs.isEmpty()) {
                return InstallResult.Error(context.getString(R.string.mcp_repository_no_repo_dir))
            }

            val mainDir = extractedDirs.first()
            AppLogger.d(TAG, "插件解压成功，主目录: ${mainDir.path}")

            progressCallback(InstallProgress.Finished)
            return InstallResult.Success(mainDir.path)

        } catch (e: Exception) {
            AppLogger.e(TAG, "安装插件失败", e)
            return InstallResult.Error(context.getString(R.string.mcp_repository_plugin_install_error, e.message ?: ""))
        }
    }

    /**
     * 从ZIP文件安装插件的内部实现
     */
    private suspend fun installPluginFromZipInternal(
        server: MCPLocalServer.PluginMetadata,
        zipUri: Uri,
        progressCallback: (InstallProgress) -> Unit
    ): InstallResult {
        progressCallback(InstallProgress.Preparing)

        try {
            AppLogger.d(TAG, "从本地ZIP安装插件 - 名称: ${server.name}, URI: $zipUri")

            val pluginDir = File(pluginsBaseDir, server.id)
            if (pluginDir.exists()) {
                AppLogger.d(TAG, "删除已存在的插件目录: ${pluginDir.path}")
                pluginDir.deleteRecursively()
            }
            pluginDir.mkdirs()

            val tempFile = File(context.cacheDir, "mcp_${server.id}_local.zip")
            if (tempFile.exists()) tempFile.delete()

            progressCallback(InstallProgress.Downloading(0))

            // 从URI读取ZIP文件
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        progressCallback(InstallProgress.Downloading(-1))
                    }
                }
            } ?: return InstallResult.Error(context.getString(R.string.mcp_repository_cannot_read_zip))

            progressCallback(InstallProgress.Extracting(0))
            val extractSuccess = extractZipFile(tempFile, pluginDir, progressCallback)
            tempFile.delete()

            if (!extractSuccess) {
                pluginDir.deleteRecursively()
                return InstallResult.Error(context.getString(R.string.mcp_repository_extract_local_zip_failed))
            }

            val extractedDirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val mainDir = if (extractedDirs.isEmpty()) pluginDir else extractedDirs.first()
            
            AppLogger.d(TAG, "本地插件解压成功，主目录: ${mainDir.path}")

            progressCallback(InstallProgress.Finished)
            return InstallResult.Success(mainDir.path)

        } catch (e: Exception) {
            AppLogger.e(TAG, "安装本地ZIP插件失败", e)
            return InstallResult.Error(context.getString(R.string.mcp_repository_install_local_zip_error, e.message ?: ""))
        }
    }

    // ==================== 下载和解压工具方法 ====================

    /**
     * 下载仓库ZIP文件
     */
    private suspend fun downloadRepositoryZip(
        owner: String,
        repoName: String,
        serverId: String,
        progressCallback: (InstallProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val defaultBranch = getGithubDefaultBranch(owner, repoName)

        if (defaultBranch == null) {
            AppLogger.e(TAG, "无法确定 $owner/$repoName 的默认分支，下载失败")
            return@withContext null
        }
        
        val zipUrl = "https://github.com/$owner/$repoName/archive/refs/heads/$defaultBranch.zip"
        AppLogger.d(TAG, "从确定的默认分支 '$defaultBranch' 下载: $zipUrl")
            
            val file = downloadFromUrl(zipUrl, serverId, progressCallback)
            if (file != null && file.exists() && file.length() > 0) {
            AppLogger.d(TAG, "从默认分支 '$defaultBranch' 下载成功")
                return@withContext file
            }
        
        AppLogger.e(TAG, "从默认分支 '$defaultBranch' 下载失败")
        null
    }

    /**
     * 使用 GitHub API 获取仓库的默认分支
     */
    private suspend fun getGithubDefaultBranch(owner: String, repoName: String): String? = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        AppLogger.d(TAG, "从 GitHub API 获取仓库信息: $apiUrl")
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader()
                val response = reader.readText()
                reader.close()

                val jsonObject = JsonParser.parseString(response).asJsonObject
                val defaultBranch = jsonObject.get("default_branch")?.asString

                if (!defaultBranch.isNullOrBlank()) {
                    AppLogger.d(TAG, "找到 $owner/$repoName 的默认分支: $defaultBranch")
                    return@withContext defaultBranch
                } else {
                    AppLogger.e(TAG, "在 $owner/$repoName 的 API 响应中找不到 'default_branch'")
                }
            } else {
                AppLogger.e(TAG, "GitHub API 请求失败，响应码: ${connection.responseCode}，URL: $apiUrl")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取 $owner/$repoName 的默认分支时出错", e)
        }
        null
    }

    /**
     * 从URL下载文件
     */
    private suspend fun downloadFromUrl(
        zipUrl: String,
        serverId: String,
        progressCallback: (InstallProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "mcp_${serverId}_repo.zip")
        
        try {
            val url = URL(zipUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doInput = true
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.e(TAG, "下载失败，HTTP响应码: ${connection.responseCode}")
                return@withContext null
            }
            
            val contentLength = connection.contentLength.toLong()
            AppLogger.d(TAG, "开始下载，文件大小: $contentLength 字节")
            
            val inputStream = BufferedInputStream(connection.inputStream)
            val outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            var lastReportedProgress = -1
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else -1
                
                if (progress != lastReportedProgress) {
                    progressCallback(InstallProgress.Downloading(progress))
                    lastReportedProgress = progress
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            AppLogger.d(TAG, "下载完成，保存到: ${tempFile.path}")
            return@withContext tempFile
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载ZIP文件失败: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
        }
    }

    /**
     * 解压ZIP文件
     */
    private suspend fun extractZipFile(
        zipFile: File,
        targetDir: File,
        progressCallback: (InstallProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()
            AppLogger.d(TAG, "开始从${zipFile.path}提取文件到${targetDir.path}")
            
            ZipFile(zipFile).use { zip ->
                val inputStream = zipFile.inputStream()
                val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))
                
                var entry = zipInputStream.nextEntry
                val totalEntries = countZipEntries(zipFile)
                var extractedCount = 0
                var lastReportedProgress = -1
                
                while (entry != null) {
                    val entryName = entry.name
                    
                    if (entryName.contains("__MACOSX") || entryName.endsWith(".DS_Store")) {
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                        continue
                    }
                    
                    val outFile = File(targetDir, entryName)
                    
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        
                        val outputStream = FileOutputStream(outFile)
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        
                        while (zipInputStream.read(buffer).also { len = it } > 0) {
                            outputStream.write(buffer, 0, len)
                        }
                        
                        outputStream.close()
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                    
                    extractedCount++
                    val progress = if (totalEntries > 0) {
                        (extractedCount * 100 / totalEntries).toInt()
                    } else -1
                    
                    if (progress != lastReportedProgress) {
                        progressCallback(InstallProgress.Extracting(progress))
                        lastReportedProgress = progress
                    }
                }
                
                zipInputStream.close()
                inputStream.close()
            }
            
            AppLogger.d(TAG, "解压完成，文件解压到: ${targetDir.path}")
            return@withContext true
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "解压ZIP文件失败", e)
            return@withContext false
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 计算ZIP文件中的条目数量
     */
    private fun countZipEntries(zipFile: File): Int {
        var count = 0
        try {
            val inputStream = zipFile.inputStream()
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))
            
            while (zipInputStream.nextEntry != null) {
                count++
                zipInputStream.closeEntry()
            }
            
            zipInputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "计算ZIP条目数量失败", e)
        }
        return count
    }

    /**
     * 从GitHub仓库URL中提取所有者和仓库名
     */
    @SuppressLint("SuspiciousIndentation")
    private fun extractOwnerAndRepo(repoUrl: String): Pair<String, String>? {
        val regex = "(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)(?:\\.git)?/?.*".toRegex()
        val matchResult = regex.find(repoUrl)
        
        if (matchResult != null && matchResult.groupValues.size >= 3) {
        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]
        
            if (owner.isNotBlank() && repo.isNotBlank()) {
                return owner to repo
            }
        }
        
        return null
    }

    /**
     * 检查插件目录中是否包含必要的关键文件
     */
    private fun checkForRequiredFiles(pluginDir: File): Boolean {
        val subdirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        
        return if (subdirs.isNotEmpty()) {
            hasPluginRequiredFiles(subdirs.first())
        } else {
            hasPluginRequiredFiles(pluginDir)
        }
    }

    /**
     * 检查目录是否包含插件所需的必要文件
     */
    private fun hasPluginRequiredFiles(dir: File): Boolean {
        val requiredFiles = listOf(
            "mcp.config.json", "README.md", "package.json",
            "index.js", "index.py", "main.py", "main.js"
        )
        
        val dirFiles = dir.listFiles() ?: return false
        
        val hasAnyRequiredFile = requiredFiles.any { requiredFile ->
            dirFiles.any { it.name.equals(requiredFile, ignoreCase = true) }
        }
        
        if (!hasAnyRequiredFile) {
            val subDirs = dirFiles.filter { it.isDirectory }
            if (subDirs.isNotEmpty()) {
                return subDirs.any { hasPluginRequiredFiles(it) }
            }
        }
        
        return hasAnyRequiredFile
    }

    /**
     * 保存插件元数据到MCPLocalServer
     */
    private suspend fun savePluginMetadata(server: MCPLocalServer.PluginMetadata, pluginPath: String) {
        val metadata = server.copy(
            type = "local",
            installedPath = pluginPath,
            installedTime = System.currentTimeMillis()
        )
        
        mcpLocalServer.addOrUpdatePluginMetadata(metadata)
    }
    // ==================== 远程服务器管理 ====================

    /**
     * 添加远程服务器
     */
    suspend fun addRemoteServer(server: MCPLocalServer.PluginMetadata) {
        withContext(Dispatchers.IO) {
            if (server.type != "remote" || server.endpoint == null) {
                AppLogger.e(TAG, "addRemoteServer调用了无效的远程服务器: ${server.id}")
                return@withContext
            }

            val metadata = server.copy(
                type = "remote",
                installedTime = System.currentTimeMillis()
            )

            // Remote servers do not create a local process. The Kotlin MCP runtime reads this metadata.
            mcpLocalServer.addOrUpdatePluginMetadata(metadata)

            // 重新加载插件状态
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 更新远程服务器
     */
    suspend fun updateRemoteServer(server: MCPLocalServer.PluginMetadata) {
        withContext(Dispatchers.IO) {
            val metadata = mcpLocalServer.getPluginMetadata(server.id)
            if (metadata == null) {
                AppLogger.e(TAG, "无法找到要更新的插件元数据: ${server.id}")
                return@withContext
            }

            val updatedMetadata = metadata.copy(
                name = server.name,
                description = server.description,
                longDescription = server.longDescription,
                author = server.author,
                endpoint = if (server.type == "remote") server.endpoint else metadata.endpoint,
                connectionType = if (server.type == "remote") server.connectionType else metadata.connectionType,
                bearerToken = if (server.type == "remote") server.bearerToken else metadata.bearerToken,
                headers = if (server.type == "remote") server.headers else metadata.headers
            )
            mcpLocalServer.addOrUpdatePluginMetadata(updatedMetadata)

            // 重新加载插件状态以更新UI
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 删除远程服务器
     */
    suspend fun removeRemoteServer(serverId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpLocalServer.removeMCPServer(serverId)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
                AppLogger.d(TAG, "远程服务器 $serverId 删除成功")
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "删除远程服务器 $serverId 时出错", e)
                false
            }
        }
    }

    // ==================== 状态同步和管理 ====================

    /** Synchronizes local stdio plugin status from the bridge. */
    suspend fun syncBridgeStatus() {
        withContext(Dispatchers.IO) {
            AppLogger.d(TAG, "开始同步本地 bridge 服务状态...")
            try {
                val localPluginServiceNames = _installedPluginIds.value
                    .mapNotNull { pluginId ->
                        val metadata = mcpLocalServer.getPluginMetadata(pluginId)
                        if (metadata?.type != "local") return@mapNotNull null

                        val serviceName = MCPConfigGenerator().extractServerNameFromConfig(
                            mcpLocalServer.getPluginConfig(pluginId)
                        ) ?: pluginId.split("/").last().lowercase()
                        serviceName to pluginId
                    }
                    .toMap()
                if (localPluginServiceNames.isEmpty()) {
                    AppLogger.d(TAG, "No local stdio plugins to synchronize from bridge")
                    return@withContext
                }
                val bridge = com.ai.assistance.operit.data.mcp.plugins.MCPBridge.getInstance(context)
                val listResponse = bridge.listMcpServices()

                if (listResponse?.optBoolean("success", false) == true) {
                    val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                    val activePluginIds = mutableSetOf<String>()
                    
                    if (services != null) {
                        for (i in 0 until services.length()) {
                            val service = services.optJSONObject(i)
                            val serviceName = service?.optString("name")
                            val isActive = service?.optBoolean("active", false) ?: false
                            val pluginId = serviceName?.let(localPluginServiceNames::get)

                            if (pluginId != null) {
                                val now = System.currentTimeMillis()
                                val wasRunning = mcpLocalServer.isServerLikelyRunning(pluginId)
                                if (isActive) {
                                    activePluginIds.add(pluginId)
                                }
                                if (isActive && !wasRunning) {
                                    mcpLocalServer.updateServerStatus(
                                        serverId = pluginId,
                                        lastStartTime = now,
                                        errorMessage = ""
                                    )
                                } else if (!isActive && wasRunning) {
                                    mcpLocalServer.updateServerStatus(
                                        serverId = pluginId,
                                        lastStopTime = now
                                    )
                                }
                            }
                        }
                    }
                    
                    localPluginServiceNames.values.forEach { pluginId ->
                        if (!activePluginIds.contains(pluginId) && mcpLocalServer.isServerLikelyRunning(pluginId)) {
                            mcpLocalServer.updateServerStatus(
                                serverId = pluginId,
                                lastStopTime = System.currentTimeMillis()
                            )
                        }
                    }
                    AppLogger.d(TAG, "本地 bridge 状态同步完成。活跃插件: ${activePluginIds.joinToString()}")
                } else {
                    AppLogger.w(TAG, "从桥接器获取服务列表失败")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "同步桥接器状态时出错", e)
            }
        }
    }

    /**
     * 同步已安装状态
     */
    suspend fun syncInstalledStatus() {
        withContext(Dispatchers.IO) {
            try {
                // 重新从MCPLocalServer加载插件信息
                loadPluginsFromMCPLocalServer()
                AppLogger.d(TAG, "同步插件安装状态完成，${_installedPluginIds.value.size} 个已安装插件")
            } catch (e: Exception) {
                AppLogger.e(TAG, "同步安装状态失败", e)
            }
        }
    }
    /**
     * 初始化仓库
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            // 重新加载插件状态
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 获取已安装插件的信息
     */
    fun getInstalledPluginInfo(pluginId: String): MCPLocalServer.PluginMetadata? {
        return mcpLocalServer.getPluginMetadata(pluginId)
    }

    suspend fun generatePluginDescription(
        pluginId: String,
        pluginName: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val metadata =
                    mcpLocalServer.getPluginMetadata(pluginId)
                        ?: return@withContext Result.failure(
                            IllegalStateException(context.getString(R.string.mcp_repository_server_not_found))
                        )

                val toolDescriptions = collectToolDescriptionsForDescriptionGeneration(metadata)
                if (toolDescriptions.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.mcp_regenerate_description_no_tools))
                    )
                }

                val targetPluginName = pluginName.trim().ifBlank { metadata.name }
                val generatedDescription =
                    EnhancedAIService.generatePackageDescription(
                        context = context,
                        pluginName = targetPluginName,
                        toolDescriptions = toolDescriptions
                    ).trim()

                if (generatedDescription.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.mcp_regenerate_description_empty))
                    )
                }

                Result.success(generatedDescription)
            } catch (e: Exception) {
                AppLogger.e(TAG, "重新生成插件描述失败: $pluginId", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun collectToolDescriptionsForDescriptionGeneration(
        metadata: MCPLocalServer.PluginMetadata
    ): List<String> {
        val cachedToolDescriptions =
            mcpLocalServer.getCachedTools(metadata.id)
                .orEmpty()
                .mapNotNull { cachedTool ->
                    val toolName = cachedTool.name.trim()
                    if (toolName.isEmpty()) {
                        null
                    } else {
                        cachedTool.description.trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { "$toolName: $it" }
                            ?: toolName
                    }
                }
        if (cachedToolDescriptions.isNotEmpty()) {
            return cachedToolDescriptions
        }

        val session = MCPManager.getInstance(context).getOrCreateSession(metadata.id)
            ?: return emptyList()
        return session.getToolDescriptions()
    }

    /**
     * Returns the names exposed by a remote MCP service for list presentation, and registers
     * the service with the AI runtime once its tools are known.
     *
     * Remote configuration is persisted independently from the in-memory runtime. This
     * method establishes that runtime boundary from the current metadata before asking
     * the Kotlin SDK session for tools, so a server added while the app is running can
     * be presented without involving the local stdio bridge.
     */
    suspend fun getRemoteToolNames(pluginId: String): List<String> = withContext(Dispatchers.IO) {
        val metadata = mcpLocalServer.getPluginMetadata(pluginId)
        if (metadata?.type != "remote") {
            return@withContext emptyList()
        }

        val toolNames = discoverRemoteToolNames(pluginId, metadata)

        // Discovery is also the moment the server proves it is usable, so hand it to the AI
        // runtime here. Otherwise the management screen lists tools that the AI never sees,
        // because the only other registration trigger runs once during startup.
        if (toolNames.isNotEmpty() && !metadata.disabled) {
            registerToolsForPlugin(pluginId)
        }

        toolNames
    }

    private suspend fun discoverRemoteToolNames(
        pluginId: String,
        metadata: MCPLocalServer.PluginMetadata
    ): List<String> {
        val cachedToolNames = mcpLocalServer.getCachedTools(pluginId)
            .orEmpty()
            .map { cachedTool -> cachedTool.name.trim() }
            .filter { toolName -> toolName.isNotEmpty() }
            .distinct()
        if (cachedToolNames.isNotEmpty()) {
            return cachedToolNames
        }

        if (metadata.disabled) {
            return emptyList()
        }

        val mcpManager = MCPManager.getInstance(context)
        mcpManager.registerRuntime(pluginId, createRuntimeDescriptor(metadata))
        val session = mcpManager.getOrCreateSession(pluginId)
            ?: return emptyList()
        val discoveredTools = session.listTools()
            .mapNotNull { tool ->
                val toolName = tool.name.trim()
                toolName.takeIf { it.isNotEmpty() }?.let { name ->
                    MCPLocalServer.CachedToolInfo(
                        name = name,
                        description = tool.description,
                        inputSchema = tool.inputSchema
                    )
                }
            }

        if (discoveredTools.isNotEmpty()) {
            mcpLocalServer.cacheServerTools(pluginId, discoveredTools)
        }

        return discoveredTools.map { tool -> tool.name }.distinct()
    }

    /**
     * 手动刷新插件列表
     * 会重新加载配置文件，自动识别新添加的 mcpServers 配置
     */
    suspend fun refreshPluginList() {
        withContext(Dispatchers.IO) {
            // 重新加载配置文件（会自动识别新的 mcpServers 配置并创建元数据）
            mcpLocalServer.reloadConfigurations()
            // 重新加载插件列表
            loadPluginsFromMCPLocalServer()
            AppLogger.d(TAG, "插件列表已刷新")
        }
    }

    /**
     * 为加载成功的插件注册工具
     * 优先使用本地缓存的工具信息，避免重复连接服务
     *
     * @param successfulPluginIds 加载成功的插件ID列表
     */
    fun registerToolsForLoadedPlugins(successfulPluginIds: List<String>) {
        if (successfulPluginIds.isEmpty()) {
            AppLogger.d(TAG, "没有成功加载的插件，无需注册工具")
            return
        }

        AppLogger.d(TAG, "开始为 ${successfulPluginIds.size} 个插件注册工具: ${successfulPluginIds.joinToString()}")
        successfulPluginIds.forEach { pluginId -> registerToolsForPlugin(pluginId) }
        AppLogger.d(TAG, "所有插件的工具注册流程完成")
    }

    /**
     * 把单个插件接入 AI 运行时：在 MCPManager 中注册服务器（这是 AI 能看到该包的前提），
     * 并把它的工具注册到 AIToolHandler。
     *
     * 注册由多个生命周期节点触发（启动校验、MCP 管理页的远程发现），所以这里必须幂等：
     * 已注册的工具会被跳过，重复调用不会产生重复注册。
     */
    fun registerToolsForPlugin(pluginId: String) {
        try {
            AppLogger.d(TAG, "正在为插件 $pluginId 注册工具...")

            val pluginMetadata = mcpLocalServer.getPluginMetadata(pluginId)
            if (pluginMetadata == null) {
                AppLogger.w(TAG, "在MCPLocalServer中找不到插件 $pluginId 的元数据")
                return
            }

            val mcpManager = MCPManager.getInstance(context)
            val runtimeDescriptor = createRuntimeDescriptor(pluginMetadata)
            val serverConfig = MCPServerConfig(
                name = pluginId,
                endpoint = when (runtimeDescriptor) {
                    is McpRuntimeDescriptor.Local -> "mcp://plugin/${runtimeDescriptor.serviceName}"
                    is McpRuntimeDescriptor.Remote -> runtimeDescriptor.endpoint
                },
                description = pluginMetadata.description,
                capabilities = listOf("tools"),
                extraData = emptyMap()
            )
            mcpManager.registerServer(pluginId, serverConfig, runtimeDescriptor)
            AppLogger.d(TAG, "已在MCPManager中注册服务器: $pluginId (类型: ${pluginMetadata.type})")

            // 获取工具信息
            val toolsToRegister = getToolsForPlugin(pluginId)

            if (toolsToRegister.isEmpty()) {
                AppLogger.w(TAG, "插件 $pluginId 没有可注册的工具")
                return
            }

            // 统一注册工具
            val toolHandler = AIToolHandler.getInstance(context)
            val mcpToolExecutor = MCPToolExecutor(context, mcpManager)
            toolsToRegister.forEach { toolInfo ->
                val prefixedToolName = "$pluginId:${toolInfo.name}"

                if (toolHandler.getToolExecutor(prefixedToolName) != null) {
                    AppLogger.d(TAG, "工具 $prefixedToolName 已注册，跳过")
                    return@forEach
                }

                toolHandler.registerTool(
                    name = prefixedToolName,
                    executor = mcpToolExecutor,
                    descriptionGenerator = { tool ->
                        val baseDescription = toolInfo.description
                        val paramsString = if (tool.parameters.isNotEmpty()) {
                            "\nParameters: " + tool.parameters.joinToString(", ") { "${it.name}='${it.value}'" }
                        } else ""
                        baseDescription + paramsString
                    }
                )
                AppLogger.i(TAG, "成功注册工具: $prefixedToolName")
            }
            AppLogger.d(TAG, "插件 $pluginId 的工具注册完成，共 ${toolsToRegister.size} 个")
        } catch (e: Exception) {
            AppLogger.e(TAG, "为插件 $pluginId 注册工具时发生异常", e)
        }
    }

    /**
     * 反注册插件对应的运行时服务器与工具，避免禁用后仍出现在系统提示词。
     */
    fun unregisterToolsForPlugins(pluginIds: List<String>) {
        if (pluginIds.isEmpty()) return

        val mcpManager = MCPManager.getInstance(context)
        val toolHandler = AIToolHandler.getInstance(context)

        pluginIds.forEach { pluginId ->
            try {
                val toolPrefix = "$pluginId:"
                val toolNamesToRemove = toolHandler.getAllToolNames().filter { it.startsWith(toolPrefix) }
                toolNamesToRemove.forEach { toolName ->
                    toolHandler.unregisterTool(toolName)
                }

                mcpManager.unregisterServer(pluginId)

                AppLogger.d(
                    TAG,
                    "Runtime MCP entries removed for $pluginId, tools=${toolNamesToRemove.size}"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to unregister runtime MCP entries for $pluginId", e)
            }
        }
    }

    private fun getToolsForPlugin(pluginId: String): List<UnifiedToolInfo> {
        // 1. 检查缓存
        val cachedTools = mcpLocalServer.getCachedTools(pluginId)
        if (cachedTools != null && cachedTools.isNotEmpty()) {
            AppLogger.d(TAG, "从缓存为插件 $pluginId 获取了 ${cachedTools.size} 个工具")
            return cachedTools.map {
                UnifiedToolInfo(it.name, it.description, it.inputSchema)
            }
        }

        // 2. 如果没有缓存，动态获取
        AppLogger.d(TAG, "插件 $pluginId 无工具缓存，使用动态连接方式获取")
        val mcpManager = MCPManager.getInstance(context)
        val serverConfig = mcpManager.getRegisteredServers()[pluginId]
        if (serverConfig == null) {
            AppLogger.e(TAG, "无法在MCPManager中找到服务器 $pluginId 的配置")
            return emptyList()
        }

        val mcpLoadResult = MCPPackage.loadFromServer(context, serverConfig)
        val mcpPackage = mcpLoadResult.mcpPackage
        if (mcpPackage == null) {
            AppLogger.w(
                TAG,
                "无法从服务器 $pluginId 获取MCP包: ${mcpLoadResult.errorMessage ?: "unknown reason"}"
            )
            return emptyList()
        }

        val toolPackage = mcpPackage.toToolPackage()
        return toolPackage.tools.map {
            val schemaParams = it.parameters.map { param ->
                mapOf(
                    "name" to param.name,
                    "description" to param.description.resolve(context),
                    "type" to param.type,
                    "required" to param.required
                )
            }
            UnifiedToolInfo(
                name = it.name,
                description = it.description.resolve(context),
                inputSchema = Gson().toJson(schemaParams) // 假设 MCPToolExecutor 可以处理
            )
        }
    }

    private fun createRuntimeDescriptor(
        metadata: MCPLocalServer.PluginMetadata
    ): McpRuntimeDescriptor = when (metadata.type) {
        "local" -> {
            val pluginConfig = mcpLocalServer.getPluginConfig(metadata.id)
            val serviceName = requireNotNull(
                MCPConfigGenerator().extractServerNameFromConfig(pluginConfig)
            ) { "Missing MCP service name for local plugin ${metadata.id}" }
            McpRuntimeDescriptor.Local(serviceName)
        }
        "remote" -> McpRuntimeDescriptor.Remote(
            endpoint = requireNotNull(metadata.endpoint) {
                "Missing endpoint for remote plugin ${metadata.id}"
            },
            connectionType = requireNotNull(metadata.connectionType) {
                "Missing connection type for remote plugin ${metadata.id}"
            },
            bearerToken = metadata.bearerToken,
            headers = metadata.headers.orEmpty()
        )
        else -> error("Unsupported MCP plugin type: ${metadata.type}")
    }
}

// ==================== 数据类定义 ====================

/** 统一的工具信息数据类 */
private data class UnifiedToolInfo(
    val name: String,
    val description: String,
    val inputSchema: String
)

/** 安装进度状态 */
sealed class InstallProgress {
    object Preparing : InstallProgress()
    data class Downloading(val progress: Int) : InstallProgress() // -1 表示未知进度
    data class Extracting(val progress: Int) : InstallProgress() // -1 表示未知进度
    object Finished : InstallProgress()
}

/** 安装结果 */
sealed class InstallResult {
    data class Success(val pluginPath: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}
