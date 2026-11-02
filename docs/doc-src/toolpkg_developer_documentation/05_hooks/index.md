---
title: ToolPkg Hook 参考
status: complete
---

# ToolPkg Hook 参考

ToolPkg Hook 并非一种统一的“监听器”。不同 Hook 的触发阶段、返回解释、调度方式和失败影响不同；开发时先按注册方法进入对应页面，不要跨 Hook 复用返回逻辑。

## Hook 类别

### 消息接管与渲染

- [消息处理插件](./message_processing.md)：先用 `probeOnly` 判断是否接管，再由第一个命中插件产生流式回复；支持取消。
- [XML 渲染与输入菜单开关](./render_and_toggle.md)：按 tag 顺序尝试 XML 返回，并描述 toggle create/toggle 生命周期与缓存。

### 聊天交互与状态

- [聊天输入](./chat_input.md)：区分异步通知和提交前决策，说明 allow/block/replace/consume、链内文本累积和总超时。
- [生命周期与聊天](./lifecycle_and_chat.md)：应用生命周期、导航动作、聊天视图、消息持久化、消息菜单和聊天运行态。

### Prompt 与模型

- [Prompt 与摘要流水线](./prompt_pipeline.md)：输入、历史、系统/工具提示词、finalize、估算路径和摘要生成阶段。
- [ToolPkg AI Provider](./ai_provider.md)：自定义模型注册、四种 provider 回调、stream、token usage、错误和取消。
- [工具调用生命周期](./tool_lifecycle.md)：请求通知、同步前置拦截、权限与执行结果事件。

## 通用事件封套

Hook 回调接收 `HookEventBase<TEventName, TPayload>`：

- `event`、`eventName`：事件/阶段名称。
- `eventPayload`：事件专属的 JSON 对象。
- `toolPkgId?`、`containerPackageName?`：ToolPkg 标识。
- `functionName?`、`pluginId?`、`hookId?`：执行函数和注册项标识。
- `timestampMs?`：外层派发时间戳；与消息 payload 中名为 `timestamp` 的字段不是同一字段。

所有 payload 字段必须按具体事件判断。声明为可选的字段可能缺失；事件类型相同但阶段不同的 Prompt payload 也可能只填充部分字段。

## 调度类别

- **决策链：** 聊天输入提交 Hook、工具调用 `tool_call_intercept`。宿主等待返回并可能因结果改变流程。
- **串行 mutation 链：** Prompt/摘要 Hook。后一个回调接收前一个回调应用后的上下文。
- **首个命中链：** 消息处理和 XML 渲染。宿主按顺序找到首个有效接管/渲染结果后停止尝试后续候选。
- **通知链：** 生命周期、聊天视图、消息持久化、聊天运行态及多数工具事件。返回值通常被忽略；失败写日志后继续。
- **查询/切换：** 输入菜单 Hook 的 `create` 产生定义，`toggle` 通知点击；定义按 runtime/chatId 缓存。

## 排序与并发

多数 ToolPkg Hook 列表按启用容器的依赖/加载顺序排序，同包内再按包名和注册 ID 排序。该顺序只描述同一注册列表；不同 Hook 家族由各自宿主调用点触发，不构成全局串行顺序。

需要交互决策的 Hook 会串行等待。通知型 Hook 有的排入 IO 队列，有的通过 scope 异步启动；不要假设所有通知都在发出事件的调用栈内完成。

## 失败与超时

- 通知型 Hook 执行失败通常记日志并继续其他 Hook。
- 消息处理探测失败视为未命中；命中后的正式执行失败会结束该执行，不回退到后续插件。
- `tool_call_intercept` 执行/解码失败会阻止工具调用；详见[工具生命周期](./tool_lifecycle.md)。
- 聊天输入、Prompt 和摘要 Hook 使用配置的共享分发预算，范围 1 至 60 秒、默认 10 秒；`message_processing` 不使用该预算。
- 超时 Hook 的返回值被丢弃。预算耗尽会停止当前 Hook 列表剩余调用，不代表对其他 Hook 家族设置了同一个全局 deadline。

## 版本与类型差异

- ToolPkg API `1.0.0` 是当前基线。
- 消息长按菜单和聊天运行态 Hook 从 ToolPkg API `1.0.1` 开始，需要 Operit `1.12.1+4`。
- `ToolLifecycleEventName` 已声明 `tool_call_intercept`；运行时仍在权限检查前同步派发该事件，阻断语义见[工具调用生命周期](./tool_lifecycle.md)。
- 各个版本化方法和数据类型应以其页面的逐项注记为准，集中策略见[API 版本与兼容](../09_compatibility/api_versions.md)。

## 权威来源

- `examples/types/toolpkg.d.ts`
- `app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/`
- 各 Hook 家族的宿主调用点见对应页面“实现依据”。
