---
fork: local workspace
branch: fix/preserve-interrupted-ai-output
status: implementation-complete
---

# 断网中断消息保留修复

## 原本状况

AI 流式输出遇到网络错误时，Provider 会先发出回滚事件，再判断是否还有重试机会。重试耗尽后，EnhancedAIService 只发布错误状态并结束流。消息处理层将这个结束视为正常完成，最终把空的流式快照写回同一条 AI 消息，覆盖先前已经持久化的内容。

## 修改意图

网络重试失败时保留已确认的 AI 输出，并按中断回合完成消息。仅在确实要开始新的尝试时发送回滚事件，避免不可重试和重试耗尽的错误提前清空消息。

## 期待结果

- 断网且重试耗尽后，已输出内容仍保留在同一条 AI 消息中
- 网络失败显示错误状态，不会被当作正常完成
- 主动取消现有的中断消息行为不变
- 成功重试的流式显示和工具调用撤回仍可用

## 作用域

- OpenAI、Claude、Gemini Provider 的重试决策与回滚顺序
- EnhancedAIService 的最终失败传播
- MessageProcessingDelegate 的中断消息收尾
- 流式消息收尾回归测试
- 回滚后的 Markdown 尾部更新

## 验证记录

- 已添加 Provider 回归测试，验证最终失败保留已输出文本且不发送回滚事件
- 已添加共享流回归测试，验证上游异常在重放部分内容后继续传递给收集端
- 依照仓库工作约束，本次未执行构建或测试命令

实现已完成。[DONE]
