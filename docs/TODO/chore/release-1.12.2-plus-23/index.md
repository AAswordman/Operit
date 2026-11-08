---
repo: https://github.com/3316891527/Operit
branch: ci/release-1.12.2-23
status: prepared
---

# 1.12.2+23 正式发行

将已完成联合测试的 #147–#161 全部 15 个来源 HEAD 合入 dev，保留 main 已有历史，再同步 main 发布。#105 仍排除。版本名为 1.12.2+23、versionCode 79，沿用 Kong 包名及既有固定签名，增量基线为 v1.12.2+22。

[发行说明](../../../../ci/distribution/updates/releases/1.12.2+23.md) · [联合测试移交](../../ci/open-pr-integration-20261108/02-release-handoff.md)

## 已有验证

- [联合 Debug 构建，第 5 次尝试](https://github.com/3316891527/Operit/actions/runs/42465317894/attempts/5)：success。
- [联合 PR Check，第 5 次尝试](https://github.com/3316891527/Operit/actions/runs/42465314656/attempts/5)：success，包含 Android build 和 Android JVM tests。
- [Android Tests，第 2 次尝试](https://github.com/3316891527/Operit/actions/runs/42465320321/attempts/2)：success。
- 用户反馈其余功能基本通过；GIF 参数缺失已核对为独立资源尚未发布，非 PR 漏合。正式发行后仍需更新并验收新的绘图资源，不把旧资源的设备使用结果记为新版 GIF 已通过。

## 临时检查清理

世界书临时 job 及其新增必过门槛已在联合分支移除，保留 [24/24 世界书回归证据](https://github.com/3316891527/Operit/actions/runs/42457174369/job/128658664317)。其余为本轮修复临时增加的 native_ripgrep cargo test 调用和 terminal 单测模块调用，在联合 PR Check 通过后移除；原有通用 PR Check 与所有回归源码保留。发行分支的 PR Check 内容恢复为本轮 dev 基线。

## 发行计划

1. 核对 15 个来源 HEAD 覆盖、排除 #105、版本递增和静态检查。
2. 以非 force 更新先合入 dev，再同步 main，保留原 main 历史。
3. 从 main 派发现有 Build，任务为 :app:verifyCiPreviewRelease，publish_release=true，previous_tag=v1.12.2+22。
4. 按现有流程从同一提交准备和校验当前内置资源，先公开资源，再公开 APK、增量包和摘要。

本次关闭仓库迁移和合并分支清理，不删除原功能分支，不创建源码仓库应用 Release。工作流受理与实际发布成功分开记录，不等待工作流完成。最终签名、APK 与资源一致性、增量还原及公开结果以正式发行工作流为准。

[DONE] 发行范围与版本准备；静态检查、分支同步及派发记录保存在本轮仓库外的发行证据目录。正式发布结果仍待工作流。
