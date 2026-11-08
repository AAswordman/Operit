---
fork: https://github.com/3316891527/Operit
status: implemented
---

# 工作流发布与 dev 检查同步

发布步骤被作为整份 android-build.yml 保存时，顶层变成 YAML 列表，GitHub 报 A sequence was not expected，任务不会开始。本次以完整工作流为基础，仅替换发布步骤，保留构建、签名、产物上传和固定资源发布任务。

发布步骤在当前检出副本内明确调整发布脚本：发行目的地固定为 Operit-Kong-patch，取消创建主仓库源码 tag，并从摘要移除 sourceTag。源码提交、归档链接、APK/增量校验和附件上传保留。规则匹配次数不为一时明确失败，不静默跳过。历史迁移开关继续保持 false，已有 tag 不被删除，正在运行的旧提交不受修改影响。

pr-check.yml 将 pull_request.branches 从 development 改为 dev，保留事件类型、merge candidate 校验和 Candidate checks 汇总。后续目标为 dev 的 PR 可按该过滤规则触发检查。

## 静态验证

两份 YAML 顶层结构、全部 run 块的 bash -n、内嵌 Python 和变换后发布脚本的 AST 解析，以及 actionlint 1.7.12 检查通过。actionlint 使用 ARM64 版本，并按官方校验清单核对 SHA-256；未启用外部 shellcheck/pyflakes。没有本地构建或测试，也没有实际发布或删除 tag。

交付时派发工作分支 Debug 构建，不开启发行；受理和执行结果分别记录，派发后不等待或轮询 CI。下载工具消失的问题按维护者要求暂不检查。

[DONE] 源码修改与静态验证完成，远端同步与工作流受理另存交付收据。

## 当前构建工作流入口

构建工作流文件已由 `.github/workflows/android-build.yml` 改名为 `.github/workflows/build.yml`，Actions 显示名称为 `Build`。使用文件名派发时改为 `gh workflow run build.yml`；构建任务、手动输入、签名、发行目的地和固定资源发布逻辑沿用原配置。

工作流自身的 push 路径过滤、PR 变更范围识别及原有路径回归数据同步采用新文件名。历史说明中的旧文件名记录当时入口，当前操作统一使用 `build.yml`。

本轮仅进行 YAML 结构与前后等价核对、修改 Python 的 AST 解析及差异空白检查，没有执行本地编译、构建或测试。编号从实际创建的新工作流运行记录确认，文件改名本身不作为已重置的验证结果。
