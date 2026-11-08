import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../../../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgRegistration.kt', import.meta.url), 'utf8');
const match = source.match(/return """\r?\n([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match, '找不到真实注册桥脚本');

function runtime() {
    const modules = [];
    const navigation = [];
    const context = vm.createContext({
        __operitToolPkgApi: {
            namespace(_name, methods) { return methods; },
            method() { return { since(_version, method) { return method; } }; },
        },
        NativeInterface: {
            registerToolPkgUiModule(spec) { modules.push(JSON.parse(spec)); },
            registerToolPkgNavigationEntry(spec) { navigation.push(JSON.parse(spec)); },
        },
    });
    vm.runInContext(match[1], context);
    return { modules, navigation, run: script => vm.runInContext(script, context) };
}

test('旧命名空间调用注册插件配置，返回 undefined 且不创建导航', () => {
    const { modules, navigation, run } = runtime();
    assert.equal(run('ToolPkg.registerToolboxUiModule({ id: "settings", screen: "ui/settings.js" })'), undefined);
    assert.deepEqual(modules, [{ id: 'settings', screen: 'ui/settings.js' }]);
    assert.equal(navigation.length, 0);
});

test('旧全局别名保留函数 screen 的路径归一化', () => {
    const { modules, navigation, run } = runtime();
    assert.equal(run(`
        var screen = function() {};
        screen.__operit_toolpkg_module_path = 'ui/settings.js';
        registerToolPkgToolboxUiModule({ id: 'settings', screen: screen });
    `), undefined);
    assert.deepEqual(modules, [{ id: 'settings', screen: 'ui/settings.js' }]);
    assert.equal(navigation.length, 0);
});

test('旧方法与新方法使用相同的配置模块定义', () => {
    const { modules, run } = runtime();
    run(`
        ToolPkg.registerUiModule({ id: 'new', screen: 'ui/settings.js', runtime: 'compose_dsl' });
        ToolPkg.registerToolboxUiModule({ id: 'old', screen: 'ui/settings.js', runtime: 'compose_dsl' });
    `);
    assert.deepEqual(modules.map(({ id, ...spec }) => spec), [
        { screen: 'ui/settings.js', runtime: 'compose_dsl' },
        { screen: 'ui/settings.js', runtime: 'compose_dsl' },
    ]);
});

test('旧注册对无效 screen 报错，不产生空配置入口', () => {
    const { modules, run } = runtime();
    assert.throws(() => run('ToolPkg.registerToolboxUiModule({ id: "invalid", screen: function() {} })'), /serializable screen/);
    assert.equal(modules.length, 0);
});
