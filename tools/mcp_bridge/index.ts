console.log('Bridge process started. Loading modules...');

/**
 * MCP TCP Bridge
 * 
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */

import * as net from 'net';
import { fork } from 'child_process';
import { randomUUID } from 'crypto';
import * as path from 'path';
import * as os from 'os';

// Configuration
interface BridgeConfig {
    port: number;
    host: string;
    mcpCommand: string;
    mcpArgs: string[];
    registryPath?: string;
    env?: Record<string, string>;
}

// MCP service registration info
export interface McpServiceInfo {
    name: string;
    description: string;
    type: 'local';
    command: string;
    args: string[];
    cwd?: string;
    env?: Record<string, string>;

    created: number;
    lastUsed?: number;
}

// Command types
type McpCommandType = 'spawn' | 'shutdown' | 'listtools' | 'toolcall' | 'list' | 'register' | 'unregister' | 'reset' | 'unspawn' | 'logs';

// Command interface
interface McpCommand {
    command: McpCommandType;
    id: string;
    params?: any;
}

// Response interface
interface McpResponse {
    id: string;
    success: boolean;
    result?: any;
    error?: {
        code: number;
        message: string;
        data?: any;
    };
}

// Tracking pending requests
interface PendingRequest {
    id: string;
    socket: net.Socket;

    timestamp: number;
    toolCallId?: string;
    timeoutMs?: number;
}

/**
 * MCP Bridge class
 */
class McpBridge {
    private config: BridgeConfig;
    private server: net.Server | null = null;

    // 本地 stdio 服务的 helper 进程映射
    private serviceHelpers: Map<string, import('child_process').ChildProcess> = new Map();
    private mcpToolsMap: Map<string, any[]> = new Map();
    private serviceReadyMap: Map<string, boolean> = new Map();

    // 服务注册表 (纯内存)
    private serviceRegistry: Map<string, McpServiceInfo> = new Map();

    // 活跃连接
    private activeConnections: Set<net.Socket> = new Set();

    // 请求跟踪
    private pendingRequests: Map<string, PendingRequest> = new Map();
    private pendingSpawnRequests: Map<string, PendingRequest> = new Map(); // 跟踪等待启动的 spawn 请求

    // 请求超时(毫秒)
    private readonly REQUEST_TIMEOUT = 180000; // 180秒超时 (3分钟)
    private readonly SPAWN_TIMEOUT = 180000; // spawn命令180秒超时

    // 服务错误记录
    private mcpErrors: Map<string, string> = new Map();
    private serviceExitSignals: Map<string, NodeJS.Signals | null> = new Map();
    private socketBuffers: Map<net.Socket, string> = new Map();

    private fatalServices: Set<string> = new Set();

    private serviceLogs: Map<string, string[]> = new Map();
    private readonly MAX_LOG_LINES = 400;
    private readonly MAX_LOG_LINE_LENGTH = 4000;

    private buildSpawnLogData(serviceName: string): any {
        return {
            name: serviceName,
            active: this.isServiceActive(serviceName),
            ready: this.serviceReadyMap.get(serviceName) || false,
            lastError: this.mcpErrors.get(serviceName) || "",
            logs: this.getServiceLogText(serviceName)
        };
    }

    private rejectPendingSpawn(serviceName: string, message: string, code: number = -32603): void {
        const pending = this.pendingSpawnRequests.get(serviceName);
        if (!pending) return;

        const response: McpResponse = {
            id: pending.id,
            success: false,
            error: {
                code,
                message,
                data: this.buildSpawnLogData(serviceName)
            }
        };
        pending.socket.write(JSON.stringify(response) + '\n');
        this.pendingSpawnRequests.delete(serviceName);
    }

    private resolvePendingSpawnSuccess(serviceName: string, toolCount: number): void {
        const pending = this.pendingSpawnRequests.get(serviceName);
        if (!pending) return;

        const response: McpResponse = {
            id: pending.id,
            success: true,
            result: {
                status: "started",
                name: serviceName,
                toolCount,
                ready: true,
                ...this.buildSpawnLogData(serviceName)
            }
        };
        pending.socket.write(JSON.stringify(response) + '\n');
        this.pendingSpawnRequests.delete(serviceName);
        console.log(`[${serviceName}] Spawn request completed successfully`);
    }

    private isFatalErrorText(text: string): boolean {
        if (!text) return false;
        return /environment variable .* is required/i.test(text)
            || /api[_-]?key.*required/i.test(text)
            || /missing required.*(api[_-]?key|token)/i.test(text);
    }

    /**
     * 检查 spawn 请求超时
     */
    private checkSpawnTimeouts(): void {
        const now = Date.now();

        for (const [serviceName, request] of this.pendingSpawnRequests.entries()) {
            const timeoutMs = request.timeoutMs ?? this.SPAWN_TIMEOUT;
            if (now - request.timestamp > timeoutMs) {
                console.log(`Spawn request timeout for service: ${serviceName}`);
                this.rejectPendingSpawn(
                    serviceName,
                    `Service '${serviceName}' failed to start within ${timeoutMs / 1000}s`
                );
            }
        }
    }

