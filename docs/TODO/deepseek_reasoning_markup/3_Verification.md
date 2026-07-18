---
title: 兼容性与验证矩阵
status: draft
document_type: implementation-step
step: 3
depends_on:
  - 1
  - 2
fork_repository: https://github.com/CATMIAOZHI/Operit
last_reviewed: 2026-07-18
---

# 兼容性与验证矩阵

## Codec

- 覆盖 `&`、`<`、`>`、未知实体和非递归单次解码
- 覆盖任意 reasoning SSE chunk 切分，包括标签与实体被逐字符拆分
- 覆盖纯空白、换行、制表符和 Unicode 的逐字符 round-trip
- 验证旧无 marker 历史解释规则不变
- 覆盖相邻 v1 block、新旧 block 混合、未闭合 marker、附加属性和未知 `xml-text-v2`
- 覆盖 opening tag 属性值中的 `>`、单双引号、属性值中的保留名字面量及完整属性 token 边界
- 覆盖 opaque body 内先出现字面量 `</think>`、随后出现 `<tool>` 的情况，断言隔离持续到 reasoning segment 终点
- 覆盖普通 `Text` 中精确 v1 wire format，断言逐字符保留且不执行实体解码

## Provider

- 覆盖 DeepSeek 流式和非流式 `reasoning_content`
- 覆盖 `enableToolCall` 开启和关闭两种状态
- 验证编码输出不改变 DeepSeek 响应输出 token 统计所使用的原始 reasoning
- 验证工具子轮次恢复出的 `reasoning_content` 与原文逐字符一致
- 验证 DeepSeek 目标在 `preserveThinkInHistory` 两种状态下都回传全部真实 v1 reasoning，并模拟遗漏 tool-call reasoning 时的 400 条件
- 验证其他 OpenAI-compatible Provider 的 reasoning 和工具顺序不变
- 分别断言 DeepSeek 响应输出 token 使用编码前 reasoning、历史输入 token 使用目标 Provider 最终 wire projection
- 验证同一个 projected request 同时驱动请求 JSON、工具调用 ID 配对和 token 输入，不允许 estimator 重新遍历 canonical history

## Provider switching matrix

用同一条由 DeepSeek 生成的 canonical assistant history，分别继续请求：

| Target Provider | Expected v1 projection |
| --- | --- |
| DeepSeek | always decode every real v1 body → its source assistant `reasoning_content` |
| Kimi/MiMo with explicitly verified textual history capability | always decode every real v1 body → assistant `reasoning_content`, independent of current thinking toggle |
| another explicitly verified textual history field | decode every real v1 body → that assistant-only field |
| reasoning-looking field or inherited implementation without explicit verification | omit v1 completely |
| Provider with genuine opaque/signature reasoning | omit DeepSeek v1; do not populate opaque fields or change existing same-Provider behavior |
| generic OpenAI Chat Completions | omit v1 block from wire `content` |
| Provider without reasoning support | omit v1 block from request history |

每个目标覆盖以下组合和内容：

- 对 v1 block 覆盖 `preserveThinkInHistory=true` / `false`，断言其不能覆盖目标能力决策；对旧无 marker block 验证仍委托给目标 Provider 现有逻辑
- `enableThinking=true` / `false` 和 `enableToolCall=true` / `false`，断言它们只控制本轮生成能力
- reasoning 包含 `</think>`、`<tool>`、`&lt;`、`&amp;lt;` 和未知实体
- 相邻 v1 block、新旧 block 混合、未知 `xml-text-v2` 和未闭合 marker
- 切换模型后继续对话，检查请求正文、reasoning 字段和 token 输入完全使用同一投影
- 确认目标 Provider 不会看到 `data-operit-content-encoding` 或 v1 encoded body
- 覆盖当前角色 assistant 与被角色隔离映射为 user 的其他角色 assistant；后者只发送普通正文和角色前缀

### DeepSeek current-generation controls

| Current thinking | Current tool exposure | Historical ReasoningV1 | Historical source native tool exchange |
| --- | --- | --- | --- |
| disabled | disabled | decode all to source assistant `reasoning_content` | preserve native assistant calls + tool results |
| disabled | enabled | decode all to source assistant `reasoning_content` | preserve native assistant calls + tool results |
| enabled | disabled | decode all to source assistant `reasoning_content` | preserve native assistant calls + tool results |
| enabled | enabled | decode all to source assistant `reasoning_content` | preserve native assistant calls + tool results |

每行再覆盖 `preserveThinkInHistory=true` / `false`，共八种组合，并覆盖流式和非流式来源、live 工具子轮次与重载后的持久化历史。当前工具关闭或工具列表为空时，请求不得发送新的 `tools`/`tool_choice`，但历史 exchange 仍必须保留原生结构；历史 reasoning 只能属于各自 source assistant，不能被相邻 turn 错配。

