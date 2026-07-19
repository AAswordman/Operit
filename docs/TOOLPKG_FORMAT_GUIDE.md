# ToolPkg 格式说明文档

## 1. 简介

**ToolPkg** 是 Operit 项目中用于打包和分发工具包的标准格式。它允许开发者将多个相关的工具脚本、资源文件和 UI 模块打包成一个单一的、易于分发和管理的文件。

### 1.1 什么是 ToolPkg？

- **文件格式**：`.toolpkg` 文件本质上是一个标准的 ZIP 压缩包
- **核心组件**：包含一个清单文件（manifest）和相关的资源文件
- **模块化设计**：支持将多个功能相关的子包（subpackages）组织在一起
- **资源管理**：可以包含二进制资源、脚本文件、UI 模块等
- **多语言支持**：内置对多语言文本的支持

### 1.2 ToolPkg vs 传统 JS 脚本

| 特性 | 传统 JS 脚本 | ToolPkg |
|------|-------------|---------|
| 文件格式 | 单个 `.js` 文件 | ZIP 压缩包 (`.toolpkg`) |
| 组织方式 | 单一脚本 | 多个子包 + 资源 + UI 模块 |
| 资源文件 | 不支持 | 支持打包任意资源 |
| UI 模块 | 不支持 | 支持 Compose DSL UI |
| 多语言 | 需手动实现 | 内置支持 |
| 版本管理 | 无标准 | 内置版本字段 |

## 2. ToolPkg 文件结构

一个典型的 `.toolpkg` 文件的内部结构如下：

```
windows_control.toolpkg (ZIP 压缩包)
├── manifest.json                          # 清单文件（必需）
├── main.js                                # ToolPkg 主入口脚本（必需）
├── main.ts                                # 主入口 TypeScript 源码（建议）
├── packages/                              # 子包脚本目录
│   └── windows_control.js                 # 子包脚本
├── ui/                                    # UI 模块目录
│   └── windows_setup/
│       └── index.ui.js                    # UI 模块脚本
├── modules/                               # 可选 WASM 核心模块
│   └── core.wasm                          # AssemblyScript 编译产物
├── resources/                             # 资源文件目录
│   └── pc_agent/
│       └── operit-pc-agent/              # 目录资源（readResource 时自动导出为 zip）
└── i18n/                                  # 国际化文件（可选）
    ├── zh-CN.js
    └── en-US.js
```

### 2.1 必需文件

- **manifest.json** 或 **manifest.hjson**：清单文件，定义包的元数据和结构

### 2.2 可选目录

