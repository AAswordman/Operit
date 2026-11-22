---
fork_repository: local-worktree
scope: DeepSeek 供应商模型默认值与 Chat Completions / Responses 联网搜索
---

# DeepSeek 联网搜索配置完善

## Current State

DeepSeek 官方文档（2026-11）中，模型 ID 已收敛为 `deepseek-flash` 与 `deepseek-v4-pro`，`deepseek-v4-flash` 仅为仍可路由的旧名。Operit 中 DEEPSEEK 供应商的默认模型仍是旧名 `deepseek-v4-flash`，定价行还包含已退役的 `deepseek-chat`、`deepseek-reasoner`，币种为官方已不再公布的 CNY。

联网搜索方面：

- `enableDeepSeekWebSearch` 开关只对 Responses 端点生效，Chat Completions 端点完全不支持。
- Responses 端点声明的工具类型是 OpenAI 旧格式 `{"type": "web_search"}`，DeepSeek 已改用自己的工具名 `deepseek_web_search`，现有声明按官方文档已无效。
- Chat Completions 响应中的 `annotations`（url_citation 引用）与 `server_tool_calls` 在解析层被丢弃，引用无法展示，多轮对话中读过的网页也不会再进入上下文。

## Intended Result

- 默认模型改为规范名 `deepseek-flash`，定价行与官方 USD 定价同步。
- 一个开关同时驱动两种端点：Chat Completions 声明 `{"type": "deepseek_web_search"}` 与 `{"type": "deepseek_read_url"}`，Responses 端点同样声明这两个 DeepSeek 自有工具类型。
- Chat Completions 响应中的 `annotations` 与 `server_tool_calls` 被解析为与 Responses 路径一致的 `<search provider="deepseek">` 展示块。
- `server_tool_calls` 通过隐藏 meta 标签持久化，下一轮请求在 assistant 消息上原样回传。
- 已发布版本的旧 Responses 工具写法彻底清理（用户已确认）。

## Scope

- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/DeepseekProvider.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt`
- `app/src/main/java/com/ai/assistance/operit/util/ChatMarkupRegex.kt`
- `app/src/main/java/com/ai/assistance/operit/data/collects/ApiProviderConfigCollect.kt`
- `app/src/main/java/com/ai/assistance/operit/data/collects/ScrapedModelPricingRowsCollect.kt`
- `app/src/main/java/com/ai/assistance/operit/data/preferences/ApiPreferences.kt`
- `app/src/main/res/values*/strings.xml`（8 语言，`enable_deepseek_web_search_desc`）

## Steps

- [x] `01_model_defaults.md`: 默认模型与定价行同步官方现状
- [x] `02_web_search_tools.md`: 两种端点的服务端工具声明
- [x] `03_citations_display.md`: Chat Completions 引用展示
- [x] `04_server_tool_calls_replay.md`: server_tool_calls 持久化与回传
- [x] `05_ui_strings.md`: 开关描述文案更新

[DONE]