- 切换 `enableThinking` 时 projected history 必须逐字符相同，只有本轮 generation parameter 改变
- 当前工具关闭或工具列表为空时不得接受或执行新工具调用；历史 exchange 只参与上下文序列化，不得被重新执行
- 持久化及重载后的 canonical 数据不得包含上游 `tool_call_id`；每次 projected request 生成 request-local IDs，并让 assistant calls 与 tool results 共同使用同一组 ID

### Kimi/MiMo textual history capability matrix

| Current thinking | Preserve | Expected v1 projection after capability registration |
| --- | --- | --- |
| disabled | false | decode all real v1 to assistant `reasoning_content` |
| disabled | true | decode all real v1 to assistant `reasoning_content` |
| enabled | false | decode all real v1 to assistant `reasoning_content` |
| enabled | true | decode all real v1 to assistant `reasoning_content` |

能力注册前必须用协议文档、fixture 或 API 验证 thinking-disabled 请求接受历史 `reasoning_content`；验证失败则该目标保持 unverified 并在四行中全部剥离 v1，不能降到普通 `content`。四行分别覆盖本轮工具开启和关闭，共八种组合。每种组合断言 thinking 状态只改变最终本轮生成参数；两条构建路径均只消费一次相同的 history projection，序列化和 token 输入不得重新遍历 canonical history。legacy extractor 只接收 projector 保留的旧无 marker 文本，Provider 子类也不能自动继承安全 reasoning capability。

## Marker isolation expectations

| Input form | Expected projection |
| --- | --- |
| exact, closed `xml-text-v1` with `ReasoningV1` provenance | decode for an explicitly registered textual history projector regardless of generation/preserve toggles; otherwise omit the whole block |
| closed `xml-text-v2` with `ReasoningV1` provenance | opaque; omit the entire reasoning segment from content, tools, and reasoning |
| single-quoted v1 or v1 with extra attributes and provenance | opaque; omit the entire reasoning segment |
| reserved opening tag or attribute quote without a closing `>` | opaque; omit the entire reasoning segment and do not search beyond its boundary |
| exact or opaque opening tag without a valid closing `</think>` | opaque; same segment-boundary fail-closed rule |
| `<tool>` after an unclosed reserved marker | remains inside the isolated suffix and never reaches tool parsing |
| opaque body containing an early literal `</think>` followed by `<tool>` | early closing is not trusted; both remain isolated through the reasoning segment end |
| opening attribute value containing `>` | quote-aware lexer does not end the opening tag inside the value |
| attribute value containing `data-operit-content-encoding` | does not count as a reserved attribute token |
| exact v1 text in a `Text` segment | preserve byte-for-byte as ordinary content; never decode or omit as reasoning |
| exact legacy `<think>` / `<thinking>` without the reserved attribute | unchanged target-Provider legacy behavior |
| `<think class="foo">` without the reserved attribute | outside v1 projection; unchanged existing content behavior |

每项同时断言普通正文、reasoning 字段、工具解析输入和 token 输入，不能只检查 UI 输出。

## Projection order and provenance

- 在最终 prompt hook 中新增或修改 marker，验证 projector 在 hook 后运行；纯文本 hook 输出不能伪造 `ReasoningV1` 来源
- 其他角色 assistant 经角色隔离映射到 user 后，v1 reasoning 不进入 user content；source role 和 target role 均进入断言
- 在目标 role 计算、wire role/角色前缀物化、assistant/tool 合并和 XML 工具解析前后分别设置观察点，确认 projector 在目标 role 计算之后只执行一次，并先于其余转换
- 覆盖模型普通 `content`、raw 编辑器和 hook 输出中的精确 v1 示例，确认均保持 `Text`
- 覆盖 raw 编辑破坏 opening tag、引号或 closing tag；原 reasoning segment 整体 opaque，其后的真实普通 segment 仍按独立边界处理
- 消息持久化、变体切换、rollback/replay 和重新加载后，segment 来源与 tool exchange 关联保持不变
- unrelated XML、旧无 marker think block 和普通 XML 工具 markup 不获得 v1 provenance metadata
- 对 Gemini signature、OpenAI Responses encrypted reasoning、Claude signed thinking 等字段，只断言 DeepSeek v1 不会填入或伪造；这些 Provider 既有的同源状态行为不由本任务新增或修改

## Tool isolation

- 验证 reasoning 内字面量 `<tool>`、`</think>` 不进入工具执行器
- 验证 Provider 生成的真实原生工具调用只执行一次
- 验证真实工具调用位于已关闭的 canonical think block 外

## Presentation and build

- 覆盖静态 UI、流式 UI、rollback/replay 和消息编辑器 round-trip
- 检查 Web Chat、TXT 和 HTML 导出的语义显示
- 通过 fork 的 GitHub Action 运行专项回归测试并构建 debug APK

## Completion

状态：未完成。完成全部验证并记录结果后，在一级标题末尾添加 `[DONE]`。
