---
fork: https://github.com/3316891527/Operit
branch: ci/release-1.12.2-plus20
status: prepared
---

# 1.12.2+20 发行集成

维护者已明确授权合并 #106、#107、#108、#109 并发布补丁 20，同时保留 #105 的 1.13.0 累积更新日志。四项来源已按完整 HEAD 合并到 `dev`，每一步的远端文件树均与本地无冲突合并结果一致；#105 保持打开，来源分支及 HEAD 不变。

## 发行范围

本次包含主题高清素材预算、MCP 自动静默加载、复制消息面板布局，以及三个聊天菜单的独立主题配置、互斥上下偏移和完整聊天预览。小预览满宽等比显示并支持滚动与部件定位，全屏完整适配。面向使用者的文字见[更新说明](release-notes.md)。

发行工作分支接入原 `main` 的发布历史，合流不额外改变文件；随后将版本从 `1.12.2+19 / 75` 更新为 `1.12.2+20 / 76`，同步发行说明和维护索引。`1.13.0` 累积日志不纳入本次提交。

## 发布参数

使用 [Build 工作流](../../../../.github/workflows/build.yml)，正式发布引用为 `main`：

- `gradle_task=:app:verifyCiPreviewRelease`
- `publish_release=true`
- `complete_repository_migration=false`
- `previous_tag=v1.12.2+19`
- `release_notes` 使用本目录的完整 Markdown 更新说明

沿用 `com.ai.assistance.operit.kong` 与现有固定签名，仅向 `3316891527/Operit-Kong-patch` 发布完整 APK、增量包和发行摘要。`v1.12.2+19` 已公开且包含完整 APK，不重复执行发行迁移。

## 验证边界

本地只检查合并树、版本递增、发行文件范围、差异空白、文档链接和既有未提交文件保护，不执行编译、构建或测试。发行分支先派发 `publish_release=false` 的签名构建，再创建发行准备 PR 和 `main` 发布同步 PR；正式发布在同步完成后单独派发，不等待或轮询。

#109 最新来源的 Build 与 PR Check 已通过，但不能代替本次集成构建。`main` 的 push 会触发 [Android Tests](../../../../.github/workflows/android-tests.yml)，签名 APK、增量生成和逐字节还原由正式发行工作流校验。HTTP 204 只表示派发受理，不表示发行成功。

主题设备回归继续使用[完整 T01–T19 场景与独立结果表](../../../doc-src/feature-protocol/theme_acceptance_checklist.md)，其他修复保留原有完整回归记录；未取得的设备结果不因发行准备而标记通过。
