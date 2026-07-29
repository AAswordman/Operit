---
fork: local
status: active
---

# 发布耗时日志

市场发布接口原本只将定时任务记录写入 R2。发布请求超时后无法从历史日志区分 GitHub 校验、D1 mutation 和 publisher shard 物化的耗时。

本次改动在 `market-v2` 发布接口记录分阶段耗时，日志异步写入 R2 调试目录。随后部署 Worker，并用临时资源进行一次真实发布验证，最后撤回测试条目并删除临时 GitHub Release。

作用域：

- `assistance_web/workers/market/src/entry.ts`
- `assistance_web/workers/market/src/index.ts`
- `assistance_web/docs/MARKET_API_ENDPOINTS.md`

步骤记录见 [01-publish-timing.md](01-publish-timing.md)。
