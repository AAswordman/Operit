# ADB JavaScript 执行与调试说明

这套 ADB JS 脚本不是单纯的“把一个文件推到手机上跑一下”。
在 Operit 仓库里，它们是主要的 **安卓端 JavaScript 运行、调试与验证入口**。

它们适合用于：

- 直接把 JS 推到真实 Android 设备上运行
- 在不走完整导入流程时调试普通 `.js` sandbox / package
- 验证导出函数的参数传递、返回值、`complete(...)`、结构化结果
- 复现只能在设备侧出现的运行时问题
- 运行 `app/src/androidTest/js` 下的 JS bridge / script mode / smoke test 资产

如果你要直接执行“顶层脚本模式”，而不是调用导出函数，请使用 `run_sandbox_script.sh` / `run_sandbox_script.bat`。

## 工具分工

### `tools/execute_js.bat` / `tools/execute_js.sh`

这是最常用的单文件运行器。

作用：

- 把一个 JS 文件临时推到设备
- 在 Operit 的 Android JS runtime 里调用某个导出函数
- 返回结构化结果

适合：

- 普通单文件 JS 调试
- 快速验证某个函数逻辑
- 复现设备侧 bug
- 开发普通 `.js` package 时做单次验证

### `tools/execute_js_dir.bat` / `tools/execute_js_dir.sh`

这是目录入口运行器。

作用：

- 以目录为单位执行入口文件
- 先做 bundle，再推送到设备
- 支持本地 `require(...)` / 多文件依赖

适合：

- 入口依赖多个本地模块
- 运行 `app/src/androidTest/js` 里的测试入口
- 跑 browser / bridge / harness 一类目录化测试

### `tools/run_sandbox_script.bat` / `tools/run_sandbox_script.sh`

这是脚本模式运行器。

作用：

- 直接执行顶层 sandbox script
- 不要求导出函数
- 适合验证 script mode 合约

适合：

- 快速跑一段顶层脚本
- 验证 `emit(...)` / `complete(...)` / `return`
- 做 inline snippet / top-level 行为测试

## 真实测试资产

仓库里的 [app/src/androidTest/js](/d:/Code/prog/assistance/app/src/androidTest/js) 不是普通示例目录，而是这套工具的重要使用场景之一。

其中包括：

- JS bridge contract 测试
- JS bridge edge case 测试
- script mode contract 测试
- browser tool smoke / probe
- utility / ffmpeg / ttscleaner 等运行时测试资产

典型目录：

- [bridge_contract](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/bridge_contract)
- [bridge_edges](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/bridge_edges)
- [script_mode_contract](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/script_mode_contract)
- [browser main.js](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/defaultTool/standard/browser/main.js)
- [ttscleaner.js](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/util/ttscleaner/ttscleaner.js)

## 先记结论

如果你只是想快速判断“我现在该用哪个”：

- 调一个普通 `.js` 文件里导出的函数，用 `execute_js.*`
- 入口文件依赖同目录其他模块，或者你在跑 `app/src/androidTest/js` 里的目录化测试，用 `execute_js_dir.*`
- 想验证顶层 script mode 行为，不想写导出函数，用 `run_sandbox_script.*`
- 你改的是 `examples/` 里的 package / toolpkg，想热更新到设备里的包系统，再去 UI 或包管理里验证，用 `tools/sync_example_packages.py`
- 你改的是 `examples/` 里的 toolpkg 调试工具，例如 `operit_editor` 的 `debug_install_toolpkg`，通常是先 `tools/sync_example_packages.py`，再用 `execute_js.bat` 去调这个工具

## 快速开始

### Windows：执行单文件导出函数

```cmd
tools\execute_js.bat path\to\your\script.js functionName @params.json
```

示例：

```cmd
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

### Linux/macOS：执行单文件导出函数

```bash
./tools/execute_js.sh path/to/your/script.js functionName '{"param1":"value1"}'
```

### 执行整个目录入口（支持 `require(...)`）

Windows：

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\bridge_contract\bridge_contract_runner.js run @params.json
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\defaultTool\standard\browser\main.js run "{}"
```

