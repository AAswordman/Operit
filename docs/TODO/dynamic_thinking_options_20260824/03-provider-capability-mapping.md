# Provider capability mapping

思考参数现在由当前模型配置中的规则生成，不再把供应商名视为运行时全局能力集合：

- 新建配置或切换供应商时，收集目录会筛出对应供应商规则并写入模型配置
- 写入模型配置后的规则使用 `match` 中的模型前缀、包含项、正则、首段和末段特征匹配模型
- 每条规则声明 `control`、`parameterLabel`、`enable`、`disable` 和 `options`；设置页以弹窗表单维护当前配置规则
- OpenRouter/Nous 默认示例写 `reasoning.max_tokens`
- OpenCode 默认示例按模型路径首段识别 Google、Anthropic、OpenAI/xAI 和 GLM 家族
- Gemini 2.5 默认示例使用 `thinkingBudget`，其它 Gemini 示例使用 `thinkingLevel`
- Anthropic 旧型号使用预算，新型号使用 adaptive thinking 与 effort
- DeepSeek 默认示例使用 `low`、`high`、`max` 三档 `reasoning_effort`
- 智谱、Kimi、MiMo、豆包、SiliconFlow、NVIDIA 和本地模板能力都通过同一套规则表达
- 选项 id 无效时请求失败，避免静默改变用户选择

本地 llama.cpp 将 enableThinking 传入 llama.cpp chat template 的 enable_thinking 输入字段。

[DONE]
