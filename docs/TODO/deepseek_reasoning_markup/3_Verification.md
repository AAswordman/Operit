---
title: 兼容性与验证矩阵
status: draft
document_type: implementation-step
step: 3
depends_on:
  - 1
  - 2
fork_repository: https://github.com/CATMIAOZHI/Operit
last_reviewed: 2026-07-17
---

# 兼容性与验证矩阵

## Codec

- 覆盖 `&`、`<`、`>`、未知实体和非递归单次解码
- 覆盖任意 reasoning SSE chunk 切分，包括标签与实体被逐字符拆分
- 覆盖纯空白、换行、制表符和 Unicode 的逐字符 round-trip
- 验证旧无 marker 历史解释规则不变
- 覆盖相邻 v1 block、新旧 block 混合、未闭合 marker、附加属性和未知 `xml-text-v2`

## Provider

- 覆盖 DeepSeek 流式和非流式 `reasoning_content`
- 覆盖 `enableToolCall` 开启和关闭两种状态
- 验证编码输出不改变 token 统计所使用的原始 reasoning
- 验证工具子轮次恢复出的 `reasoning_content` 与原文逐字符一致
- 验证其他 OpenAI-compatible Provider 的 reasoning 和工具顺序不变

## Provider switching matrix

用同一条由 DeepSeek 生成的 canonical assistant history，分别继续请求：

| Target Provider | Expected v1 projection |
| --- | --- |
| DeepSeek | decoded body → `reasoning_content` |
| Kimi / reasoning-capable compatible Provider | decoded body → provider reasoning field |
| generic OpenAI Chat Completions | omit v1 block from wire `content` |
| Provider without reasoning support | omit v1 block from request history |

每个目标覆盖以下组合和内容：

- `preserveThinkInHistory=true` / `false`
- `enableToolCall=true` / `false`
- reasoning 包含 `</think>`、`<tool>`、`&lt;`、`&amp;lt;` 和未知实体
- 相邻 v1 block、新旧 block 混合、未知 `xml-text-v2` 和未闭合 marker
- 切换模型后继续对话，检查请求正文、reasoning 字段和 token 输入完全使用同一投影
- 确认目标 Provider 不会看到 `data-operit-content-encoding` 或 v1 encoded body

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
