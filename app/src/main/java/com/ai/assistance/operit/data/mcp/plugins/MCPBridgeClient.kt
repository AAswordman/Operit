package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.R
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** MCPBridgeClient - Client for communicating with MCP services through a bridge */
class MCPBridgeClient(private val context: Context, private val serviceName: String) {
    companion object {
        private const val TAG = "MCPBridgeClient"
        private const val DEFAULT_SPAWN_TIMEOUT_MS = 180000L

        fun buildRegisterLocalCommand(
            name: String,
            command: String,
            args: List<String> = emptyList(),
            description: String? = null,
            env: Map<String, String> = emptyMap(),
            cwd: String? = null
        ): JSONObject {
            val params = JSONObject().apply {
                put("type", "local")
                put("name", name)
                put("command", command)
                if (args.isNotEmpty()) {
                    put("args", JSONArray().apply { args.forEach { put(it) } })
                }
                if (description != null) {
                    put("description", description)
                }
                if (env.isNotEmpty()) {
                    val envObj = JSONObject()
                    env.forEach { (key, value) -> envObj.put(key, value) }
                    put("env", envObj)
                }
                if (cwd != null) {
                    put("cwd", cwd)
                }
            }

            return JSONObject().apply {
                put("command", "register")
                put("id", UUID.randomUUID().toString())
                put("params", params)
            }
        }

        fun buildUnregisterCommand(name: String): JSONObject {
            return JSONObject().apply {
                put("command", "unregister")
                put("id", UUID.randomUUID().toString())
                put("params", JSONObject().apply { put("name", name) })
            }
        }

        fun buildListServicesCommand(serviceName: String? = null): JSONObject {
            return JSONObject().apply {
                put("command", "list")
                put("id", UUID.randomUUID().toString())
                if (serviceName != null) {
                    put("params", JSONObject().apply { put("name", serviceName) })
                }
            }
        }

        fun buildSpawnCommand(
            name: String? = null,
            command: String? = null,
            args: List<String>? = null,
            env: Map<String, String>? = null,
            cwd: String? = null,
            timeoutMs: Long? = null
        ): JSONObject {
            val params = JSONObject()
            if (name != null) {
                params.put("name", name)
            }
            if (command != null) {
                params.put("command", command)
            }
            if (args != null && args.isNotEmpty()) {
                params.put("args", JSONArray().apply { args.forEach { put(it) } })
            }
            if (env != null && env.isNotEmpty()) {
                val envObj = JSONObject()
                env.forEach { (key, value) -> envObj.put(key, value) }
                params.put("env", envObj)
            }
            if (cwd != null) {
                params.put("cwd", cwd)
            }
            if (timeoutMs != null) {
                params.put("timeoutMs", timeoutMs)
            }

            return JSONObject().apply {
                put("command", "spawn")
                put("id", UUID.randomUUID().toString())
                put("params", params)
            }
        }

        fun buildUnspawnCommand(name: String): JSONObject {
            return JSONObject().apply {
                put("command", "unspawn")
                put("id", UUID.randomUUID().toString())
                put("params", JSONObject().apply { put("name", name) })
            }
        }

        fun buildListToolsCommand(serviceName: String? = null): JSONObject {
            val params = JSONObject()
            if (serviceName != null) {
                params.put("name", serviceName)
            }

            return JSONObject().apply {
                put("command", "listtools")
                put("id", UUID.randomUUID().toString())
                if (params.length() > 0) {
                    put("params", params)
                }
            }
        }

        fun buildToolCallCommand(
            name: String? = null,
            method: String,
            params: JSONObject
        ): JSONObject {
            val callParams = JSONObject().apply {
                put("method", method)
                put("params", params)
                if (name != null) {
                    put("name", name)
                }
                put("id", UUID.randomUUID().toString())
            }

            return JSONObject().apply {
                put("command", "toolcall")
                put("id", UUID.randomUUID().toString())
                put("params", callParams)
            }
        }

        fun buildLogsCommand(name: String): JSONObject {
            return JSONObject().apply {
                put("command", "logs")
                put("id", UUID.randomUUID().toString())
                put("params", JSONObject().apply { put("name", name) })
            }
        }

        fun buildResetCommand(): JSONObject {
            return JSONObject().apply {
                put("command", "reset")
                put("id", UUID.randomUUID().toString())
            }
        }
    }