    /**
     * 检查并关闭闲置的服务
     */
    private checkIdleServices(): void {
        const now = Date.now();
        for (const serviceName of this.serviceHelpers.keys()) {
            const serviceInfo = this.serviceRegistry.get(serviceName);
            if (serviceInfo && serviceInfo.lastUsed) {
                if (now - serviceInfo.lastUsed > this.IDLE_TIMEOUT_MS) {
                    console.log(`[${serviceName}] Service has been idle for over ${this.IDLE_TIMEOUT_MS / 1000}s. Unspawning...`);

                    // Manually unspawn the service
                    const helper = this.serviceHelpers.get(serviceName);
                    if (helper) {
                        // Temporarily remove from registry to prevent auto-restart logic from firing
                        this.serviceRegistry.delete(serviceName);
                        helper.kill();
                        // Re-add to registry after a short delay
                        setTimeout(() => this.serviceRegistry.set(serviceName, serviceInfo), 100);
                    }
                }
            }
        }
    }

    private appendServiceLog(serviceName: string, line: string): void {
        if (!serviceName) return;
        if (!line) return;

        const normalized = line.length > this.MAX_LOG_LINE_LENGTH
            ? line.slice(0, this.MAX_LOG_LINE_LENGTH)
            : line;

        const list = this.serviceLogs.get(serviceName) || [];
        list.push(normalized);
        while (list.length > this.MAX_LOG_LINES) {
            list.shift();
        }
        this.serviceLogs.set(serviceName, list);
    }

    private getServiceLogText(serviceName: string): string {
        const list = this.serviceLogs.get(serviceName) || [];
        return list.join('');
    }

    // 重启跟踪
    private restartAttempts: Map<string, number> = new Map();
    private readonly MAX_RESTART_ATTEMPTS = 5; // 最多重启5次
    private readonly RESTART_DELAY_MS = 5000; // 基础重启延迟5秒
    private readonly IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 默认5分钟闲置超时

    constructor(config: Partial<BridgeConfig> = {}) {
        // 默认配置
        this.config = {
            port: 8752,
            host: '127.0.0.1',
            mcpCommand: 'node',
            mcpArgs: ['../your-mcp-server.js'],
            ...config
        };

        // 设置超时检查
        setInterval(() => this.checkRequestTimeouts(), 5000);
        setInterval(() => this.checkSpawnTimeouts(), 5000); // 检查 spawn 请求超时
        setInterval(() => this.checkIdleServices(), 60 * 1000); // 每分钟检查一次
    }

    /**
     * 注册新的MCP服务
     */
    private registerService(name: string, info: Partial<McpServiceInfo>): boolean {
        if (!name || !info.command) {
            return false;
        }

        const serviceInfo: McpServiceInfo = {
            name,
            type: 'local',
            command: info.command,
            args: info.args || [],
            cwd: info.cwd,
            description: info.description || `MCP Service: ${name}`,
            env: info.env || {},
            created: Date.now(),
            lastUsed: undefined
        };

        this.serviceRegistry.set(name, serviceInfo);
        return true;
    }

    /**
     * 注销MCP服务
     */
    private unregisterService(name: string): boolean {
        if (!this.serviceRegistry.has(name)) {
            return false;
        }

        this.serviceRegistry.delete(name);
        return true;
    }

    /**
     * 获取已注册MCP服务列表
     */
    private getServiceList(): McpServiceInfo[] {
        return Array.from(this.serviceRegistry.values());
    }

    /**
     * 检查服务是否激活 (运行中或已连接)
     */
    private isServiceActive(serviceName: string): boolean {
        // 统一检查：客户端是否存在
        return this.serviceHelpers.has(serviceName);
    }

    /** Handles a local stdio service closure and restart. */
    private handleServiceClosure(serviceName: string, signal: NodeJS.Signals | null = null): void {
        console.log(`Service ${serviceName} connection closed or failed.`);
        this.serviceHelpers.delete(serviceName);
        this.serviceReadyMap.set(serviceName, false);

        if (this.fatalServices.has(serviceName)) {
            console.error(`[${serviceName}] Fatal startup error detected. Will not attempt restart.`);
            return;
        }

        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            return; // Service unregistered, don't reconnect
        }

        const attempts = (this.restartAttempts.get(serviceName) || 0) + 1;
        this.restartAttempts.set(serviceName, attempts);

