import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createContext, Script } from 'node:vm';

const repositoryRoot = new URL('../../', import.meta.url);
const jsDirectory = 'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/';

function kotlinScript(filename, functionName) {
    const source = readFileSync(new URL(jsDirectory + filename, repositoryRoot), 'utf8');
    const functionStart = source.indexOf(`fun ${functionName}(`);
    assert.ok(functionStart >= 0, `missing Kotlin function ${functionName}`);
    const scriptStart = source.indexOf('"""', functionStart) + 3;
    const scriptEnd = source.indexOf('""".trimIndent()', scriptStart);
    assert.ok(scriptStart > functionStart && scriptEnd > scriptStart, 'missing JS literal');
    return source.slice(scriptStart, scriptEnd).replaceAll("${'$'}", '$');
}

const bridgeSource = kotlinScript('JsJavaBridge.kt', 'buildJavaClassBridgeDefinition');
const registrySource = kotlinScript('JsInitRuntimeScriptBuilder.kt', 'buildRuntimeCallRegistryScript');
const preludeSource = kotlinScript('JsExecutionScriptBuilder.kt', 'buildExecutionPreludeSource');
const runtimeSource = kotlinScript('JsExecutionScriptBuilder.kt', 'buildExecutionRuntimeBridgeScript')
    .replaceAll('$TOOLPKG_EXECUTION_ENTRY_FUNCTION', '__operitExecuteScriptFunction')
    .replace('$preludeSource', JSON.stringify(preludeSource));
const plain = (value) => JSON.parse(JSON.stringify(value));

function harness() {
    const calls = [];
    const outcomes = new Map();
    const intermediate = [];
    const states = new Map();
    const sandbox = {
        console,
        __operitGetCallState: (id) => states.get(id),
        NativeInterface: {
            javaClassExists: () => true,
            javaCallStaticSuspend: (className, method, argsJson, callbackId) =>
                calls.push({ entry: 'static', className, method, argsJson, callbackId }),
            javaCallInstanceSuspend: (handle, method, argsJson, callbackId) =>
                calls.push({ entry: 'instance', handle, method, argsJson, callbackId }),
            javaCallStaticSuspendForCall: (callId, className, method, argsJson, callbackId) =>
                calls.push({ entry: 'static-for-call', callId, className, method, argsJson, callbackId }),
            javaCallInstanceSuspendForCall: (callId, handle, method, argsJson, callbackId) =>
                calls.push({ entry: 'instance-for-call', callId, handle, method, argsJson, callbackId }),
            setCallResult: (callId, value) => outcomes.set(callId, JSON.parse(value)),
            setCallError: (callId, value) => outcomes.set(callId, JSON.parse(value)),
            sendCallIntermediateResult: (callId, value) => intermediate.push({ callId, value: JSON.parse(value) })
        }
    };
    sandbox.window = sandbox;
    sandbox.__operitExpose = (name, value) => { sandbox[name] = value; };
    const context = createContext(sandbox);
    new Script(bridgeSource, { filename: 'java-bridge.js' }).runInContext(context);
    return {
        sandbox, context, calls, outcomes, intermediate, states,
        bound(callId) {
            states.set(callId, { completed: false });
            return sandbox.__operitJavaBridgeForCall(callId);
        },
        finish(call, value) {
            return sandbox.__operitJavaBridgeInvokeJsObject(call.callbackId, '', [null, value]);
        },
        installRuntime() {
            new Script(registrySource, { filename: 'call-registry.js' }).runInContext(context);
            new Script(runtimeSource, { filename: 'execution-runtime.js' }).runInContext(context);
        }
    };
}

test('execution-bound static and returned instance calls retain the same exact owner', async () => {
    const h = harness();
    const java = h.bound('execution-a');
    const promise = java.type('Probe').callSuspend('open');
    const opening = h.calls[0];
    assert.equal(opening.entry, 'static-for-call');
    assert.equal(opening.callId, 'execution-a');
    h.finish(opening, { __javaHandle: 'stream-a', __javaClass: 'ProbeStream' });
    const stream = await promise;

    h.sandbox.__operitCurrentCallId = 'unrelated-execution';
    const collection = stream.callSuspend('collect', 'argument');
    const collecting = h.calls[1];
    assert.equal(collecting.entry, 'instance-for-call');
    assert.equal(collecting.callId, 'execution-a');
    assert.equal(collecting.handle, 'stream-a');
    assert.deepEqual(JSON.parse(collecting.argsJson), ['argument']);
    h.finish(collecting, 'done');
    assert.equal(await collection, 'done');
});

test('out of order completion cannot move returned proxies between execution owners', async () => {
    const h = harness();
    const first = h.bound('execution-a').callSuspend('Probe', 'open');
    const second = h.bound('execution-b').callSuspend('Probe', 'open');
    assert.notEqual(h.calls[0].callbackId, h.calls[1].callbackId);
    h.finish(h.calls[1], { __javaHandle: 'stream-b', __javaClass: 'ProbeStream' });
    const secondStream = await second;
    h.finish(h.calls[0], { __javaHandle: 'stream-a', __javaClass: 'ProbeStream' });
    const firstStream = await first;
    const firstEnd = firstStream.callSuspend('close');
    const secondEnd = secondStream.callSuspend('close');
    assert.equal(h.calls[2].callId, 'execution-a');
    assert.equal(h.calls[3].callId, 'execution-b');
    h.finish(h.calls[2], 'a closed');
    h.finish(h.calls[3], 'b closed');
    assert.deepEqual(await Promise.all([firstEnd, secondEnd]), ['a closed', 'b closed']);
});

