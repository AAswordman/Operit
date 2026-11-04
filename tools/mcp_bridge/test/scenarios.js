#!/usr/bin/env node
/**
 * Repeatable bridge scenarios for local stdio MCP startup.
 *
 * Each scenario starts a fresh bridge on a free port, sends the same newline-delimited JSON
 * commands the Android client sends, and asserts the failure detail the management page shows.
 * Run `pnpm --dir tools/mcp_bridge run build` first; the script uses `dist/`.
 */
const { spawn } = require('child_process');
const net = require('net');
const path = require('path');
const { randomUUID } = require('crypto');

const bridgeDir = path.resolve(__dirname, '..');
const bridgeEntry = path.join(bridgeDir, 'dist', 'index.js');
const fixturePath = path.join(__dirname, 'fixtures', 'stdio-mcp-server.js');

function findFreePort() {
    return new Promise((resolve, reject) => {
        const server = net.createServer();
        server.once('error', reject);
        server.listen(0, '127.0.0.1', () => {
            const { port } = server.address();
            server.close(() => resolve(port));
        });
    });
}

function send(port, command, params, timeoutMs = 20000) {
    return new Promise((resolve) => {
        const started = Date.now();
        const socket = net.createConnection({ host: '127.0.0.1', port }, () => {
            socket.write(JSON.stringify({ command, id: randomUUID(), params }) + '\n');
        });

        let buffer = '';
        let settled = false;
        const finish = (value) => {
            if (settled) return;
            settled = true;
            socket.destroy();
            resolve({ ...value, elapsedMs: Date.now() - started });
        };

        socket.on('data', (data) => {
            buffer += data.toString();
            const index = buffer.indexOf('\n');
            if (index < 0) return;
            try {
                finish(JSON.parse(buffer.slice(0, index)));
            } catch (error) {
                finish({ parseError: error.message, raw: buffer.slice(0, index) });
            }
        });
        socket.on('error', (error) => finish({ socketError: error.message }));
        socket.setTimeout(timeoutMs, () => finish({ timeout: true }));
    });
}

async function startBridge(port) {
    const child = spawn(process.execPath, [bridgeEntry, String(port)], {
        cwd: bridgeDir,
        stdio: 'ignore'
    });

    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
        if (child.exitCode !== null) {
            throw new Error(`bridge exited early with code ${child.exitCode}`);
        }
        const probe = await send(port, 'list', undefined, 500);
        if (probe.success === true) return child;
        await new Promise((resolve) => setTimeout(resolve, 150));
    }
    child.kill();
    throw new Error('bridge did not start within 15s');
}

async function stopBridge(child) {
    if (!child || child.exitCode !== null) return;
    child.kill('SIGTERM');
    const deadline = Date.now() + 5000;
    while (Date.now() < deadline) {
        if (child.exitCode !== null) return;
        await new Promise((resolve) => setTimeout(resolve, 100));
    }
    child.kill('SIGKILL');
}

function assert(condition, message, details) {
    if (condition) return;
    const suffix = details === undefined ? '' : `\n  actual: ${JSON.stringify(details)}`;
    throw new Error(message + suffix);
}

function registration(name, mode, extra = {}) {
    return {
        type: 'local',
        name,
        command: 'node',
        args: [fixturePath, mode],
        cwd: path.dirname(fixturePath),
        env: extra.env || {},
        ...extra
    };
}

