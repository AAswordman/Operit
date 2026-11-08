import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const sourceRoot = new URL('../../../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/', import.meta.url);

function kotlinScript(name) {
    const source = readFileSync(new URL(name, sourceRoot), 'utf8');
    const match = source.match(/return """\r?\n([\s\S]*?)"""\.trimIndent\(\)/);
    assert.ok(match, `找不到运行时脚本：${name}`);
    return match[1];
}

const bridge = kotlinScript('JsComposeDslBridge.kt');
const wrapper = kotlinScript('JsComposeDslRuntimeScript.kt');

function createRuntime() {
    const commands = [];
    const context = vm.createContext({
        NativeInterface: {
            composeWebViewControllerCommand(payload) {
                commands.push(JSON.parse(payload));
                return JSON.stringify({ success: true, data: null });
            },
        },
    });
    vm.runInContext(bridge, context);
    vm.runInContext('var bundle = OperitComposeDslRuntime.createContext({});', context);
    return { context, commands, run: source => vm.runInContext(source, context) };
}

test('反复重绘后仅保留当前界面的回调', () => {
    const { run } = createRuntime();
    const retainedCount = run(`
        var oldId = null;
        var retainedCount = 0;
        for (var index = 0; index < 5000; index++) {
            var node = bundle.ctx.UI.Button({
                onClick: (function(value) { return function() { return value; }; })(index)
            });
            var id = node.props.onClick.__actionId;
            retainedCount = bundle.retainActions(bundle.getActionSnapshot(), [id]);
            if (bundle.invokeAction(id) !== index) throw new Error('当前回调失效');
            if (oldId) {
                var retired = false;
                try { bundle.invokeAction(oldId); } catch (error) { retired = true; }
                if (!retired) throw new Error('旧回调未释放');
            }
            oldId = id;
        }
        retainedCount;
    `);
    assert.equal(retainedCount, 1);
});

test('界面尚未提交时保留旧树及新生成树的回调', () => {
    const { run } = createRuntime();
    assert.equal(run(`
        var first = bundle.ctx.UI.Button({ onClick: function() { return 1; } }).props.onClick.__actionId;
        var firstSnapshot = bundle.getActionSnapshot();
        var second = bundle.ctx.UI.Button({ onClick: function() { return 2; } }).props.onClick.__actionId;
        bundle.retainActions(firstSnapshot, [first]);
        bundle.invokeAction(first) + bundle.invokeAction(second);
    `), 3);
});

test('排队中的旧回调在执行完成前继续可用', () => {
    const { run } = createRuntime();
    assert.equal(run(`
        var first = bundle.ctx.UI.Button({ onClick: function() { return 7; } }).props.onClick.__actionId;
        var second = bundle.ctx.UI.Button({ onClick: function() { return 8; } }).props.onClick.__actionId;
        var count = bundle.retainActions(bundle.getActionSnapshot(), [second, first]);
        if (count !== 2 || bundle.invokeAction(first) !== 7) throw new Error('排队回调失效');
        bundle.retainActions(bundle.getActionSnapshot(), [second]);
    `), 1);
});

test('迟到的旧界面确认不能删除新界面仍在使用的缓存回调', () => {
    const { run } = createRuntime();
    assert.equal(run(`
        var first = bundle.ctx.UI.Button({ onClick: function() { return 11; } }).props.onClick.__actionId;
        var second = bundle.ctx.UI.Button({ onClick: function() { return 12; } }).props.onClick.__actionId;
        var older = bundle.getActionSnapshot();
        var newer = bundle.getActionSnapshot();
        bundle.retainActions(newer, [first, second]);
        bundle.retainActions(older, [first]);
        bundle.invokeAction(second);
    `), 12);
});

test('另一执行上下文的界面确认不影响当前回调', () => {
    const { run } = createRuntime();
    assert.equal(run(`
        var foreign = OperitComposeDslRuntime.createContext({}).getActionSnapshot();
        var current = bundle.ctx.UI.Button({ onClick: function() { return 19; } }).props.onClick.__actionId;
        bundle.retainActions(foreign, []);
        bundle.invokeAction(current);
    `), 19);
});

test('独立注册的 WebView 接口回调不随界面树回收', () => {
    const { run, commands, context } = createRuntime();
    run(`
        var controller = bundle.ctx.createWebViewController('player');
        controller.addJavascriptInterface('Bridge', { ping: function() { return 23; } });
        var tree = bundle.ctx.UI.Button({ onClick: function() { return 1; } });
        bundle.retainActions(bundle.getActionSnapshot(), []);
    `);
    context.interfaceActionId = commands[0].payload.object.ping.__actionId;
    assert.equal(run('bundle.invokeAction(interfaceActionId);'), 23);
});

test('正式渲染响应包含用于回收的执行上下文和渲染版本', () => {
    const { context, run } = createRuntime();
    const screen = `
        var module = { exports: {} };
        var exports = module.exports;
        exports.default = function(ctx) {
            return ctx.UI.Button({ onClick: function() { return 31; } });
        };
    `;
    vm.runInContext(wrapper.replace('$script', screen), context);
    const response = run('__operit_render_compose_dsl({});');
    assert.equal(typeof response.__composeActions.contextId, 'string');
    assert.equal(response.__composeActions.generation, 1);
    assert.equal(response.__composeActions.counter, 1);
    const rerender = run('__operit_rerender_compose_dsl({});');
    assert.equal(rerender.__composeActions.generation, 2);
    assert.equal(rerender.__composeActions.counter, 2);
    assert.equal(rerender.__composeActions.contextId, response.__composeActions.contextId);
});