Linux/macOS：

```bash
./tools/execute_js_dir.sh app/src/androidTest/js com/ai/assistance/operit/core/tools/javascript/bridge_contract/bridge_contract_runner.js run @params.json
```

### 执行顶层 sandbox script

Windows：

```cmd
tools\run_sandbox_script.bat temp\probe.js @params.json
```

Linux/macOS：

```bash
./tools/run_sandbox_script.sh temp/probe.js @params.json
```

## 结果查看

这些执行器现在不是靠 `adb logcat` 抓输出，而是等待设备上的结构化结果文件。
执行结束后会打印 JSON 结果，通常包含：

- `success`
- `result`
- `error`
- `events`
- `durationMs`

这对于运行时调试和 JS 合约测试特别有用，因为失败原因会更集中，不需要再手翻大量日志。

可以通过下面的环境变量调整等待超时：

```cmd
set OPERIT_RESULT_WAIT_SECONDS=30
tools\execute_js.bat examples\my_script.js main @params.json
```

## 作用和调用方法

### 1. `execute_js.*` 是干什么的

它是最直接的“安卓真机 JS 运行器”。

你给它：

- 一个 JS 文件
- 一个导出函数名
- 一份 JSON 参数

它就会把这个文件放到设备上，在 Operit 的 JS runtime 里直接调用这个函数。

适合：

- 单文件功能验证
- 调某个导出函数
- 复现设备侧运行时报错
- 调试 package 里的某个工具实现

最常见调用：

```cmd
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

你在写新脚本时，最小可运行要求就是：

- 文件是合法 JS，不是 TS 原文
- 目标函数已经导出
- 参数按 JSON 传入

例如：

```javascript
function main(params) {
  return {
    success: true,
    echo: params
  };
}

exports.main = main;
```

### 2. `execute_js_dir.*` 是干什么的

它是“目录入口执行器”。

如果你的入口文件不是一个孤立文件，而是还会 `require("./xxx")`、依赖同目录工具模块、测试工具、runner、helper，那么不要继续用单文件执行器，直接换这个。

适合：

- `app/src/androidTest/js` 下的测试入口
- bridge contract / browser smoke / harness 这种目录化测试
- 你自己新加了一套多文件测试目录

最常见调用：

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\bridge_contract\bridge_contract_runner.js run @params.json
```

调用格式：

```cmd
tools\execute_js_dir.bat <根目录> <入口相对路径> <函数名> [JSON参数|@参数文件]
```

这里最关键的是：

- 第一个参数是“测试根目录”
- 第二个参数是“相对这个根目录的入口文件”
- 第三个参数通常是 runner 暴露的 `run`

### 3. `run_sandbox_script.*` 是干什么的

它不是调导出函数，而是直接跑“整段顶层脚本”。

适合：

- 验证 script mode 合约
- 快速试 `emit(...)`
- 快速试 `complete(...)`
- 快速做一段临时探针脚本

最常见调用：

```cmd
tools\run_sandbox_script.bat temp\probe.js @params.json
```

如果你的代码天然就是“顶层执行”，这个比先包一层 `exports.main = ...` 更顺手。

### 4. `tools/sync_example_packages.py` 是干什么的

它不是单纯的“复制文件脚本”，而是 `examples/` 包开发时的同步入口。

它最重要的作用是：

- 先把 `examples/` 里相关 TS 预编译成 JS
- 把带 `manifest.json` / `manifest.hjson` 的目录打成 `.toolpkg`
- 把普通 `.js` 或 `.toolpkg` 同步到 `app/src/main/assets/packages`
- 如果连着设备，还会把变化的包热更新到设备

所以它适合：

- 你改的是 `examples/` 里的正式包
- 你想让包管理 / 沙箱包列表 / 内置加载逻辑看到最新结果
- 你要验证 folder -> `.toolpkg` -> debug install 这条链路

最常见调用：

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include sidebar_bing_action
```

如果你要全量测试 examples 里的可同步项目，可以用：

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --mode test
```

