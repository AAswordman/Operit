---
status: in-progress
---

# 发布阶段日志

旧实现只在 cron 执行时记录日志。发布接口会在写入 D1 后同步物化私有 publisher shard，但没有记录每个阶段的耗时。

新实现记录发布请求的总耗时和以下阶段：

- 市场 session 校验
- 发布者写入
- GitHub Release 资产校验
- 项目版本查询
- D1 mutation
- publisher shard 物化

日志写入 `market/v2/debug/publish/`，使用 Worker `waitUntil`，不阻塞发布响应。
