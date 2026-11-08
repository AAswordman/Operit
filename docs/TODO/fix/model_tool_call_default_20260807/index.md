---
fork_repository: https://github.com/AAswordman/Operit.git
---

# 模型配置默认启用 Tool Call

当前模型配置数据类的 `enableToolCall` 默认值为关闭，首次创建的 DeepSeek 配置也因此关闭。普通新建配置虽已显式开启，但默认来源不统一。

本次将模型配置的默认 Tool Call 状态统一为开启，并让首次初始化的 DeepSeek 配置使用同一默认值。已保存配置中的显式值保持不变。

作用域：

- `ModelConfigDefaults` 与 `ModelConfigData` 的默认值
- `ModelConfigManager` 的首次 DeepSeek 配置与新建配置
- Tool Call 协议文档中的默认行为说明

实现步骤：

- [01 统一默认值与初始化配置](01_default_value_and_initial_config.md)
