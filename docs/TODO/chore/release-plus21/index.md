---
fork: https://github.com/3316891527/Operit
branch: ci/release-1.12.2-plus21
status: prepared
---

# 1.12.2+21 发行集成

维护者已明确授权将除更新日志 #105 外的所有开放 PR 合入开发分支，再同步主分支并发布补丁 21。#119、#120、#121、#122 已按完整 HEAD 合入 `dev`，#105 的来源分支和内容保持原样。

## 发行范围

本次整合自 +20 以来的终端取消恢复、摘要协议过滤、通知打开聊天、Gemini 空片段处理、MCP 启动反馈及斜杠菜单，并包含 A2UI HTML 标签切换修复、同轮表单等待、连续主题气泡外壳与工作室编辑位置记忆。面向使用者的文字见[更新说明](release-notes.md)。模型领域整理及对应回归修复一并随开发分支进入发行。

发行工作分支先接入原 `main` 的 +20 发布历史，接入前后文件树一致；随后将版本从 `1.12.2+20 / 76` 更新为 `1.12.2+21 / 77`，同步发行说明和维护索引。`1.13.0` 累积日志不纳入本次提交。

## 发布参数

使用 [Build 工作流](../../../../.github/workflows/build.yml)，正式发布引用为 `main`：

- `gradle_task=:app:verifyCiPreviewRelease`
- `publish_release=true`
- `complete_repository_migration=false`
- `previous_tag=v1.12.2+20`
- `release_notes` 使用本目录的完整 Markdown 更新说明

`v1.12.2+20` 已公开，包含完整 APK、增量包与发行元数据。继续使用 `com.ai.assistance.operit.kong` 和现有固定签名，仅向 `3316891527/Operit-Kong-patch` 发布，不重复迁移或清理历史发行。

## 验证边界

维护者反馈 #121 和 #122 已分别单独验证通过。尚未逐项取得[气泡完整 B01–B16 结果](../../fix/message-bubble-surface/index.md)和[工作室 M01–M10 结果](../../fix/theme-studio-memory/index.md)，不据此批量填写各用例；[A2UI 等待 W01–W15](../../feat/a2ui-await-submission/index.md)和[HTML A01–A07](../../fix/a2ui-tab-composition/index.md)继续保留原有结果记录。

本地仅检查合并树、版本递增、差异空白、仓库卫生、本地化与文档链接，不执行编译、构建或测试。工作分支先派发 `publish_release=false` 的签名构建及 Android Tests，再创建发行准备 PR；合入 `dev` 后，通过维护者发布同步 PR 将文件树同步到 `main`，最后单独派发正式发布。

固定签名 APK、基础包比对、增量生成及逐字节还原由发行工作流校验。HTTP 204 只表示派发受理，不表示构建或公开发行已经完成。派发后不等待或轮询。