test('global Java retains its existing unscoped native call signature', async () => {
    const h = harness();
    const globalJava = h.sandbox.Java;
    h.bound('execution-a');
    assert.equal(h.sandbox.Java, globalJava, 'creating an owned facade must not replace global Java');
    const promise = globalJava.callSuspend('Probe', 'outsideExecution', 7);
    assert.equal(h.calls[0].entry, 'static');
    assert.equal(h.calls[0].className, 'Probe');
    assert.equal(h.calls[0].callId, undefined);
    assert.deepEqual(JSON.parse(h.calls[0].argsJson), [7]);
    h.finish(h.calls[0], 'ok');
    assert.equal(await promise, 'ok');
});

test('ended execution callbacks cannot invoke their registered JavaScript implementation', async () => {
    const h = harness();
    let invocations = 0;
    const java = h.bound('execution-a');
    const callback = java.implement('ProbeCallback', {
        emit() { invocations += 1; }
    });
    const registered = java.callSuspend('Probe', 'register', callback);
    h.finish(h.calls[0], 'registered');
    await registered;
    h.states.get('execution-a').completed = true;
    assert.throws(
        () => h.sandbox.__operitJavaBridgeInvokeJsObject(callback.__javaJsObjectId, 'emit', []),
        /execution session has ended/u
    );
    assert.equal(invocations, 0);
    h.sandbox.__operitJavaBridgeReleaseCallObjects('execution-a');
    assert.throws(
        () => h.sandbox.__operitJavaBridgeInvokeJsObject(callback.__javaJsObjectId, 'emit', []),
        /js object not found/u
    );
});

test('per-execution callback cleanup leaves other owners registered', async () => {
    const h = harness();
    const invoked = [];
    const firstJava = h.bound('execution-a');
    const secondJava = h.bound('execution-b');
    const first = firstJava.implement('ProbeCallback', { emit() { invoked.push('a'); } });
    const second = secondJava.implement('ProbeCallback', { emit() { invoked.push('b'); } });
    const firstRegistered = firstJava.callSuspend('Probe', 'register', first);
    const secondRegistered = secondJava.callSuspend('Probe', 'register', second);
    h.finish(h.calls[0], 'a registered');
    h.finish(h.calls[1], 'b registered');
    await Promise.all([firstRegistered, secondRegistered]);
    h.sandbox.__operitJavaBridgeReleaseCallObjects('execution-a');
    assert.throws(() => h.sandbox.__operitJavaBridgeInvokeJsObject(first.__javaJsObjectId, 'emit', []));
    h.sandbox.__operitJavaBridgeInvokeJsObject(second.__javaJsObjectId, 'emit', []);
    assert.deepEqual(invoked, ['b']);
});

test('cached module calls capture independent context snapshots before their first await', async () => {
    const h = harness();
    h.installRuntime();
    const source = `
        globalThis.moduleLoads = (globalThis.moduleLoads || 0) + 1;
        exports.run = async function() {
            const execution = getToolExecutionContext();
            await execution.java.callSuspend('Probe', 'pause');
            execution.emitIntermediate(execution.chatId);
            return {
                callId: execution.callId, chatId: execution.chatId,
                callerCardId: execution.callerCardId, language: execution.language,
                frozen: Object.isFrozen(execution), moduleLoads: globalThis.moduleLoads
            };
        };
    `;
    const execute = (callId, suffix) => h.sandbox.__operitExecuteScriptFunction(callId, {
        __operit_package_chat_id: `chat-${suffix}`,
        __operit_package_caller_card_id: `card-${suffix}`,
        __operit_package_lang: suffix
    }, source, 'run', null, null, null);
    execute('execution-a', 'a');
    execute('execution-b', 'b');
    assert.equal(h.calls[0].callId, 'execution-a');
    assert.equal(h.calls[1].callId, 'execution-b');
    h.finish(h.calls[1], 'resume b');
    await new Promise(setImmediate);
    h.finish(h.calls[0], 'resume a');
    await new Promise(setImmediate);

    assert.deepEqual(plain(h.outcomes.get('execution-a')), {
        callId: 'execution-a', chatId: 'chat-a', callerCardId: 'card-a',
        language: 'a', frozen: true, moduleLoads: 1
    });
    assert.deepEqual(plain(h.outcomes.get('execution-b')), {
        callId: 'execution-b', chatId: 'chat-b', callerCardId: 'card-b',
        language: 'b', frozen: true, moduleLoads: 1
    });
    assert.deepEqual(plain(h.intermediate), [
        { callId: 'execution-b', value: 'chat-b' },
        { callId: 'execution-a', value: 'chat-a' }
    ]);
});