- **packages/**：存放子包的 JavaScript 脚本文件
- **ui/**：存放 UI 模块的脚本文件
- **resources/**：存放任意资源文件（图片、压缩包、配置文件等）
- **i18n/**：存放国际化相关文件

## 3. Manifest 清单文件

清单文件是 ToolPkg 的核心，定义了包的所有元数据和结构。支持两种格式：

- **manifest.json**：标准 JSON 格式
- **manifest.hjson**：HJSON 格式（支持注释和更宽松的语法）

### 3.1 完整示例

```json
{
  "schema_version": 1,
  "toolpkg_id": "com.operit.windows_bundle",
  "version": "0.2.0",
  "author": ["Operit Team", "Alice"],
  "main": "main.js",
  "display_name": {
    "zh": "Windows 工具包",
    "en": "Windows Bundle"
  },
  "description": {
    "zh": "Windows 一键配置与控制工具包",
    "en": "Windows one-click setup and control bundle"
  },
  "subpackages": [
    {
      "id": "windows_control",
      "entry": "packages/windows_control.js",
      "enabled_by_default": false,
      "display_name": {
        "zh": "Windows 控制",
        "en": "Windows Control"
      },
      "description": {
        "zh": "通过 Operit PC Agent 控制 Windows",
        "en": "Control Windows via Operit PC Agent"
      }
    }
  ],
  "resources": [
    {
      "key": "pc_agent_zip",
      "path": "resources/pc_agent/operit-pc-agent.zip",
      "mime": "application/zip"
    }
  ],
  "wasm_modules": [
    {
      "id": "core",
      "path": "modules/core.wasm",
      "exports": ["isPrime", "nthPrime"],
      "source_language": "assemblyscript",
      "abi": "assemblyscript"
    }
  ],
  "workflow_templates": [
    {
      "id": "quick_chat_workflow",
      "display_name": {
        "zh": "快速对话工作流",
        "en": "Quick Chat Workflow"
      },
      "description": {
        "zh": "手动触发后自动启动聊天并发送一条引导消息。",
        "en": "Starts a chat and sends a guidance message after a manual trigger."
      },
      "resource_key": "demo_workflow_template"
    }
  ],
  "workspace_templates": [
    {
      "id": "quick_start_workspace",
      "display_name": {
        "zh": "快速开始工作区",
        "en": "Quick Start Workspace"
      },
      "description": {
        "zh": "包含 .operit/config.json 的最小工作区模板。",
        "en": "A minimal workspace template containing .operit/config.json."
      },
      "resource_key": "demo_workspace_template",
      "project_type": "template_try"
    }
  ]
}
```

### 3.2 字段说明

#### 3.2.1 顶层字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `schema_version` | number | 是 | 清单架构版本，当前为 `1` |
| `toolpkg_id` | string | 是 | 包的唯一标识符，建议使用反向域名格式（如 `com.operit.windows_bundle`） |
| `version` | string | 否 | 包的版本号，建议使用语义化版本（如 `0.2.0`） |
| `author` | string \| string[] | 否 | 作者信息，支持单个作者字符串或作者字符串数组 |
| `main` | string | 是 | ToolPkg 主入口脚本路径（相对于 ZIP 根目录），用于执行注册函数 |
| `display_name` | LocalizedText | 否 | 包的显示名称，支持多语言 |
| `description` | LocalizedText | 否 | 包的描述信息，支持多语言 |
| `subpackages` | array | 否 | 子包列表，每个子包是一个独立的工具集 |
| `resources` | array | 否 | 资源文件列表，可以是任意类型的文件 |
| `wasm_modules` | array | 否 | 企业核心算法模块列表，当前用于声明和校验 `.wasm` 产物 |
| `workflow_templates` | array | 否 | 注册到宿主“工作流”入口的工作流模板列表 |
| `workspace_templates` | array | 否 | 注册到宿主“工作区创建”入口的工作区模板列表 |

#### 3.2.2 LocalizedText 类型

`LocalizedText` 支持两种格式：

**格式 1：简单字符串**
```json
"display_name": "Windows Bundle"
```

**格式 2：多语言对象**
```json
"display_name": {
  "zh": "Windows 工具包",
  "zh-CN": "Windows 工具包",
  "en": "Windows Bundle",
  "en-US": "Windows Bundle",
  "default": "Windows Bundle"
}
```

语言代码优先级：
1. 完整语言标签（如 `zh-CN`、`en-US`）
2. 语言代码（如 `zh`、`en`）
3. `default` 键
4. 对象中的任意值

#### 3.2.3 Subpackages（子包）

子包是 ToolPkg 的核心功能单元，每个子包包含一组相关的工具。

```json
{
  "id": "windows_control",
  "entry": "packages/windows_control.js",
  "enabled_by_default": false,
  "display_name": {
    "zh": "Windows 控制",
    "en": "Windows Control"
  },
  "description": {
    "zh": "通过 Operit PC Agent 控制 Windows",
    "en": "Control Windows via Operit PC Agent"
  }
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 子包的唯一标识符，在容器内必须唯一 |
| `entry` | string | 是 | 子包脚本的入口文件路径（相对于 ZIP 根目录） |
| `enabled_by_default` | boolean | 否 | 是否默认启用，默认为 `false` |
| `display_name` | LocalizedText | 否 | 子包的显示名称 |
| `description` | LocalizedText | 否 | 子包的描述信息 |

**子包脚本格式**：
- 子包脚本必须是标准的 JavaScript 文件
- 必须包含 `METADATA` 注释块（参考 [SCRIPT_DEV_GUIDE.md](./SCRIPT_DEV_GUIDE.md)）
- 脚本中定义的工具会被注册为 `<subpackage_id>:<tool_name>` 格式

#### 3.2.4 WASM 模块

`wasm_modules` 用于声明企业插件的核心算法模块。推荐由 AssemblyScript 编译得到 `.wasm`，插件作者入口写 `src/main.ts`，再通过同目录内的 typed facade 调用 WASM；打包阶段生成宿主执行用的 `main.js`。

```json
{
  "id": "core",
  "path": "modules/core.wasm",
  "exports": ["isPrime", "nthPrime"],
  "source_language": "assemblyscript",
  "abi": "assemblyscript"
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 模块 ID，在容器内必须唯一 |
| `path` | string | 是 | `.wasm` 文件路径（相对于 manifest 所在目录） |
| `exports` | string[] | 否 | 计划暴露给 JS 桥的导出函数名 |
| `source_language` | string | 否 | 源语言标记，AssemblyScript 模块写 `assemblyscript` |
| `abi` | string | 否 | 调用约定标记，AssemblyScript 模块写 `assemblyscript` |

推荐结构：

```text
my_toolpkg/
├── manifest.json
├── package.json
├── src/
│   ├── main.ts
│   └── wasm/
│       ├── core.ts
│       └── core.as.ts
├── build/
│   └── main.js
└── modules/
    └── core.wasm
```

AssemblyScript 核心模块示例 `src/wasm/core.as.ts`：

```ts
export function isPrime(n: i32): i32 {
  if (n < 2) return 0;
  for (let divisor: i32 = 2; divisor <= n / divisor; divisor += 1) {
    if (n % divisor === 0) return 0;
  }
  return 1;
}
```

编译示例：

```bash
npx asc src/wasm/core.as.ts --outFile modules/core.wasm --optimize
```

当前接入范围：

- `manifest` 会校验模块 ID、`.wasm` 路径和导出名。
- 加密发布时，`.wasm` 与其他包内资源一起进入保护格式，运行期按 manifest 元数据读取并解密。
- JS 运行时仍以 `main.js` 和已有 `exports` 为插件对外接口；`main.js` 可以由 TS 构建生成。
- `ToolPkg.wasm.call(moduleId, exportName, args)` 已接入 native WAMR runtime，当前 ABI 支持 `i32`、`i64`、`f32`、`f64` 数值参数和返回值。
- `i64` 结果以字符串返回；JS 传入 `i64` 时推荐使用字符串，避免超过 JS safe integer 后丢精度。

TS facade 示例 `src/wasm/core.ts`：

```ts
export async function isPrime(n: number): Promise<boolean> {
  const result = await ToolPkg.wasm.call("core", "isPrime", [{ type: "i32", value: n }]);
  if (typeof result !== "number") {
    throw new Error("core.isPrime returned a non-number result");
  }
  return result === 1;
}
```

主入口示例 `src/main.ts`：

```ts
import { isPrime } from "./wasm/core";

export async function run(params: { n: number }) {
  return { is_prime: await isPrime(params.n) };
}
```

#### 3.2.5 Main 脚本注册

ToolPkg 的 UI 模块和生命周期钩子不再写在 `manifest` 里，而是由 `main` 脚本通过注册函数声明。

`main.js` 示例：

```javascript
const toolboxUI = require("./ui/windows_setup/index.ui.js").default;

function registerToolPkg() {
  ToolPkg.registerToolboxUiModule({
    id: "windows_setup",
    runtime: "compose_dsl",
    screen: toolboxUI,
    params: {},
    title: {
      zh: "Windows 一键配置",
      en: "Windows Quick Setup"
    }
  });

  ToolPkg.registerUiRoute({
    id: "windows_dashboard",
    route: "toolpkg:com.example.windows_bundle:ui:windows_dashboard",
    runtime: "compose_dsl",
    screen: toolboxUI,
    title: {
      zh: "Windows 面板",
      en: "Windows Dashboard"
    }
  });

  ToolPkg.registerNavigationEntry({
    id: "windows_dashboard_toolbox",
    route: "toolpkg:com.example.windows_bundle:ui:windows_dashboard",
    surface: "toolbox",
    title: {
      zh: "Windows 面板",
      en: "Windows Dashboard"
    }
  });

  ToolPkg.registerDesktopWidget({
    id: "windows_dashboard_widget",
    route: "toolpkg:com.example.windows_bundle:ui:windows_dashboard",
    render: "toolpkg:com.example.windows_bundle:ui:windows_dashboard_widget",
    title: {
      zh: "Windows 面板小组件",
      en: "Windows Widget"
    },
    subtitle: {
      zh: "点击打开面板",
      en: "Tap to open dashboard"
    }
  });

  ToolPkg.registerAppLifecycleHook({
    id: "windows_app_create",
    event: "application_on_create",
    function: onApplicationCreate
  });

  ToolPkg.registerMessageProcessingPlugin({
    id: "windows_message_processing",
    function: onMessageProcessing
  });

  ToolPkg.registerXmlRenderPlugin({
    id: "windows_xml_status",
    tag: "windows_status",
    function: onXmlRender
  });

  ToolPkg.registerInputMenuTogglePlugin({
    id: "windows_input_menu_toggle",
    function: onInputMenuToggle
  });

  return true;
}

function onApplicationCreate() {
  return { ok: true };
}

function onMessageProcessing(params) {
  return { matched: false };
}

function onXmlRender(params) {
  if (params.tagName !== "windows_status") {
    return { handled: false };
  }
  return { handled: true, text: "Windows status ready" };
}

function onInputMenuToggle(params) {
  if (params.action === "create") {
    return {
      toggles: [
        {
          id: "windows_mode",
          title: "Windows Mode",
          description: "Enable Windows mode",
          isChecked: false
        }
      ]
    };
  }
  if (params.action === "toggle" && params.toggleId === "windows_mode") {
    return { ok: true };
  }
  return { ok: false };
}

exports.registerToolPkg = registerToolPkg;
exports.onApplicationCreate = onApplicationCreate;
exports.onMessageProcessing = onMessageProcessing;
exports.onXmlRender = onXmlRender;
exports.onInputMenuToggle = onInputMenuToggle;
```

注册项字段：

| 注册函数 | 字段 | 必需 | 说明 |
|------|------|------|------|
| `ToolPkg.registerToolboxUiModule` | `id` | 是 | UI 模块唯一标识 |
| `ToolPkg.registerToolboxUiModule` | `runtime` | 否 | 运行时类型，默认 `compose_dsl` |
| `ToolPkg.registerToolboxUiModule` | `screen` | 是 | UI 模块函数（推荐 `import/require ... default` 后传入） |
| `ToolPkg.registerToolboxUiModule` | `params` | 否 | UI 模块初始化参数对象 |
| `ToolPkg.registerToolboxUiModule` | `title` | 否 | 模块标题（支持 `LocalizedText`） |
| `ToolPkg.registerUiRoute` | `id` | 是 | UI 路由唯一标识 |
| `ToolPkg.registerUiRoute` | `route` / `routeId` | 否 | 稳定路由 ID；不填时宿主按 `toolpkg:<toolpkg_id>:ui:<id>` 自动生成 |
| `ToolPkg.registerUiRoute` | `runtime` | 否 | 运行时类型，默认 `compose_dsl` |
| `ToolPkg.registerUiRoute` | `screen` | 是 | UI 模块函数 |
| `ToolPkg.registerUiRoute` | `params` | 否 | UI 模块初始化参数对象 |
| `ToolPkg.registerUiRoute` | `title` | 否 | 路由标题（支持 `LocalizedText`） |
| `ToolPkg.registerNavigationEntry` | `id` | 是 | 导航入口唯一标识 |
| `ToolPkg.registerNavigationEntry` | `route` | 是 | 已注册路由 ID |
| `ToolPkg.registerNavigationEntry` | `surface` | 是 | 挂载面，当前支持 `toolbox`、`main_sidebar_plugins` |
| `ToolPkg.registerNavigationEntry` | `title` | 否 | 导航入口标题（支持 `LocalizedText`） |
| `ToolPkg.registerNavigationEntry` | `icon` | 否 | 图标名 |
| `ToolPkg.registerNavigationEntry` | `order` | 否 | 同一 surface 内排序值，越小越靠前 |
| `ToolPkg.registerDesktopWidget` | `id` | 是 | 小组件唯一标识 |
| `ToolPkg.registerDesktopWidget` | `route` / `routeId` | 是 | 已注册路由 ID |
| `ToolPkg.registerDesktopWidget` | `render` / `renderRouteId` | 否 | 小组件渲染所使用的 UI route；默认等于 `route` |
| `ToolPkg.registerDesktopWidget` | `title` | 否 | 小组件标题（支持 `LocalizedText`） |
| `ToolPkg.registerDesktopWidget` | `subtitle` | 否 | 小组件副标题（支持 `LocalizedText`） |
| `ToolPkg.registerDesktopWidget` | `description` | 否 | 小组件配置说明（支持 `LocalizedText`） |
| `ToolPkg.registerDesktopWidget` | `icon` | 否 | 图标名，供宿主配置页等场景使用 |
| `ToolPkg.registerDesktopWidget` | `order` | 否 | 排序值，越小越靠前 |
| `ToolPkg.registerAppLifecycleHook` | `id` | 是 | 生命周期钩子唯一标识 |
| `ToolPkg.registerAppLifecycleHook` | `event` | 是 | 生命周期事件名（见下方完整列表） |
| `ToolPkg.registerAppLifecycleHook` | `function` | 是 | 函数引用（支持箭头函数） |
| `ToolPkg.registerMessageProcessingPlugin` | `id` | 是 | 消息处理插件唯一标识 |
| `ToolPkg.registerMessageProcessingPlugin` | `function` | 是 | 函数引用（支持箭头函数） |
| `ToolPkg.registerXmlRenderPlugin` | `id` | 是 | XML 渲染插件唯一标识 |
| `ToolPkg.registerXmlRenderPlugin` | `tag` | 是 | 目标 XML 标签名 |
| `ToolPkg.registerXmlRenderPlugin` | `function` | 是 | 函数引用（支持箭头函数） |
| `ToolPkg.registerInputMenuTogglePlugin` | `id` | 是 | 输入菜单开关插件唯一标识 |
| `ToolPkg.registerInputMenuTogglePlugin` | `function` | 是 | 函数引用（支持箭头函数） |

`ToolPkg.registerAppLifecycleHook` 支持的 `event`：

- `application_on_create`
- `application_on_foreground`
- `application_on_background`
- `application_on_low_memory`
- `application_on_trim_memory`
- `application_on_terminate`
- `activity_on_create`
- `activity_on_start`
- `activity_on_resume`
- `activity_on_pause`
- `activity_on_stop`
- `activity_on_destroy`

**Compose DSL 运行时**：
- 使用 JavaScript 编写声明式 UI
- 提供丰富的 UI 组件（Column, Row, Button, TextField 等）
- 支持状态管理和事件处理
- 可以调用工具和访问资源

#### 3.2.5 执行上下文、模块实例与 IPC

ToolPkg 运行时按执行来源分为四类上下文：

| 上下文 | 典型入口 | 用途 | 实例边界 |
|------|------|------|------|
| `main` | `manifest.main` 指向的包入口脚本 | ToolPkg 的包级逻辑：注册 hook、执行 hook、注册/处理包级 IPC、承载包级内存态 | 同一个 ToolPkg 容器共用一个包级 JS engine，内部 context key 形如 `toolpkg_main:<toolpkg_id>` |
| `ui` | `*.ui.js` 的 Compose DSL screen / action handler | 渲染界面、响应点击、读取 UI state | 每个 UI route、widget、XML render 实例有自己的 JS engine |
| `sandbox` | 独立工具脚本、子包工具脚本、调试脚本 | 执行一次性工具逻辑；可以通过 IPC 调用所属 ToolPkg 的 `main` | 按工具脚本执行链路管理 |
| `provider` | `ToolPkg.registerAiProvider(...)` 注册的 AI provider handler | 处理自定义 AI provider 的模型列表、连接测试、发消息、token 估算 | 每个 ToolPkg provider 使用独立 JS engine，内部 context key 形如 `toolpkg_provider:<toolpkg_id>:<provider_id>` |

`manifest.main` 指向 ToolPkg 的包级入口脚本，这个入口脚本运行在 `main` 上下文。`main` 适合承载需要跨 UI、子包工具共享的内存态，例如当前会话状态、后台任务句柄、缓存和 IPC handler。

`ToolPkg.ipc` 的 `meta.currentRuntime` 使用这些字面量：

- `main`：ToolPkg 包级 main 上下文。
- `ui`：Compose DSL UI 上下文。
- `sandbox`：独立工具脚本、子包工具脚本、调试脚本上下文。
- `provider`：自定义 AI provider 上下文。

按照示例工程当前的 `tsconfig` 配置，可以在 TypeScript 源码中正常使用 ES `import` / `export` 语法；同步脚本编译后会输出 CommonJS 形式，运行时按 `require` 加载模块。

直接 `import` / `require` 共享模块时，运行时会在当前执行上下文内创建模块实例。也就是说，ToolPkg main 上下文和每个 UI 上下文各自导入同一个文件，会得到各自的一份实例；模块顶层变量不会自动跨上下文共享。

这不是错误，也不是不能用。纯函数、常量、类型辅助、i18n 文案解析这类无内存态模块适合直接导入：

```javascript
const { resolveText } = require("./shared/i18n.js");
```

但带内存态的模块需要特别注意：

```javascript
// shared/counter.js
let count = 0;

exports.inc = function () {
  count += 1;
  return count;
};
```

ToolPkg main 上下文导入一次、`ui/panel/index.ui.js` 再导入一次时，`count` 是两份。UI 里点按钮增加的值，不会自动改变 ToolPkg main 那份内存态。

跨上下文共享状态或调用能力时，请显式使用 `ToolPkg.ipc`：

```javascript
// manifest.main 指向的脚本顶层
let enabled = false;

ToolPkg.ipc.on("demo.get_state", async function () {
  return { enabled };
});

ToolPkg.ipc.on("demo.set_enabled", async function (payload) {
  enabled = payload.enabled === true;
  return { enabled };
});

function registerToolPkg() {
  return true;
}

exports.registerToolPkg = registerToolPkg;
```

```javascript
// ui/panel/index.ui.js
async function loadState() {
  const state = await ToolPkg.ipc.call("demo.get_state");
  return state.enabled;
}

async function setEnabled(enabled) {
  await ToolPkg.ipc.call("demo.set_enabled", { enabled });
}
```

需要调用指定 runtime 实例时，可以传第三个参数：

```javascript
await ToolPkg.ipc.call(
  "demo.refresh_panel",
  { reason: "settings_changed" },
  { targetRuntime: "ui", targetContextKey: panelContextKey }
);
```

`ToolPkg.ipc` 的基本语义：

- `ToolPkg.ipc.on(channel, handler)` 在当前上下文注册通道处理函数。
- `ToolPkg.ipc.call(channel, payload)` 保持原语义：非 main 上下文调用同包 ToolPkg main；main 上下文调用本地 handler。
- `ToolPkg.ipc.call(channel, payload, options)` 可指定 `targetRuntime` / `targetContextKey` 调用目标 runtime 实例。
- `payload` 和返回值应使用 JSON 可序列化数据：字符串、数字、布尔值、数组、普通对象和 `null`。
- 对象会按数据复制传输，不保留引用身份、原型、方法闭包或类实例。
- 同一个上下文内调用已注册通道会直接进入本地 handler。
- 指定 `ui`、`provider`、`sandbox` 目标时需要提供明确的 `targetContextKey`；目标不存在会直接报错。

判断准则：

- 只读常量、纯函数、文案解析：直接 `import`。
- 需要共享的内存态、缓存、当前会话状态、后台任务句柄：放在 ToolPkg main 逻辑里，通过 `ToolPkg.ipc` 访问。
- UI state 只服务当前界面展示：放在 `ctx.useState` / `ctx.useMemo`。

#### 3.2.6 Resources（资源文件）

资源文件可以是任意类型的文件，如图片、压缩包、配置文件等。

```json
{
  "key": "pc_agent_zip",
  "path": "resources/pc_agent/operit-pc-agent.zip",
  "mime": "application/zip"
}
```

也支持声明目录资源：

```json
{
  "key": "pc_agent_zip",
  "path": "resources/pc_agent/operit-pc-agent",
  "mime": "inode/directory"
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `key` | string | 是 | 资源的唯一键，用于在代码中引用 |
| `path` | string | 是 | 资源文件在 ZIP 包中的路径 |
| `mime` | string | 否 | 资源的 MIME 类型 |

**访问资源**：
- 在子包脚本中：通过 PackageManager API 访问
- 在 UI 模块中：通过 `ToolPkg.readResource(key)` 访问

目录资源说明：
- 当 `mime` 是目录类型（如 `inode/directory`、`vnd.android.document/directory`）时，`ToolPkg.readResource(key)` 会先将该目录压缩成 zip，再返回这个 zip 的临时文件路径。
- 未显式传 `outputFileName` 时，目录资源默认会自动补上 `.zip` 后缀。

#### 3.2.7 Workflow Templates（工作流模板）

ToolPkg 现在可以通过 `manifest` 直接注册工作流模板。注册后，模板会出现在宿主当前的“工作流 -> 从模板新建”入口里，也会显示在包管理的详情弹窗中。

示例：

```json
{
  "workflow_templates": [
    {
      "id": "quick_chat_workflow",
      "display_name": {
        "zh": "快速对话工作流",
        "en": "Quick Chat Workflow"
      },
      "description": {
        "zh": "手动触发后自动启动聊天并发送一条引导消息。",
        "en": "Starts a chat and sends a guidance message after a manual trigger."
      },
      "resource_key": "demo_workflow_template"
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 模板唯一标识，在当前 ToolPkg 内必须唯一 |
| `display_name` | LocalizedText | 否 | 模板显示名称 |
| `description` | LocalizedText | 否 | 模板描述 |
| `resource_key` | string | 是 | 指向 `resources` 中某个文件资源 |

要求：
- `resource_key` 必须引用一个文件资源，不能是目录资源
- 文件内容必须是可被宿主反序列化的 `Workflow` JSON
- 节点建议保留 `__type`，以便和宿主当前的 `kotlinx.serialization` 结构稳定对齐

导入行为：
- 宿主导入时会重新生成工作流 `id`
- 执行统计字段会被重置
- 导入成功后会落库成正式 `Workflow`

#### 3.2.8 Workspace Templates（工作区模板）

ToolPkg 也可以通过 `manifest` 注册工作区模板。注册后，模板会出现在宿主当前的“工作区 -> 创建默认”入口里，也会显示在包管理的详情弹窗中。

示例：

```json
{
  "resources": [
    {
      "key": "demo_workspace_template",
      "path": "resources/workspaces/quick_start",
      "mime": "inode/directory"
    }
  ],
  "workspace_templates": [
    {
      "id": "quick_start_workspace",
      "display_name": {
        "zh": "快速开始工作区",
        "en": "Quick Start Workspace"
      },
      "description": {
        "zh": "包含 .operit/config.json 的最小工作区模板。",
        "en": "A minimal workspace template containing .operit/config.json."
      },
      "resource_key": "demo_workspace_template",
      "project_type": "template_try"
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 模板唯一标识，在当前 ToolPkg 内必须唯一 |
| `display_name` | LocalizedText | 否 | 模板显示名称 |
| `description` | LocalizedText | 否 | 模板描述 |
| `resource_key` | string | 是 | 指向 `resources` 中某个目录资源 |
| `project_type` | string | 否 | 传给宿主 UI 展示的项目类型标签 |

要求：
- `resource_key` 必须引用一个目录资源，常见 `mime` 可写 `inode/directory` 或 `application/x-directory`
- 目录内容里必须包含 `.operit/config.json`
- 宿主导入时会把整个目录复制到当前 chat 的 workspace 目录

建议目录结构：

```text
resources/
  workspaces/
    quick_start/
      .operit/
        config.json
      README.md
      src/
        ...
```

最小可参考示例：
- `examples/template_try/`
- 里面同时演示了 `workflow_templates`、`workspace_templates`、目录资源和最小 `main.ts`

## 4. 创建 ToolPkg

### 4.1 手动创建

**步骤 1：准备文件结构**

```bash
my_toolpkg/
├── manifest.json
├── packages/
│   └── my_tool.js
├── ui/
│   └── my_ui/
│       └── index.ui.js
└── resources/
    └── icon.png
```

**步骤 2：编写 manifest.json**

参考第 3 节的示例编写清单文件。

**步骤 3：编写子包脚本**

子包脚本必须包含 `METADATA` 块，参考 [SCRIPT_DEV_GUIDE.md](./SCRIPT_DEV_GUIDE.md)。

**步骤 4：打包成 ZIP**

使用任意 ZIP 工具将整个目录打包，并重命名为 `.toolpkg` 扩展名：

```bash
# Linux/macOS
cd my_toolpkg
zip -r ../my_toolpkg.toolpkg *

# Windows (PowerShell)
Compress-Archive -Path my_toolpkg\* -DestinationPath my_toolpkg.toolpkg
```

### 4.2 使用 Python 脚本自动打包

项目提供了 `tools/sync_example_packages.py` 脚本，可以自动将 `examples/` 目录下的包打包成 `.toolpkg` 文件。

**使用方法**：

```bash
# 打包所有白名单中的包
python tools/sync_example_packages.py

# 以“非白名单附加”的方式打包特定包
python tools/sync_example_packages.py --include windows_control

# 例如只额外同步 template_try 这个示例
python tools/sync_example_packages.py --include template_try

# 查看打包结果（不实际写入）
python tools/sync_example_packages.py --dry-run

# 删除不在白名单中的包
python tools/sync_example_packages.py --delete-extra
```

**工作原理**：
1. 扫描 `examples/` 目录
2. 查找包含 `manifest.json` 或 `manifest.hjson` 的文件夹
3. 将整个文件夹打包成 `.toolpkg` ZIP 文件
4. 输出到 `app/src/main/assets/packages/` 目录

## 5. 子包脚本开发

### 5.1 基本结构

子包脚本必须遵循标准的脚本格式，包含 `METADATA` 块：

```javascript
/* METADATA
{
    "name": "windows_control",
    "description": {
        "zh": "通过 HTTP 调用 Operit PC Agent 控制 Windows 电脑",
        "en": "Control a Windows PC through Operit PC Agent over HTTP"
    },
    "enabledByDefault": false,
    "env": [
        {
            "name": "WINDOWS_AGENT_BASE_URL",
            "description": {
                "zh": "Operit PC Agent 地址",
                "en": "Operit PC Agent URL"
            },
            "required": true
        }
    ],
    "tools": [
        {
            "name": "windows_exec",
            "description": {
                "zh": "在 Windows 上执行命令",
                "en": "Execute commands on Windows"
            },
            "parameters": [
                {
                    "name": "command",
                    "description": {
                        "zh": "要执行的命令",
                        "en": "Command to execute"
                    },
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}
*/

