---
status: done
For_Agent: DeepSeek API 端点是协议的唯一配置来源
---

# 端点驱动协议路由

## 原实现问题

独立的 DeepSeek API 模式与 API 端点表达同一件事。保存的端点可以是 Chat Completions，而运行时模式又将请求改写成 Responses，导致界面值、持久化值和实际请求地址不一致。

## 修正意图

- 在既有 API 端点选择器中提供官方 Chat Completions 与 Responses 地址
- 仅根据端点路径选择 `DeepseekProvider` 或 `OpenAIResponsesProvider`
- 删除 `deepSeekApiMode` 配置字段、设置状态和协议选择弹窗
- Web Search 开关仅在 Responses 端点显示并生效
- 已发布配置保存的 Chat Completions 地址保持不变

## 验证范围

- Chat Completions、Responses、尾部斜杠、查询参数和 `#` 精确地址的协议判定
- Factory、配置准备状态和设置界面使用同一个端点协议判定
- 工程中不再引用 `DeepSeekApiMode` 或 `deepSeekApiMode`
- 静态差异与空白检查

## 完成记录

- DeepSeek 端点选择器已提供 Chat Completions 与 Responses 官方地址
- `EndpointCompleter.resolveDeepSeekProtocol` 成为 UI 与 Factory 共用的协议判定入口
- 持久化协议字段、保存参数和独立协议弹窗已删除
- 已发布 Chat Completions 端点保持原地址和请求实现
- `git diff --check` 通过
- 按工程执行准则，本轮未运行 Gradle 编译、构建或测试

[DONE]
