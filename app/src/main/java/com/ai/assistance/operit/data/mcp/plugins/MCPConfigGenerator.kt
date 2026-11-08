package com.ai.assistance.operit.data.mcp.plugins

import com.ai.assistance.operit.util.logging.AppLogger
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * MCP 配置生成器
 *
 * 负责生成MCP插件的配置文件
 */
class MCPConfigGenerator {

    companion object {
        private const val TAG = "MCPConfigGenerator"
    }

    /**
     * 生成MCP配置
     *
     * @param pluginId 插件ID
     * @param projectStructure 项目结构
     * @param environmentVariables 环境变量键值对
     * @param pluginDirPath 插件在终端环境中的部署路径（如 ~/mcp_plugins/plugin_name）
     * @return MCP配置JSON
     */
    fun generateMcpConfig(
            pluginId: String,
            projectStructure: ProjectStructure,
            environmentVariables: Map<String, String> = emptyMap(),
            pluginDirPath: String? = null
    ): String {
        // 存储最终要使用的配置JSON
        var finalConfigJson: JsonObject? = null
        // 存储找到的服务器名称
        var existingServerName: String? = null

        // 如果从README提取到了配置示例，先尝试解析它
        if (projectStructure.configExample != null) {
            try {
                // 验证JSON是否有效
                val jsonObject = JsonParser.parseString(projectStructure.configExample).asJsonObject
                if (jsonObject.has("mcpServers")) {
                    // 保存这个配置，但不立即返回
                    finalConfigJson = jsonObject
                    AppLogger.d(TAG, "从配置示例提取到有效的配置JSON")

                    // 尝试提取第一个服务器名称作为已存在的服务器名
                    val mcpServers = jsonObject.getAsJsonObject("mcpServers")
                    if (mcpServers.size() > 0) {
                        existingServerName = mcpServers.keySet().firstOrNull()
                        AppLogger.d(TAG, "从配置示例提取到服务器名称: $existingServerName")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析配置示例失败，将使用生成的配置", e)
            }
        }

        // 确定要使用的服务器名称
        val serverName = pluginId
        AppLogger.d(TAG, "使用服务器名称: $serverName")

        // 如果没有从示例提取到配置，或者是TypeScript项目需要进行特殊处理
        if (finalConfigJson == null || projectStructure.type == ProjectType.TYPESCRIPT) {
            // 创建新的配置或使用现有配置作为基础
            val configJson = finalConfigJson ?: JsonObject()
            var mcpServersJson: JsonObject

            // 确保mcpServers存在
            if (configJson.has("mcpServers")) {
                mcpServersJson = configJson.getAsJsonObject("mcpServers")
            } else {
                mcpServersJson = JsonObject()
                configJson.add("mcpServers", mcpServersJson)
            }

            var serverJson: JsonObject

            // 检查服务器配置是否已存在
            if (mcpServersJson.has(serverName)) {
                serverJson = mcpServersJson.getAsJsonObject(serverName)
            } else {
                serverJson = JsonObject()
                mcpServersJson.add(serverName, serverJson)
            }

            // 根据项目类型设置或修改配置
            when (projectStructure.type) {
                ProjectType.PYTHON -> {
                    if (!serverJson.has("command")) {
                        // 如果提供了插件目录路径，使用 venv 中的 Python
                        // 否则使用系统 Python（兼容旧逻辑）
                        val pythonCommand = if (pluginDirPath != null) {
                            "$pluginDirPath/venv/bin/python"
                        } else {
                            "python"
                        }
                        serverJson.addProperty("command", pythonCommand)
                        AppLogger.d(TAG, "Python 项目使用命令: $pythonCommand")
                    }

                    if (!serverJson.has("args")) {
                        val argsArray = com.google.gson.JsonArray()
                        argsArray.add("-m")
                        // 优先级: pyproject.toml 包名 > mainPythonModule > 插件ID
                        val moduleName =
                                projectStructure.pythonPackageName
                                        ?: projectStructure.mainPythonModule
                                                ?: pluginId.replace("-", "_").lowercase()
                        argsArray.add(moduleName)
                        AppLogger.d(TAG, "Python 项目使用模块名: $moduleName (来源: ${when {
                            projectStructure.pythonPackageName != null -> "pyproject.toml"
                            projectStructure.mainPythonModule != null -> "mainPythonModule"
                            else -> "pluginId"
                        }})")
                        serverJson.add("args", argsArray)
                    }
                }
                ProjectType.TYPESCRIPT -> {
                    // 对于TypeScript项目，始终覆盖为使用node命令，无论是新生成还是从示例提取
                    serverJson.addProperty("command", "node")

                    // 如果没有args或者要强制覆盖已有的args，生成新的args
                    if (!serverJson.has("args") || finalConfigJson != null) {
                        val argsArray = com.google.gson.JsonArray()

                        // 使用从项目分析中获取的TypeScript配置
                        val outDir = projectStructure.tsConfigOutDir ?: "dist"
                        val rootDir = projectStructure.tsConfigRootDir ?: "src"

                        AppLogger.d(TAG, "TypeScript编译配置 - outDir: $outDir, rootDir: $rootDir")

                        // 根据项目结构决定可能的输出路径
                        val mainTsFile = projectStructure.mainTsFile
                        if (mainTsFile != null) {
                            // 标准化 rootDir（为空或null都视为根目录）
                            val normalizedRootDir = if (rootDir.isNullOrEmpty()) "" else rootDir
                            
                            // 尝试确定编译输出位置
                            val compiledPath = when {
                                // 如果rootDir为空或为根目录，文件会直接编译到outDir下
                                normalizedRootDir.isEmpty() -> {
                                    // 对于 rootDir="." 和 mainTsFile="index.ts" -> dist/index.js
                                    val relativePath = mainTsFile.removePrefix("src/")
                                    val jsFile = relativePath.replace(".ts", ".js")
                                    "$outDir/$jsFile"
                                }
                                // 如果文件在rootDir中，需要保持相对路径结构
                                mainTsFile.startsWith("$normalizedRootDir/") -> {
                                    mainTsFile
                                        .removePrefix("$normalizedRootDir/")
                                        .replace(".ts", ".js")
                                        .let { "$outDir/$it" }
                                }
                                // 如果是src目录结构
                                mainTsFile.startsWith("src/") && normalizedRootDir == "src" -> {
                                    mainTsFile
                                        .removePrefix("src/")
                                        .replace(".ts", ".js")
                                        .let { "$outDir/$it" }
                                }
                                // 其他情况，假设文件在根目录
                                else -> {
                                    val jsFile = mainTsFile.replace(".ts", ".js")
                                    "$outDir/$jsFile"
                                }
                            }
                            
                            argsArray.add(compiledPath)
                            AppLogger.d(TAG, "TypeScript编译路径推断: $mainTsFile (rootDir=$normalizedRootDir) -> $compiledPath")
                        } else {
                            // 如果没有找到主TS文件，使用常见的输出位置
                            argsArray.add("$outDir/index.js")
                            AppLogger.d(TAG, "使用默认编译路径: $outDir/index.js")
                        }

                        // 更新服务器配置中的args
                        serverJson.add("args", argsArray)
                    }
                }
                ProjectType.NODEJS -> {
                    if (!serverJson.has("command")) {
                        serverJson.addProperty("command", "node")
                    }

                    if (!serverJson.has("args")) {
                        val argsArray = com.google.gson.JsonArray()
                        val mainFile = projectStructure.mainJsFile ?: "index.js"
                        argsArray.add(mainFile)
                        serverJson.add("args", argsArray)
                    }
                }
                else -> {
                    if (!serverJson.has("command")) {
                        // 使用默认配置（尝试使用 venv 中的 Python）
                        val pythonCommand = if (pluginDirPath != null) {
                            "$pluginDirPath/venv/bin/python"
                        } else {
                            "python"
                        }
                        serverJson.addProperty("command", pythonCommand)
                        AppLogger.d(TAG, "UNKNOWN 项目类型使用命令: $pythonCommand")
                    }

                    if (!serverJson.has("args")) {
                        val argsArray = com.google.gson.JsonArray()
                        argsArray.add("-m")
                        argsArray.add(pluginId.replace("-", "_").lowercase())
                        serverJson.add("args", argsArray)
                    }
                }
            }

            // 确保其他必要字段存在
            // 注意：disabled字段不再默认生成，不写表示启用（符合MCP标准）

            if (!serverJson.has("autoApprove")) {
                val autoApproveArray = com.google.gson.JsonArray()
                serverJson.add("autoApprove", autoApproveArray)
            }

            // 添加环境变量配置
            if (environmentVariables.isNotEmpty()) {
                val envJson = JsonObject()
                for ((key, value) in environmentVariables) {
                    envJson.addProperty(key, value)
                }
                serverJson.add("env", envJson)
                AppLogger.d(TAG, "已添加环境变量配置: ${environmentVariables.keys}")
            } else if (!serverJson.has("env")) {
                // 如果没有提供环境变量但配置中也没有，添加一个空对象
                serverJson.add("env", JsonObject())
            }

            finalConfigJson = configJson
        }

        // 确保finalConfigJson不为空
        if (finalConfigJson == null) {
            finalConfigJson = JsonObject()
            val mcpServersJson = JsonObject()
            finalConfigJson.add("mcpServers", mcpServersJson)
        }

        // 使用格式化的JSON输出
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(finalConfigJson)
    }

