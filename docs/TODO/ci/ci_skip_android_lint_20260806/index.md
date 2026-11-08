---
For_Agent: Disable Android lint enforcement in PR checks
---

# CI 跳过 Android Lint

## 原本状况

PR 的 Candidate checks 已不执行 `:app:lintDebug`，但仍运行 lint baseline 归一化检查。仓库没有 `app/lint-baseline.xml`，因此该检查会让所有候选提交失败。

## 改动

- 从 PR workflow 删除 lint baseline 快速检查及其聚合门禁
- 删除引用不存在 baseline 的 Gradle 配置和专用归一化脚本
- 更新 CI 与贡献文档，明确 Android lint 当前不属于 PR 校验

## 预期结果

Candidate checks 不再因为 Android lint 或 lint baseline 失败，其他快速检查与按路径运行的构建、单测保持不变。

[DONE]
