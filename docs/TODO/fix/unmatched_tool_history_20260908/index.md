---
fork: local workspace
branch: fix/unmatched-tool-history
status: implementation-complete
---

# 未匹配工具历史修复

## 原本状况

历史序列化会在工具调用没有对应执行结果时补写 `role=tool` 消息，以满足各 Provider 的工具调用协议。旧实现把该消息标记为 `User cancelled`，造成模型把历史缺失误解为手动取消。PR #1131 将文字改为“未匹配”，但尚未合入 `dev`。

## 修改意图

合并 PR #1131 的语义修正，并统一当前 Provider 与结构化桥接层的占位记录。占位记录仅表达本地历史缺失，不表示工具已经执行或对话被取消。

## 期待结果

- 未配对工具调用仍满足 Provider 的请求协议
- 历史占位内容不再含有 `User cancelled`
- 真实取消链路保持使用实际取消原因
- OpenAI 兼容 Provider、Claude、Gemini、DeepSeek 与 Kimi 使用相同语义

## 作用域

- PR #1131 合并
- 工具历史序列化占位内容和日志
- 相关 JVM 回归测试

## 进度

- [DONE] 日志与当前实现核查
- [DONE] 合并 PR #1131 并解决当前 `dev` 差异
- [DONE] 统一未匹配工具调用占位语义
- [DONE] 静态检查变更
- [DONE] 合并回 `dev`

## 验证记录

- 已检查历史序列化不再使用 `User cancelled` 或旧的静态未匹配结果文本
- 已补充部分批次与历史结束时的占位内容回归断言
- 依照仓库工作约束，本次未执行构建或测试命令
