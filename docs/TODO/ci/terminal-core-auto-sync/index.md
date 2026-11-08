---
作者仓库: https://github.com/3316891527/Operit
终端仓库: https://github.com/3316891527/OperitTerminalCore
状态: 已部署，dev 与 main 真实工作流均成功
---

# 终端核心默认分支自动同步

## 现状与目标

实施前，主应用通过 terminal 子模块采用的终端提交已推送到独立仓库的工作分支，但默认分支 master 仍停留在旧版本，导致仓库首页显示的源码落后于应用。

现已将默认分支沿已有历史推进到应用实际采用的提交，并在主应用 dev、main 更新终端引用后自动执行。终端代码继续作为独立仓库维护。

## 01 同步规则 [DONE]

读取两个可信应用分支的最新 gitlink，核对目标仓库并选择沿历史较新的终端版本，再与独立仓库默认分支比较。新提交只作快进，已一致或已包含的提交不更新，分叉直接报告错误。使用 GitHub 的非强制引用更新，完整保留提交历史和固定的源码版本。

## 02 自动触发 [DONE]

Sync terminal core 工作流在 dev、main 的终端 gitlink、来源配置或同步实现变化时触发，并提供手动入口。两个分支共用并发组；每次重新读取最新引用，覆盖等待事件被合并的情况。工作流只在维护者仓库的可信分支运行，使用既有 OPERIT_DISTRIBUTION_TOKEN 更新终端仓库。开发与权限说明见 [CI 文档](../../../../ci/README.md#terminal-core-sync)。

## 03 部署与核对 [DONE]

2026-11-08 已通过 [PR #167](https://github.com/3316891527/Operit/pull/167) 启用 dev 工作流，通过 [PR #168](https://github.com/3316891527/Operit/pull/168) 同步 main 工作流。两个分支采用同一份同步实现。

- [dev 自动运行](https://github.com/3316891527/Operit/actions/runs/42497229657)成功，将终端 master 从 857af28a490716feefbb7b78e98273e77a3f0cfe 快进到 62dbf46a0c1af5255eb22f0c48ca215974ab1a10，补齐四个提交、29 个文件的差异
- [main 自动运行](https://github.com/3316891527/Operit/actions/runs/42497314079)成功，识别为版本已一致，没有产生额外终端提交
- GitHub API 再次确认：Operit 的 dev、main 终端 gitlink 与 OperitTerminalCore 的 master 均为 62dbf46a0c1af5255eb22f0c48ca215974ab1a10

本地 Python AST 语法与差异空白检查通过。PR #167 的 Fast checks 和 Candidate checks 通过；main 合并候选的完整 Git tree 与该审核树一致。未手动运行编译、构建或测试，Android 构建及 JVM 测试未触发。两个同步动作使用真实 GitHub Actions 和现有跨仓库凭据执行，运行日志已确认更新前后版本。

本任务未修改应用业务源码、终端代码或 App 版本，未发布安装包或资源。
