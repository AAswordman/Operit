# API 文档：`toolpkg.d.ts`

`toolpkg.d.ts` 描述的是工具包插件注册系统。它的核心目标不是“调用工具”，而是**向宿主注册模块、钩子和插件**，让一个 tool package 可以在应用生命周期、消息处理、XML 渲染、输入菜单和提示词流水线中插入自己的行为。

## 作用

当前类型定义覆盖：

- 工具箱 UI 模块注册。
- 应用生命周期钩子。
- 消息处理插件。
- XML 渲染插件。
- 输入菜单开关插件。
- AI 聊天输入框监听和提交 Hook。
- 聊天消息持久化通知 Hook。
- 工具执行生命周期钩子。
- Prompt 输入、历史、系统提示词、工具提示词、最终发送前的各类钩子。
- 摘要生成阶段的各类钩子。

## 类型命名空间与运行时对象

`toolpkg.d.ts` 里同时存在两个层面的 `ToolPkg`：

- `namespace ToolPkg`：承载类型定义。
- `const ToolPkg: ToolPkg.Registry`：全局运行时注册对象。

因此脚本里常见的实际写法是：

```ts
ToolPkg.registerAppLifecycleHook(...)
ToolPkg.registerMessageProcessingPlugin(...)
```

## 前置 Hook 超时门禁

宿主在“显示与行为”中提供“前置插件 Hook 超时”设置，取值范围为 1 至 60 秒，默认值为 10 秒。

该限制覆盖同步执行的聊天输入、Prompt 与摘要生成 Hook。一次 bridge 分发内的多个 Hook 共用总等待时间，不会为每个 Hook 重新计算完整超时。

达到截止时间时，宿主会中断当前 QuickJS 执行并忽略该 Hook 的返回值，随后使用此前已累积的上下文继续消息处理。开发者应确保 Hook 能尽快完成，并且不应依赖超时后的副作用。

`message_processing` 的回复接管链保持独立，不使用该设置。

用户通过聊天输入框提交消息时，聊天输入 Hook 的超时会显示一条聊天内提示，说明该 Hook 已被跳过且消息会继续发送。消息注入使用的 Prompt 输入 Hook 超时会复用 AI 请求重试的非致命错误事件，并由聊天/悬浮窗 Toast 显示；其他 Prompt 与摘要 Hook 超时仍仅写入日志。

此外，全局还声明了一组辅助函数：

- `registerToolPkgToolboxUiModule(...)`
- `registerToolPkgAppLifecycleHook(...)`
- `registerToolPkgMessageProcessingPlugin(...)`
- `registerToolPkgXmlRenderPlugin(...)`
- `registerToolPkgInputMenuTogglePlugin(...)`
- `registerToolPkgChatInputHook(...)`
- `registerToolPkgChatMessageHook(...)`
- `registerToolPkgToolLifecycleHook(...)`
- `registerToolPkgPromptInputHook(...)`
- `registerToolPkgPromptHistoryHook(...)`
- `registerToolPkgPromptEstimateHistoryHook(...)`
- `registerToolPkgSystemPromptComposeHook(...)`
- `registerToolPkgToolPromptComposeHook(...)`
- `registerToolPkgPromptFinalizeHook(...)`
- `registerToolPkgPromptEstimateFinalizeHook(...)`
- `registerToolPkgSummaryGenerateHook(...)`

## 基础类型

### `ToolPkg.LocalizedText`

```ts
type LocalizedText = string | { [lang: string]: string }
```

适合标题、描述等多语言文本。

### `ToolPkg.JsonPrimitive` / `ToolPkg.JsonValue` / `ToolPkg.JsonObject`

这一组类型用于约束所有插件返回值和事件载荷的 JSON 结构。

## 事件分类

### 应用生命周期事件：`AppLifecycleEvent`

支持：

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

### 通用事件名：`HookEventName`

这是全部 hook 事件的联合类型，除生命周期外还包括：

- `message_processing`
- `xml_render`
- `input_menu_toggle`
- 聊天输入框事件
- 聊天消息持久化事件
- 工具生命周期事件
- Prompt 输入 / 历史 / 系统提示词 / 工具提示词 / 最终发送事件
- 摘要生成事件

### Prompt 轮次类型：`PromptTurnKind` / `PromptTurn`

Prompt 相关 hook 和 `message_processing` 插件里的历史消息，统一使用结构化的 `PromptTurn`：

