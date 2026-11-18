# 步骤 1：落库可观测性

## 旧实现

`TokenTrackingAIService.RequestTracker.finish()` 在四个条件下返回 null：
cancelled、成功 attempt 未记录（usage 从未上报）、快照缺失、快照无已知字段。
所有分支静默返回，`persist` 跳过时同样无日志。`OpenAIProvider.applyUsageToCounters`
在 `parseUsageCounts` 返回 null 时静默 early-return，无法区分"端点没回 usage"
与"回了但格式不识别"。GLM-5.2 排查需要翻数据库才能确认落库缺失。

## 意图

每个跳过分支记录原因与身份信息（configId、providerModel），usage 解析失败
记录端点原始键集合。日志是观测手段，不改变任何跳过行为。

## 期待的新实现

- finish() 的每个 null 分支输出 `AppLogger.w`，带跳过原因。
- usage 非 null 但解析失败时输出 `AppLogger.d`，带字段名列表。
- usage 为 null 的分块不输出日志（每个内容分块都会命中，逐条输出会刷屏）；
  该场景由 finish() 的"成功 attempt 无 usage 上报"日志兜住观测面。

[DONE]
