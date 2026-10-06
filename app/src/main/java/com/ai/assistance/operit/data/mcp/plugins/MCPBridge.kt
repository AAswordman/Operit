package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.PortProcessKiller
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/**
 * MCPBridge - 用于与TCP桥接器通信的插件类 支持以下命令:
 * - spawn: 启动新的MCP服务
 * - shutdown: 关闭当前MCP服务
 * - listtools: 列出所有可用工具
 * - toolcall: 调用特定工具
 * - list: 列出已注册的MCP服务或查询单个服务状态
 * - register: 注册新的MCP服务
 * - unregister: 取消注册MCP服务
 * - reset: 重置桥接器
 */
class MCPBridge private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPBridge"
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val BRIDGE_PORT = 8752  // 远程bridge监听的端口
        private const val CLIENT_PORT = 8751  // Android客户端连接的端口（SSH转发）
        private const val TERMUX_BRIDGE_PATH = "~/bridge"
        private const val START_COMMAND_THROTTLE_MS = 4000L
        private const val COMMAND_CONNECTION_KEEP_MS = 3500L
        private const val DETECT_PORT_CACHE_MS = 3500L

        // Connect timeout for every bridge socket. Kept short: the bridge is on loopback.
        private const val SOCKET_CONNECT_TIMEOUT_MS = 5000

        // Read timeout for the shared control channel (list / listtools / register / ...).
        // These commands are served from in-memory maps, so a slow answer means the bridge is
        // wedged. Never let a control command occupy the shared socket for 3 minutes.
        private const val CONTROL_SO_TIMEOUT_MS = 15_000

        // Read timeout for toolcall on its dedicated socket. Tool calls can legitimately be slow
        // (a shell command with sudo, a network fetch), so this is longer than the control
        // channel but still bounded well below the previous 180s blanket value.
        private const val TOOLCALL_SO_TIMEOUT_MS = 60_000

        // Read timeout for spawn on its dedicated socket.
        private const val SPAWN_SO_TIMEOUT_MS = 180_000

        private var appContext: Context? = null

        private val startBridgeMutex = Mutex()

        private val commandConnectionMutex = Mutex()
        private val commandConnectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var commandSocket: Socket? = null

        @Volatile
        private var commandWriter: PrintWriter? = null

        @Volatile
        private var commandReader: BufferedReader? = null

        @Volatile
        private var commandHost: String? = null

        @Volatile
        private var commandPort: Int? = null

        @Volatile
        private var commandLastUsedAtMs: Long = 0L

        @Volatile
        private var commandCloseJob: Job? = null

        private val detectPortMutex = Mutex()

        @Volatile
        private var cachedDetectedPort: Int? = null

        @Volatile
        private var cachedDetectedPortAtMs: Long = 0L

        @Volatile
        private var startBridgeDeferred: CompletableDeferred<Boolean>? = null

        @Volatile
        private var lastStartCommandAtMs: Long = 0L
        
        @Volatile
        private var INSTANCE: MCPBridge? = null
        
        fun getInstance(context: Context): MCPBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MCPBridge(context.applicationContext).also { 
                    INSTANCE = it
                    appContext = context.applicationContext
                }
            }
        }
        
        /**
         * 智能检测可用端口
         * 优先尝试 8752（本地直连），失败后尝试 8751（SSH转发）
         */
        private suspend fun detectPort(): Int = withContext(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val cached = cachedDetectedPort
            if (cached != null && nowMs - cachedDetectedPortAtMs <= DETECT_PORT_CACHE_MS) {
                return@withContext cached
            }

            return@withContext detectPortMutex.withLock {
                val lockNowMs = System.currentTimeMillis()
                val lockCached = cachedDetectedPort
                if (lockCached != null && lockNowMs - cachedDetectedPortAtMs <= DETECT_PORT_CACHE_MS) {
                    return@withLock lockCached
                }

                val detectedPort =
                    if (isPortAvailable(DEFAULT_HOST, BRIDGE_PORT)) {
                        AppLogger.d(TAG, "检测到本地环境，使用端口 $BRIDGE_PORT")
                        BRIDGE_PORT
                    } else {
                        AppLogger.d(TAG, "本地连接失败，尝试SSH转发端口 $CLIENT_PORT")
                        CLIENT_PORT
                    }

                cachedDetectedPort = detectedPort
                cachedDetectedPortAtMs = lockNowMs
                return@withLock detectedPort
            }
        }
        
        /**
         * 检查端口是否可用（快速检测，无日志污染）
         */
        private fun isPortAvailable(host: String, port: Int): Boolean {
            var socket: Socket? = null
            return try {
                socket = Socket()
                socket.reuseAddress = true
                socket.connect(java.net.InetSocketAddress(host, port), 500) // 500ms快速检测
                socket.isConnected
            } catch (e: Exception) {
                false // 静默失败，不记录日志
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // 静默关闭
                }
            }
        }

        private fun closeCommandConnectionLocked() {
            try { commandWriter?.close() } catch (e: Exception) { }
            try { commandReader?.close() } catch (e: Exception) { }
            try { commandSocket?.close() } catch (e: Exception) { }
            commandWriter = null
            commandReader = null
            commandSocket = null
            commandHost = null
            commandPort = null
            commandLastUsedAtMs = 0L
            commandCloseJob?.cancel()
            commandCloseJob = null
        }

        private fun isCommandSocketReusable(host: String, port: Int, nowMs: Long): Boolean {
            val socket = commandSocket ?: return false
            val writer = commandWriter ?: return false
            val reader = commandReader ?: return false
            if (commandHost != host) return false
            if (commandPort != port) return false
            if (nowMs - commandLastUsedAtMs > COMMAND_CONNECTION_KEEP_MS) return false
            if (!socket.isConnected) return false
            if (socket.isClosed) return false
            if (socket.isInputShutdown || socket.isOutputShutdown) return false
            if (writer.checkError()) return false
            return true
        }

        private fun scheduleCommandConnectionCloseLocked() {
            commandCloseJob?.cancel()
            val expectedLastUsed = commandLastUsedAtMs
            commandCloseJob = commandConnectionScope.launch {
                delay(COMMAND_CONNECTION_KEEP_MS)
                commandConnectionMutex.withLock {
                    if (commandLastUsedAtMs == expectedLastUsed &&
                        System.currentTimeMillis() - commandLastUsedAtMs >= COMMAND_CONNECTION_KEEP_MS
                    ) {
                        closeCommandConnectionLocked()
                    }
                }
            }
        }

        // 部署桥接器到终端
        suspend fun deployBridge(context: Context, sessionId: String? = null): Boolean {
            appContext = context.applicationContext
            return withContext(Dispatchers.IO) {
                try {
                    // 1. 首先将桥接器从assets复制到sdcard/Download/Operit/bridge目录
                    val publicBridgeDir = OperitPaths.bridgeDir()

                    // 复制打包后的 index.js 到公共目录（已包含所有依赖）
                    val inputStream = context.assets.open("bridge/index.js")
                    val indexJsContent = inputStream.bufferedReader().use { it.readText() }
                    val outputFile = File(publicBridgeDir, "index.js")
                    outputFile.writeText(indexJsContent)
                    inputStream.close()

                    // 复制打包后的 spawn-helper.js 到公共目录（已包含所有依赖）
                    val spawnHelperInputStream = context.assets.open("bridge/spawn-helper.js")
                    val spawnHelperJsContent = spawnHelperInputStream.bufferedReader().use { it.readText() }
                    val spawnHelperOutputFile = File(publicBridgeDir, "spawn-helper.js")
                    spawnHelperOutputFile.writeText(spawnHelperJsContent)
                    spawnHelperInputStream.close()

                    AppLogger.d(TAG, "桥接器文件已复制到公共目录: ${publicBridgeDir.absolutePath}")

                    // 2. 确保终端目录存在并复制文件
                    // 获取终端管理器
                    val terminal = Terminal.getInstance(context)
                    
                    // 确保已连接到终端服务
                    if (!terminal.isConnected()) {
                        val connected = terminal.initialize()
                        if (!connected) {
                            AppLogger.e(TAG, "无法连接到终端服务")
                            return@withContext false
                        }
                    }

                    // 使用传入的sessionId或创建新的会话
                    val actualSessionId = sessionId ?: run {
                        val newSessionId = terminal.createSession("mcp-bridge-deploy")
                        if (newSessionId == null) {
                            AppLogger.e(TAG, "无法创建终端会话或会话初始化超时")
                            return@withContext false
                        }
                        newSessionId
                    }

                    // 使用sdcard路径而不是Android storage路径
                    val sdcardBridgePath = OperitPaths.bridgePathSdcard()
                    
                    // 获取 AIToolHandler 实例
                    val toolHandler = AIToolHandler.getInstance(context)
                    
                    // 先创建目标目录
                    val mkdirCommand = "mkdir -p $TERMUX_BRIDGE_PATH"
                    terminal.executeCommand(actualSessionId, mkdirCommand)
                    delay(100) // 等待目录创建
                    
                    // 使用 AIToolHandler 复制打包后的文件（跨环境复制：Android -> Linux）
                    // 打包后的文件已包含所有依赖，不需要 package.json 和 node_modules
                    val filesToCopy = listOf("index.js", "spawn-helper.js")
                    
                    for (fileName in filesToCopy) {
                        val copyTool = AITool(
                            name = "copy_file",
                            parameters = listOf(
                                ToolParameter("source", "$sdcardBridgePath/$fileName"),
                                ToolParameter("destination", "$TERMUX_BRIDGE_PATH/$fileName"),
                                ToolParameter("source_environment", "android"),
                                ToolParameter("dest_environment", "linux"),
                                ToolParameter("recursive", "false")
                            )
                        )
                        
                        val result = toolHandler.executeTool(copyTool)
                        if (!result.success) {
                            AppLogger.e(TAG, "复制文件 $fileName 失败: ${result.error}")
                            return@withContext false
                        }
                        AppLogger.d(TAG, "成功复制文件: $fileName")
                    }
                    
                    // 打包后的文件已包含所有依赖，无需安装 node_modules

                    AppLogger.d(TAG, "桥接器成功部署到终端")
                    return@withContext true
                } catch (e: Exception) {
                    AppLogger.e(TAG, "部署桥接器异常", e)
                    return@withContext false
                }
            }
        }

        // 在终端中启动桥接器
        suspend fun startBridge(
                context: Context? = null,
                port: Int = BRIDGE_PORT,
                mcpCommand: String? = null,
                mcpArgs: List<String>? = null,
                sessionId: String? = null
        ): Boolean =
                withContext(Dispatchers.IO) {
                    // 使用传入的context或保存的appContext
                    val ctx = context ?: appContext
                    if (ctx == null) {
                        AppLogger.e(TAG, "没有可用的上下文，无法执行命令")
                        return@withContext false
                    }

                    var isLeader = false
                    val deferred = startBridgeMutex.withLock {
                        val inFlight = startBridgeDeferred
                        if (inFlight != null && !inFlight.isCompleted) {
                            inFlight
                        } else {
                            val newDeferred = CompletableDeferred<Boolean>()
                            startBridgeDeferred = newDeferred
                            isLeader = true
                            newDeferred
                        }
                    }

                    if (!isLeader) {
                        return@withContext deferred.await()
                    }

                    try {
                        closeCommandConnectionLocked()
                        PortProcessKiller.killListeners(port)
                        cachedDetectedPort = null
                        cachedDetectedPortAtMs = 0L

                        // 获取终端管理器
                        val terminal = Terminal.getInstance(ctx)
                        
                        // 确保已连接到终端服务
                        if (!terminal.isConnected()) {
                            val connected = terminal.initialize()
                            if (!connected) {
                                AppLogger.e(TAG, "无法连接到终端服务")
                                deferred.complete(false)
                                return@withContext false
                            }
                        }

                        // 使用传入的sessionId或创建新的会话
                        val actualSessionId = sessionId ?: run {
                            val newSessionId = terminal.createSession("mcp-bridge-daemon")
                            if (newSessionId == null) {
                                AppLogger.e(TAG, "无法创建终端会话或会话初始化超时")
                                deferred.complete(false)
                                return@withContext false
                            }
                            newSessionId
                        }

                        // 构建启动命令 - 使用后台方式运行
                        val command = StringBuilder("cd $TERMUX_BRIDGE_PATH && node index.js $port")
                        if (mcpCommand != null) {
                            command.append(" $mcpCommand")
                            if (mcpArgs != null && mcpArgs.isNotEmpty()) {
                                command.append(" ${mcpArgs.joinToString(" ")}")
                            }
                        }
                        command.append(" &")

                        val now = System.currentTimeMillis()
                        val shouldSendStartCommand = startBridgeMutex.withLock {
                            val last = lastStartCommandAtMs
                            if (now - last < START_COMMAND_THROTTLE_MS) {
                                false
                            } else {
                                lastStartCommandAtMs = now
                                true
                            }
                        }

                        if (shouldSendStartCommand) {
                            AppLogger.d(TAG, "发送启动命令: $command")
                            AppLogger.d(TAG, "进行桥接器启动...")
                            terminal.executeCommand(actualSessionId, command.toString())
                        } else {
                            AppLogger.w(TAG, "桥接器启动命令发送过于频繁，跳过本次发送")
                        }

                        // 等待一段时间让桥接器启动
                        AppLogger.d(TAG, "等待桥接器启动...")
                        delay(2000)

                        // 验证桥接器是否成功启动 - 尝试三次
                        var isRunning = false
                        for (i in 1..3) {
                            val checkResult = getInstance(ctx).listMcpServices()
                            if (checkResult != null && checkResult.optBoolean("success", false)) {
                                AppLogger.d(TAG, "桥接器成功启动，list响应: $checkResult")
                                isRunning = true
                                break
                            }
                            AppLogger.d(TAG, "第${i}次尝试连接桥接器失败，等待1秒后重试")
                            delay(1000)
                        }

                        // 如果三次尝试后仍然无法ping通，检查日志
                        if (!isRunning) {
                            AppLogger.e(TAG, "桥接器可能未成功启动。请检查终端会话 'mcp-bridge-daemon' 的输出。")
                        }

                        deferred.complete(isRunning)
                        return@withContext isRunning
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "启动桥接器异常", e)
                        deferred.complete(false)
                        return@withContext false
                    } finally {
                        startBridgeMutex.withLock {
                            if (startBridgeDeferred === deferred) {
                                startBridgeDeferred = null
                            }
                        }
                    }
                }

        // 重置桥接器（静态方法）
        suspend fun reset(context: Context? = null): JSONObject? =
                withContext(Dispatchers.IO) {
                    try {
                        AppLogger.d(TAG, "重置桥接器 - 关闭所有服务并清空注册表...")
                        val ctx = context ?: appContext
                        if (ctx == null) {
                            AppLogger.e(TAG, "Cannot reset bridge: context is null")
                            return@withContext null
                        }
                        val response = sendCommand(ctx, MCPBridgeClient.buildResetCommand())
                        if (response?.optBoolean("success", false) == true) {
                            AppLogger.i(TAG, "桥接器重置成功")
                        } else {
                            AppLogger.w(TAG, "桥接器重置失败")
                        }
                        return@withContext response
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "重置桥接器异常", e)
                        return@withContext null
                    }
                }

        private fun sendCommandThroughStream(
            command: JSONObject,
            writer: PrintWriter,
            reader: BufferedReader,
            cmdId: String,
            cmdType: String,
            serviceName: String?,
            emptyResponseMessage: String
        ): JSONObject? {
            return try {
                writer.println(command.toString())
                writer.flush()

                val response = reader.readLine()
                if (response.isNullOrBlank()) {
                    AppLogger.e(TAG, emptyResponseMessage)
                    null
                } else {
                    AppLogger.d(TAG, "命令[$cmdId: $cmdType${if (!serviceName.isNullOrBlank()) " service=$serviceName" else ""}]响应: $response")
                    JSONObject(response)
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Distinguish a read timeout from a generic transport failure. Previously both
                // surfaced as "通信或解析失败", which hid the single most common cause of a
                // "stuck" MCP call: the bridge accepted the request but never answered, so the
                // client sat on readLine() until soTimeout elapsed.
                AppLogger.e(
                    TAG,
                    "命令[$cmdId: $cmdType${if (!serviceName.isNullOrBlank()) " service=$serviceName" else ""}]读取超时，" +
                        "桥接器未在该超时内返回结果（服务可能仍在运行该工具，或子进程已挂起）"
                )
                null
            } catch (e: Exception) {
                AppLogger.e(TAG, "命令[$cmdId: $cmdType]通信或解析失败: ${e.message}")
                null
            }
        }

        suspend fun sendCommand(
            command: JSONObject,
            host: String = DEFAULT_HOST,
            port: Int? = null
        ): JSONObject? {
            val ctx = appContext
            if (ctx == null) {
                AppLogger.e(TAG, "Cannot send command: appContext is null")
                return null
            }
            return sendCommand(ctx, command, host, port)
        }

        // 发送命令到桥接器
        suspend fun sendCommand(
                context: Context,
                command: JSONObject,
                host: String = DEFAULT_HOST,
                port: Int? = null
        ): JSONObject? =
                withContext(Dispatchers.IO) {
                    try {
                        // 自动检测端口（如果未指定）
                        val actualPort = port ?: detectPort()
                        val nowMs = System.currentTimeMillis()
                        
                        // Extract command details for better logging
                        val cmdType = command.optString("command", "unknown")
                        val cmdId = command.optString("id", "no-id")
                        val params = command.optJSONObject("params")

                        // Enhanced logging with special handling for commands with service names
                        val serviceName = params?.optString("name")
                        val logMessage =
                                if (serviceName != null && serviceName.isNotEmpty()) {
                                    "${context.getString(R.string.mcp_send_command)}[$cmdId]: $cmdType ${context.getString(R.string.mcp_service_label)}: $serviceName ${context.getString(R.string.mcp_other_params)}: ${params.toString()}"
                                } else {
                                    "${context.getString(R.string.mcp_send_command)}[$cmdId]: $cmdType ${if (params != null) "参数: $params" else ""}"
                                }

                        AppLogger.d(TAG, logMessage)

                        // `spawn` and `toolcall` run on a dedicated socket instead of the shared
                        // control channel.
                        //
                        // Why this matters: the shared channel is guarded by a single global
                        // `commandConnectionMutex`, and a read on it blocks for the whole
                        // socket read timeout. Previously a `toolcall` that never returned (a
                        // sudo/shell command waiting on a password prompt, a wedged child
                        // process, a dead PTY) held that global mutex for up to 180s, which
                        // stalled *every* other MCP command in the app -- including the
                        // `list`/`listtools` calls that back isActive(), ping() and getTools().
                        // One stuck tool call therefore froze the entire MCP subsystem.
                        //
                        // Isolating tool calls on their own connection bounds the damage to the
                        // single call that is actually stuck.
                        if (cmdType == "spawn" || cmdType == "toolcall") {
                            val dedicatedSoTimeout =
                                if (cmdType == "spawn") SPAWN_SO_TIMEOUT_MS else TOOLCALL_SO_TIMEOUT_MS
                            var dedicatedSocket: Socket? = null
                            return@withContext try {
                                dedicatedSocket = Socket().apply {
                                    reuseAddress = true
                                    soTimeout = dedicatedSoTimeout
                                    connect(
                                        java.net.InetSocketAddress(host, actualPort),
                                        SOCKET_CONNECT_TIMEOUT_MS
                                    )
                                }

                                val dedicatedWriter = PrintWriter(dedicatedSocket.getOutputStream(), true)
                                val dedicatedReader = BufferedReader(InputStreamReader(dedicatedSocket.getInputStream()))

                                sendCommandThroughStream(
                                    command = command,
                                    writer = dedicatedWriter,
                                    reader = dedicatedReader,
                                    cmdId = cmdId,
                                    cmdType = cmdType,
                                    serviceName = serviceName,
                                    emptyResponseMessage = "命令[$cmdId: $cmdType]没有收到响应（独立连接）"
                                )
                            } catch (e: java.net.SocketTimeoutException) {
                                AppLogger.e(
                                    TAG,
                                    "命令[$cmdId: $cmdType]在 ${dedicatedSoTimeout}ms 内没有收到响应（独立连接），已放弃等待"
                                )
                                null
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "发送独立连接命令失败[$cmdType]: ${e.message}")
                                null
                            } finally {
                                try {
                                    dedicatedSocket?.close()
                                } catch (_: Exception) {
                                }
                            }
                        }

                        return@withContext commandConnectionMutex.withLock {
                            val canReuse = isCommandSocketReusable(host, actualPort, nowMs)
                            if (!canReuse) {
                                closeCommandConnectionLocked()

                                val newSocket = Socket()
                                newSocket.reuseAddress = true
                                newSocket.soTimeout = CONTROL_SO_TIMEOUT_MS
                                newSocket.connect(
                                    java.net.InetSocketAddress(host, actualPort),
                                    SOCKET_CONNECT_TIMEOUT_MS
                                )

                                commandSocket = newSocket
                                commandWriter = PrintWriter(newSocket.getOutputStream(), true)
                                commandReader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                                commandHost = host
                                commandPort = actualPort
                            }

                            commandLastUsedAtMs = nowMs
                            scheduleCommandConnectionCloseLocked()

                            val writer = commandWriter
                            val reader = commandReader
                            if (writer == null || reader == null) {
                                closeCommandConnectionLocked()
                                return@withLock null
                            }

                            val jsonResponse = sendCommandThroughStream(
                                command = command,
                                writer = writer,
                                reader = reader,
                                cmdId = cmdId,
                                cmdType = cmdType,
                                serviceName = serviceName,
                                emptyResponseMessage = "命令[$cmdId: $cmdType]没有收到响应"
                            )

                            if (jsonResponse == null) {
                                closeCommandConnectionLocked()
                                return@withLock null
                            }

                            commandLastUsedAtMs = System.currentTimeMillis()
                            scheduleCommandConnectionCloseLocked()
                            return@withLock jsonResponse
                        }
                    } catch (e: Exception) {
                        // 简化错误日志 - 只记录关键信息
                        val cmdType = command.optString("command", "unknown")
                        AppLogger.e(TAG, "发送命令失败[$cmdType]: ${e.message}")
                        return@withContext null
                    }
                }
    }

    // 注册新的MCP服务
    suspend fun registerMcpService(
        name: String,
        command: String,
        args: List<String> = emptyList(),
        description: String? = null,
        env: Map<String, String> = emptyMap(),
        cwd: String? = null
    ): JSONObject? {
        return sendCommand(
            MCPBridgeClient.buildRegisterLocalCommand(
                name = name,
                command = command,
                args = args,
                description = description,
                env = env,
                cwd = cwd
            )
        )
    }

    // 取消注册MCP服务
    suspend fun unregisterMcpService(name: String): JSONObject? {
        return sendCommand(MCPBridgeClient.buildUnregisterCommand(name))
    }

    // 列出所有注册的MCP服务或查询单个服务
    suspend fun listMcpServices(serviceName: String? = null): JSONObject? {
        return sendCommand(MCPBridgeClient.buildListServicesCommand(serviceName))
    }

    // 启动MCP服务
    suspend fun spawnMcpService(
        name: String? = null,
        command: String? = null,
        args: List<String>? = null,
        env: Map<String, String>? = null,
        cwd: String? = null,
        timeoutMs: Long? = null
    ): JSONObject? {
        return sendCommand(
            MCPBridgeClient.buildSpawnCommand(
                name = name,
                command = command,
                args = args,
                env = env,
                cwd = cwd,
                timeoutMs = timeoutMs
            )
        )
    }

    // 停止MCP服务（不注销）
    suspend fun unspawnMcpService(name: String): JSONObject? {
        return sendCommand(MCPBridgeClient.buildUnspawnCommand(name))
    }

    // 获取工具列表
    suspend fun listTools(serviceName: String? = null): JSONObject? {
        AppLogger.d(TAG, "获取工具列表${if (serviceName != null) " 服务: $serviceName" else " (默认服务)"}")
        return sendCommand(MCPBridgeClient.buildListToolsCommand(serviceName))
    }

    // 调用工具
    suspend fun callTool(method: String, params: JSONObject): JSONObject? {
        return sendCommand(MCPBridgeClient.buildToolCallCommand(name = null, method = method, params = params))
    }

    // 简化调用工具的方法
    suspend fun toolcall(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> paramsJson.put(key, value) }
        return callTool(method, paramsJson)
    }

    /**
     * 查询特定MCP服务的状态
     *
     * @param serviceName 要查询的服务名称
     * @return 服务信息响应，如果失败则返回null
     */
    suspend fun getServiceStatus(serviceName: String): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "查询服务 $serviceName 的状态")
                return@withContext listMcpServices(serviceName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "查询服务状态时出错: ${e.message}")
                return@withContext null
            }
        }

    suspend fun getServiceLogs(serviceName: String): JSONObject? {
        return sendCommand(MCPBridgeClient.buildLogsCommand(serviceName))
    }

    /**
     * 重置桥接器 - 关闭所有服务、清空注册表和池子
     *
     * @return 重置是否成功
     */
    suspend fun resetBridge(): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "开始重置桥接器，关闭所有服务并清空注册表...")
                val response = sendCommand(MCPBridgeClient.buildResetCommand())

                if (response?.optBoolean("success", false) == true) {
                    AppLogger.i(TAG, "桥接器重置成功")
                    return@withContext response
                } else {
                    AppLogger.w(TAG, "桥接器重置失败")
                    return@withContext null
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "重置桥接器时出错: ${e.message}")
                return@withContext null
            }
        }
}
