import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createContext, Script } from 'node:vm';

const repositoryRoot = new URL('../../', import.meta.url);
const readSource = (path) => readFileSync(new URL(path, repositoryRoot), 'utf8');

// Execute the production JS literals, rather than reimplementing the decoder or SDK.
function kotlinScript(path, functionName) {
    const source = readSource(path);
    const functionStart = source.indexOf(`fun ${functionName}(`);
    assert.ok(functionStart >= 0, `missing Kotlin function ${functionName}`);
    const scriptStart = source.indexOf('"""', functionStart) + 3;
    const scriptEnd = source.indexOf('""".trimIndent()', scriptStart);
    assert.ok(scriptStart > functionStart && scriptEnd > scriptStart, 'missing JS literal');
    return source.slice(scriptStart, scriptEnd);
}

const runtimeSource = kotlinScript(
    'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsInitRuntimeScriptBuilder.kt',
    'buildRuntimeToolCallScript'
);
const toolsSource = kotlinScript(
    'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt',
    'getJsToolsDefinition'
);
const packageSources = [
    'examples/super_admin.js',
    'app/src/main/assets/packages/super_admin.js'
];
const plain = (value) => JSON.parse(JSON.stringify(value));
const terminalData = (sessionId = 'session-1', output = 'partial output') => ({
    command: 'sleep 10', output, exitCode: -1, sessionId, timedOut: true, sessionAlive: true
});
const timedOut = (data, dataType = 'terminal_command_result') => ({
    success: false, message: 'Command timed out after 3000ms', data, dataType
});
const final = (result) => ({ result, intermediate: [] });

function harness(handlers, packagePath = packageSources[0]) {
    let toolSessionId = 'chat-1';
    const requests = [];
    const savedFiles = new Map();
    const sandbox = {
        exports: {},
        console: { log() {}, error() {} },
        OPERIT_CLEAN_ON_EXIT_DIR: '/operit/clean',
        getToolSessionId: () => toolSessionId,
        getChatId: () => { throw new Error('terminal must use its tool-session identity'); },
        __operitToolPkgApi: {
            namespace: (_name, value) => value,
            method: () => ({ since: (_version, implementation) => implementation })
        }
    };
    sandbox.window = sandbox;
    sandbox.__operitExpose = (name, value) => { sandbox[name] = value; };
    const toolHandlers = {
        create_terminal_session: (params) => final({
            success: true,
            data: { sessionId: `session:${params.session_name}`, sessionName: params.session_name, isNewSession: false }
        }),
        make_directory: () => final({ success: true, data: 'OK' }),
        write_file: (params) => {
            savedFiles.set(params.path, params.content);
            return final({ success: true, data: 'OK' });
        },
        ...handlers
    };
    function dispatch(callbackId, intermediateCallbackId, name, paramsJson) {
        const params = JSON.parse(paramsJson);
        requests.push({ name, params, streaming: Boolean(intermediateCallbackId) });
        const handler = toolHandlers[name];
        assert.equal(typeof handler, 'function', `unexpected native tool ${name}`);
        const response = handler(params);
        for (const event of response.intermediate) {
            assert.ok(intermediateCallbackId, 'intermediate event needs a streaming caller');
            sandbox[intermediateCallbackId](event, !event.success);
        }
        sandbox[callbackId](response.result, !response.result.success);
    }
    sandbox.NativeInterface = {
        callToolAsync: (callbackId, _type, name, paramsJson) => dispatch(callbackId, '', name, paramsJson),
        callToolAsyncStreaming: (callbackId, intermediateCallbackId, _type, name, paramsJson) =>
            dispatch(callbackId, intermediateCallbackId, name, paramsJson)
    };
    const context = createContext(sandbox);
    for (const [source, filename] of [
        [runtimeSource, 'runtime-tool-call.js'],
        [toolsSource, 'tools.js'],
        [readSource(packagePath), packagePath]
    ]) {
        new Script(source, { filename }).runInContext(context);
    }
    return {
        sdk: sandbox.Tools.System.terminal,
        tools: sandbox.exports,
        requests,
        savedFiles,
        setToolSessionId(value) { toolSessionId = value; }
    };
}

test('all three published terminal SDK paths return the original typed timeout data', async () => {
    const data = terminalData();
    const hiddenData = { command: 'sleep 10', output: 'hidden partial', exitCode: -1, executorKey: 'worker', timedOut: true };
    const h = harness({
        execute_in_terminal_session: () => final(timedOut(data)),
        execute_in_terminal_session_streaming: () => final(timedOut(data)),
        execute_hidden_terminal_command: () => final(timedOut(hiddenData, 'hidden_terminal_command_result'))
    });
    assert.equal(await h.sdk.exec('session-1', 'sleep 10', 3000), data);
    assert.equal(await h.sdk.execStreaming('session-1', 'sleep 10', { timeoutMs: 3000 }), data);
    assert.equal(await h.sdk.hiddenExec('sleep 10', { executorKey: 'worker', timeoutMs: 3000 }), hiddenData);
    assert.equal(h.requests.length, 3, 'a timeout must not trigger another tool execution');
});

