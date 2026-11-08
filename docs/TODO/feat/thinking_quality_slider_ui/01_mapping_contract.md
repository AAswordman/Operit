# 映射契约

## 旧实现

OpenAI、Gemini、DeepSeek、NVIDIA、SiliconFlow 和 OpenRouter 在 provider 内部各自保存全局档位到请求参数的映射，UI 无法读取同一份描述。

## 新实现

`ThinkingQualityMapping` 描述控制类型、参数名称和每个内部档位的显示值与 wire value。provider 请求构建和 UI 都通过 `ThinkingQualityMappingRegistry` 获取定义。

每个 level 保留独立位置，即使多个 level 使用相同的 wire value。UI 只显示 `displayLabel`，内部请求继续使用类型化的 `wireValue`。

没有程度参数的 provider 显式使用 `TOGGLE_ONLY`，不会由 UI 猜测档位含义。