const scenarios = [
    {
        name: 'ok',
        async run(port) {
            const service = 'scenario-ok';
            const register = await send(port, 'register', registration(service, 'ok', {
                env: { TEST_MCP_TOKEN: 'super-secret-token-value' }
            }));
            assert(register.success === true, 'register should succeed', register);

            const spawnResult = await send(port, 'spawn', { name: service, timeoutMs: 15000 }, 30000);
            assert(spawnResult.success === true, 'spawn should succeed', spawnResult);
            assert(spawnResult.result?.ready === true, 'service should be ready', spawnResult);
            assert(spawnResult.result?.toolCount === 1, 'one tool should be discovered', spawnResult);

            const list = await send(port, 'list', { name: service });
            assert(list.success === true && list.result?.active === true, 'service should be active', list);

            const tools = await send(port, 'listtools', { name: service });
            const toolNames = (tools.result?.tools || []).map((tool) => tool.name);
            assert(toolNames.includes('env_status'), 'tool list should include env_status', tools);
        }
    },
    {
        name: 'missing-env',
        async run(port) {
            const service = 'scenario-missing-env';
            await send(port, 'register', registration(service, 'missing-env'));

            const spawnResult = await send(port, 'spawn', { name: service, timeoutMs: 15000 }, 30000);
            assert(spawnResult.success === false, 'spawn should fail without env', spawnResult);
            const text = JSON.stringify(spawnResult);
            assert(text.includes('TEST_MCP_TOKEN is required.'), 'failure should name the missing env var', spawnResult);
            assert((spawnResult.elapsedMs ?? 0) < 15000, 'failure should be reported before the spawn timeout', spawnResult);
        }
    },
    {
        name: 'crash',
        async run(port) {
            const service = 'scenario-crash';
            await send(port, 'register', registration(service, 'crash'));

            const spawnResult = await send(port, 'spawn', { name: service, timeoutMs: 15000 }, 30000);
            assert(spawnResult.success === false, 'spawn should fail when the process exits', spawnResult);
            const text = JSON.stringify(spawnResult);
            assert(text.includes('simulated crash before handshake'), 'failure should include process stderr', spawnResult);
            assert((spawnResult.elapsedMs ?? 0) < 15000, 'failure should be reported before the spawn timeout', spawnResult);
        }
    },
    {
        name: 'handshake-fail',
        async run(port) {
            const service = 'scenario-handshake-fail';
            await send(port, 'register', registration(service, 'handshake-fail'));

            const spawnResult = await send(port, 'spawn', { name: service, timeoutMs: 15000 }, 30000);
            assert(spawnResult.success === false, 'spawn should fail when initialize fails', spawnResult);
            const text = JSON.stringify(spawnResult);
            assert(text.includes('simulated initialize failure'), 'failure should include the handshake error', spawnResult);
        }
    },
    {
        name: 'handshake-timeout',
        async run(port) {
            const service = 'scenario-handshake-timeout';
            await send(port, 'register', registration(service, 'handshake-timeout'));

            const spawnResult = await send(port, 'spawn', { name: service, timeoutMs: 5000 }, 20000);
            assert(spawnResult.success === false, 'spawn should fail when initialize never answers', spawnResult);
            const text = JSON.stringify(spawnResult);
            assert(text.includes('failed to start within 5s'), 'failure should report the configured timeout', spawnResult);
            assert((spawnResult.elapsedMs ?? 0) < 15000, 'timeout should follow timeoutMs, not the default', spawnResult);
        }
    },
    {
        name: 'no-config',
        async run(port) {
            const spawnResult = await send(port, 'spawn', { name: 'scenario-never-registered', timeoutMs: 5000 }, 15000);
            assert(spawnResult.success === false, 'spawn should fail without a registered service', spawnResult);
            const text = JSON.stringify(spawnResult);
            assert(text.includes('is not registered and no command provided'), 'failure should explain missing configuration', spawnResult);
        }
    }
];

async function main() {
    const only = process.argv[2];
    const selected = only ? scenarios.filter((scenario) => scenario.name === only) : scenarios;
    assert(selected.length > 0, `unknown scenario: ${only}`);

    let failures = 0;
    for (const scenario of selected) {
        const port = await findFreePort();
        let child = null;
        try {
            child = await startBridge(port);
            await scenario.run(port);
            console.log(`PASS ${scenario.name}`);
        } catch (error) {
            failures += 1;
            console.error(`FAIL ${scenario.name}: ${error.message}`);
        } finally {
            await stopBridge(child);
        }
    }

    if (failures > 0) {
        console.error(`${failures}/${selected.length} scenario(s) failed`);
        process.exit(1);
    }
    console.log(`${selected.length}/${selected.length} scenario(s) passed`);
}

main().catch((error) => {
    console.error(error.stack || error.message);
    process.exit(1);
});