```ts
type PromptTurnKind =
  | 'SYSTEM'
  | 'USER'
  | 'ASSISTANT'
  | 'TOOL_CALL'
  | 'TOOL_RESULT'
  | 'SUMMARY'

interface PromptTurn {
  kind: PromptTurnKind
  content: string
  toolName?: string
  metadata?: JsonObject
}
```

注意：

- 这里不再使用旧的 `{ role, content }` 结构。
- `message_processing` 插件收到的 `chatHistory` 也是 `PromptTurn[]`。
- 如果你要复用旧的 role 语义，需要自己把 `kind` 映射成对应角色。

### 工具生命周期事件：`ToolLifecycleEventName`

- `tool_call_requested`
- `tool_permission_checked`
- `tool_execution_started`
- `tool_execution_result`
- `tool_execution_error`
- `tool_execution_finished`

### Prompt 流水线事件

#### `PromptInputEventName`

- `before_process`
- `after_process`

#### `PromptHistoryEventName`

- `before_prepare_history`
- `after_prepare_history`

#### `SystemPromptComposeEventName`

- `before_compose_system_prompt`
- `compose_system_prompt_sections`
- `after_compose_system_prompt`

#### `ToolPromptComposeEventName`

- `before_compose_tool_prompt`
- `filter_tool_prompt_items`
- `after_compose_tool_prompt`

#### `PromptFinalizeEventName`

- `before_finalize_prompt`
- `before_send_to_model`

#### `SummaryGenerateEventName`

- `before_prepare_summary_prompt`
- `before_send_to_model`
- `after_generate_summary`

## 事件对象

所有 hook 事件都继承自：

### `HookEventBase<TEventName, TPayload>`

公共字段包括：

- `event`
- `eventName`
- `eventPayload`
- `toolPkgId?`
- `containerPackageName?`
- `functionName?`
- `pluginId?`
- `hookId?`
- `timestampMs?`

## 各类 payload

### `MessageProcessingEventPayload`

字段包括：

- `messageContent?`
- `chatHistory?: PromptTurn[]`
- `workspacePath?`
- `maxTokens?`
- `tokenUsageThreshold?`
- `probeOnly?`
- `executionId?`

### `XmlRenderEventPayload`

字段包括：

- `xmlContent?`
- `tagName?`

### `InputMenuToggleEventPayload`

字段包括：

- `action?: 'create' | 'toggle' | string`
- `toggleId?`

### `ChatInputEventPayload`

字段包括：

- `chatId?`
- `text?`
- `selectionStart?`
- `selectionEnd?`
- `hasAttachments?`
- `attachmentCount?`
- `isProcessing?`
- `inputStyle?`
- `source?`
- `submitSource?`

聊天输入框事件名包括：

- `input_changed`
- `submit_requested`
- `submitted`

### `ChatMessageEventPayload`

字段包括：

- `chatId`
- `timestamp`
- `sender`
- `roleName`
- `content`
- `completedAt`
- `provider`
- `modelName`
- `inputTokens`
- `outputTokens`
- `cachedInputTokens`
- `sentAt`
- `outputDurationMs`
- `waitDurationMs`
- `displayMode`
- `selectedVariantIndex`
- `isFavorite`

聊天消息事件名包括：

- `message_persisted`

说明：

- `timestamp` 对应 `ChatMessage.timestamp` / `MessageEntity.timestamp`，也是当前工程用于定位消息的稳定字段。
- `timestampMs` 是 hook 外层事件的派发时间，和 `eventPayload.timestamp` 不是同一个含义。
- `sender` 对应 `ChatMessage.sender` / `MessageEntity.sender`，表示落库消息来源，例如 `user`、`ai`、`summary`。
- `roleName` 对应 `ChatMessage.roleName` / `MessageEntity.roleName`，表示消息展示或角色卡名称，不表示消息来源。
- `message_persisted` 是通知事件，返回值不会改变已持久化的消息。

### `ToolLifecycleEventPayload`

字段包括：

- `toolName`
- `parameters?`
- `description?`
- `granted?`
- `reason?`
- `success?`
- `errorMessage?`
- `resultText?`
- `resultJson?`

### `PromptHookEventPayload`

字段包括：

- `stage?`
- `functionType?`
- `promptFunctionType?`
- `useEnglish?`
- `rawInput?`
- `processedInput?`
- `chatHistory?: PromptTurn[]`
- `preparedHistory?: PromptTurn[]`
- `systemPrompt?`
- `toolPrompt?`
- `modelParameters?`
- `availableTools?`
- `metadata?`

### `SummaryGenerateEventPayload`