/// <reference path="../../types/index.d.ts" />

const WindowsControl = (function () {
    async function wrap(func, params) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error) {
            complete({ success: false, message: error.message });
        }
    }

    async function windows_exec(params) {
        const { command } = params;
        // 实现逻辑...
        return { success: true, output: "..." };
    }

    return {
        windows_exec: (params) => wrap(windows_exec, params),
    };
})();

exports.windows_exec = WindowsControl.windows_exec;
```

### 5.2 多语言支持

子包脚本的 `METADATA` 中的所有文本字段都支持多语言：

- `description`：包描述
- `tools[].description`：工具描述
- `tools[].parameters[].description`：参数描述
- `env[].description`：环境变量描述

### 5.3 环境变量

子包可以声明所需的环境变量：

```json
"env": [
    {
        "name": "API_KEY",
        "description": { "zh": "API 密钥", "en": "API Key" },
        "required": true
    },
    {
        "name": "TIMEOUT",
        "description": { "zh": "超时时间", "en": "Timeout" },
        "required": false,
        "defaultValue": "30000"
    }
]
```

### 5.4 Java / Kotlin Bridge 返回值与自动类型转换

如果子包里用到了 `Java.type(...)` / `Java.xxx.yyy` 这一套桥接，最需要记住的是：

- **桥接会把很多 Java 类型自动归一成 JS 常用结构。**

尤其是下面这些返回值，不要再按 Java 容器 API 去写：

| Java / Kotlin 返回值 | JS 侧实际使用方式 |
|------|------|
| `List` / `Set` / 其他 `Iterable` | 当普通数组用：`length`、索引、`map/filter` |
| Java 数组 / `JSONArray` | 当普通数组用 |
| `Map` / `JSONObject` | 当普通对象用 |
| `String` / `char` | 当字符串用 |
| Java 方法返回的 `CharSequence` 值 | 可按字符串用 |
| `Enum` / `Class<?>` | 当字符串用 |
| 普通 Java / Kotlin 对象 | 当 Java 实例代理用，可继续调方法 / 读写字段 |

典型误区：

```javascript
const items = someJavaApi.listSomething();

