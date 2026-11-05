---
fork: https://github.com/yoruuuchan/Operit
branch: fix/memory-autosave-reliability
status: verification-done
---

# 长期记忆自动保存可靠性修复

- [01_implementation.md](01_implementation.md)：实现细节与状态机
- [02_verification.md](02_verification.md)：测试、构建与本地化验证记录

## 原状

`MemoryAutoSaveScheduler` 的轮询协程没有异常隔离，`runOnce()` 抛出的异常会结束整个轮询循环。`MemoryAutoSaveCandidateRepository` 的失败候选没有重试上限，`FAILED` 候选会按轮询间隔反复调用模型；`PROCESSING` 候选没有回收路径，进程中断后会永久滞留在处理中状态。模型返回 `{}` 时，自动保存路径把它当作成功并删除候选，日志无法区分「无需保存」与「保存失败」。

## 修改意图

保留现有候选表结构和已有记忆数据，只补充状态与策略：单轮异常留在轮询层处理，协程取消继续向上传播；每个候选记录明确的重试次数，超过上限进入 `dead_letter` 终态并在状态详情中可见；调度器每轮扫描前恢复进程中断遗留的 `PROCESSING` 候选；`saveMemoryNow()` 返回保存结果，调用方据此区分已写入、无需保存和失败。

## 作用域

- `MemoryAutoSaveScheduler`：轮询循环、按记忆空间隔离异常、候选恢复、保存结果记录
- `MemoryAutoSaveCandidate`：重试常量、状态迁移方法
- `MemoryAutoSaveCandidateRepository`：可重试候选查询、失败/取消/恢复状态写入、死信计数
- `MemoryLibrary`：保存结果类型与空分析判断
- 自动保存状态详情与手动保存反馈的资源字符串
- JVM 单元测试覆盖轮询异常、取消、重试上限和进程恢复

## 验收条件

- 单轮异常不会结束轮询循环，下一轮仍会执行
- 协程取消不会被当作保存失败，取消后候选恢复为待处理
- 每个候选最多尝试三次，超过后进入 `dead_letter`，不再消耗模型请求
- 应用进程重启后的第一轮扫描会恢复遗留的 `PROCESSING` 候选
- 模型返回 `{}` 被记录为「无需保存」，与实际保存失败在日志和用户界面中可区分

## [DONE]

实现与验证均已完成，结果见 `02_verification.md`。未在真机执行强制杀进程测试；进程恢复由候选状态单测和调度器调用路径覆盖。