    /**
     * 保存MCP配置到文件
     *
     * @param pluginId 插件ID
     * @param config 配置内容
     * @param serverPath 服务器路径
     * @return 保存结果
     */
    fun saveMcpConfig(pluginId: String, config: String, serverPath: String): Boolean {
        try {
            val pluginDir = File(serverPath, "plugins/$pluginId")
            if (!pluginDir.exists()) {
                pluginDir.mkdirs()
            }

            val configFile = File(pluginDir, "config.json")
            configFile.writeText(config)

            AppLogger.d(TAG, "已保存配置到: ${configFile.absolutePath}")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存配置失败: $pluginId", e)
            return false
        }
    }

    /**
     * 从配置文件中提取服务器名称
     *
     * @param configJson 配置JSON
     * @return 服务器名称，如果解析失败则返回null
     */
    fun extractServerNameFromConfig(configJson: String): String? {
        if (configJson.isBlank()) return null

        try {
            val jsonObject = JsonParser.parseString(configJson).asJsonObject
            val mcpServers = jsonObject.getAsJsonObject("mcpServers")
            return mcpServers?.keySet()?.firstOrNull()
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析配置JSON失败", e)
            return null
        }
    }

    /**
     * 提取配置中的环境变量
     *
     * @param configJson 配置JSON
     * @return 环境变量Map，如果解析失败则返回空Map
     */
    fun extractEnvironmentVariables(configJson: String): Map<String, String> {
        try {
            val jsonObject = JsonParser.parseString(configJson).asJsonObject
            if (jsonObject.has("mcpServers")) {
                val mcpServers = jsonObject.getAsJsonObject("mcpServers")
                if (mcpServers.size() > 0) {
                    val serverName = mcpServers.keySet().firstOrNull() ?: return emptyMap()
                    val server = mcpServers.getAsJsonObject(serverName)

                    if (server.has("env")) {
                        val envObject = server.getAsJsonObject("env")
                        val result = mutableMapOf<String, String>()

                        envObject.keySet().forEach { key ->
                            result[key] = envObject.get(key).asString
                        }

                        return result
                    }
                }
            }
            return emptyMap()
        } catch (e: Exception) {
            AppLogger.e(TAG, "提取环境变量失败", e)
            return emptyMap()
        }
    }
}