items.size(); // 不要这样写
items.get(0); // 不要这样写

items.length; // 对
items[0];     // 对
```

反过来，JS 传给 Java / Kotlin 时也会自动做一轮适配：

- JS 数组可自动转 Java 数组 / `Collection` / `JSONArray`
- plain object 可自动转 `Map` / `JSONObject`
- plain object 或 `Java.implement(...)` 结果在目标是接口时可自动转接口代理
- Java 实例代理会自动还原成原始 Java 对象

补充建议：

- 上表描述的是**Java/Kotlin 方法返回值**的归一化结果；如果你自己 `new Java.java.lang.StringBuilder()`、`new Java.java.util.ArrayList()`，拿到的仍然是 Java 实例代理。
- Java 实例代理默认推荐 `obj.method()` 语法糖；运行时会优先把实例成员按方法解释。
- `obj.call('method', ...)` 仍然可用，但主要用于极少数字段/方法同名冲突或调试场景。
- `Java.implement(...)` 的 JS 回调会回到 QuickJS 运行时线程执行，不等于把 JS 逻辑真正挪到 Java 子线程。

详细规则见：

- [README.md](../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/README.md)

## 6. UI 模块开发

### 6.1 Compose DSL 简介

Compose DSL 是一种基于 JavaScript 的声明式 UI 框架，灵感来自 Jetpack Compose。

**特点**：
- 声明式语法
- 组件化设计
- 状态管理
- 事件处理

### 6.2 基本示例

```javascript
/// <reference path="../../types/index.d.ts" />

