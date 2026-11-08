---
fork_repository: https://github.com/AAswordman/Operit.git
status: completed
---

# 工作流启动前异常状态

## 原本状况

定时工作流由仓库层写入 `RUNNING`，再交给执行器生成运行记录和最终结果。

若执行器尚未创建运行记录便发生异常，仓库层仍会把工作流状态写成 `FAILED`。这会让工作流状态、执行次数和 `_execution_logs` 中的记录彼此不一致。

## 意图与结果

每次失败都必须持久化包含 `runId`、失败阶段和异常原文的运行记录，再同步最终状态与统计信息。

运行环境初始化失败由调度端以退避策略延迟重试。节点和工作流逻辑失败仍按本次运行的最终失败处理。

## 作用域

- `WorkflowRepository` 的启动、状态与统计更新边界
- `WorkflowWorker` 与 `WorkflowScheduler` 对可重试启动异常的处理
- 工作流日志模型与日志视图的失败阶段展示
- 此目录中的修复记录

## PR

未创建

[DONE]
