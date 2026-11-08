---
topic: Isolate OpenCode provider behavior
status: done
---

# OpenCode provider isolation

## 原本状况

OpenCode 的首版接入把路由、认证和思考参数通过 marker 注入 Claude、Gemini、OpenAI 和 Responses provider。这样普通 provider 需要识别 OpenCode 特例，导致公共请求路径承担了不属于自身的协议差异。

## 修正意图

让 OpenCode 的协议适配留在 OpenCode 专用实现中。普通 provider 只保留自己的通用行为；必要的继承点只表达稳定的请求构建扩展，不引用 OpenCode 类型或分支判断。

## 作用域

- 恢复公共 provider 的原有 reasoning、认证和端点行为
- 为 OpenCode 的 Chat Completions、Responses、Anthropic 和 Gemini 路由提供隔离实现
- 修正 OpenCode Gemini 的认证头和 SSE URL
- 保留现有设置、模型目录和路由测试，并补充隔离边界说明

## 细化步骤

## 完成记录

- 公共 Provider 已移除 OpenCode marker、端点判断和认证分支。
- OpenCode Chat、Responses、Anthropic、Gemini 专用实现已合并到 OpenCodeProvider.kt。
- 已执行 git diff --check，未运行构建、Gradle 或测试命令。

1. `01-public-provider-baseline.md`：记录公共 provider 的恢复边界
2. `02-opencode-routing.md`：记录专用路由和协议适配方式
3. `03-verification.md`：记录静态验证结果
