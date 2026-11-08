# 公共 provider 恢复边界

## 旧实现

公共 provider 通过 `OpenCodeReasoningParameters` marker 改变 reasoning 自动注入、参数过滤和 Gemini 请求端点。该 marker 只为一个 provider 服务，却进入了所有普通请求构建路径。

## 目标实现

公共 provider 不再导入或识别 OpenCode 类型。OpenCode 请求使用专用子类或专用请求构建钩子；普通请求的认证、端点和 thinking 行为保持原样。

## 完成标准

- `ClaudeProvider.kt`、`GeminiProvider.kt`、`OpenAIProvider.kt`、`OpenAIResponsesProvider.kt` 不包含 OpenCode 特例判断
- OpenCode 专用代码承担显式 reasoning 参数和 Gemini `x-goog-api-key` / SSE URL
