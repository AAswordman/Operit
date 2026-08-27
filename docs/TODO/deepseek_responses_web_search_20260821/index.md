---
issue: https://github.com/AAswordman/Operit/issues/877
branch: feat/deepseek-responses-web-search
status: done
For_Agent: 按三个最小单元实施，未经明确授权不运行 Gradle 构建或测试
---

# DeepSeek Responses 与服务器搜索

## 原本状况

已发布版本的 DeepSeek 配置只使用 Chat Completions。工程已有通用 Responses 请求与流式解析能力，但 DeepSeek 没有选择协议的入口，也没有声明 `web_search`、显示搜索阶段或保存 `web_search_call`。

## 修改意图

在保留 `ApiProviderType.DEEPSEEK` 和既有 Chat Completions 行为的前提下，为 DeepSeek 配置增加 Responses 端点和可关闭的服务器搜索。API 端点是协议的唯一配置来源，旧配置保存的 Chat Completions 端点继续保持原行为。

## 作用域

- DeepSeek 端点选项、设置界面、协议判定与服务路由
- Responses 请求中的 `web_search` 工具声明
- 搜索阶段的临时状态，以及 `web_search_call` 的隐藏持久化与下一轮恢复
- 服务端工具调用的只读记录，并与客户端工具调用明确区分
- 复制、渲染和普通消息请求对隐藏协议元数据的隔离
- 对应开发文档与单元测试代码
- 使用 mise 固定项目要求的 JDK 21 构建环境

## 非目标

- 不新增独立的 DeepSeek provider 类型
- 不改变 Chat Completions 的请求与历史格式
- 不实现搜索引用的专用展示
- 不增加独立的持久化协议字段
- 不迁移或重写既有端点配置

## 细化步骤

1. [配置兼容与运行时路由 [DONE]](./01_configuration_and_routing.md)
2. [搜索状态与历史协议 [DONE]](./02_web_search_stream_and_history.md)
3. [界面、文档与验证 [DONE]](./03_ui_docs_and_verification.md)
4. [Responses 工具历史完整性 [DONE]](./04_responses_tool_history_integrity.md)
5. [端点驱动协议路由 [DONE]](./05_endpoint_driven_protocol.md)
6. [服务端工具状态展示 [DONE]](./06_server_tool_status.md)
7. [服务端工具调用记录 [DONE]](./07_server_tool_call_record.md)
8. [Responses reasoning item 回放 [DONE]](./08_reasoning_item_replay.md)

[DONE]
