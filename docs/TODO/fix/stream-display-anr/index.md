---
fork: https://github.com/3316891527/Operit
branch: fix/stream-display-anr
status: implementing
---
# 流式显示 ANR 修复

2026-11-08 的五次设备 ANR 主线程均停留在 ProtocolMetaDisplayFilter 的历史前缀正则匹配、字符串复制或其引起的 GC 等待中，包含正常追加与回滚重建两条路径。

- 将显示过滤改为增量解析，保持协议隐藏及已闭合代码示例的显示行为
- 将追加与修订放入后台串行处理，主线程仅更新 Compose 显示状态
- 保存点记录解析状态，回滚恢复状态并处理必要尾部

修改范围限于显示过滤、流式显示适配、必要的文本坐标查询及相关回归。诊断功能另行处理。使用既有构建和测试工作流验证。

- [实现与行为边界](01-implementation.md)
- [验证记录](02-validation.md)
