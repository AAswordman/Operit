'use strict';

const {
  assert,
  assertEq,
  test,
  runSuite,
} = require('./common');

const AndroidShellExecutor = Java.com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor;
const StandardActionListener = Java.com.ai.assistance.operit.core.tools.system.action.StandardActionListener;
const StreamBuildersKt = Java.type('com.ai.assistance.operit.util.stream.StreamBuildersKt');

const SUSPEND_TIMEOUT_MS = 2000;

function withTimeout(promise, label) {
  return Promise.race([
    promise,
    new Promise(function (_resolve, reject) {
      setTimeout(function () {
        reject(new Error((label || 'suspend call') + ' timed out after ' + SUSPEND_TIMEOUT_MS + 'ms'));
      }, SUSPEND_TIMEOUT_MS);
    }),
  ]);
}

function assertCommandResultProxy(result, label) {
  assert(result && typeof result === 'object', (label || 'result') + ' should be an object');
  assertEq(
    String(result.className),
    'com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor$CommandResult',
    (label || 'result') + ' className mismatch'
  );
  assertEq(typeof result.success, 'boolean', (label || 'result') + ' success should be boolean');
  assertEq(typeof result.stdout, 'string', (label || 'result') + ' stdout should be string');
  assertEq(typeof result.stderr, 'string', (label || 'result') + ' stderr should be string');
  assertEq(typeof result.exitCode, 'number', (label || 'result') + ' exitCode should be number');
}

async function expectRejectMessage(factory) {
  try {
    await withTimeout(factory(), 'expected rejection');
  } catch (error) {
    return String(error && error.message ? error.message : error);
  }
  throw new Error('expected promise rejection');
}

exports.run = async function run() {
  return runSuite([
    test('Java.callSuspend awaits companion suspend method and returns proxy result', async () => {
      const result = await withTimeout(
        Java.callSuspend(
          'com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor',
          'executeShellCommand',
          'echo bridge-contract-suspend-top-level'
        ),
        'Java.callSuspend companion suspend method'
      );
      assertCommandResultProxy(result, 'Java.callSuspend');
    }),
    test('class proxy callSuspend awaits companion suspend method and returns proxy result', async () => {
      const result = await withTimeout(
        AndroidShellExecutor.callSuspend(
          'executeShellCommand',
          'echo bridge-contract-suspend-class'
        ),
        'class proxy callSuspend companion suspend method'
      );
      assertCommandResultProxy(result, 'class proxy callSuspend');
    }),
    test('instance proxy callSuspend awaits suspend method and unwraps boolean result', async () => {
      const listener = new StandardActionListener(Java.getApplicationContext());
      const available = await withTimeout(
        listener.callSuspend('isAvailable'),
        'instance proxy callSuspend suspend method'
      );
      assertEq(typeof available, 'boolean');
      assertEq(available, true);
    }),
    test('Promise.all can await multiple suspend bridge calls', async () => {
      const listener = new StandardActionListener(Java.getApplicationContext());
      const results = await withTimeout(
        Promise.all([
          listener.callSuspend('isAvailable'),
          listener.callSuspend('isAvailable'),
          listener.callSuspend('isAvailable'),
        ]),
        'Promise.all suspend bridge batch'
      );
      assertEq(results.length, 3);
      assertEq(typeof results[0], 'boolean');
      assertEq(typeof results[1], 'boolean');
      assertEq(typeof results[2], 'boolean');
      assertEq(results[0], true);
      assertEq(results[1], true);
      assertEq(results[2], true);
    }),
    test('instance proxy callSuspend collect pumps Java-to-JS callbacks while awaiting stream collection', async () => {
      const stream = StreamBuildersKt.streamOf('a', 'b', 'c');
      const chunks = [];

      await withTimeout(
        stream.callSuspend('collect', {
          emit(value) {
            chunks.push(String(value));
          },
        }),
        'instance proxy callSuspend stream collect'
      );

      assertEq(chunks.length, 3);
      assertEq(chunks.join(''), 'abc');
    }),
    test('Java.callSuspend rejects with explicit message for non-suspend methods', async () => {
      const message = await expectRejectMessage(async function () {
        await Java.callSuspend('java.lang.Integer', 'parseInt', '1');
      });
      assert(
        message.indexOf("static suspend method 'parseInt' not found on java.lang.Integer") >= 0,
        'missing non-suspend rejection detail: ' + message
      );
    }),
    test('instance proxy callSuspend rejects with explicit message for missing suspend methods', async () => {
      const listener = new StandardActionListener(Java.getApplicationContext());
      const message = await expectRejectMessage(async function () {
        await listener.callSuspend('missingSuspendMethod');
      });
      assert(
        message.indexOf(
          "instance suspend method 'missingSuspendMethod' not found on com.ai.assistance.operit.core.tools.system.action.StandardActionListener"
        ) >= 0,
        'missing instance rejection detail: ' + message
      );
    }),
  ]);
};
