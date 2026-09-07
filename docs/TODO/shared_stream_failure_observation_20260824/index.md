---
For_Agent: 共享流异常收尾契约
---

# 共享流异常收尾契约

## 原本状况

OpenAI、Claude、Gemini 及其协议代理在重试耗尽后都会把终态异常向上抛出。聊天消息被转换为共享流后，该异常会被所有订阅者重新抛出；消息处理订阅者能收尾，但只负责渲染的订阅者会把网络失败升级为全局未捕获异常。

## 修改意图

保留 provider 的终态异常日志和中断消息内容，同时让聊天共享流的消息处理订阅、UI 订阅都正常结束。聊天发送链路在 provider 流式收集阶段结束当前流，不再把该异常转换为用户侧报错。

## 作用域

- `HotStream` 的共享流结束原因和可选异常传播
- `shareRevisable` 的聊天流配置
- `EnhancedAIService` 与 `MessageProcessingDelegate` 的 provider 失败收尾关系
- `LlamaProvider` 与 `MNNProvider` 的错误文本输出
- 共享流 API 文档和回归测试

## 期待结果

- 各远程 provider 的重试、usage 和终态异常行为保持不变
- 聊天 UI 等旁观订阅者不会因 provider 网络异常触发全局崩溃
- 消息处理层保存已收到的中断内容，不再弹出 provider 终态错误
- 本地 provider 失败不再把错误文本写进 AI 回复
