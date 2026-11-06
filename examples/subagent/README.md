# 被动式子代理示例

`subagent_run` 执行一个明确的委托任务。包版本 0.1.2 声明 ToolPkg API 1.2.0，需要包含子任务执行预算、独立会话范围和结构化结果回调的配套宿主构建。旧宿主会在加载前报告不支持该 API 版本；仅凭应用版本名 1.12.2 不能判断是否包含本次修复。工具名、参数和历史 XML 消息结构保持兼容。

## 输入

- `task` 必须是非空白字符串。无效任务在模型配置读取、请求和进度通知之前失败。
- `context_text` 提供补充上下文和限制。
- `target_paths_json` 可省略；提供时必须是 JSON 数组，每项均为非空路径字符串。
- `max_tool_calls` 可省略，默认 8；提供时必须是 1 至 64 的整数。宿主执行层实际限制调用次数，不能用小数、超大数或其他类型绕过限制。

每次入口在第一次 `await` 前调用一次 `getToolExecutionContext()`，捕获父会话、角色卡、语言、本次调用绑定的 Java facade 与进度发送函数。后续异步处理不会重新读取共享的当前调用身份，原生服务、stream 和其返回代理由本次调用的 Java facade 创建；停止或脚本超时对应同一次原生执行。

捕获的 `chatId` 和 `callerCardId` 保留父身份，供宿主继续执行既有权限判断。工具会话隔离使用宿主的 `toolSessionScopeId`，不通过修改父身份实现。示例的证据归档写入不计入子任务模型的工具预算。

## 状态与协议

子任务最后一轮模型输出必须是完整 JSON 对象，也可使用完整的 `json` 代码围栏：

```json
{"status":"completed","summary":"完成内容与实际结果","evidence_ids":["宿主给出的实际证据 ID"]}
```

状态仅有 `completed`、`blocked`、`failed`。不调用工具的算术和文本任务可使用空 `evidence_ids`。使用工具的完成声明至少要引用一个本次运行的成功工具结果。无关运行的 ID、普通摘要、残缺 JSON 或残缺内部标签都不能成为成功结果。JSON 字符串中的字面标签保持原样，输出 XML 时统一转义。

宿主提供的预算耗尽、委托拒绝、权限拒绝、token 限制、取消和执行异常，优先决定最终状态。模型声明不能覆盖这些事件。即使模型声称完成，只要真实账本仍有失败记录，结果就是 `blocked`、原因为 `tool_failure`；失败证据不会被隐藏。非致命模型错误会记录日志，最终状态仍由本次执行的宿主终态决定。

最终返回仍包含 `<subagent><final run="..." success="..." tool_count="...">正文</final></subagent>`，新增 `status`、`reason`、`summary_truncated` 和有归档时的 `evidence_path`。`tool_count` 只统计实际执行的工具，未执行的拒绝记录不计数。

失败或受阻通过既有脚本失败信封 `{success:false,message,data:XML字符串}` 返回，外层工具同样报告失败。`message` 仅包含简短状态和原因，完整摘要只在 XML `final` 中出现一次。渲染插件继续支持旧历史消息的 `success` 字段，新消息明确展示“完成”“受阻”或“失败”。

## 真实证据

存在工具记录时，示例先把完整宿主 Outcome、实际回调账本与最终判定写入 `OPERIT_CLEAN_ON_EXIT_DIR/subagent_<UUID>.json`。UUID 来自宿主，不使用时间尾段和小随机数拼接。

每条账本记录包括 `id`、工具名、参数、`success`、原始 `result`、`error`、`executed` 和 `resultTruncated`。结果由宿主长度上限处理时，归档会保留这个标志，不能把被截断的工具结果说成完整结果。父代理通过 `evidence_path` 读取或分段检查该 JSON。该目录会按宿主的退出清理约定删除，长期保留应另行保存。

XML 的独立 `evidence` 块只带简短索引和总数，每类最多 8 个 ID，并标明 `index_truncated`。完整工具结果不塞进一个 XML 工具结果，以免触发单结果长度限制并破坏标签闭合。

内联摘要按 XML 转义后的长度限制为 4000 字符，避免长正文挤掉闭合标签；超过该长度的完整正文在同一 JSON 归档中，`summary_truncated=true` 和 `evidence_path` 明确标识。没有工具但摘要超过内联限制时，也必须先归档完整正文。通常的数百字摘要不受旧 320 字符截断影响。

目录创建或证据写入失败会记录错误并返回 `failed/evidence_write_failed`，不会继续报告无证据成功。

## 源码与验证

`src/shared/subagent_protocol.ts` 包含参数验证、严格 JSON 协议、宿主结果校验、状态合成和有界 XML 输出。入口和渲染器的 TypeScript 位于 `src/`，已跟踪运行产物在 `dist/`。

2026-11-07 已使用 TypeScript 5.9.3 编译通过并重新生成 dist。以下为编译与行为测试命令，行为测试尚未执行：

```bash
pnpm exec tsc -p examples/subagent/tsconfig.json
node --test examples/subagent/tests/subagent_protocol.test.cjs
```

用例覆盖非法输入零调用、预算整数边界、严格终态与跨运行证据、真实失败阻止完成、特殊标签字面内容、残缺隐藏块、长证据与完整 XML、旧历史和失败信封渲染、异步交错调用的身份／语言隔离、归档失败及 collect 重抛后的宿主终态。Java 桥、真实工具权限和 Android 卡片还需配套宿主构建与设备验证。