    private val bridge = MCPBridge.getInstance(context)
    private val isConnected = AtomicBoolean(false)
    private var lastPingTime = 0L
    @Volatile private var lastConnectionFailureDetail: String? = null

    fun getLastConnectionFailureDetail(): String? = lastConnectionFailureDetail

    private fun setLastConnectionFailureDetail(detail: String?) {
        lastConnectionFailureDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun buildSpawnFailureDetail(spawnResp: JSONObject?, fallbackMessage: String): String {
        val errorObj = spawnResp?.optJSONObject("error")
        val errorMessage = errorObj?.optString("message")?.takeIf { it.isNotBlank() } ?: fallbackMessage
        val dataObj = errorObj?.optJSONObject("data")
        val lastError = dataObj?.optString("lastError")?.trim().orEmpty()
        val logs = dataObj?.optString("logs")?.trim().orEmpty()
        val logSnippet =
            logs.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .takeLast(6)
                .joinToString(" | ")
                .take(500)

        return buildString {
            append(errorMessage)
            if (lastError.isNotBlank() && !errorMessage.contains(lastError)) {
                append(" Last error: ")
                append(lastError)
            }
            if (logSnippet.isNotBlank()) {
                append(" Logs: ")
                append(logSnippet)
            }
        }
    }

    /**
     * Connect to the MCP service.
     * If the service is registered but not active, this will attempt to spawn it.
     */
    suspend fun connect(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    // 1. First, try a quick ping. If it's already running and responsive, we're good.
                    if (ping()) {
                        AppLogger.d(TAG, "Service $serviceName is already connected and responsive.")
                        isConnected.set(true)
                        setLastConnectionFailureDetail(null)
                        return@withContext true
                    }

                    AppLogger.d(
                            TAG,
                            "Service $serviceName is not immediately responsive. Checking status and attempting to spawn if needed."
                    )

                    // 2. If ping fails, check the actual service info from the bridge.
                    val serviceInfo = getServiceInfo()

                    if (serviceInfo == null) {
                        AppLogger.w(TAG, "Service $serviceName is not registered with the bridge.")
                        isConnected.set(false)
                        setLastConnectionFailureDetail(
                            "Service is not registered with the bridge. The bridge may have restarted, reset, or the runtime service name may not match the plugin ID."
                        )
                        return@withContext false
                    }

                    if (serviceInfo.active && serviceInfo.ready) {
                        isConnected.set(true)
                        setLastConnectionFailureDetail(null)
                        return@withContext true
                    }

                    AppLogger.i(TAG, "Service $serviceName is not ready. Attempting blocking spawn...")
                    val spawnResp = spawnBlocking()
                    if (spawnResp?.optBoolean("success", false) == true) {
                        val result = spawnResp.optJSONObject("result")
                        val ready = result?.optBoolean("ready", false) ?: false
                        if (ready) {
                            AppLogger.i(TAG, "Successfully connected to service $serviceName.")
                            isConnected.set(true)
                            setLastConnectionFailureDetail(null)
                            return@withContext true
                        }
                    }

                    val errorMsg =
                        spawnResp?.optJSONObject("error")?.optString("message")
                            ?: "service not ready"
                    AppLogger.e(TAG, "Failed to connect to service $serviceName: $errorMsg")
                    isConnected.set(false)
                    setLastConnectionFailureDetail(buildSpawnFailureDetail(spawnResp, errorMsg))
                    return@withContext false
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error connecting to MCP service $serviceName: ${e.message}", e)
                    isConnected.set(false)
                    setLastConnectionFailureDetail(
                        "Exception while connecting: ${e.message ?: e.javaClass.simpleName}"
                    )
                    return@withContext false
                }
            }

