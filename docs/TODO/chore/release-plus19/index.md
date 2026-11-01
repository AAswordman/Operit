---
fork: https://github.com/3316891527/Operit
branch: ci/release-1.12.2-plus19
status: prepared
---

# 1.12.2+19 发行集成

维护者已授权将当前所有开放 PR 合入 `dev`，同步 `main` 并发布补丁 19。PR #102、#100、#101、#99 已按完整 HEAD 合并，合并前各自最新 Candidate checks、JVM 检查与独立签名构建均成功。PR 内按路径规则跳过的构建项不计为执行成功。

## 发行范围

本版包含主题脚本与原生工作室、密钥池错误归属、总结重试即时提示及附件成功提示四个刚合并的 PR，同时带上 `dev` 已有的工具权限与文件操作整理、聊天和工作区目录整理、界面标签命名、终端 `apply_patch` 和硅基流动音频修复。具体面向使用者的变化见[更新说明](release-notes.md)。

`main` 的两条独有提交为既往发布同步，合并基线到 `main` 没有文件差异。发布工作分支先接入该历史，保留双方提交；合流本身没有改变任何文件，随后将版本从 `1.12.2+18 / 74` 更新为 `1.12.2+19 / 75`。

## 发布参数

使用 [Build 工作流](../../../../.github/workflows/build.yml)，发布分支为 `main`，参数如下：

- `gradle_task=:app:verifyCiPreviewRelease`
- `publish_release=true`
- `complete_repository_migration=false`
- `previous_tag=v1.12.2+18`
- `release_notes` 使用本目录的完整 Markdown 更新说明

`v1.12.2+18` 已公开，完整 `app-kong.apk` 已存在；准备时 `v1.12.2+19` 尚不存在。发行继续使用 `com.ai.assistance.operit.kong` 和现有固定签名，只向 `3316891527/Operit-Kong-patch` 发布应用与补丁，不重复执行旧发行迁移。名称、应用 ID 和 Logo 调整已暂停。

## 验证边界

本地仅核对合并祖先、无冲突状态、版本递增、差异空白、文档链接和原有未提交文件保护，不执行编译、构建或测试。合并后的 JVM 回归交由 [Android Tests](../../../../.github/workflows/android-tests.yml)，签名 APK、增量生成及逐字节还原交由正式发行工作流。四个来源 PR 的成功检查不能代替本次集成结果，工作流受理也不等于发行成功。

主题继续使用[完整验收场景](../../../doc-src/feature-protocol/theme_acceptance_checklist.md)与[63 项回归及独立结果表](../../feat/theme-script-api/03-verification.md)；其他功能保留各自原有验证资料和未取得的设备结果，不因发行准备而标记通过。

[DONE] 开放 PR 合并、历史合流、版本和说明准备；远端分支同步、工作流受理与最终发行结果以真实远端记录为准。