test('streaming delivers chunks before returning the typed timeout result', async () => {
    const data = terminalData();
    const event = { type: 'chunk', sessionId: 'session-1', command: 'sleep 10', chunk: 'partial output' };
    const chunks = [];
    const h = harness({
        execute_in_terminal_session_streaming: () => ({ result: timedOut(data), intermediate: [{ success: true, data: event }] })
    });
    const result = await h.sdk.execStreaming('session-1', 'sleep 10', {
        timeoutMs: 3000,
        onIntermediateResult: (value) => chunks.push(value)
    });
    assert.equal(result, data);
    assert.deepEqual(chunks, [event]);
    assert.equal(h.requests[0].streaming, true);
});

test('untyped failures and non-timeout terminal failures still reject with their details', async () => {
    for (const envelope of [
        timedOut({ ...terminalData(), timedOut: false }),
        timedOut(terminalData(), 'other_result'),
        { success: false, message: 'missing session', data: 'session-1' }
    ]) {
        const h = harness({ execute_in_terminal_session: () => final(envelope) });
        await assert.rejects(h.sdk.exec('session-1', 'sleep 10', 3000), (error) => {
            assert.equal(error.message, envelope.message);
            assert.equal(error.data, envelope.data);
            return true;
        });
    }
});

for (const packagePath of packageSources) {
    test(`${packagePath}: a failed command retains exit code and actual shell state without replay`, async () => {
        for (const sessionAlive of [true, false]) {
            const h = harness({
                execute_in_terminal_session: (params) => final({
                    success: false, dataType: 'terminal_command_result',
                    message: 'Command exited with code 3',
                    data: { command: params.command, output: 'before', exitCode: 3,
                        sessionId: params.session_id, timedOut: false, sessionAlive }
                })
            }, packagePath);
            const result = await h.tools.terminal({ command: 'exit 3', timeoutMs: '3000' });
            assert.equal(result.success, false);
            assert.equal(result.data.exitCode, 3);
            assert.equal(result.data.output, 'before');
            assert.equal(result.data.context_preserved, sessionAlive);
            assert.equal(h.requests.filter((request) => request.name === 'execute_in_terminal_session').length, 1);
        }
    });

    test(`${packagePath}: a closed shell stays false even on a successful exit`, async () => {
        const h = harness({
            execute_in_terminal_session: (params) => final({ success: true,
                data: { command: params.command, output: '', exitCode: 0, sessionId: params.session_id,
                    timedOut: false, sessionAlive: false }
            })
        }, packagePath);
        const result = await h.tools.terminal({ command: 'exit 0', timeoutMs: '3000' });
        assert.equal(result.exitCode, 0);
        assert.equal(result.context_preserved, false);
    });

    test(`${packagePath}: a wait cannot hide a nonzero result behind a marker`, async () => {
        const h = harness({
            execute_in_terminal_session: (params) => final({ success: false, dataType: 'terminal_command_result',
                message: 'Command exited with code 3',
                data: { command: params.command, output: params.command.match(/__OPERIT_TERMINAL_WAIT_DONE_\d+_\d+__/u)[0],
                    exitCode: 3, sessionId: params.session_id, timedOut: false, sessionAlive: false }
            })
        }, packagePath);
        const result = await h.tools.terminal_wait({ sessionId: 'ended', timeoutMs: '3000' });
        assert.equal(result.success, false);
        assert.equal(result.data.waitCompleted, false);
        assert.equal(result.data.markerSeen, true);
        assert.equal(result.data.context_preserved, false);
    });

    test(`${packagePath}: model tool reports timeout failure with session and partial output`, async () => {
        const h = harness({
            execute_in_terminal_session: (params) => final(timedOut(terminalData(params.session_id)))
        }, packagePath);
        const result = plain(await h.tools.terminal({ command: 'sleep 10', timeoutMs: '3000' }));
        assert.equal(result.success, false);
        assert.equal(result.message, 'Command timed out after 3000ms');
        assert.deepEqual(result.data, {
            command: 'sleep 10', output: 'partial output', exitCode: -1,
            sessionId: 'session:super_admin_default_session_chat-1', timedOut: true,
            timeoutMsUsed: 3000, sessionAlive: true, context_preserved: true
        });
    });

    test(`${packagePath}: a large timed-out output is saved before returning its failure`, async () => {
        const output = 'large output\n'.repeat(1200);
        const h = harness({
            execute_in_terminal_session: (params) => final(timedOut(terminalData(params.session_id, output)))
        }, packagePath);
        const result = await h.tools.terminal({ command: 'sleep 10', timeoutMs: '3000' });
        assert.equal(result.success, false);
        assert.equal(result.data.output, '(saved_to_file)');
        assert.equal(result.data.timedOut, true);
        assert.equal(result.data.timeoutMsUsed, 3000);
        assert.equal(result.data.output_chars, output.length);
        assert.equal(h.savedFiles.get(result.data.output_saved_to), output);
        assert.deepEqual(h.requests.map((request) => request.name), [
            'create_terminal_session', 'execute_in_terminal_session', 'make_directory', 'write_file'
        ]);
    });

    test(`${packagePath}: successful terminal calls retain their published result shape`, async () => {
        const h = harness({
            execute_in_terminal_session: (params) => final({
                success: true, data: { command: params.command, output: 'ok', exitCode: 0, sessionId: params.session_id, timedOut: false, sessionAlive: true }
            })
        }, packagePath);
        assert.deepEqual(plain(await h.tools.terminal({ command: 'echo ok', timeoutMs: '3000' })), {
            command: 'echo ok', output: 'ok', exitCode: 0,
            sessionId: 'session:super_admin_default_session_chat-1', timedOut: false,
            timeoutMsUsed: 3000, sessionAlive: true, context_preserved: true
        });
    });

    test(`${packagePath}: a wait timeout and missing marker are distinct failures with output`, async () => {
        for (const didTimeout of [true, false]) {
            const h = harness({
                execute_in_terminal_session: (params) => final(didTimeout
                    ? timedOut(terminalData(params.session_id, 'still working'))
                    : { success: true, data: { command: params.command, output: 'marker absent', exitCode: 0, sessionId: params.session_id, timedOut: false, sessionAlive: true } })
            }, packagePath);
            const result = await h.tools.terminal_wait({ sessionId: 'background-session', timeoutMs: '3000' });
            assert.equal(result.success, false);
            assert.equal(result.message, didTimeout
                ? 'Terminal wait timed out after 3000ms'
                : 'Terminal wait completed without observing the completion marker');
            assert.equal(result.data.sessionId, 'background-session');
            assert.equal(result.data.timedOut, didTimeout);
            assert.equal(result.data.waitCompleted, false);
            assert.equal(result.data.markerSeen, false);
            assert.equal(result.data.output, didTimeout ? 'still working' : 'marker absent');
            assert.equal(h.requests.length, 1, 'an explicit session must not create a new terminal');
        }
    });

    test(`${packagePath}: observing the marker preserves the successful wait result shape`, async () => {
        const h = harness({
            execute_in_terminal_session: (params) => {
                const marker = params.command.match(/__OPERIT_TERMINAL_WAIT_DONE_\d+_\d+__/u);
                assert.ok(marker);
                return final({ success: true, data: { command: params.command, output: marker[0], exitCode: 0, sessionId: params.session_id, timedOut: false, sessionAlive: true } });
            }
        }, packagePath);
        const result = plain(await h.tools.terminal_wait({ sessionId: 'background-session', timeoutMs: '3000' }));
        assert.deepEqual(Object.keys(result).sort(), [
            'context_preserved', 'elapsedMs', 'exitCode', 'markerSeen', 'sessionAlive', 'sessionId', 'timedOut', 'timeoutMsUsed', 'waitCompleted'
        ]);
        assert.equal(result.waitCompleted, true);
        assert.equal(result.markerSeen, true);
        assert.equal(result.timedOut, false);
    });

    test(`${packagePath}: default terminals follow tool-session identity and reuse it within a run`, async () => {
        const h = harness({
            execute_in_terminal_session: (params) => final({
                success: true, data: { command: params.command, output: 'ok', exitCode: 0, sessionId: params.session_id, timedOut: false, sessionAlive: true }
            })
        }, packagePath);
        for (const identity of ['chat-1', 'run-a', 'run-a', 'run-b', undefined]) {
            h.setToolSessionId(identity);
            await h.tools.terminal({ command: 'pwd', timeoutMs: '3000' });
        }
        assert.deepEqual(h.requests.filter((request) => request.name === 'create_terminal_session')
            .map((request) => request.params.session_name), [
                'super_admin_default_session_chat-1', 'super_admin_default_session_run-a',
                'super_admin_default_session_run-a', 'super_admin_default_session_run-b',
                'super_admin_default_session'
            ]);
    });
}
