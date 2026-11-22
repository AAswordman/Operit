# 02 两种端点的服务端工具声明

## 旧实现

- `enableDeepSeekWebSearch` 仅传入 `DeepseekResponsesProvider`，Chat Completions 分支忽略该开关。
- Responses 端点 `appendWebSearchTool` 追加 OpenAI 旧格式 `{"type": "web_search"}`，并以此类型做去重判断。

## 意图修正

DeepSeek 已改用自有工具类型：Chat Completions 为 `{"type": "deepseek_web_search"}`、`{"type": "deepseek_read_url"}`（按官方 curl 示例，仅 type 字段）；Responses 端点同样使用这两个类型。旧 `web_search` 写法按用户确认彻底清理，不做兼容分支。工具本身在服务端执行，声明不依赖 enableToolCall。

## 新实现

- `DeepseekProvider` 增加 `enableWebSearch` 构造参数，`create()` 两个分支都传入 `config.enableDeepSeekWebSearch`。
- Chat Completions `createRequestBody` 在工具定义之后追加两个 DeepSeek 联网工具并置 `tool_choice = "auto"`，`toolsJson` 一并更新以保持 token 计数一致。
- Responses `appendWebSearchTool` 改为声明 `deepseek_web_search` 与 `deepseek_read_url`。

[DONE]
