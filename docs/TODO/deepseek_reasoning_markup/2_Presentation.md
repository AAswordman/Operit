---
title: 展示与编辑边界
status: draft
document_type: implementation-step
step: 2
depends_on:
  - 1
fork_repository: https://github.com/CATMIAOZHI/Operit
last_reviewed: 2026-07-18
---

# 展示与编辑边界

- Android 静态和流式 think body 在隔离结构后解码
- rollback/replay 从 canonical snapshot 重建流式 decoder 状态
- 消息编辑器 visual 模式解码，重组时重新编码
- Web Chat structured block 输出语义正文并保留 canonical raw content
- TXT 和 HTML 可读导出显示解码后的 reasoning
- 未带精确 marker 的旧历史维持现有解释规则，不按新格式解码
- 含保留属性的未知版本、附加属性、raw 编辑后的 marker 和未闭合 marker 按步骤 1 的 reserved grammar 显示为 opaque think body，不执行 v1 解码
- 普通 `Text` segment 中与精确 v1 wire format 相同的 XML 逐字符显示为正文，不按 reasoning 展示或删除

展示层不得对 canonical 编码体做通用 HTML/XML 解码，也不得扩大 v1 decoder 的精确 marker 匹配范围；更宽的结构隔离只能把未知 block 视为 opaque，不能触发解码。

编辑器必须以 segment 来源而不是 marker 文本决定语义。visual 模式只编辑 `ReasoningV1` body；raw 模式在 `Text` 中输入 marker 不会获得 reasoning 来源，修改已有 reasoning block 则保留其 segment 边界。跨越 segment 边界的编辑必须在提交前产生确定的 segment 结果；受损的 reasoning segment 整体 opaque，不能通过其内部伪造的 closing tag 提前恢复工具或正文解析。

## Completion

状态：未完成。完成静态、流式、rollback/replay、编辑器和导出验证后，在一级标题末尾添加 `[DONE]`。
