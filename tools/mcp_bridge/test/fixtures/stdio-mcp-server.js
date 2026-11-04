#!/usr/bin/env node
/**
 * Minimal stdio MCP server used by the bridge scenario tests.
 *
 * Modes mirror the startup failures users hit from the MCP management page:
 * ok, missing-env, crash, handshake-fail, handshake-timeout.
 */
const readline = require('readline');

const mode = process.argv[2] || 'ok';
const requiredEnv = process.env.TEST_MCP_TOKEN;

if (mode === 'missing-env' && !requiredEnv) {
    console.error('TEST_MCP_TOKEN is required.');
    process.exit(1);
}

if (mode === 'crash') {
    console.error('simulated crash before handshake');
    process.exit(2);
}

const rl = readline.createInterface({ input: process.stdin });

function reply(id, result) {
    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, result }) + '\n');
}

function replyError(id, code, message) {
    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, error: { code, message } }) + '\n');
}

rl.on('line', (line) => {
    if (!line.trim()) return;

    let message;
    try {
        message = JSON.parse(line);
    } catch (error) {
        console.error('bad json: ' + error.message);
        return;
    }

    if (message.method === 'initialize') {
        if (mode === 'handshake-fail') {
            replyError(message.id, -32603, 'simulated initialize failure');
            return;
        }
        if (mode === 'handshake-timeout') {
            return;
        }
        reply(message.id, {
            protocolVersion: '2024-11-05',
            capabilities: { tools: { listChanged: true } },
            serverInfo: { name: 'bridge-scenario-server', version: '0.1.0' }
        });
        return;
    }

    if (message.method === 'tools/list') {
        reply(message.id, {
            tools: [
                {
                    name: 'env_status',
                    description: 'Reports whether the configured test env key reached the process.',
                    inputSchema: { type: 'object', properties: {} }
                }
            ]
        });
        return;
    }

    if (message.method === 'tools/call') {
        reply(message.id, {
            content: [
                {
                    type: 'text',
                    text: requiredEnv ? 'TEST_MCP_TOKEN present' : 'TEST_MCP_TOKEN missing'
                }
            ]
        });
        return;
    }

    if (message.id !== undefined) {
        replyError(message.id, -32601, 'unknown method ' + message.method);
    }
});
