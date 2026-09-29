package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * MCP工具执行器
 *
 * 处理MCP工具的调用，类似于已有的PackageToolExecutor
 */
class MCPToolExecutor(private val context: Context, private val mcpManager: MCPManager) :
        ToolExecutor {
    companion object {
        private const val TAG = "MCPToolExecutor"
        private const val REPLACEMENT_CHARACTER = '\uFFFD'

        /**
         * Hard ceiling for one MCP tool invocation as seen by the caller.
         *
         * The transport already bounds itself (the bridge socket read timeout for toolcall is
         * 60s), but this is the last line of defence: it guarantees the blocking
         * [invoke] boundary always returns instead of wedging a Dispatchers.Default thread.
         * Slightly larger than the socket timeout so the socket layer gets to produce the
         * more precise error first.
         */
        private const val TOOL_CALL_TIMEOUT_MS = 70_000L

        /** Timeout for the connect/spawn step, which may need to boot a whole MCP process. */
        private const val CONNECT_TIMEOUT_MS = 180_000L

        /**
         * Per-server cache of tool definitions.
         *
         * Previously every single tool call performed a `listtools` round trip just to read the
         * inputSchema of the tool being called (see [getToolInfo]). That tripled the number of
         * socket round trips per call and, because all of them serialise on the bridge's shared
         * control connection, made concurrent calls pile up against each other.
         *
         * Kept static so [MCPManager] can invalidate it whenever a runtime is re-registered.
         */
        private val toolSchemaCache = ConcurrentHashMap<String, List<McpRuntimeTool>>()

        /** Drop the cached tool definitions for a server (or all servers when null). */
        fun invalidateToolSchemaCache(serverName: String? = null) {
            if (serverName == null) {
                toolSchemaCache.clear()
            } else {
                toolSchemaCache.remove(serverName)
            }
        }
    }

    private data class ArgumentIntegrityViolation(
            val parameterName: String,
            val replacementCharacterCount: Int,
            val characterOffset: Int,
            val utf8ByteOffset: Int
    )

    /** 保存过长的 MCP 结果，并返回适合内联展示的内容 */
    private fun persistLongResultIfNeeded(
            result: String,
            serverName: String,
            toolName: String
    ): String {
        val maxResultLength = ToolExecutionLimits.MAX_TEXT_RESULT_LENGTH

        if (result.length <= maxResultLength) {
            return result
        }

        val outputFile = writeMcpResultToFile(result, serverName, toolName)
        val truncated = result.substring(0, maxResultLength).trimEnd()
        val remainingLength = result.length - maxResultLength
        return "$truncated\n\n[Result too long. Full MCP result saved to file: ${outputFile.absolutePath}]\n[Original result length: ${result.length} chars, inline preview omitted $remainingLength chars]\nUse read_file_part or grep_code to inspect the saved file."
    }

    private fun writeMcpResultToFile(result: String, serverName: String, toolName: String): File {
        val outputDir = OperitPaths.cleanOnExitInternalDir(context)
        val safeServerName = sanitizeFileNamePart(serverName)
        val safeToolName = sanitizeFileNamePart(toolName)
        val file =
                File(
                        outputDir,
                        "mcp_${safeServerName}_${safeToolName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.txt"
                )
        file.writeText(result, Charsets.UTF_8)
        return file
    }

    private fun sanitizeFileNamePart(value: String): String =
            value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80)

    /**
     * 从 MCP 结果中提取内容
     * 
     * 解析标准 content 数组和 structuredContent，智能识别并提取不同类型的内容：
     * - text: 直接提取文本，如果是 JSON 字符串则尝试格式化
     * - image: 显示图像信息
     * - resource: 提取资源内容或显示资源信息
     * 
     * @param resultData MCP 返回的 result 对象
     * @return 提取后的文本内容
     */
    private fun extractContentFromResult(resultData: JSONObject?): String {
        if (resultData == null) {
            return "{}"
        }

        val contentText = extractStandardContent(resultData.optJSONArray("content"))
        if (contentText.isNotEmpty()) {
            return contentText
        }

        val structuredText = extractStructuredContent(resultData.opt("structuredContent"))
        if (structuredText.isNotEmpty()) {
            return structuredText
        }

        return resultData.toString()
    }

    private fun extractStandardContent(contentArray: org.json.JSONArray?): String {
        if (contentArray == null || contentArray.length() == 0) {
            return ""
        }

        val extractedText = StringBuilder()
        for (i in 0 until contentArray.length()) {
            val contentItem = contentArray.optJSONObject(i) ?: continue
            val contentType = contentItem.optString("type", "text")

            when (contentType) {
                "text" -> {
                    val text = contentItem.optString("text", "")
                    extractedText.append(if (isJsonString(text)) formatJson(text) else text)
                }
                "image" -> {
                    val mimeType = contentItem.optString("mimeType", "image/png")
                    val data = contentItem.optString("data", "")
                    if (data.isNotEmpty()) {
                        val imageId = ImagePoolManager.addImageFromBase64(data, mimeType)
                        if (imageId != "error") {
                            extractedText.append("<link type=\"image\" id=\"$imageId\"></link>")
                        } else {
                            extractedText.append("[Image: $mimeType, Size: ${data.length} bytes]")
                        }
                    } else {
                        extractedText.append("[Image: $mimeType, Size: 0 bytes]")
                    }
                }
                "resource" -> {
                    val resource = contentItem.optJSONObject("resource")
                    if (resource != null) {
                        val uri = resource.optString("uri", "")
                        val text = resource.optString("text")
                        val mimeType = resource.optString("mimeType", "")
                        val blob = resource.optString("blob", "")
                        val data = if (blob.isNotEmpty()) blob else resource.optString("data", "")
                        val isImage = mimeType.startsWith("image/") && data.isNotEmpty()
                        if (isImage) {
                            val finalMimeType = if (mimeType.isNotEmpty()) mimeType else "image/png"
                            val imageId = ImagePoolManager.addImageFromBase64(data, finalMimeType)
                            if (imageId != "error") {
                                extractedText.append("<link type=\"image\" id=\"$imageId\"></link>")
                            } else if (text.isNotEmpty()) {
                                extractedText.append(text)
                            } else {
                                extractedText.append("[Resource: $uri]")
                            }
                        } else if (text.isNotEmpty()) {
                            extractedText.append(text)
                        } else {
                            extractedText.append("[Resource: $uri]")
                        }
                    }
                }
                else -> {
                    extractedText.append("[Unknown content type '$contentType': ${contentItem}]")
                }
            }

            if (i < contentArray.length() - 1) {
                extractedText.append("\n")
            }
        }
        return extractedText.toString()
    }

    /**
     * Remote MCP servers may put their payload in structuredContent. Some services place
     * a JSON document in its result field, so parse that document before selecting text.
     */
    private fun extractStructuredContent(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject -> {
                val nestedResult = value.opt("result")
                val nestedResultText =
                    if (nestedResult == null || nestedResult == JSONObject.NULL) "" else extractStructuredContent(nestedResult)
                val contentText = value.optString("content", "")
                when {
                    nestedResultText.isNotEmpty() -> nestedResultText
                    contentText.isNotEmpty() -> contentText
                    else -> value.toString()
                }
            }
            is org.json.JSONArray -> value.toString()
            is String -> {
                val parsedValue = parseStructuredJson(value)
                if (parsedValue == null) value else extractStructuredContent(parsedValue)
            }
            else -> value.toString()
        }
    }

    private fun parseStructuredJson(value: String): Any? {
        val trimmed = value.trim()
        return try {
            when {
                trimmed.startsWith("{") && trimmed.endsWith("}") -> JSONObject(trimmed)
                trimmed.startsWith("[") && trimmed.endsWith("]") -> org.json.JSONArray(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 判断字符串是否为 JSON 格式
     * 
     * @param text 待判断的字符串
     * @return 如果是 JSON 返回 true
     */
    private fun isJsonString(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        
        // 检查是否以 JSON 对象或数组的标志开头和结尾
        val isJsonObject = trimmed.startsWith("{") && trimmed.endsWith("}")
        val isJsonArray = trimmed.startsWith("[") && trimmed.endsWith("]")
        
        if (!isJsonObject && !isJsonArray) return false
        
        // 尝试解析以确认
        return try {
            if (isJsonObject) {
                JSONObject(trimmed)
            } else {
                org.json.JSONArray(trimmed)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 格式化 JSON 字符串为单行紧凑格式
     * 
     * @param jsonString JSON 字符串
     * @return 紧凑格式的 JSON 字符串
     */
    private fun formatJson(jsonString: String): String {
        val trimmed = jsonString.trim()
        
        return try {
            if (trimmed.startsWith("{")) {
                // JSON 对象
                val jsonObject = JSONObject(trimmed)
                jsonObject.toString()
            } else if (trimmed.startsWith("[")) {
                // JSON 数组
                val jsonArray = org.json.JSONArray(trimmed)
                jsonArray.toString()
            } else {
                jsonString
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "JSON 格式化失败: ${e.message}")
            jsonString
        }
    }

    override fun invoke(tool: AITool): ToolResult {
        // 从工具名称中提取服务器名称和工具名称
        // 格式：服务器名称:工具名称
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid MCP tool name format, should be 'server_name:tool_name'"
            )
        }

        val serverName = toolNameParts[0]
        val actualToolName = toolNameParts.subList(1, toolNameParts.size).joinToString(":")

        val mcpClient =
                try {
                    runBlocking { withTimeout(CONNECT_TIMEOUT_MS) { mcpManager.getOrCreateSession(serverName) } }
                } catch (e: TimeoutCancellationException) {
                    AppLogger.e(TAG, "连接 MCP 服务超时: $serverName")
                    null
                }
        if (mcpClient == null) {
            val detailedReason = mcpManager.getLastConnectionFailureReason(serverName)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            detailedReason?.let {
                                "Cannot connect to MCP server '$serverName': $it"
                            }
                                    ?: "Cannot connect to MCP server: $serverName"
            )
        }

        // NOTE: the previous `mcpClient.isActive()` pre-check has been removed. It cost a full
        // `list` round trip (enumerating every registered service) on *every* tool call, and it
        // went through the bridge's shared control connection. `callTool` already reconnects and
        // spawns on demand, so the check was pure overhead on the hot path. The "not activated"
        // diagnostic is preserved below by mapping a failed connect to the same guidance.

        AppLogger.d(TAG, "准备调用MCP工具: $serverName:$actualToolName")

        // 将AITool参数转换为Map
        val parameters = tool.parameters.associate { it.name to it.value }

        // 获取工具参数类型信息 (如果可用)
        val toolInfo = getToolInfo(serverName, actualToolName)

        // 自动类型转换处理
        val convertedParameters = convertParameterTypes(parameters, toolInfo)

        // The synchronous ToolExecutor boundary delegates the actual transport call to a coroutine.
        val result =
                try {
                    val response = runBlocking {
                        withTimeout(TOOL_CALL_TIMEOUT_MS) {
                            mcpClient.callTool(actualToolName, convertedParameters)
                        }
                    }

                    if (response.success) {
                        val extractedContent = extractContentFromResult(response.result)
                        val displayResult =
                                persistLongResultIfNeeded(
                                        result = extractedContent,
                                        serverName = serverName,
                                        toolName = actualToolName
                                )
                        AppLogger.d(TAG, "MCP工具调用成功: $serverName:$actualToolName")
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData(displayResult),
                                error = null
                        )
                    } else {
                        val errorMessage = response.errorMessage ?: "Tool call failed"
                        AppLogger.w(TAG, "MCP工具调用失败: $serverName:$actualToolName - $errorMessage")
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = errorMessage
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    val errorMessage =
                            "MCP tool '$actualToolName' on server '$serverName' did not return " +
                                    "within ${TOOL_CALL_TIMEOUT_MS / 1000}s and was abandoned. " +
                                    "The service process may still be running the tool (for example a " +
                                    "shell command waiting on a prompt); it was not force-killed."
                    AppLogger.e(TAG, errorMessage)
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = errorMessage
                    )
                } catch (e: Exception) {
                    val errorMessage = "Exception occurred while calling tool: ${e.message}"
                    AppLogger.e(TAG, "调用MCP工具时发生异常: $errorMessage", e)
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = errorMessage
                    )
                }

        return result
    }

    /**
     * 获取指定工具的定义信息。
     *
     * 结果按服务器缓存：工具定义在一次连接生命周期内不会变化，而每次工具调用都去
     * `listtools` 会额外增加一次经由桥接器共享控制连接的往返，在并发调用时互相排队。
     * 缓存会在服务器重新注册（[MCPManager.registerRuntime]）时失效。
     */
    private fun getToolInfo(serverName: String, toolName: String): McpRuntimeTool? {
        toolSchemaCache[serverName]?.let { cached ->
            return cached.find { it.name == toolName }
        }

        try {
            val client = mcpManager.getOrCreateSession(serverName) ?: return null
            val tools = runBlocking { withTimeout(CONNECT_TIMEOUT_MS) { client.listTools() } }
            toolSchemaCache[serverName] = tools

            return tools.find { it.name == toolName }
        } catch (e: TimeoutCancellationException) {
            AppLogger.w(TAG, "获取工具信息超时: $serverName")
            return null
        } catch (e: Exception) {
            AppLogger.w(TAG, "获取工具信息失败: ${e.message}")
            return null
        }
    }

    /**
     * 自动转换参数类型
     *
     * 将字符串参数转换为适当的类型（包括 number、boolean、array 等）
     * 支持递归处理数组内的元素
     */
    private fun convertParameterTypes(
            parameters: Map<String, Any>,
            toolInfo: McpRuntimeTool?
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        parameters.forEach { (name, value) ->
            // 尝试从工具定义中获取参数类型（从 inputSchema.properties 中获取）
            val expectedType =
                    toolInfo?.inputSchemaObject()?.optJSONObject("properties")?.let {
                                properties ->
                        properties.optJSONObject(name)?.optString("type")
                    }

            // 使用 MCPToolParameter.smartConvert 进行智能类型转换
            val convertedValue = MCPToolParameter.smartConvert(value, expectedType)

            if (convertedValue != value) {
                AppLogger.d(
                        TAG,
                        "参数 $name 从 ${value::class.java.simpleName} 转换为 ${convertedValue::class.java.simpleName}: $value -> $convertedValue"
                )
            }

            result[name] = convertedValue
        }

        return result
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 验证工具名称格式
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "Invalid MCP tool name format, should be 'server_name:tool_name'"
            )
        }

        findArgumentIntegrityViolation(tool)?.let { violation ->
            // U+FFFD means the original argument bytes have already been lost. Sending a write
            // request would silently persist corrupted user content on the remote MCP server.
            AppLogger.e(
                    TAG,
                    "Blocked MCP tool call with corrupted argument: tool=${tool.name}, " +
                            "parameter=${violation.parameterName}, " +
                            "replacementCount=${violation.replacementCharacterCount}, " +
                            "characterOffset=${violation.characterOffset}, " +
                            "utf8ByteOffset=${violation.utf8ByteOffset}"
            )
            return ToolValidationResult(
                    valid = false,
                    errorMessage =
                            "MCP tool argument '${violation.parameterName}' contains " +
                                    "${violation.replacementCharacterCount} invalid UTF-8 " +
                                    "replacement character(s) (U+FFFD); the call was blocked " +
                                    "before it reached the MCP server. First UTF-8 byte offset: " +
                                    violation.utf8ByteOffset
            )
        }

        return ToolValidationResult(valid = true)
    }

    private fun findArgumentIntegrityViolation(tool: AITool): ArgumentIntegrityViolation? {
        val parameter = tool.parameters.firstOrNull { it.value.contains(REPLACEMENT_CHARACTER) }
                ?: return null
        val characterOffset = parameter.value.indexOf(REPLACEMENT_CHARACTER)
        val utf8ByteOffset = parameter.value
                .substring(0, characterOffset)
                .toByteArray(Charsets.UTF_8)
                .size
        return ArgumentIntegrityViolation(
                parameterName = parameter.name,
                replacementCharacterCount = parameter.value.count { it == REPLACEMENT_CHARACTER },
                characterOffset = characterOffset,
                utf8ByteOffset = utf8ByteOffset
        )
    }
}

