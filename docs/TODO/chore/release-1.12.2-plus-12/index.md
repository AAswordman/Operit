---
fork: https://github.com/3316891527/Operit
status: 版本与发行说明已准备，正式产物由仓库工作流生成
---

# 1.12.2+12 发布同步

本次将 [PR #37](https://github.com/3316891527/Operit/pull/37) 的界面、本地化、工作区及媒体修复合入 dev，再由维护者将 dev 同步到 main，从 main 发布正式 Release。应用版本名为 `1.12.2+12`，内部版本号为 `68`；上一正式版为 `1.12.2+11 / 67`。

面向用户的变化见 [发行说明](release-notes.md)，具体功能与回归范围见 [界面与媒体交互修复](../../fix/interface-media-polish/index.md)。正式版沿用既有 `com.ai.assistance.operit.kong` 包名、固定签名和 `app-kong.apk` 名称；开发测试继续使用独立 Debug 变体。

## 发布输入

- 分支：main
- 工作流：android-build.yml
- gradle_task：`:app:verifyCiPreviewRelease`
- publish_release：true
- previous_tag：`v1.12.2+11`
- release_notes：本目录的中文发行说明

工作流校验正式签名、包名、资源和版本，生成完整 APK、从 +11 到 +12 的增量包及配套元数据，文件齐备后再公开 Release。派发受理不代表产物已经发布。

## 已确认验证

- 功能提交 `c581ea8a6ce0ef28508e3f0e27cfda803fff40e5` 的 [Android Debug 构建](https://github.com/3316891527/Operit/actions/runs/40162895047)通过。
- 功能提交 `e3249d8f0d13cf62e50629702e1e1b5bc9106ead` 的 [Android JVM 测试](https://github.com/3316891527/Operit/actions/runs/40159089986)通过。
- 维护者已安装测试版本，反馈原九项验收合格，语音条能够正确生成附件。
- 本次版本准备只更新应用版本和发布文档；静态核对后在工作分支派发 Debug 构建，正式发行校验由 main 发布工作流执行。

版本与发行说明准备 [DONE]。合并和正式发布结果以实际 PR 与工作流记录为准。