如果你只想更新本地资产，不想碰设备热更新：

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include sidebar_bing_action --no-hot-reload
```

## 测试如何加

这里最容易混淆的是：并不是所有“测试”都要放到同一个地方。

### 1. 加单文件函数测试

如果你只是想验证一个函数逻辑是否正确，最轻量的方式是：

- 先写一个普通 JS 文件
- 导出一个 `main` 或 `run`
- 用 `execute_js.*` 直接调

这种方式适合：

- 小逻辑验证
- 参数解析验证
- 返回结构验证
- 某个 bug 的最小复现

### 2. 加目录化运行时测试

如果你的测试会引用多个本地模块，建议直接放到 [app/src/androidTest/js](/d:/Code/prog/assistance/app/src/androidTest/js) 下面，按一个小目录来组织。

推荐组织方式：

- 一个入口 runner 文件，例如 `xxx_runner.js`
- 若干测试文件 / helper 文件
- runner 暴露 `run(params)`

这样你就能直接用：

```cmd
tools\execute_js_dir.bat app\src\androidTest\js <你的入口相对路径> run "{}"
```

这类测试最适合：

- bridge contract
- 多模块协作
- 真实运行时 smoke test
- 需要本地 `require(...)` 的测试

### 3. 加 script mode 测试

如果你要测的是：

- 顶层 `return`
- `emit(...)`
- `complete(...)`
- script mode 和 function mode 的差异

那就不要强行塞进导出函数测试，直接写成 script mode 文件，再用 `run_sandbox_script.*` 跑。

### 4. 给 `examples/` 里的 package / toolpkg 加验证

如果你加的是一个真正要被包系统加载的例子，重点不只是“JS 能跑”，还要验证“包能不能被同步、刷新、安装、识别”。

这时推荐这样加：

- 在 `examples/` 下新增或修改你的 `.ts` / `.js` / manifest 目录
- 如果是目录化 package，保证它有 `manifest.json` 或 `manifest.hjson`
- 用 `tools/sync_example_packages.py` 同步
- 再用 `execute_js.bat` 去调用相关调试工具，或者直接在应用里验证加载结果

也就是说：

- 运行器测试的是“这段 JS 在安卓 runtime 里能不能执行”
- `tools/sync_example_packages.py` 测的是“这个 examples 包能不能进入真正的包加载链路”

## 和 `tools/sync_example_packages.py` 怎么配合

### 场景 1：你改的是普通 JS 脚本

这种情况下通常不需要先 sync。

直接：

```cmd
tools\execute_js.bat path\to\script.js main @params.json
```

因为你只是在验证脚本逻辑，不是在验证包加载。

### 场景 2：你改的是 `examples/` 里的 package

推荐顺序：

1. 改 `examples/...`
2. 运行 `tools/sync_example_packages.py`
3. 看它是否把输出同步到 `app/src/main/assets/packages`
4. 如果连着设备，顺便验证热更新是否成功
5. 再在应用里或用调试工具验证功能

这种适合：

- 普通 `.js` 包
- 要进入 packages 列表的例子
- 改包描述、manifest、依赖结构

### 场景 3：你改的是目录型 toolpkg

这是最需要和 `tools/sync_example_packages.py` 配合的场景。

推荐顺序：

1. 改 `examples/<toolpkg目录>`
2. 运行 `tools/sync_example_packages.py --include <目录名>`
3. 让它把目录打成 `.toolpkg`
4. 让它把生成物同步到 assets 和设备
5. 再用 `operit_editor` 的 `debug_install_toolpkg` 或相关调试入口验证安装结果

这样测到的是完整链路：

- 目录内容是否能正确打包
- `.toolpkg` 是否能被热更新到设备
- debug install 是否能识别这个包
- 包刷新后是否真的进入加载列表

### 场景 4：你改的是 `operit_editor` 这类调试工具本身

推荐顺序：

1. 先 `tools/sync_example_packages.py` 更新 `operit_editor` 自己
2. 再准备一个待安装的 package / toolpkg
3. 用 `execute_js.bat` 调 `operit_editor.js` 的调试函数
4. 看结构化结果里的成功、失败和相关告警

例如：

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include operit_editor --include sidebar_bing_action
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

这适合验证：

- debug install 调用方式对不对
- folder 打包成 `.toolpkg` 后能否被正确安装
- 安装结束后返回给调用方的告警 / 错误是否完整

## 推荐调用清单

### 1. 跑一个普通导出函数

```cmd
tools\execute_js.bat examples\my_script.js main @params.json
```

### 2. 跑一个 `androidTest/js` 目录测试入口

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\bridge_contract\bridge_contract_runner.js run @params.json
```