字段包括：

- `stage?`
- `functionType?`
- `useEnglish?`
- `previousSummary?`
- `chatHistory?: PromptTurn[]`
- `preparedHistory?: PromptTurn[]`
- `systemPrompt?`
- `summaryPrompt?`
- `summaryResult?`
- `modelParameters?`
- `metadata?`

## 返回值类型

### 消息处理插件返回：`MessageProcessingHookReturn`

允许返回：

- `boolean`
- `string`
- `MessageProcessingHookObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中 `MessageProcessingHookObjectResult` 可包含：

- `matched?`
- `text?`
- `content?`
- `chunks?`

### XML 渲染插件返回：`XmlRenderHookReturn`

允许返回：

- `string`
- `XmlRenderHookObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中 `XmlRenderHookObjectResult` 可包含：

- `handled?`
- `text?`
- `content?`
- `composeDsl?`

`composeDsl` 结构里可以返回：

- `screen: ComposeDslScreen`
- `state?`
- `memo?`
- `moduleSpec?`

### 输入菜单开关返回：`InputMenuToggleHookReturn`

允许返回：

- `InputMenuToggleDefinitionResult[]`
- `InputMenuToggleObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中单个开关定义包含：

- `id`
- `title`
- `description?`
- `isChecked?`

### 聊天输入框返回：`ChatInputHookReturn`

`input_changed`、`submitted` 的返回值会被忽略。

`submit_requested` 支持返回：

- `null`
- `void`
- `string`：表示替换本次提交文本
- `{ action: 'allow' }`
- `{ action: 'block', message?: string }`
- `{ action: 'replace', text: string }`
- `{ action: 'consume', message?: string, clearInput?: boolean }`
- 或对应的 `Promise`

### 聊天消息持久化返回

`message_persisted` 的返回值会被忽略。

### Prompt 相关返回

- `PromptInputHookReturn`
- `PromptHistoryHookReturn`
- `SystemPromptComposeHookReturn`
- `ToolPromptComposeHookReturn`
- `PromptFinalizeHookReturn`
- `SummaryGenerateHookReturn`

这几类返回允许在字符串、消息数组、结构化对象与空返回之间切换，具体以类型定义为准。
其中：

- `PromptHistoryHookReturn` 里的数组元素类型是 `PromptTurn`
- `PromptFinalizeHookReturn` 里的数组元素类型也是 `PromptTurn`
- 估算阶段的 `PromptEstimateHistoryHook` / `PromptEstimateFinalizeHook` 复用相同的 payload 和返回结构
- `SummaryGenerateHookReturn` 可以返回字符串或结构化对象；字符串在摘要生成前阶段会被当作 `summaryPrompt`，在 `after_generate_summary` 阶段会被当作 `summaryResult`

## 注册定义对象

### `ToolboxUiModuleRegistration`

字段：

- `id`
- `runtime?`
- `screen: ComposeDslScreen`
- `params?`
- `title?`

### `AppLifecycleHookRegistration`

字段：

- `id`
- `event`
- `function`

### `MessageProcessingPluginRegistration`

字段：

- `id`
- `function`

### `XmlRenderPluginRegistration`

字段：

- `id`
- `tag`
- `function`

### `InputMenuTogglePluginRegistration`

字段：

- `id`
- `function`

### `ChatInputHookRegistration`

字段：

- `id`
- `function`

### `ChatMessageHookRegistration`

字段：

- `id`
- `function`

### 其余注册对象

以下注册对象结构都很简单，字段都是：`id` + `function`：

- `ToolLifecycleHookRegistration`
- `PromptInputHookRegistration`
- `PromptHistoryHookRegistration`
- `PromptEstimateHistoryHookRegistration`
- `SystemPromptComposeHookRegistration`
- `ToolPromptComposeHookRegistration`
- `PromptFinalizeHookRegistration`
- `PromptEstimateFinalizeHookRegistration`
- `SummaryGenerateHookRegistration`

## `ToolPkg.Registry`

运行时 `ToolPkg` 对象实现了这个接口，提供以下方法：

- `registerToolboxUiModule(definition)`
- `registerUiRoute(definition)`
- `registerNavigationEntry(definition)`
- `registerDesktopWidget(definition)`
- `registerAppLifecycleHook(definition)`
- `registerMessageProcessingPlugin(definition)`
- `registerXmlRenderPlugin(definition)`
- `registerInputMenuTogglePlugin(definition)`
- `registerChatInputHook(definition)`
- `registerChatMessageHook(definition)`
- `registerToolLifecycleHook(definition)`
- `registerPromptInputHook(definition)`
- `registerPromptHistoryHook(definition)`
- `registerPromptEstimateHistoryHook(definition)`
- `registerSystemPromptComposeHook(definition)`
- `registerToolPromptComposeHook(definition)`
- `registerPromptFinalizeHook(definition)`
- `registerPromptEstimateFinalizeHook(definition)`
- `registerSummaryGenerateHook(definition)`
- `readResource(key, outputFileName?)`

### `ToolPkg.readResource(...)`

把当前 toolpkg `manifest.resources` 里声明的资源按 `key` 释放到宿主临时目录，并返回落盘后的绝对路径。

```ts
const jarPath = await ToolPkg.readResource('apktool_lib_jar', 'apktool-lib.jar');
```

说明：

- 这个方法不依赖 `compose_dsl` 的 `ctx`，普通子包工具函数、主入口 hook、UI 模块都可以直接调用。
- `key` 对应 `manifest.json` 里的 `resources[].key`。
- `outputFileName` 可选；不传时会使用清单资源原始文件名。
- 如果资源 `mime` 是目录类型（例如 `inode/directory`、`vnd.android.document/directory`），运行时会先把该目录压成 zip，再返回这个 zip 文件的绝对路径；默认文件名会自动补 `.zip`。
- `registerToolPkg()` 执行期间不可调用；调用会立即抛出异常。

## AssemblyScript WASM 模块

企业插件可以在 `manifest.json` 中声明 AssemblyScript 编译得到的 `.wasm` 核心模块：

```json
{
  "wasm_modules": [
    {
      "id": "core",
      "path": "modules/core.wasm",
      "exports": ["isPrime", "nthPrime"],
      "source_language": "assemblyscript",
      "abi": "assemblyscript"
    }
  ]
}
```

建议结构：

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

当前宿主会解析和校验 `wasm_modules`。插件的对外入口仍然是 JS `exports` 和 `ToolPkg.register...` 系列 API；作者入口建议写 `src/main.ts`，构建时生成宿主执行用的 `main.js`。`ToolPkg.wasm.call(...)` 不可在 `registerToolPkg()` 执行期间调用，调用会立即抛出异常。

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

当前 ABI 支持 `i32`、`i64`、`f32`、`f64`。`i64` 结果以字符串返回；传入 `i64` 时推荐使用字符串，避免 JS number 精度损失。

## 示例

### 注册工具箱 UI 模块

```ts
import toolboxUI from './index.ui.js';