/** Manages transport-neutral MCP runtime sessions and their presentation metadata. */
class MCPManager(private val context: Context) {
    companion object {
        private const val TAG = "MCPManager"

        @Volatile private var INSTANCE: MCPManager? = null

        fun getInstance(context: Context): MCPManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: MCPManager(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    private val sessionCache = ConcurrentHashMap<String, McpRuntimeSession>()
    private val runtimeDescriptorCache = ConcurrentHashMap<String, McpRuntimeDescriptor>()

    // 缓存服务器配置
    private val serverConfigCache = ConcurrentHashMap<String, MCPServerConfig>()
    private val connectionFailureReasons = ConcurrentHashMap<String, String>()

    /**
     * 检查服务器是否已注册
     *
     * @param serverName 服务器名称
     * @return 如果服务器已注册则返回true
     */
    fun isServerRegistered(serverName: String): Boolean {
        return serverConfigCache.containsKey(serverName)
    }

    /**
     * 获取所有已注册的服务器配置
     *
     * @return 服务器名称到服务器配置的映射
     */
    fun getRegisteredServers(): Map<String, MCPServerConfig> {
        return serverConfigCache.toMap()
    }

    fun getLastConnectionFailureReason(serverName: String): String? {
        return connectionFailureReasons[serverName]
    }

    fun registerRuntime(pluginId: String, descriptor: McpRuntimeDescriptor) {
        val previous = runtimeDescriptorCache.put(pluginId, descriptor)
        connectionFailureReasons.remove(pluginId)
        // The runtime behind this plugin changed, so its tool definitions may have changed too.
        MCPToolExecutor.invalidateToolSchemaCache(pluginId)
        if (previous != null && previous != descriptor) {
            sessionCache.remove(pluginId)?.let { oldSession ->
                kotlinx.coroutines.runBlocking { oldSession.close() }
            }
        }
    }

    fun getOrCreateSession(pluginId: String): McpRuntimeSession? {
        val cachedSession = sessionCache[pluginId]
        if (cachedSession != null) {
            if (cachedSession.isConnected()) return cachedSession

            try {
                if (kotlinx.coroutines.runBlocking { cachedSession.connect() }) {
                    connectionFailureReasons.remove(pluginId)
                    return cachedSession
                }
            } catch (e: Exception) {
                connectionFailureReasons[pluginId] =
                        "Exception while reconnecting MCP runtime: ${e.message ?: e.javaClass.simpleName}"
                AppLogger.e(TAG, "重连 MCP runtime 失败: $pluginId", e)
            }
            sessionCache.remove(pluginId)
            kotlinx.coroutines.runBlocking { cachedSession.close() }
        }

        val descriptor = runtimeDescriptorCache[pluginId]
                ?: run {
                    connectionFailureReasons[pluginId] =
                            "MCP runtime is not registered for plugin $pluginId"
                    return null
                }

        val session = when (descriptor) {
            is McpRuntimeDescriptor.Local ->
                com.ai.assistance.operit.data.mcp.plugins.BridgeMcpRuntimeSession(context, descriptor.serviceName)
            is McpRuntimeDescriptor.Remote ->
                com.ai.assistance.operit.data.mcp.plugins.RemoteMcpRuntimeSession(pluginId, descriptor)
        }

        return try {
            AppLogger.d(TAG, "连接 MCP runtime: $pluginId")
            if (!kotlinx.coroutines.runBlocking { session.connect() }) {
                connectionFailureReasons[pluginId] = "MCP runtime connection failed"
                kotlinx.coroutines.runBlocking { session.close() }
                null
            } else {
                sessionCache[pluginId] = session
                connectionFailureReasons.remove(pluginId)
                session
            }
        } catch (e: Exception) {
            connectionFailureReasons[pluginId] =
                    "Exception while creating MCP runtime: ${e.message ?: e.javaClass.simpleName}"
            AppLogger.e(TAG, "创建 MCP runtime 失败: $pluginId", e)
            kotlinx.coroutines.runBlocking { session.close() }
            null
        }
    }

    /**
     * 注册MCP服务器配置
     *
     * @param serverName 服务器名称
     * @param serverConfig 服务器配置
     */
    fun registerServer(
            pluginId: String,
            serverConfig: MCPServerConfig,
            descriptor: McpRuntimeDescriptor
    ) {
        serverConfigCache[pluginId] = serverConfig
        registerRuntime(pluginId, descriptor)
    }

    /**
     * 注销MCP服务器配置
     *
     * @param serverName 服务器名称
     */
    fun unregisterServer(serverName: String) {
        serverConfigCache.remove(serverName)
        runtimeDescriptorCache.remove(serverName)
        connectionFailureReasons.remove(serverName)
        MCPToolExecutor.invalidateToolSchemaCache(serverName)
        sessionCache.remove(serverName)?.let { session ->
            kotlinx.coroutines.runBlocking { session.close() }
        }
    }

    /** 关闭所有MCP客户端连接 */
    fun shutdown() {
        sessionCache.values.forEach { session ->
            kotlinx.coroutines.runBlocking { session.close() }
        }
        sessionCache.clear()
        runtimeDescriptorCache.clear()
        serverConfigCache.clear()
        MCPToolExecutor.invalidateToolSchemaCache()
    }
}