    /** Check if connected */
    fun isConnected(): Boolean = isConnected.get()

    /** Ping the service */
    suspend fun ping(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val startTime = System.currentTimeMillis()
                    val result = bridge.getServiceStatus(serviceName)

                    if (result != null && result.optBoolean("success", false)) {
                        val responseObj = result.optJSONObject("result")
                        
                        // getServiceStatus always returns single service object format
                        val active = responseObj?.optBoolean("active", false) ?: false
                        val ready = responseObj?.optBoolean("ready", false) ?: false
                        
                        // Only consider it connected when active AND ready.
                        if (active && ready) {
                            lastPingTime = System.currentTimeMillis() - startTime
                            isConnected.set(true)
                            return@withContext true
                        }

                        // If it's registered but not active, we're not truly connected
                        AppLogger.d(TAG, "Service $serviceName status - active: $active, ready: $ready")
                        return@withContext false
                    }
                    return@withContext false
                } catch (e: Exception) {
                    return@withContext false
                }
            }

    /** Synchronous ping method */
    fun pingSync(): Boolean = kotlinx.coroutines.runBlocking { ping() }

    /** Spawn the MCP service if it's not already active */
    suspend fun spawnBlocking(timeoutMs: Long = DEFAULT_SPAWN_TIMEOUT_MS): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    val response =
                        MCPBridge.sendCommand(
                            context,
                            buildSpawnCommand(name = serviceName, timeoutMs = timeoutMs)
                        )
                    if (response != null && !response.optBoolean("success", false)) {
                        val errorObj = response.optJSONObject("error")
                        val errorMessage = errorObj?.optString("message").orEmpty()

                        if (errorObj != null && errorMessage.isNotBlank()) {
                            val dataObj = errorObj.optJSONObject("data") ?: JSONObject().also {
                                errorObj.put("data", it)
                            }
                            val lastError = dataObj.optString("lastError").orEmpty()
                            val logs = dataObj.optString("logs").orEmpty()

                            if (lastError.isBlank()) {
                                dataObj.put("lastError", errorMessage)
                            }
                            if (logs.isBlank()) {
                                dataObj.put("logs", errorMessage)
                            }
                        }
                    }
                    return@withContext response
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Exception during spawn for service $serviceName: ${e.message}", e)
                    return@withContext null
                }
            }

    /** Unspawn the MCP service (stops the process, but keeps it registered) */
    suspend fun unspawn(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    AppLogger.d(TAG, "Attempting to unspawn service: $serviceName")
                    val unspawnResult = MCPBridge.sendCommand(context, buildUnspawnCommand(serviceName))
                    if (unspawnResult?.optBoolean("success", false) == true) {
                        AppLogger.i(TAG, "Service $serviceName unspawned successfully.")
                        disconnect() // Set local state to disconnected
                        return@withContext true
                    } else {
                        val error =
                                unspawnResult?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"
                        AppLogger.e(TAG, "Failed to unspawn service $serviceName: $error")
                        return@withContext false
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Exception during unspawn for service $serviceName: ${e.message}", e)
                    return@withContext false
                }
            }

    /** Check if the service is currently active on the bridge */
    suspend fun isActive(): Boolean {
        return getServiceInfo()?.active ?: false
    }

    /** Call a tool on the MCP service - 返回完整的响应（包括 success, result, error） */
    suspend fun callTool(method: String, params: JSONObject): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    // Connect if not connected
                    if (!isConnected.get()) {
                        AppLogger.d(TAG, "尝试重新连接 $serviceName 服务")
                        val connectSuccess = connect()
                        if (!connectSuccess) {
                            AppLogger.e(TAG, "无法连接到 $serviceName 服务")
                            // 返回一个包含错误信息的响应
                            return@withContext JSONObject().apply {
                                put("success", false)
                                put("error", JSONObject().apply {
                                    put("code", -1)
                                    put("message", context.getString(R.string.mcp_bridge_cannot_connect_service, serviceName))
                                })
                            }
                        }
                    }

                    // Build parameters
                    val callParams =
                            JSONObject().apply {
                                put("method", method)
                                put("params", params)
                                put("name", serviceName)
                                put("id", UUID.randomUUID().toString())
                            }

                    // Build command
                    val command = buildToolCallCommand(name = serviceName, method = method, params = params)

                    // Send command
                    val response = MCPBridge.sendCommand(context, command)

                    if (response == null) {
                        // 如果响应为空，返回一个包含错误信息的对象
                        return@withContext JSONObject().apply {
                            put("success", false)
                            put("error", JSONObject().apply {
                                put("code", -1)
                                put("message", context.getString(R.string.mcp_bridge_cannot_connect_or_no_response))
                            })
                        }
                    }

                    // 返回完整的响应（包括 success, result, error）
                    if (response.optBoolean("success", false)) {
                        return@withContext response
                    } else {
                        val errorMsg =
                                response.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"

                        // Check for connection errors and handle reconnection
                        if (errorMsg.contains("not available") ||
                                        errorMsg.contains("not connected") ||
                                        errorMsg.contains("connection closed") ||
                                        errorMsg.contains("timeout")
                        ) {
                            AppLogger.w(TAG, "检测到连接错误: $errorMsg, 标记为已断开")
                            isConnected.set(false)

                            // Try to reconnect once
                            AppLogger.d(TAG, "尝试立即重新连接")
                            if (connect()) {
                                // If reconnect succeeds, try the call again (one retry)
                                AppLogger.d(TAG, "重新连接成功，重试工具调用")
                                val retryCommand = JSONObject(command.toString())
                                val retryResponse = MCPBridge.sendCommand(context, retryCommand)

                                if (retryResponse != null) {
                                    return@withContext retryResponse
                                }
                            }
                        }

                        AppLogger.e(TAG, "工具调用错误: $errorMsg")
                        return@withContext response
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error calling tool $method: ${e.message}", e)
                    // Mark as disconnected on exception
                    isConnected.set(false)
                    // 返回包含异常信息的响应
                    return@withContext JSONObject().apply {
                        put("success", false)
                        put("error", JSONObject().apply {
                            put("code", -1)
                            put("message", context.getString(R.string.mcp_bridge_tool_call_exception, e.message))
                        })
                    }
                }
            }
    /** Synchronous tool call */
    fun callToolSync(method: String, params: JSONObject): JSONObject? {
        return kotlinx.coroutines.runBlocking { callTool(method, params) }
    }

    /** Synchronous tool call with Map */
    fun callToolSync(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> 
            // 将值转换为正确的 JSON 类型
            val jsonValue = convertToJsonType(value)
            paramsJson.put(key, jsonValue)
        }
        return callToolSync(method, paramsJson)
    }

    /**
     * 将 Kotlin 类型转换为 JSON 类型
     * - List -> JSONArray
     * - Map -> JSONObject
     * - 其他 -> 保持原样
     */
    private fun convertToJsonType(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is List<*> -> {
                val jsonArray = JSONArray()
                value.forEach { item ->
                    jsonArray.put(convertToJsonType(item))
                }
                jsonArray
            }
            is Map<*, *> -> {
                val jsonObject = JSONObject()
                value.forEach { (k, v) ->
                    if (k is String) {
                        jsonObject.put(k, convertToJsonType(v))
                    }
                }
                jsonObject
            }
            // 基本类型和 JSONObject/JSONArray 保持原样
            else -> value
        }
    }

    /** Get all tools provided by the service */
    suspend fun getTools(): List<JSONObject> =
            withContext(Dispatchers.IO) {
                try {
                    // Connect if not connected
                    if (!isConnected.get()) {
                        AppLogger.d(TAG, "尝试重新连接 $serviceName 服务以获取工具列表")
                        val connectSuccess = connect()
                        if (!connectSuccess) {
                            AppLogger.e(TAG, "无法连接到 $serviceName 服务")
                            return@withContext emptyList()
                        }
                    }

                    val response = MCPBridge.sendCommand(context, buildListToolsCommand(serviceName))

                    if (response?.optBoolean("success", false) == true) {
                        val toolsArray =
                                response.optJSONObject("result")?.optJSONArray("tools")
                                        ?: return@withContext emptyList()

                        val tools = mutableListOf<JSONObject>()
                        for (i in 0 until toolsArray.length()) {
                            val tool = toolsArray.optJSONObject(i)
                            if (tool != null) {
                                tools.add(tool)
                            }
                        }

                        if (tools.isNotEmpty()) {
                            AppLogger.d(TAG, "成功获取 ${tools.size} 个工具")
                        } else {
                            AppLogger.w(TAG, "服务 $serviceName 未返回任何工具")
                        }

                        return@withContext tools
                    } else {
                        // Check for connection errors
                        val errorMsg =
                                response?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"
                        if (errorMsg.contains("not available") ||
                                        errorMsg.contains("not connected") ||
                                        errorMsg.contains("connection closed") ||
                                        errorMsg.contains("timeout") ||
                                        response == null
                        ) {
                            AppLogger.w(TAG, "获取工具列表时检测到连接错误，标记为已断开")
                            isConnected.set(false)
                        }

                        AppLogger.e(TAG, "获取工具列表失败: $errorMsg")
                        return@withContext emptyList()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting tools: ${e.message}")
                    // Mark as disconnected on exception
                    isConnected.set(false)
                    return@withContext emptyList()
                }
            }

    /** Get service info including tools count and running status */
    suspend fun getServiceInfo(): ServiceInfo? =
            withContext(Dispatchers.IO) {
                try {
                    val listResponse = MCPBridge.sendCommand(context, buildListServicesCommand()) ?: return@withContext null
                    
                    if (listResponse.optBoolean("success", false)) {
                        val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                        
                        if (services != null) {
                            for (i in 0 until services.length()) {
                                val service = services.optJSONObject(i)
                                val name = service?.optString("name", "")
                                
                                if (name == serviceName) {
                                    val active = service.optBoolean("active", false)
                                    val ready = service.optBoolean("ready", false)
                                    val toolCount = service.optInt("toolCount", 0)
                                    
                                    // 从响应中提取工具名称列表
                                    val toolNames = mutableListOf<String>()
                                    val toolsArray = service.optJSONArray("tools")
                                    if (toolsArray != null) {
                                        for (j in 0 until toolsArray.length()) {
                                            val tool = toolsArray.optJSONObject(j)
                                            val toolName = tool?.optString("name", "")
                                            if (!toolName.isNullOrEmpty()) {
                                                toolNames.add(toolName)
                                            }
                                        }
                                    }
                                    
                                    return@withContext ServiceInfo(
                                        name = name,
                                        active = active,
                                        ready = ready,
                                        toolCount = toolCount,
                                        toolNames = toolNames
                                    )
                                }
                            }
                        }
                    }
                    return@withContext null
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting service info: ${e.message}")
                    return@withContext null
                }
            }

    /** Get tool descriptions provided by the service as a list of strings */
    suspend fun getToolDescriptions(): List<String> = 
            withContext(Dispatchers.IO) {
                try {
                    val tools = getTools()
                    return@withContext tools.mapNotNull { tool ->
                        val name = tool.optString("name", "")
                        val description = tool.optString("description", "")
                        if (name.isNotEmpty()) {
                            if (description.isNotEmpty()) {
                                "$name: $description"
                            } else {
                                name
                            }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error getting tool descriptions: ${e.message}")
                    return@withContext emptyList()
                }
            }

    /** Disconnect from the service */
    fun disconnect() {
        isConnected.set(false)
    }
}

/** Data class to hold service information */
data class ServiceInfo(
    val name: String,
    val active: Boolean,
    val ready: Boolean,
    val toolCount: Int,
    val toolNames: List<String>
)