ToolPkg.registerToolboxUiModule({
  id: 'demo_toolbox',
  runtime: 'compose_dsl',
  screen: toolboxUI,
  params: {},
  title: {
    zh: '示例模块',
    en: 'Demo Module'
  }
});
```

### 注册桌面小组件

```ts
ToolPkg.registerDesktopWidget({
  id: 'demo_widget',
  route: 'toolpkg:com.example.demo:ui:dashboard',
  render: 'toolpkg:com.example.demo:ui:dashboard_widget',
  title: {
    zh: '示例小组件',
    en: 'Demo Widget'
  },
  subtitle: {
    zh: '点击直接打开面板',
    en: 'Tap to open dashboard'
  },
  description: {
    zh: '用于桌面添加时的说明',
    en: 'Shown in widget picker'
  }
});
```

说明：

- `route` / `routeId` 必须指向已经注册的 UI route。
- `render` / `renderRouteId` 用于指定小组件本体渲染所使用的 UI route；不填时默认等于 `route`。
- 当前宿主使用一个通用桌面小组件承载多个 ToolPkg widget；用户在添加到桌面时，会先进入配置页选择具体条目。
- 当前点击行为是打开对应 route。

### 注册应用生命周期钩子

```ts
ToolPkg.registerAppLifecycleHook({
  id: 'demo_app_create',
  event: 'application_on_create',
  function(event) {
    console.log(JSON.stringify(event.eventPayload ?? {}));
    return { ok: true };
  }
});
```

### 注册消息处理插件

```ts
ToolPkg.registerMessageProcessingPlugin({
  id: 'demo_message_plugin',
  async function(event) {
    const message = String(event.eventPayload?.messageContent ?? '').trim();
    if (!message.startsWith('/demo')) {
      return { matched: false };
    }
    return {
      matched: true,
      text: '已命中 demo 插件'
    };
  }
});
```

### 注册 XML 渲染插件

```ts
ToolPkg.registerXmlRenderPlugin({
  id: 'demo_xml',
  tag: 'demo',
  function(event) {
    const xml = String(event.eventPayload?.xmlContent ?? '');
    if (!xml) {
      return { handled: false };
    }
    return {
      handled: true,
      text: 'XML 已处理'
    };
  }
});
```

### 注册输入菜单开关插件

```ts
ToolPkg.registerInputMenuTogglePlugin({
  id: 'demo_toggle',
  function(event) {
    if (event.eventPayload?.action === 'create') {
      return [
        {
          id: 'demo_feature',
          title: 'Demo Feature',
          description: '示例开关',
          isChecked: true
        }
      ];
    }
    return [];
  }
});
```

### 注册聊天输入框 Hook

```ts
ToolPkg.registerChatInputHook({
  id: 'demo_chat_input',
  function(event) {
    if (event.eventName === 'input_changed') {
      console.log('draft:', event.eventPayload.text);
      return;
    }

    if (event.eventName === 'submit_requested') {
      const text = event.eventPayload.text || '';
      if (text.includes('/blocked')) {
        return {
          action: 'block',
          message: '这条消息被插件阻止发送'
        };
      }
      if (text.startsWith('/upper ')) {
        return {
          action: 'replace',
          text: text.slice('/upper '.length).toUpperCase()
        };
      }
    }
  }
});
```

### 注册聊天消息持久化 Hook

```ts
ToolPkg.registerChatMessageHook({
  id: 'demo_chat_message_sync',
  function(event) {
    if (event.eventName !== 'message_persisted') {
      return;
    }

    const message = event.eventPayload;
    const key = `${message.chatId}:${message.timestamp}`;
    console.log('persisted message:', {
      key,
      sender: message.sender,
      roleName: message.roleName,
      completedAt: message.completedAt,
      length: message.content.length
    });
  }
});
```

### 注册摘要生成 Hook

```ts
ToolPkg.registerSummaryGenerateHook({
  id: 'demo_summary_hook',
  function(event) {
    if (event.eventName === 'before_prepare_summary_prompt') {
      return {
        summaryPrompt: '请重点总结最近的工程决策、已完成事项和下一步待办。'
      };
    }

    if (event.eventName === 'after_generate_summary') {
      return {
        summaryResult: String(event.eventPayload?.summaryResult ?? '').trim()
      };
    }

    return null;
  }
});
```

## 关于 `registerToolPkg()` 入口

从 `examples/linux_ssh/src/main.ts` 与 `examples/deepsearching/src/plugin/deep-search-plugin.ts` 可以看出，工具包通常会在入口文件中导出一个 `registerToolPkg()` 函数，并在里面集中调用上述注册方法。

这是一种**从仓库示例总结出的约定**；它不是 `toolpkg.d.ts` 本身直接声明的函数签名。

包管理器会在独立的临时 QuickJS 引擎中执行每个工具包的 `registerToolPkg()`，单包最长执行 12 秒。该入口只应用于声明注册项，不应启动常驻定时器、无限循环或等待长期任务。`ToolPkg.readResource(...)` 与 `ToolPkg.wasm.call(...)` 在此阶段会立即抛出异常。注册结束后临时引擎会被销毁，工具调用与 UI hook 在各自的运行时 context 中执行，因此不要依赖注册阶段留下的 JavaScript 全局状态。

## 开发调试安装

`toolpkg.d.ts` 这里描述的是注册 API，本身不负责“如何调试安装到手机”。

如果你在开发 ToolPkg，需要注意：

- 普通 `.js` 包可以用 `tools/adb/execute_js.bat` / `tools/adb/execute_js.sh` 做单次执行调试
- `toolpkg` 不适合这样调试，因为它涉及 `manifest`、`main` 注册、ToolPkg cache、以及多类 hook/runtime 的重新同步
- 调试 ToolPkg 时，应使用 `tools/toolpkg/debug_toolpkg.bat` / `tools/toolpkg/debug_toolpkg.sh` / `tools/toolpkg/debug_toolpkg.py`

完整的打包、烧录、启用、刷新 hook/runtime 的工作流说明，见 [TOOLPKG_FORMAT_GUIDE.md](../../TOOLPKG_FORMAT_GUIDE.md) 中的“10.3 使用调试安装脚本快速烧录到手机”。

## 相关文件

- `examples/types/toolpkg.d.ts`
- `examples/types/compose-dsl.d.ts`
- `docs/doc-src/package-dev/core.md`
- `docs/TOOLPKG_FORMAT_GUIDE.md`
