---
fork: https://github.com/yoruuuchan/Operit
branch: fix/memory-autosave-reliability
status: implementation-done
---

# 实现记录

## 1. 轮询循环异常隔离

`MemoryAutoSaveScheduler` 原本在 `Application` 级 `SupervisorJob` 中运行：

```
while (isActive) {
    delay(LOOP_TICK_MS)
    runOnce()
}
```

`runOnce()` 依赖的设置、DataStore 或 ObjectBox 一旦抛异常，协程直接结束，后续自动保存永久停摆。

抽出 `MemoryAutoSavePollingLoop`：

- `delay` 和 `onTick` 位于 `try` 内。
- `CancellationException` 先捕获并原样抛出，保留结构化取消语义。
- 其他 `Exception` 交给 `onError` 记录，循环继续下一轮。
- 首次 tick 前可以执行一次 `onStart` 恢复；`onStart` 失败同样只记录，不阻止轮询启动。

`runOnce()` 的 `isRunning` 仍由 `try/finally` 释放；`scanAndProcessCandidates` 中每个记忆空间单独 `try/catch`，一个空间失败不会中断同轮其他空间。

## 2. 有限重试与死信

`MemoryAutoSaveCandidate` 增加状态迁移：

- `beginProcessing()`：进入 processing，清空 `lastError`。
- `markFailed()`：`attemptCount += 1`；达到 `MAX_ATTEMPTS = 3` 时直接进入 `dead_letter`，否则 `failed`。
- `restorePending()`：协程取消时恢复 pending，不消耗重试次数。
- `recoverInterrupted()`：进程中断恢复时计入一次尝试；达到上限同样进入 `dead_letter`。
- `isRetryable()`：只有 pending 或未达上限的 failed 才可再次处理。

`MemoryAutoSaveCandidateRepository`：

- `getPendingAndFailedCandidates()` 查询后按 `isRetryable()` 过滤，死信不再进入模型调用。
- `markFailed()` 返回本次进入死信的数量，调度器据此输出明确日志。
- `expireExhaustedCandidates()` 把旧版本遗留的、`attemptCount` 已达上限的 `failed` 行迁移为 `dead_letter`，避免它们既不重试也不可见。
- 候选总条数阈值只约束全新的 pending 候选；已有重试次数的候选（失败或中断恢复）不再等待凑满 5 条，保证重试策略能真正执行完。

## 3. 进程中断恢复

`recoverInterruptedCandidates()` 查询所有 `processing` 候选并调用 `recoverInterrupted()`，把它们恢复为 pending（或达到上限时转为死信）。

调用时机：

- 调度器启动、第一轮 tick 之前立即执行一次；
- 每轮扫描开始时再次执行。`runOnce()` 由 `isRunning` 串行化，且恢复发生在任何候选被标记 processing 之前，所以扫描看到的 processing 行只可能来自已经死亡的进程。

这样启动恢复失败不会被永久跳过，下一轮扫描仍会重试恢复。

## 4. `{}` 的语义与可观测性

`MemoryLibrary` 新增：

```kotlin
enum class MemorySaveResult { SAVED, NO_MEMORY_EXTRACTED }
```

- `ParsedAnalysis.isEmpty()` 统一判断空分析；`{}`、全空数组都归为「无需保存」。
- `saveMemory()` 现在返回 `MemorySaveResult`：成功写入返回 `SAVED`，模型明确没有可保存内容返回 `NO_MEMORY_EXTRACTED`。
- 删除了原先 `propagateAnalysisFailure` / `propagateFailure` 的静默吞异常分支；模型失败、JSON 解析失败和图谱持久化失败一律抛出，自动保存据此标记失败并进入重试。
- 自动保存对 `SAVED` 与 `NO_MEMORY_EXTRACTED` 分别记录日志，但都会删除候选（本轮已成功处理）。
- 手动保存回调拿到结果，`NO_MEMORY_EXTRACTED` 时提示「模型判断本次对话无需保存记忆」，不再显示「记忆已手动更新」。

持久化可观测状态：

- `MemoryAutoSaveOutcome`：`IDLE` / `SAVED` / `NO_MEMORY_EXTRACTED` / `FAILED`。
- 每轮记忆空间处理结束后按失败优先、其次已保存、再次无需保存的顺序汇总写入该 profile 的 `SharedPreferences`。
- 聊天输入菜单的自动保存详情新增「上次自动保存」与「已停止自动重试」两行，用户可以直接区分正常无需保存、失败和死信。

## 5. 取消语义

`processChatCandidateGroup` 先捕获 `CancellationException`：

- 调用 `restorePending()` 把候选放回 pending，不增加 `attemptCount`；
- 然后原样抛出，让调度器协程正常结束。

`MemoryLibrary` 中图谱保存、分析生成、profile 文档保存的 catch 均先处理 `CancellationException` 并 rethrow。已有记忆数据不会被删除；取消只会撤销候选状态。

## 6. 作用域外

- 未改动 `MIN_TOTAL_CANDIDATES_TO_EXTRACT = 5` 对全新候选的累计策略，只让它不阻塞重试。
- 未新增 ObjectBox 字段，候选表结构不变；新增状态值只是字符串。
- 未改动记忆图谱写入和去重逻辑。
