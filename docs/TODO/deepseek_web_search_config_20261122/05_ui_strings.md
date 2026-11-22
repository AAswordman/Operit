# 05 开关描述文案更新

## 旧实现

`enable_deepseek_web_search_desc`（8 语言）描述为“使用 DeepSeek Responses 端点时……”，与功能实际覆盖范围不符。

## 意图修正

开关现在同时驱动 Chat Completions 与 Responses 两种端点，且包含读取链接能力，文案需如实描述。

## 新实现

8 个语言文件的 `enable_deepseek_web_search_desc` 统一改为“启用后模型可在 DeepSeek 服务端联网搜索并读取用户消息中的链接，Chat Completions 与 Responses 端点均支持”的对应译文。

[DONE]
