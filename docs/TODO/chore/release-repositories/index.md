---
fork: https://github.com/3316891527/Operit
branch: feat/release-repositories
status: release-workflow-prepared
---
# 正式版、补丁与离线资源分仓

从下一补丁版计划切换发行位置。维护者通过 QQ 群或频道分发新完整包，并告知旧版检查更新与资源下载地址停用；本次按此安排直接替换地址。

- Operit 保存源码、源码版本标签与不带加号的正式版 Release
- Operit-Kong-patch 保存全部正式版和补丁版的完整 APK、增量包及元数据
- Operit-Kong-resources 保存固定离线资源，不混入应用更新

本分支已完成客户端地址、发布分流、历史发行复制工具与验证准备，并设置 +15 / 71。先合入 dev，再同步 main，通过签名工作流完成迁移、发行和最后的旧 Release 清理。新模型原有 Hugging Face、ModelScope 及上游固定模型地址继续按原清单使用。

## 实施步骤

1. [客户端与发布分流](01-implementation.md)
2. [迁移、验证与切换](02-migration-and-verification.md)

## 验证状态

尚未运行本地编译、构建或测试。代码完成后从工作分支派发仓库工作流，发行参数保持 publish_release=false，不消耗新的公开版本。

## 激活状态

客户端、工具和 .github/workflows/android-build.yml 已在工作分支接入分仓发布。两个目标仓库和源码仓库 OPERIT_DISTRIBUTION_TOKEN 已配置。历史文件迁移与核对由签名发行工作流在 GitHub 托管环境执行，成功后才进入构建、发布和清理。工作分支可直接运行不发布的 Debug 与 JVM 验证。

只读迁移预览已完成，快照含 15 个 Release、119 个附件、3,658,281,675 字节；已备份源发行元数据，目标仓库已创建，源发行尚未删除；大文件搬迁交由发行工作流完成。

本轮发布与清理顺序见[+15 发行记录](../release-1.12.2-plus-15/index.md)。complete_repository_migration 默认为 false，仅在本次切换发行时显式开启。
