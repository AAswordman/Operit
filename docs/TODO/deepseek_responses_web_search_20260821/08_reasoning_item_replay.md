---
status: done
For_Agent: 保持 DeepSeek Responses 工具续轮中的纯文本 reasoning item 完整
---

# Responses reasoning item 回放

## 直接现象

DeepSeek Responses 在思考模式下先返回纯文本 `reasoning` item，再返回客户端函数调用。客户端执行工具并发送下一轮请求时，服务端返回：

```text
The `reasoning_text` in the thinking mode must be passed back to the API.
```

通用 Responses 历史只保存带 `encrypted_content` 的 reasoning item。DeepSeek 使用 `content` 中的 `reasoning_text`，因此首轮推理虽然能够显示，却没有进入隐藏历史协议，工具续轮缺少服务端要求的 reasoning item。

## 修正意图

- DeepSeek 的纯文本 reasoning item 使用现有隐藏 metadata 通道保存
- 下一轮 Responses input 在函数调用和结果之前恢复原 reasoning item
- 现有 OpenAI 加密 reasoning item 的保存格式和读取行为保持不变
- 流式与非流式响应共用同一组 reasoning metadata 编解码规则

## 作用域

- Responses reasoning metadata 的生成与恢复
- 纯文本和加密 reasoning item 的针对性单元测试
- 本协议文档中的无状态工具续轮约定

## 非目标

- 不改变思考内容的界面展示
- 不改变客户端工具授权与执行流程
- 不改变 HTTP 状态码和重试策略
- 不修改 DeepSeek Chat Completions 历史格式

## 验证

- 纯文本 reasoning item 保存后可按原 ID 和内容恢复
- 恢复后的顺序为 reasoning、assistant message、function call、function output
- 加密 reasoning item 的既有格式继续可读写

## 完成记录

- 流式与非流式 Responses 解析按 provider 生成对应的 reasoning metadata。
- DeepSeek 纯文本 reasoning item 已保存原 ID 与 `reasoning_text` content，并在工具调用前恢复。
- 成功恢复 reasoning item 时，对应 assistant message 不再重复携带 `<think>` 内容。
- 现有 OpenAI 加密 reasoning metadata 格式保持不变。
- 已增加纯文本、加密 reasoning item 与函数工具续轮顺序的回归测试代码。
- `git diff --check`、46 个 CI 门禁单测、仓库 hygiene、Markdown 链接、本地化与 WebChat typecheck 通过。
- 最新 `development` 合并候选上的 `OpenAIResponsesPayloadAdapterTest` 与 `assembleDebug` 通过。
- 完整 JVM 单测执行 1212 项，其中 6 项失败；最新 `development` 基线运行对应 23 项得到相同 6 项失败，本次改动未新增失败。

[DONE]
