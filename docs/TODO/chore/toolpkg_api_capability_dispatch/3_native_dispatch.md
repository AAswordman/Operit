# Native 分发边界

## 目标

移除业务参数里的内部版本字段，避免底层工具处理器感知 ToolPkg API 版本。

## 预期行为

- `toolCall()` 继续通过执行会话携带 ToolPkg API 上下文
- 公开 facade 自己声明并检查能力限制
- native 工具分发不维护 tool 名称到公开 API 名称的集中映射
- 业务工具处理器不接收 `__operit_toolpkg_api_version`
