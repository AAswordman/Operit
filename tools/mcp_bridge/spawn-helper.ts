import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import * as path from 'path';
import * as os from 'os';
import { McpServiceInfo } from './index';

let client: Client | null = null;
let serviceName: string;
let serviceInfo: McpServiceInfo;

/**
 * 展开路径中的 ~ 符号为用户主目录
 */
function expandPath(filePath: string): string {
    if (!filePath) return filePath;
    if (filePath.startsWith('~/') || filePath === '~') {
        return path.join(os.homedir(), filePath.slice(1));
    }
    return filePath;
}

const handlers = {
    async init(params: { serviceName: string, serviceInfo: McpServiceInfo }) {
        serviceName = params.serviceName;
        serviceInfo = params.serviceInfo;

        const mcpClient = new Client({
            name: `helper-for-${serviceName}`,
            version: '1.0.0',
        });
        client = mcpClient;

        // Resolve launch parameters before the try block: the catch path reports them back to
        // the bridge, and they must stay in scope when the SDK throws during connect.
        let workingDir = serviceInfo.cwd || path.join(os.homedir(), 'mcp_plugins', serviceName);
        workingDir = expandPath(workingDir);

        let actualCommand = expandPath(serviceInfo.command);
        let actualArgs = serviceInfo.args;
        if (actualCommand === 'npx') {
            actualCommand = 'pnpm';
            const filteredArgs = actualArgs.filter((arg: string) => arg !== '-y' && arg !== '--yes');
            actualArgs = ['dlx', ...filteredArgs];
        }

        try {
            const mergedEnv = {
                ...process.env,
                ...serviceInfo.env,
                ...(serviceInfo.env?.npm_config_cache ? {} : { npm_config_cache: path.join(workingDir, '.npm-cache') }),
                ...(serviceInfo.env?.npm_config_prefer_offline ? {} : { npm_config_prefer_offline: 'true' }),
                ...(serviceInfo.env?.UV_LINK_MODE ? {} : { UV_LINK_MODE: 'copy' }),
                ...(os.platform() === 'linux' ? { NODE_OPTIONS: '--openssl-legacy-provider' } : {}),
            };

            const transport = new StdioClientTransport({
                command: actualCommand,
                args: actualArgs,
                cwd: workingDir,
                env: mergedEnv,
            });
            await mcpClient.connect(transport);

            console.log(`[${serviceName}] Helper connected successfully.`);

            const tools = await listAllTools(mcpClient);
            process.send!({
                event: 'ready',
                params: { serviceName, tools }
            });

        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            // The SDK reports transport failures as a bare "Connection closed". Add the launch
            // context so a failed spawn (missing executable, bad cwd, wrong entry path) is
            // diagnosable from the management page without reading device logs. Environment
            // values are deliberately omitted; only the variable names are listed.
            const envKeys = Object.keys(serviceInfo.env || {}).sort();
            const launchContext = [
                `command=${actualCommand}`,
                `args=${JSON.stringify(actualArgs || [])}`,
                `cwd=${workingDir}`,
                `envKeys=${envKeys.length > 0 ? envKeys.join(',') : '(none)'}`
            ].join(', ');
            console.error(`[${serviceName}] Helper connection failed: ${errorMessage} (${launchContext})`);
            process.send!({
                event: 'closed',
                params: { serviceName, error: `${errorMessage} (${launchContext})` }
            });
            // Exit after sending error to allow parent to restart
            process.exit(1);
        }
    },

    async toolcall(params: { id: string, name: string, args: any }) {
        if (!client) {
            process.send!({
                event: 'tool_result',
                id: params.id,
                result: { success: false, error: { message: 'Client not initialized' } }
            });
            return;
        }
        try {
            const result = await client.callTool({
                name: params.name,
                arguments: params.args || {},
            });

            const toolCallResult = { content: result.content };
            const toolErrorMessage = ("content" in result && Array.isArray(result.content)
                ? result.content
                    .filter((content): content is { type: 'text'; text: string } =>
                        typeof content === 'object' &&
                        content !== null &&
                        'type' in content &&
                        content.type === 'text' &&
                        'text' in content &&
                        typeof content.text === 'string'
                    )
                    .map((content) => content.text)
                : [])
                .join('\n');
            const toolCallError = result.isError ? { code: -32000, message: toolErrorMessage } : undefined;

            process.send!({
                event: 'tool_result',
                id: params.id,
                result: {
                    success: !toolCallError,
                    result: toolCallResult,
                    error: toolCallError
                }
            });

        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            process.send!({
                event: 'tool_result',
                id: params.id,
                result: {
                    success: false,
                    error: { message: `Tool call failed: ${errorMessage}` }
                }
            });
        }
    },

    async shutdown() {
        if (client) {
            await client.close();
        }
        process.exit(0);
    }
};

async function listAllTools(mcpClient: Client) {
    let response = await mcpClient.listTools();
    const tools = [...response.tools];

    while (response.nextCursor) {
        response = await mcpClient.listTools({ cursor: response.nextCursor });
        tools.push(...response.tools);
    }

    return tools;
}

process.on('message', async (message: { command: string, id?: string, params: any }) => {
    const handler = handlers[message.command as keyof typeof handlers];
    if (handler) {
        if (message.command === 'toolcall') {
            await handler({ id: message.id!, ...message.params });
        } else {
            await (handler as (p: any) => Promise<void>)(message.params);
        }
    } else {
        console.error(`[${serviceName}] Helper received unknown command: ${message.command}`);
    }
});

// Handle process exit
process.on('disconnect', () => {
    console.log(`[${serviceName}] Parent process disconnected. Shutting down helper.`);
    handlers.shutdown();
});

// Prevent crashing the helper process on unhandled exceptions
process.on('uncaughtException', (err) => {
    console.error(`[${serviceName}] Uncaught exception in helper: ${err.stack}`);
    process.send!({
        event: 'closed',
        params: { serviceName, error: `Uncaught exception: ${err.message}` }
    });
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error(`[${serviceName}] Unhandled rejection in helper:`, reason);
    process.send!({
        event: 'closed',
        params: { serviceName, error: `Unhandled rejection: ${reason}` }
    });
    process.exit(1);
});

console.log('Spawn helper process started.');
