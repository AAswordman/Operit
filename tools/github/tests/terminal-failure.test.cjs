const test = require('node:test');
const assert = require('node:assert/strict');
const { toolFailure } = require('../../../examples/github/src/utils/toolFailure.js');

function nativeFailure(code, output) {
    const error = new Error(`终端命令以非零状态退出（exitCode=${code}）`);
    error.data = { command: 'probe', output, exitCode: code, sessionId: 'probe-session', timedOut: false };
    return error;
}

test('nonzero exits retain stdout, stderr, command and exit code', () => {
    const error = nativeFailure(7, 'stdout: 完整输出\nstderr: 完整错误\n');
    const result = toolFailure(error, '终端执行失败');
    assert.equal(result.success, false);
    assert.strictEqual(result.data, error.data);
    assert.equal(result.data.exitCode, 7);
    assert.equal(result.data.output, 'stdout: 完整输出\nstderr: 完整错误\n');
    assert.equal(result.data.command, 'probe');
    assert.equal(result.data.sessionId, 'probe-session');
});

test('large failure output is not truncated or rewritten by the wrapper', () => {
    const output = '中英文 output\n'.repeat(20000) + 'LAST-LINE';
    assert.equal(toolFailure(nativeFailure(23, output), '失败').data.output, output);
});

test('timeout and native saved-output fields survive without reconstruction', () => {
    const error = nativeFailure(124, 'partial');
    error.data.timedOut = true;
    error.data.output_saved_to = '/tmp/full-output.log';
    const result = toolFailure(error, '失败');
    assert.strictEqual(result.data, error.data);
    assert.equal(result.data.timedOut, true);
    assert.equal(result.data.output_saved_to, '/tmp/full-output.log');
});

test('transport errors remain failures without invented terminal data', () => {
    const result = toolFailure(new Error('connection unavailable'), '终端执行失败');
    assert.equal(result.success, false);
    assert.equal(result.message, '终端执行失败: connection unavailable');
    assert.equal(Object.hasOwn(result, 'data'), false);
});

test('structured errors outside the terminal retain their original payload', () => {
    const error = { message: 'request rejected', data: { status: 403, reason: 'permission' } };
    assert.strictEqual(toolFailure(error, '调用失败').data, error.data);
});

test('string errors retain their message without producing a success result', () => {
    const result = toolFailure('failure text', '调用失败');
    assert.equal(result.success, false);
    assert.equal(result.message, '调用失败: failure text');
});
