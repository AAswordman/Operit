---
title: Claude tool result pairing repair
status: complete
---

# Claude 工具结果配对修复

## 原因

提交 `2454f4bf` 将 Claude 历史中的工具结果改为按名称严格配对，但 Claude 的
`package_proxy` 工具调用名称是 `package_proxy`，执行结果名称是被代理的实际工具名。
这会让已经成功执行的结果无法匹配，随后被序列化为 `User cancelled`。

## 目标

- 保持严格配对，不按位置或旧结果顺序兜底
- 对 Claude、Gemini 和 OpenAI 的包代理调用使用同一套真实工具名解析
- 关闭思考时不把服务端误返回的 thinking block 暴露给用户

## 作用域

- `StructuredToolCallBridge.toolCallName` 支持 Claude `input`
- Claude 历史序列化使用解析后的匹配名
- Claude 非流式和流式响应按 `enableThinking` 过滤 thinking block
- 添加包代理配对回归覆盖

## 进度

- 详细步骤：[1_claude_history_and_thinking.md](1_claude_history_and_thinking.md)
- [DONE] 根因与回归提交确认
- [DONE] 严格匹配键修复
- [DONE] thinking block 请求级过滤
- [DONE] 回归测试补充