### 3. 跑一个顶层 script mode 探针

```cmd
tools\run_sandbox_script.bat temp\probe.js @params.json
```

### 4. 同步一个 `examples/` 包到 assets 和设备

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include sidebar_bing_action
```

### 5. 同步后再调用调试安装工具

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include operit_editor --include sidebar_bing_action
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

## Script Mode 注意事项

当你使用 `run_sandbox_script.sh` / `run_sandbox_script.bat`，或者在包工具里走 `debug_run_sandbox_script` 的 `source_code` 路径时，代码会按 **顶层脚本** 执行，而不是按 `function(params) { ... }` 执行。

这意味着：

- 用 `console.log(...)` / `console.info(...)` / `console.warn(...)` / `console.error(...)` 打日志
- 用 `emit(...)` 发送中间事件
- 用 `return result` 或 `complete(...)` 完成执行
- 不要假设 `params` 会直接出现在顶层作用域
- 不要用 `intermediate(...)`，当前约定是 `emit(...)`

推荐顶层片段：

```javascript
console.log("inline debug start");
emit({ stage: "inline", ok: true });
complete({
  success: true,
  message: "inline debug finished"
});
```

## JS 文件要求

### 对 `execute_js.*`

- 文件必须是合法 JavaScript
- 目标函数必须导出，例如 `exports.myFunction = myFunction`
- 函数接收 `params`
- 可以 `return result`，也可以调用 `complete(result)`

示例：

```javascript
function myFunction(params) {
  const name = params.name || "default";
  return {
    success: true,
    result: `Result: ${name}`
  };
}

exports.myFunction = myFunction;
```

### 对 `run_sandbox_script.*`

- 不要求导出函数
- 按顶层脚本模式运行
- 更适合 script mode 验证与快速 probe

## 技术路径

1. 脚本把 JS 文件推到设备临时目录
2. `execute_js_dir.*` 会先把目录入口 bundle 成单文件
3. JSON 参数会先落成临时文件，再推到设备，避免命令行引号问题
4. 通过 ADB broadcast 发送执行请求
5. 应用内的 `ScriptExecutionReceiver` 接收请求
6. `JsEngine` 在应用运行时里执行函数或脚本
7. 执行结果写入设备侧 JSON 文件
8. 执行器等待这个结果文件并输出

相关接收器：

- [ScriptExecutionReceiver.kt](/d:/Code/prog/assistance/app/src/main/java/com/ai/assistance/operit/core/tools/javascript/ScriptExecutionReceiver.kt)
- [ToolPkgDebugInstallReceiver.kt](/d:/Code/prog/assistance/app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgDebugInstallReceiver.kt)

## 故障排查

### 1. 找不到 ADB

确认 Android SDK 的 `platform-tools` 已加入 PATH。

### 2. 没有检测到设备

- 确认设备已连接
- 确认已开启 USB 调试
- 确认电脑已获得调试授权

### 3. 广播发出去了但没有执行

- 确认应用已安装
- 确认目标 Receiver 已注册
- 看结构化结果里的 `error` / `events`

### 4. JS 语法错误

- 不要把 TypeScript 语法直接喂给运行器
- 检查导出函数是否存在
- 检查参数 JSON 是否正确

### 5. 多文件依赖跑不起来

优先改用 `execute_js_dir.*`，不要继续用单文件执行器硬跑 `require(...)` 场景。

## 安全说明

- 这套工具主要用于开发、调试、验证
- 它们依赖应用内 Receiver 接收外部 broadcast
- 在生产环境中应谨慎暴露这类调试入口
