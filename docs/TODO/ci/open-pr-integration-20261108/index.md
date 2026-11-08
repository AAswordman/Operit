---
repo: https://github.com/3316891527/Operit
branch: test/open-pr-integration-20261108
---

# Open PR 联合测试

以 `dev` 的 `e9bb154a631300ec6fd7d9a073b50d58792aca38` 为基线，将 2026-11-08 核对的 open PR #147–#161 合入独立测试分支；#105 不在合入范围。原 PR 及其分支保持独立。

[实施与验证](01-integration.md) · [返回 CI 计划](../README.md)

## 计划

1. 固定各 PR 的远端 HEAD，保留合并来源。
2. 依次合并并按功能语义处理冲突，保留此前用户验收的行为。
3. 检查全部 15 个 HEAD 的祖先覆盖、冲突标记和仓库静态规则。
4. 推送测试分支并派发现有 Android 构建与测试；不发布 Release，不等待 CI 结束。

诊断功能不在本轮范围。临时专项 PR 检查按既有约定在通过并保留证据后清理，通用检查保留。
