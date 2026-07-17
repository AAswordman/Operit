---
title: Codec、canonical grammar 与 Provider 历史投影
status: draft
document_type: implementation-step
step: 1
depends_on: []
fork_repository: https://github.com/CATMIAOZHI/Operit
last_reviewed: 2026-07-17
---

# Codec、canonical grammar 与 Provider 历史投影

- 新增 DeepSeek 专用、版本化的 `xml-text-v1` codec
- 编码顺序固定为 `&`、`<`、`>`，解码为严格、非递归的单次扫描
- OpenAI Provider 只提供默认无行为变化的 reasoning emission seam
- DeepSeek 流式与非流式输出统一编码，token 统计使用编码前原文
- DeepSeek 历史只对精确版本 marker 解码
- 工具子轮次逐字符保留 reasoning，不 trim、不插入换行
- 原生工具调用保持在已关闭的 think block 外
- `enableToolCall` 只决定工具协议，不改变 reasoning 的结构安全处理

## Canonical wire format

新格式只接受以下精确结构：

```xml
<think data-operit-content-encoding="xml-text-v1">ENCODED_BODY</think>
```

- opening tag 必须与上述字符串完全一致：属性名、双引号、属性顺序和空格均固定，不允许附加属性
- closing tag 固定为 `</think>`；canonical producer 在正常结束、流结束和工具调用边界都必须闭合
- `ENCODED_BODY` 按 `&` → `&amp;`、`<` → `&lt;`、`>` → `&gt;` 的顺序编码
- 解码器只识别 `&amp;`、`&lt;`、`&gt;`，并以非递归单次扫描恢复；未知实体逐字符保留
- 未闭合的 v1 marker 属于 malformed canonical data：不得解码，也不得投影为普通正文或工具输入
- 未知版本、额外属性或被编辑过的 marker 不按 v1 解码；它们作为 opaque think block 隔离，不进入普通正文或工具输入
- 相邻 v1 block 按源顺序逐字符拼接，不插入分隔符
- 混合历史按源顺序处理：v1 body 逐字符追加；旧 `<think>`/`<thinking>` body 维持现有 `trim()` 语义，并在已有 reasoning 后追加旧格式分隔换行
- visual 编辑器只编辑已隔离的 body，不暴露 marker；raw 编辑导致 marker 不再精确匹配时，按 opaque think block 处理

## Storage contract

`streamBuffer`、`PromptTurn`、数据库和工具解析输入始终保持编码态。普通 `content` 不编码，以免改变现有 XML 工具协议。

## Cross-provider history projection

`xml-text-v1` 是共享 canonical 历史格式，不是 DeepSeek 请求私有格式。DeepSeek 负责在响应边界编码，但所有 Provider 在构建请求历史和 token 输入前必须通过同一共享 projector 解释 canonical assistant turns。

- DeepSeek → DeepSeek：v1 body 解码到 `reasoning_content`，普通正文进入 `content`
- DeepSeek → Kimi 或其他有独立 reasoning 字段的 Provider：v1 body 解码到对应 reasoning 字段
- DeepSeek → 通用 OpenAI：v1 block 从 wire `content` 剥离；除非该 Provider 明确定义了安全 reasoning 通道，否则不发送 reasoning
- DeepSeek → 不支持 reasoning 的 Provider：v1 block 从请求历史剥离，不转换成普通正文
- `preserveThinkInHistory=false`：剥离所有 reasoning；`true` 只允许通过目标 Provider 明确支持的安全表示保留，不得原样发送 canonical marker 或 encoded body
- 旧无 marker 历史继续遵循现有 Provider 行为；共享 projector 不把旧实体文本按 v1 解码
- token 统计必须使用最终 Provider wire projection，不能使用投影前 canonical 字符串
- ToolCall 开关两种状态必须复用同一投影结果，工具解析只能接收已移除 think block 的普通正文

## Completion

状态：未完成。实现共享 projector 并完成授权验证后，在一级标题末尾添加 `[DONE]`。
