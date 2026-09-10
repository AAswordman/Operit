---
topic: Update OpenCode Zen and Go provider requirements
status: done
---

# OpenCode provider current requirements

## 官方变化

OpenCode 当前将 Zen 和 Go 的公开 API 放在带版本号的 `/v1` 路径下。模型 ID 分别使用 `opencode/<model-id>` 与 `opencode-go/<model-id>`，模型协议由官方目录决定，不再能只按模型名称的粗略前缀判断。

OpenCode Go 还要求客户端使用自己的 `User-Agent`，并为每个会话发送稳定的 `x-opencode-session` 请求头。

参考：

- [OpenCode Zen](https://opencode.ai/docs/zen)
- [OpenCode Go](https://opencode.ai/docs/go)
- [OpenCode provider 源码](https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/provider/provider.ts)

## 仓库现状

- 请求路由已经隔离在 `OpenCodeProvider.kt`，但模型列表地址缺少 `/v1`。
- MiniMax 在 Zen 与 Go 使用不同协议，旧路由没有区分端点。
- Qwen、Muse Spark 和当前 Go 模型集合没有完整覆盖。
- Go 请求没有声明 Operit 的客户端身份和会话头。

## 修改范围

- 修正 Zen/Go 模型列表端点。
- 按官方 Zen/Go 端点表更新 Responses、Anthropic、Gemini 和 Chat Completions 路由。
- 为 Go 请求增加 Operit `User-Agent` 和稳定的配置会话标识。
- 保持公共 provider 不感知 OpenCode 专用协议。
