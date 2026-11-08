---
repo: https://github.com/3316891527/Operit
status: IMPLEMENTED_PENDING_VALIDATION
---

# 模型上下文协议手动启动与元数据兼容

后续加载规则已由[静默加载迭代](../mcp-silent-startup/index.md)更新：主界面自动检查并加载已有启用服务，不再显示加载浮层。本文保留原阶段的实现记录。

移除应用开屏和完成引导后的 MCP 自动启动及启动专用提示，保留用户主动启动服务时的进度和日志。中文界面统一使用模型上下文协议与技能，包管理页签支持完整中文名称。

日志确认 MCPLocalServer.PluginMetadata 的作者被 JSON 空值覆盖，随后在元数据 copy 时触发 NPE。修复配置反序列化，使可选字段使用模型已经声明的默认值，继续检查必要字段；不在界面吞掉异常。

按维护者要求直接在 dev 实现。范围：主界面启动入口、手动加载组件、包管理标签和资源、配置解析及对应回归。实施与完整回归见 [实施记录](1-implementation.md)。