function Screen(ctx) {
    // 状态管理
    const [url, setUrl] = ctx.useState('url', '');
    const [token, setToken] = ctx.useState('token', '');

    // 事件处理
    async function handleConnect() {
        const result = await ctx.callTool('windows_control:windows_test_connection', {
            base_url: url,
            token: token
        });

        if (result.success) {
            await ctx.showToast('连接成功！');
        } else {
            await ctx.showToast('连接失败：' + result.error);
        }
    }

    // UI 布局
    return ctx.UI.Column({ padding: 16 }, [
        ctx.UI.Text({ text: 'Windows Agent 配置', fontSize: 20, bold: true }),
        ctx.UI.Spacer({ height: 16 }),

        ctx.UI.TextField({
            value: url,
            onValueChange: setUrl,
            label: 'Agent 地址',
            placeholder: 'http://192.168.1.8:58321'
        }),
        ctx.UI.Spacer({ height: 8 }),

        ctx.UI.TextField({
            value: token,
            onValueChange: setToken,
            label: 'Token',
            placeholder: '输入 Token'
        }),
        ctx.UI.Spacer({ height: 16 }),

        ctx.UI.Button({
            text: '测试连接',
            onClick: handleConnect
        })
    ]);
}

exports.default = Screen;
```

### 6.3 可用组件

#### 布局组件
- `Column`：垂直布局
- `Row`：水平布局
- `Box`：容器
- `Spacer`：间距
- `LazyColumn`：可滚动列表

#### 基础组件
- `Text`：文本
- `TextField`：文本输入框
- `Button`：按钮
- `IconButton`：图标按钮
- `Switch`：开关
- `Checkbox`：复选框
- `Card`：卡片
- `Icon`：图标

#### 进度组件
- `LinearProgressIndicator`：线性进度条
- `CircularProgressIndicator`：圆形进度条

### 6.4 Context API

UI 模块通过 `ctx` 对象访问各种功能：

#### 状态管理
```javascript
const [value, setValue] = ctx.useState('key', initialValue);
const memoValue = ctx.useMemo('key', () => computeValue(), [deps]);
```

#### 工具调用
```javascript
const result = await ctx.callTool('package:tool_name', { param: value });
```

#### 环境变量
```javascript
const apiKey = ctx.getEnv('API_KEY');
await ctx.setEnv('API_KEY', 'new_value');
await ctx.setEnvs({ API_KEY: 'value1', TOKEN: 'value2' });
```

#### 资源访问
```javascript
const filePath = await ToolPkg.readResource('resource_key');
```

#### 包管理
```javascript
const isImported = await ctx.isPackageImported('package_name');
await ctx.importPackage('package_name');
await ctx.removePackage('package_name');
await ctx.usePackage('package_name');
const packages = await ctx.listImportedPackages();
```

#### 工具名解析
```javascript
const toolName = await ctx.resolveToolName({
    packageName: 'my_package',
    subpackageId: 'my_subpackage',
    toolName: 'my_tool',
    preferImported: true
});
```

#### UI 交互
```javascript
await ctx.showToast('消息内容');
const routes = ctx.listRoutes?.() ?? [];
const hostRoutes = ctx.getHostRoutes?.() ?? [];
await ctx.navigate('native.settings', {});
ctx.reportError(error);
```

`ctx.navigate(route, args?)` 现在会触发真实路由跳转。
`ctx.listRoutes()` 会返回当前可导航路由列表（包含 `routeId`、`runtime` 等字段）。
`ctx.getHostRoutes()` 只返回宿主 Native 路由，便于插件显式发现可用原生页面。
Native 路由 ID 命名规则：`native.<Screen对象名的snake_case>`，例如 `Screen.Toolbox -> native.toolbox`。

兼容说明：

- `ToolPkg.registerToolboxUiModule(...)` 仍然保留。
- 宿主内部会把它自动映射为：
  - 注册一个 `compose_dsl` UI route
  - 自动挂载一个 `toolbox` 导航入口
- 旧接口不会自动创建主侧边栏插件入口；若需要主侧边栏插件入口，请额外调用 `ToolPkg.registerNavigationEntry(...)` 并使用 `surface: "main_sidebar_plugins"`。

#### 其他
```javascript
const locale = getLang(); // 'zh' 或 'en'
const text = ctx.formatTemplate('Hello {name}!', { name: 'World' });
const packageName = ctx.getCurrentPackageName();
const toolPkgId = ctx.getCurrentToolPkgId();
const moduleId = ctx.getCurrentUiModuleId();
const spec = ctx.getModuleSpec();
```

## 7. 资源文件管理

### 7.1 添加资源

在 `manifest.json` 中声明资源：

```json
"resources": [
    {
        "key": "icon",
        "path": "resources/icon.png",
        "mime": "image/png"
    },
    {
        "key": "config",
        "path": "resources/config.json",
        "mime": "application/json"
    }
]
```

### 7.2 WASM 模块资源

`wasm_modules` 声明的 `.wasm` 文件不需要同时写入 `resources`。它们由宿主按模块 ID 管理，适合放企业核心算法。作者侧建议在 `src/wasm/*.ts` 写 typed facade，facade 内部调用 `ToolPkg.wasm.call(moduleId, exportName, args)`，业务入口直接导入 facade。

### 7.3 访问资源

**在 UI 模块中**：
```javascript
const iconPath = await ToolPkg.readResource('icon');
// iconPath 是资源文件在设备上的临时路径
```

如果 `icon` 对应的是目录资源，返回值会是运行时临时生成的 zip 文件路径。

**在子包脚本中**：
```javascript
// 通过 PackageManager API 访问（需要原生桥接）
```

## 8. 部署和分发

### 8.1 内置包

将 `.toolpkg` 文件放入 `app/src/main/assets/packages/` 目录，会被打包到 APK 中。

### 8.2 外部包

用户可以通过以下方式导入外部包：
1. 将 `.toolpkg` 文件复制到设备的 `Android/data/com.ai.assistance.operit/files/packages/` 目录
2. 在应用中使用"导入包"功能

### 8.3 版本管理

建议使用语义化版本号：
- `MAJOR.MINOR.PATCH`（如 `1.2.3`）
- MAJOR：不兼容的 API 变更
- MINOR：向后兼容的功能新增
- PATCH：向后兼容的问题修复

## 9. 最佳实践

### 9.1 命名规范

- **toolpkg_id**：使用反向域名格式，如 `com.operit.windows_bundle`
- **subpackage id**：使用小写字母和下划线，如 `windows_control`
- **resource key**：使用小写字母和下划线，如 `pc_agent_zip`
- **ui_module id**：使用小写字母和下划线，如 `windows_setup`

### 9.2 文件组织

```
my_toolpkg/
├── manifest.json              # 清单文件
├── packages/                  # 子包目录
│   ├── tool1.js
│   └── tool2.js
├── ui/                        # UI 模块目录
│   ├── setup/
│   │   └── index.ui.js
│   └── dashboard/
│       └── index.ui.js
├── resources/                 # 资源目录
│   ├── images/
│   │   └── icon.png
│   └── data/
│       └── config.json
└── i18n/                      # 国际化目录（可选）
    ├── zh-CN.js
    └── en-US.js
```

### 9.3 多语言支持

- 所有面向用户的文本都应提供多语言版本
- 至少提供中文（`zh`）和英文（`en`）
- 使用 `default` 键作为默认值

### 9.4 资源优化

- 压缩图片和其他资源文件
- 避免包含不必要的文件
- 使用合适的 MIME 类型

### 9.5 错误处理

- 在子包脚本中使用 `try-catch` 捕获错误
- 在 UI 模块中使用 `ctx.reportError()` 报告错误
- 提供清晰的错误消息

### 9.6 测试

- 在打包前测试所有子包脚本
- 测试 UI 模块的各种交互场景
- 验证资源文件可以正确访问
- 测试多语言切换

## 10. 故障排查

### 10.1 常见问题

**问题 1：包无法导入**
- 检查 `manifest.json` 格式是否正确
- 确认 `toolpkg_id` 是否唯一
- 验证 ZIP 文件结构是否正确

**问题 2：子包无法加载**
- 检查 `entry` 路径是否正确
- 确认脚本文件包含有效的 `METADATA`
- 查看应用日志获取详细错误信息

**问题 3：资源无法访问**
- 检查资源 `key` 是否正确
- 确认资源 `path` 在 ZIP 中存在
- 验证资源文件没有损坏

**问题 4：UI 模块不显示**
- 检查 `main.js` 是否导出 `registerToolPkg`
- 检查是否调用了 `ToolPkg.registerToolboxUiModule(...)`
- 确认 `runtime` 类型正确
- 确认 `screen` 传的是已导入的模块函数（例如 `const ui = require(...).default`）
- 验证 UI 脚本语法正确

### 10.2 调试技巧

1. **使用 dry-run 模式**：
   ```bash
   python tools/sync_example_packages.py --dry-run
   ```

2. **查看应用日志**：
   ```bash
   adb logcat -s PackageManager:* JsEngine:*
   ```

3. **手动解压检查**：
   ```bash
   unzip -l my_toolpkg.toolpkg
   ```

4. **验证 JSON 格式**：
   使用在线 JSON 验证工具检查 `manifest.json`

### 10.3 使用调试安装脚本快速烧录到手机

普通 `.js` 包可以直接用 `tools/execute_js.bat` / `tools/execute_js.sh` 临时推送后单次执行；但 `toolpkg` 不适合这样调试。

原因是 `toolpkg` 不只是“跑一个函数”，它还涉及：

- 读取 `manifest.json` / `manifest.hjson`
- 解析 `toolpkg_id`
- 加载 `main` 脚本里的注册逻辑
- 同步 UI 模块、消息处理插件、Prompt Hook、Tool Lifecycle Hook 等宿主级注册
- 刷新 ToolPkg cache 与运行时 hook 映射

因此，`toolpkg` 调试的正确思路不是“一次运行”，而是“快速重新安装”。

项目现在提供了专门的调试安装脚本：

- Windows：`tools/debug_toolpkg.bat`
- Linux/macOS：`tools/debug_toolpkg.sh`
- 共享实现：`tools/debug_toolpkg.py`

它们会执行以下流程：

1. 从 ToolPkg 目录或现成 `.toolpkg` 中读取 `manifest`
2. 解析 `toolpkg_id` 与 `main`
3. 如果输入是目录，则先临时打包成 `.toolpkg`
4. 通过 `adb push` 将包推送到手机的 `Android/data/com.ai.assistance.operit/files/packages/`
5. 发送调试广播，让 App 重新扫描外部 packages 目录
6. 按 `toolpkg_id` 启用该 ToolPkg 容器
7. 按 manifest 默认值重新应用 subpackage 启用状态（可选关闭）
8. 刷新 ToolPkg cache、hook/runtime 映射，并尝试重新激活先前已注册过的 subpackage 工具

这条链路更接近真实安装行为，适合调试：

- `ToolPkg.registerToolboxUiModule(...)`
- `ToolPkg.registerMessageProcessingPlugin(...)`
- `ToolPkg.registerXmlRenderPlugin(...)`
- `ToolPkg.registerInputMenuTogglePlugin(...)`
- `ToolPkg.registerToolLifecycleHook(...)`
- Prompt 相关 hook

#### 10.3.1 用法

直接传 ToolPkg 目录：

```bash
python tools/debug_toolpkg.py examples/windows_control
```

也可以传 `manifest.json`：

```bash
python tools/debug_toolpkg.py examples/windows_control/manifest.json
```

或者传现成 `.toolpkg`：

```bash
python tools/debug_toolpkg.py /path/to/windows_control.toolpkg
```

Windows 下可直接使用：

```bat
tools\debug_toolpkg.bat examples\windows_control
tools\debug_toolpkg.bat examples\windows_control\manifest.json
tools\debug_toolpkg.bat D:\tmp\windows_control.toolpkg --device emulator-5554
```

Linux/macOS 下可直接使用：

```bash
bash tools/debug_toolpkg.sh examples/windows_control
bash tools/debug_toolpkg.sh examples/windows_control/manifest.json
```

#### 10.3.2 常用参数

- `--device <serial>`：指定 adb 设备；不传时，若只连了一台设备则自动选中
- `--no-reset-subpackage-states`：保留本机已有的 subpackage 开关状态，而不是按 manifest 默认值重置
- `--log-wait-seconds <n>`：发送广播后等待多少秒再抓取日志；默认读取 `OPERIT_LOG_WAIT_SECONDS`，否则为 `6`

#### 10.3.3 日志查看

脚本默认会抓取这些日志标签：

```bash
adb logcat -d -s ToolPkgDebugInstallReceiver:* ToolPkg:* PackageManager:*
```

如果你怀疑是 JS 执行期问题，也可以再看：

```bash
adb logcat -d -s JsEngine:* ToolPkg:* PackageManager:*
```

#### 10.3.4 注意事项

- 这个脚本依赖手机上的 Operit 已包含 `ToolPkgDebugInstallReceiver` 调试广播入口；如果手机装的是旧版本 App，广播不会生效。
- 脚本会根据 `toolpkg_id` 处理同名外部 ToolPkg 的覆盖安装；调试时应保持 `toolpkg_id` 稳定，不要频繁改名。
- 如果你调试的是 hook 行为，优先使用这套安装脚本，不要试图把 `toolpkg` 当普通 `.js` 包去跑。

## 11. 示例项目

### 11.1 Windows Control Bundle

完整示例位于 `examples/windows_control/`：

```
windows_control/
├── manifest.json
├── packages/
│   └── windows_control.js
├── ui/
│   └── windows_setup/
│       └── index.ui.js
├── resources/
│   └── pc_agent/
│       └── operit-pc-agent/
└── i18n/
    ├── zh-CN.js
    └── en-US.js
```

**功能**：
- 通过 HTTP 控制 Windows 电脑
- 提供一键配置 UI
- 包含 PC Agent 安装包资源
- 支持中英文双语

### 11.2 打包命令

```bash
# 打包 windows_control
python tools/sync_example_packages.py --include windows_control

# 查看打包结果
ls -lh app/src/main/assets/packages/windows_control.toolpkg
```

## 12. 参考资料

- [脚本开发指南](./SCRIPT_DEV_GUIDE.md)：了解如何编写子包脚本
- [PackageManager.kt](../app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManager.kt)：包管理器源码
- [ToolPkgParser.kt](../app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt)：解析器源码
- [JsComposeDslBridge.kt](../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsComposeDslBridge.kt)：Compose DSL 桥接

## 13. 更新日志

### v1.0.0 (2024-02-14)
- 初始版本
- 支持子包、UI 模块、资源文件
- 支持多语言
- 提供 Compose DSL UI 框架
