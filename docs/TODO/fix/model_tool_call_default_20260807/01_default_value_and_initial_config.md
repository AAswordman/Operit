# 统一默认值与初始化配置

旧实现：

- `ModelConfigData.enableToolCall` 的默认值为 `false`
- 首次初始化的默认 DeepSeek 配置没有显式设置 Tool Call，因此使用关闭状态
- 普通新建配置单独写入 `true`，默认来源不统一

意图修正：

- 在 `ModelConfigDefaults` 中集中定义 Tool Call 默认状态并设为开启
- 让 `ModelConfigData` 和 `ModelConfigManager` 的初始化、新建路径使用该默认状态
- 保留已保存配置的显式 Tool Call 开关值

预期结果：

- 首次启动生成的 DeepSeek 配置默认开启 Tool Call
- 新建模型配置默认开启 Tool Call
- 通过配置数据类创建的新配置使用同一默认值
- 用户已保存的关闭状态不会因本次默认值调整被改写

验证：

- 静态检查默认常量、初始化路径及新建路径的引用
- 按仓库执行准则不运行构建或测试命令

## 结果 [DONE]

`ModelConfigDefaults.DEFAULT_ENABLE_TOOL_CALL` 与 `ModelConfigData` 已改为开启，首次 DeepSeek 配置和普通新建配置均引用该默认值；协议文档已补充默认行为说明。