        if (attempts > this.MAX_RESTART_ATTEMPTS) {
            console.error(`Service ${serviceName} has failed too many times. Will not reconnect again.`);

            const errorMessage = this.mcpErrors.get(serviceName) || 'Service failed to start after multiple attempts';
            this.rejectPendingSpawn(serviceName, `Service '${serviceName}' failed to start: ${errorMessage}`);

            return;
        }

        let reconnectDelay: number;
        if (signal === 'SIGABRT') {
            reconnectDelay = 0; // 立刻重启
            console.log(`[${serviceName}] Abort (SIGABRT) detected. Restarting immediately (attempt ${attempts}/${this.MAX_RESTART_ATTEMPTS})...`);
        } else {
            reconnectDelay = this.RESTART_DELAY_MS * Math.pow(2, attempts - 1);
            console.log(`Attempting to reconnect to service ${serviceName} in ${reconnectDelay / 1000}s (attempt ${attempts})...`);
        }

        setTimeout(() => {
            if (this.serviceRegistry.has(serviceName)) { // Check if service is still registered
                this.spawnServiceHelper(serviceName);
            }
        }, reconnectDelay);
    }

    /**
     * 启动本地MCP服务 (使用官方 MCPClient 的 stdio 连接)
     */
    /**
     * 展开路径中的 ~ 符号为用户主目录
     */
    private expandPath(filePath: string): string {
        if (filePath.startsWith('~/') || filePath === '~') {
            return path.join(os.homedir(), filePath.slice(1));
        }
        return filePath;
    }

    private async startLocalService(
        serviceName: string,
        command: string,
        args: string[],
        env?: Record<string, string>,
        cwd?: string
    ): Promise<void> {
        if (!command) {
            console.log(`[${serviceName}] No command specified, skipping startup`);
            return;
        }

        if (this.isServiceActive(serviceName)) {
            console.log(`[${serviceName}] Service is already running`);
            return;
        }

        console.log(`[${serviceName}] Starting local service via helper...`);
        this.spawnServiceHelper(serviceName);
    }

    /**
     * Spawns a helper process for a service
     */
    private spawnServiceHelper(serviceName: string): void {
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            console.error(`[${serviceName}] Cannot spawn helper: service not registered.`);
            return;
        }

        // Clean up any existing stale helper
        if (this.serviceHelpers.has(serviceName)) {
            this.serviceHelpers.get(serviceName)?.kill();
            this.serviceHelpers.delete(serviceName);
        }

        const helperPath = path.join(__dirname, 'spawn-helper.js');
        console.log(`[${serviceName}] Forking helper process: ${helperPath}`);

        const helper = fork(helperPath, [], {
            stdio: ['pipe', 'pipe', 'pipe', 'ipc'] // Enable IPC
        });

        this.serviceHelpers.set(serviceName, helper);
        this.serviceReadyMap.set(serviceName, false);
        this.mcpToolsMap.delete(serviceName);
        this.mcpErrors.delete(serviceName);
        this.serviceExitSignals.delete(serviceName); // Initialize the new map

        helper.stdout?.on('data', (data) => {
            const text = data.toString();
            this.appendServiceLog(serviceName, text);
            console.log(`[${serviceName}-helper]: ${text.trim()}`);
        });
        helper.stderr?.on('data', (data) => {
            const stderrStr = data.toString();
            this.appendServiceLog(serviceName, stderrStr);
            console.error(`[${serviceName}-helper-stderr]: ${stderrStr.trim()}`);
            if (this.isFatalErrorText(stderrStr)) {
                this.fatalServices.add(serviceName);
                this.mcpErrors.set(serviceName, stderrStr.trim());
                const message = `Service '${serviceName}' failed to start: ${this.mcpErrors.get(serviceName) || "Fatal error"}`;
                this.rejectPendingSpawn(serviceName, message);
            }
            if (/SIGABRT/i.test(stderrStr)) {
                console.log(`[${serviceName}] SIGABRT detected in stderr stream. Flagging for immediate restart.`);
                this.serviceExitSignals.set(serviceName, 'SIGABRT');
            }
        });

        helper.on('message', (message: any) => {
            this.handleHelperMessage(message);
        });

        helper.on('exit', (code, signal) => {
            console.log(`[${serviceName}] Helper process exited with code ${code}, signal ${signal}`);

            // 检查是否有我们从'closed'事件中存储的合成信号
            const exitSignal = this.serviceExitSignals.get(serviceName) || signal;
            this.serviceExitSignals.delete(serviceName); // 清理

            this.serviceHelpers.delete(serviceName);
            this.serviceReadyMap.set(serviceName, false);

            // A helper that exits before ever reporting ready never completes the MCP
            // handshake. Fail the pending spawn right away with the collected reason so the UI
            // can show stderr (missing env, bad entry path, crashed process) instead of waiting
            // out the spawn timeout and reporting only "not responding".
            if (this.pendingSpawnRequests.has(serviceName)) {
                const reason = this.mcpErrors.get(serviceName)
                    || this.getServiceLogText(serviceName).trim()
                    || `helper process exited with code ${code ?? 'unknown'} signal ${signal ?? 'none'}`;
                this.rejectPendingSpawn(
                    serviceName,
                    `Service '${serviceName}' failed to start: ${reason}`
                );
            }

            // Dont unregister, allow reconnection logic to handle it
            if (this.serviceRegistry.has(serviceName)) {
                this.handleServiceClosure(serviceName, exitSignal);
            }
        });

        helper.on('error', (err) => {
            console.error(`[${serviceName}] Error on helper process: ${err.message}`);
            this.serviceHelpers.delete(serviceName);
            this.serviceReadyMap.set(serviceName, false);
            if (this.serviceRegistry.has(serviceName)) {
                this.handleServiceClosure(serviceName, null);
            }
        });

        // Send initialization info
        helper.send({
            command: 'init',
            params: {
                serviceName,
                serviceInfo
            }
        });
    }

    /**
     * Handles messages from helper processes
     */
    private handleHelperMessage(message: any): void {
        const { event, id, params, result } = message;
        const serviceName = params?.serviceName;

        switch (event) {
            case 'ready':
                console.log(`MCP service ${serviceName} is ready with ${params.tools.length} tools`);
                this.mcpToolsMap.set(serviceName, params.tools);
                this.serviceReadyMap.set(serviceName, true);
                this.restartAttempts.set(serviceName, 0); // Reset restart attempts on successful connection
                this.fatalServices.delete(serviceName);

                this.resolvePendingSpawnSuccess(serviceName, params.tools.length);
                break;

            case 'tool_result':
                const pendingRequest = this.pendingRequests.get(id);
                if (pendingRequest) {
                    const response: McpResponse = {
                        id,
                        success: result.success,
                        result: result.result,
                        error: result.error
                    };
                    pendingRequest.socket.write(JSON.stringify(response) + '\n');
                    this.pendingRequests.delete(id);
                } else {
                    console.warn(`Received tool result for unknown request ID: ${id}`);
                }
                break;

            case 'closed':
                console.log(`Service ${params.serviceName} connection closed by helper.`);
                if (params.error) {
                    this.mcpErrors.set(params.serviceName, params.error);
                }
                if (params.error && this.isFatalErrorText(params.error)) {
                    this.fatalServices.add(params.serviceName);
                }
                if (params.signal) { // The synthetic signal from the helper
                    this.serviceExitSignals.set(params.serviceName, params.signal);
                }
                if (this.fatalServices.has(params.serviceName)) {
                    const errorMessage = this.mcpErrors.get(params.serviceName) || params.error || 'Fatal startup error';
                    this.rejectPendingSpawn(params.serviceName, `Service '${params.serviceName}' failed to start: ${errorMessage}`);
                } else if (params.error && this.pendingSpawnRequests.has(params.serviceName)) {
                    // A helper that fails before the MCP handshake already knows why (missing
                    // file, crash, protocol error). Answer the pending spawn immediately instead
                    // of letting the request sit until the multi-minute spawn timeout, which
                    // hides the reason behind a generic "verification failed".
                    this.rejectPendingSpawn(
                        params.serviceName,
                        `Service '${params.serviceName}' failed to start: ${params.error}`
                    );
                }
                // The 'exit' event on the helper process will trigger handleServiceClosure
                break;

            case 'error':
                console.error(`Received error from ${params.serviceName} helper: ${params.error}`);
                this.mcpErrors.set(params.serviceName, params.error);
                break;
        }
    }

    /**
     * 获取特定服务的MCP工具列表 (统一处理本地和远程服务)
     */
    private async fetchMcpTools(serviceName: string): Promise<void> {
        // This is now handled by the helper process via IPC ('ready' event)
        console.log(`[${serviceName}] Tool fetch is now managed by the helper process.`);
        if (!this.isServiceActive(serviceName)) {
            this.serviceReadyMap.set(serviceName, true); // Mark as ready to avoid deadlocks
        }
    }

    /**
     * 处理客户端MCP命令
     */
    private handleMcpCommand(command: McpCommand, socket: net.Socket): void {
        const { id, command: cmdType, params } = command;
        let response: McpResponse;

        try {
            switch (cmdType) {
                case 'logs':
                    const logsServiceName = params?.name;

                    if (!logsServiceName) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    response = {
                        id,
                        success: true,
                        result: {
                            name: logsServiceName,
                            active: this.isServiceActive(logsServiceName),
                            ready: this.serviceReadyMap.get(logsServiceName) || false,
                            lastError: this.mcpErrors.get(logsServiceName) || "",
                            logs: this.getServiceLogText(logsServiceName)
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'listtools':
                    // 查询特定服务的可用工具列表
                    const serviceToList = params?.name;

                    if (serviceToList) {
                        const hasCachedTools = this.mcpToolsMap.has(serviceToList);
                        const cachedTools = this.mcpToolsMap.get(serviceToList) || [];
                        if (hasCachedTools) {
                            // If tools are in the cache, it means the service has been spawned at least once.
                            response = {
                                id,
                                success: true,
                                result: {
                                    active: this.isServiceActive(serviceToList),
                                    tools: cachedTools
                                }
                            };
                        } else {
                            // If not in the cache, it has never been successfully spawned.
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32603,
                                    message: `Service '${serviceToList}' has not been activated, tool list is unavailable.`
                                }
                            };
                        }
                    } else {
                        // 未指定服务，列出所有曾经启动过的服务的工具
                        const allTools: Record<string, any> = {};

                        for (const [name, tools] of this.mcpToolsMap.entries()) {
                            allTools[name] = {
                                active: this.isServiceActive(name),
                                tools: tools
                            };
                        }

                        response = {
                            id,
                            success: true,
                            result: {
                                serviceTools: allTools
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'list':
                    // 列出已注册的MCP服务并附带运行状态，或查询单个服务
                    const queryServiceName = params?.name;

                    if (queryServiceName) {
                        // 查询单个服务
                        if (this.serviceRegistry.has(queryServiceName)) {
                            const serviceInfo = this.serviceRegistry.get(queryServiceName);
                            const isActive = this.isServiceActive(queryServiceName);
                            const isReady = this.serviceReadyMap.get(queryServiceName) || false;
                            const tools = this.mcpToolsMap.get(queryServiceName) || [];

                            response = {
                                id,
                                success: true,
                                result: {
                                    name: queryServiceName,
                                    type: serviceInfo?.type,
                                    description: serviceInfo?.description,
                                    active: isActive,
                                    ready: isReady,
                                    toolCount: tools.length,
                                    tools: tools,
                                    timestamp: Date.now()
                                }
                            };

                            // 更新最后使用时间
                            if (serviceInfo) {
                                serviceInfo.lastUsed = Date.now();
                            }
                        } else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32601,
                                    message: `Service '${queryServiceName}' not registered`
                                }
                            };
                        }
                    } else {
                        // 列出所有服务
                        const services = this.getServiceList().map(service => {
                            const tools = this.mcpToolsMap.get(service.name) || [];
                            return {
                                ...service,
                                active: this.isServiceActive(service.name),
                                ready: this.serviceReadyMap.get(service.name) || false,
                                toolCount: tools.length,
                                tools: tools
                            };
                        });

                        response = {
                            id,
                            success: true,
                            result: {
                                services,
                                timestamp: Date.now()
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'spawn':
                    // 启动新的MCP服务，不关闭其他服务
                    if (!params) {
                        response = {
                            id,

                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing parameters"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    const spawnServiceName = params.name;
                    let serviceCommand = params.command;
                    let serviceArgs = params.args || [];
                    let serviceEnv = params.env;
                    let serviceCwd = params.cwd;
                    const timeoutMs = typeof params.timeoutMs === 'number' ? params.timeoutMs : undefined;

                    if (this.fatalServices.has(spawnServiceName)) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32603,
                                message: this.mcpErrors.get(spawnServiceName) || `Service '${spawnServiceName}' failed with fatal error`,
                                data: {
                                    name: spawnServiceName,
                                    active: this.isServiceActive(spawnServiceName),
                                    ready: this.serviceReadyMap.get(spawnServiceName) || false,
                                    lastError: this.mcpErrors.get(spawnServiceName) || "",
                                    logs: this.getServiceLogText(spawnServiceName)
                                }
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    if ((this.serviceReadyMap.get(spawnServiceName) || false) && this.isServiceActive(spawnServiceName)) {
                        response = {
                            id,
                            success: true,
                            result: {
                                status: "ready",
                                name: spawnServiceName,
                                active: true,
                                ready: true,
                                toolCount: (this.mcpToolsMap.get(spawnServiceName) || []).length,
                                lastError: this.mcpErrors.get(spawnServiceName) || "",
                                logs: this.getServiceLogText(spawnServiceName)
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    const existingPending = this.pendingSpawnRequests.get(spawnServiceName);
                    if (existingPending) {
                        const cancelResponse: McpResponse = {
                            id: existingPending.id,
                            success: false,
                            error: {
                                code: -32603,
                                message: `Service '${spawnServiceName}' spawn request replaced`,
                                data: this.buildSpawnLogData(spawnServiceName)
                            }
                        };
                        existingPending.socket.write(JSON.stringify(cancelResponse) + '\n');
                        this.pendingSpawnRequests.delete(spawnServiceName);
                    }

                    // 优先从注册表查找服务信息
                    const serviceInfo = this.serviceRegistry.get(spawnServiceName);

                    // 如果未提供命令，但服务已注册，则使用注册表信息
                    if (serviceInfo) {
                        this.startLocalService(
                            spawnServiceName,
                            serviceInfo.command,
                            serviceInfo.args,
                            serviceInfo.env,
                            serviceInfo.cwd
                        );
                    } else if (serviceCommand) {

                        // 如果服务未注册，但提供了command，则假定为本地服务并自动注册
                        this.registerService(spawnServiceName, {
                            type: 'local',
                            command: serviceCommand,
                            args: serviceArgs,
                            cwd: serviceCwd,
                            description: `Auto-registered service ${spawnServiceName}`,
                            env: serviceEnv,
                        });
                        console.log(`Auto-registered new service: ${spawnServiceName}`);
                        this.startLocalService(spawnServiceName, serviceCommand, serviceArgs, serviceEnv, serviceCwd);
                    } else {
                        // 如果服务未注册且没有提供command，则无法启动
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${spawnServiceName}' is not registered and no command provided.`
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    this.pendingSpawnRequests.set(spawnServiceName, {
                        id,
                        socket,
                        timestamp: Date.now(),
                        timeoutMs
                    });
                    break;

                case 'shutdown':
                    // 关闭特定的MCP服务
                    const serviceToShutdown = params?.name;

                    if (!serviceToShutdown) {
                        // 未指定服务，返回错误
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                    } else if (!this.isServiceActive(serviceToShutdown)) {
                        // 服务未运行
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${serviceToShutdown}' not active`
                            }
                        };
                    } else {
                        // 获取客户端并关闭（异步操作）
                        const helper = this.serviceHelpers.get(serviceToShutdown);
                        if (helper) {
                            console.log(`[${serviceToShutdown}] Closing MCP helper and unregistering...`);
                            // Prevent auto-restarting by removing from registry BEFORE killing
                            this.serviceRegistry.delete(serviceToShutdown);
                            helper.kill(); // This will trigger the 'exit' handler which cleans up the maps
                        }

                        response = {
                            id,
                            success: true,
                            result: {
                                status: "shutdown",
                                name: serviceToShutdown
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'unspawn':
                    // 只关闭服务进程，但不从注册表注销
                    const serviceToUnspawn = params?.name;

                    if (!serviceToUnspawn) {
                        response = { id, success: false, error: { code: -32602, message: "Missing required parameter: name" } };
                    } else if (!this.isServiceActive(serviceToUnspawn)) {
                        response = { id, success: true, result: { status: "already_unspawned", name: serviceToUnspawn } };
                    } else {
                        const helper = this.serviceHelpers.get(serviceToUnspawn);
                        if (helper) {
                            console.log(`[${serviceToUnspawn}] Unspawning service helper...`);
                            // Don't unregister, just kill the process. The 'exit' handler will clean up the helper map.
                            // Temporarily remove from registry to prevent immediate auto-restart
                            const serviceInfo = this.serviceRegistry.get(serviceToUnspawn);
                            this.serviceRegistry.delete(serviceToUnspawn);

                            helper.kill();

                            // Add it back to the registry after a short delay
                            if (serviceInfo) {
                                setTimeout(() => this.serviceRegistry.set(serviceToUnspawn, serviceInfo), 100);
                            }
                        }
                        response = { id, success: true, result: { status: "unspawned", name: serviceToUnspawn } };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'register':
                    // 注册新的MCP服务
                    if (!params || !params.name || !params.type) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameters: name, type"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    if (params.type !== 'local') {
                        response = {
                            id,
                            success: false,
                            error: { code: -32602, message: "Bridge accepts only local stdio services" }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    if (!params.command) {
                        response = {
                            id,
                            success: false,
                            error: { code: -32602, message: "Missing parameter 'command' for local service" }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    const registered = this.registerService(params.name, {
                        type: 'local',
                        command: params.command,
                        args: params.args || [],
                        cwd: params.cwd,
                        description: params.description,
                        env: params.env,
                    });

                    response = {
                        id,
                        success: registered,
                        result: registered ? {
                            status: 'registered',
                            name: params.name
                        } : undefined,
                        error: !registered ? {
                            code: -32602,
                            message: "Failed to register service"
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'unregister':
                    // 注销MCP服务
                    if (!params || !params.name) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    const serviceNameToUnregister = params.name;

                    if (!this.serviceRegistry.has(serviceNameToUnregister)) {
                        response = { id, success: false, error: { code: -32602, message: `Service '${serviceNameToUnregister}' not registered` } };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    // 如果运行中，先关闭
                    if (this.isServiceActive(serviceNameToUnregister)) {
                        const helper = this.serviceHelpers.get(serviceNameToUnregister);
                        helper?.kill();
                        this.serviceHelpers.delete(serviceNameToUnregister);
                    }

                    const unregistered = this.unregisterService(serviceNameToUnregister);

                    response = {
                        id,
                        success: unregistered,
                        result: unregistered ? {
                            status: 'unregistered',
                            name: serviceNameToUnregister
                        } : undefined,
                        error: !unregistered ? {
                            code: -32602,
                            message: `Service '${serviceNameToUnregister}' does not exist`
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'toolcall':
                    // 调用工具
                    this.handleToolCall(command, socket);
                    break;

                case 'reset':
                    // 重置所有服务：关闭所有客户端，清空注册表
                    console.log('Resetting bridge: closing all services and clearing registry...');

                    // 关闭所有活跃的服务客户端
                    for (const [name, helper] of this.serviceHelpers.entries()) {
                        try {
                            console.log(`Closing service: ${name}`);
                            helper.kill();
                        } catch (error) {
                            console.error(`Error closing service ${name}: ${error instanceof Error ? error.message : String(error)}`);
                        }
                    }

                    // 清空所有映射和状态
                    this.serviceHelpers.clear();
                    this.mcpToolsMap.clear();
                    this.serviceReadyMap.clear();
                    this.mcpErrors.clear();
                    this.restartAttempts.clear();
                    this.serviceExitSignals.clear(); // Clear the new map

                    // 清空服务注册表
                    this.serviceRegistry.clear();

                    // 清空待处理的请求
                    this.pendingRequests.clear();
                    this.pendingSpawnRequests.clear();

                    console.log('Bridge reset complete: all services closed, registry cleared');

                    response = {
                        id,
                        success: true,
                        result: {
                            status: 'reset',
                            message: 'All services closed and registry cleared'
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                default:
                    // 未知命令
                    response = {
                        id,
                        success: false,
                        error: {
                            code: -32601,
                            message: `Unknown command: ${cmdType}`
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
            }
        } catch (error) {
            // 通用错误处理
            const errorResponse: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal server error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(errorResponse) + '\n');
        }
    }

    /**
     * 处理工具调用请求
     */
    private async handleToolCall(command: McpCommand, socket: net.Socket): Promise<void> {
        const { id, params } = command;
        const { method, params: methodParams, name: requestedServiceName } = params || {};

        // 确定使用哪个服务
        const serviceName = requestedServiceName || (this.serviceHelpers.keys().next().value);

        if (!serviceName) {
            console.error(`Cannot handle tool call: No service specified and no default available`);
            const response: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32602,
                    message: 'No service specified and no default available'
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }

        // 更新服务最后使用时间
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (serviceInfo) {
            serviceInfo.lastUsed = Date.now();
        }

        if (!this.isServiceActive(serviceName)) {
            const response: McpResponse = {
                id,
                success: false,
                error: { code: -32603, message: `Service '${serviceName}' is not active` }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }

        try {
            // 记录请求
            this.pendingRequests.set(id, {
                id,
                socket,
                timestamp: Date.now()
            });

            const helper = this.serviceHelpers.get(serviceName);
            if (!helper) {
                throw new Error(`Helper for service ${serviceName} not found`);
            }

            helper.send({
                command: 'toolcall',
                id: id,
                params: {
                    name: method,
                    args: methodParams || {}
                }
            });

        } catch (error) {
            console.error(`Error handling tool call for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);

            // 发送错误响应
            const response: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(response) + '\n');

            // 清理
            this.pendingRequests.delete(id);
        }
    }

    /**
     * 检查请求超时
     */
    private checkRequestTimeouts(): void {
        const now = Date.now();

        for (const [requestId, request] of this.pendingRequests.entries()) {
            if (now - request.timestamp > this.REQUEST_TIMEOUT) {
                console.log(`Request timeout: ${requestId}`);

                // 发送超时响应
                const response: McpResponse = {
                    id: requestId,
                    success: false,
                    error: {
                        code: -32603,
                        message: "Request timeout"
                    }
                };

                request.socket.write(JSON.stringify(response) + '\n');

                // 清理
                this.pendingRequests.delete(requestId);
                if (request.toolCallId) {
                    // this.toolResponseMapping.delete(request.toolCallId); // No longer needed
                    // this.toolCallServiceMap.delete(request.toolCallId); // No longer needed
                }
            }
        }
    }

    /**
     * 启动TCP服务器
     */
    public start(): void {
        try {
            console.log('Attempting to start TCP server...');
            // 创建TCP服务器 - 默认不启动任何MCP进程
            this.server = net.createServer((socket: net.Socket) => {
                console.log(`New client connection: ${socket.remoteAddress}:${socket.remotePort}`);
                this.activeConnections.add(socket);
                this.socketBuffers.set(socket, '');

                // 添加socket超时以防止客户端挂起
                // 设置为请求超时的 2 倍，确保有足够时间完成工具调用
                socket.setTimeout(120000); // 120秒超时 (REQUEST_TIMEOUT * 2)
                socket.on('timeout', () => {
                    console.log(`Socket timeout: ${socket.remoteAddress}:${socket.remotePort}`);
                    socket.end();
                    this.activeConnections.delete(socket);
                });

                // 处理来自客户端的数据
                socket.on('data', (data: Buffer) => {
                    let buffer = (this.socketBuffers.get(socket) || '') + data.toString();
                    let newlineIndex;

                    while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
                        const message = buffer.substring(0, newlineIndex).trim();
                        buffer = buffer.substring(newlineIndex + 1);

                        if (!message) {
                            continue;
                        }

                        try {
                            // 解析命令
                            const command = JSON.parse(message) as McpCommand;

                            // 确保命令有ID
                            if (!command.id) {
                                command.id = randomUUID();
                            }

                            // 处理命令
                            if (command.command) {
                                this.handleMcpCommand(command, socket);
                            } else {
                                // 非桥接命令，无默认服务转发
                                socket.write(JSON.stringify({
                                    jsonrpc: '2.0',
                                    id: this.extractId(message),
                                    error: {
                                        code: -32600,
                                        message: 'Invalid request: no service specified'
                                    }
                                }) + '\n');
                            }
                        } catch (e) {
                            console.error(`Failed to parse client message: ${e}`);

                            // 发送错误响应
                            socket.write(JSON.stringify({
                                jsonrpc: '2.0',
                                id: null,
                                error: {
                                    code: -32700,
                                    message: `Invalid JSON: ${e}`
                                }
                            }) + '\n');
                        }
                    }
                    this.socketBuffers.set(socket, buffer);
                });

                // 处理客户端断开连接
                socket.on('close', () => {
                    console.log(`Client disconnected: ${socket.remoteAddress}:${socket.remotePort}`);
                    this.activeConnections.delete(socket);
                    this.socketBuffers.delete(socket);

                    // 清理此连接的待处理请求
                    for (const [requestId, request] of this.pendingRequests.entries()) {
                        if (request.socket === socket) {
                            const toolCallId = request.toolCallId;
                            this.pendingRequests.delete(requestId);
                            if (toolCallId) {
                                // this.toolResponseMapping.delete(toolCallId); // No longer needed
                                // this.toolCallServiceMap.delete(toolCallId); // No longer needed
                            }
                        }
                    }

                    // 清理此连接的待处理 spawn 请求
                    for (const [serviceName, request] of this.pendingSpawnRequests.entries()) {
                        if (request.socket === socket) {
                            this.pendingSpawnRequests.delete(serviceName);
                            console.log(`[${serviceName}] Spawn request cancelled due to client disconnect`);
                        }
                    }
                });

                // 处理客户端错误
                socket.on('error', (err: Error) => {
                    console.error(`Client error: ${err.message}`);
                    this.activeConnections.delete(socket);
                    this.socketBuffers.delete(socket);
                });
            });

            // 启动TCP服务器
            this.server.listen(this.config.port, this.config.host, () => {
                console.log(`TCP bridge server running on ${this.config.host}:${this.config.port}`);
            });

            // 处理服务器错误
            this.server.on('error', (err: Error) => {
                console.error(`Server error: ${err.message}`);
            });

            // 处理进程信号
            process.on('SIGINT', () => this.shutdown());
            process.on('SIGTERM', () => this.shutdown());
        } catch (error) {
            console.error('FATAL ERROR during server startup:', error);
            process.exit(1);
        }
    }

    /**
     * 关闭桥接器
     */
    public shutdown(): void {
        console.log('Shutting down bridge...');

        // 关闭所有客户端连接
        for (const socket of this.activeConnections) {
            socket.end();
        }

        // 关闭服务器
        if (this.server) {
            this.server.close();
        }

        // 终止所有MCP进程
        for (const [name, helper] of this.serviceHelpers.entries()) {
            console.log(`Closing MCP helper: ${name}`);
            helper.kill();
        }
        this.serviceHelpers.clear();

        console.log('Bridge shut down');
        process.exit(0);
    }

    /**
     * 从JSON-RPC请求中提取ID
     */
    private extractId(request: string): string | null {
        try {
            const json = JSON.parse(request);
            return json.id || null;
        } catch (e) {
            return null;
        }
    }
}

// If running this script directly, create and start bridge
if (require.main === module) {
    try {
        console.log('Bridge script is main module. Initializing...');
        // Parse config from command line args
        const args = process.argv.slice(2);
        const port = parseInt(args[0]) || 8752;
        const mcpCommand = args[1] || 'node';
        const mcpArgs = args.slice(2);

        const bridge = new McpBridge({
            port,
            mcpCommand,
            mcpArgs: mcpArgs.length > 0 ? mcpArgs : undefined
        });

        bridge.start();
        console.log('Bridge initialization complete.');
    } catch (e) {
        console.error("FATAL ERROR in main execution block:", e);
        process.exit(1);
    }
}

// Export bridge class for use by other modules
export default McpBridge;
