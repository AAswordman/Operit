---
fork: https://github.com/3316891527/Operit
status: 版本与发行说明已准备，正式产物由 main 工作流生成
---

# 1.12.2+13 发布同步

维护者要求核对约八小时内的修复分支，清理已合并分支，将 dev 同步到 main 并正式发布。已确认 #39–#46 八个分支的最新提交全部合入 dev，无需补合并；每个分支的改动文件均与对应合并提交一致，后续叠加修复继续保留。

应用版本为 `1.12.2+13 / 69`，上一正式版本为 `1.12.2+12 / 68`。面向用户的变化见[发行说明](release-notes.md)。正式版沿用 `com.ai.assistance.operit.kong` 包名、固定签名和 `app-kong.apk` 名称。

## 分支核对

<details><summary>已核对的八个分支</summary>

| 分支 | PR | dev 合并提交 | 核对结果 |
| --- | --- | --- | --- |
| `fix/cancel-request-crash` | [#39](https://github.com/3316891527/Operit/pull/39) | `e203badd` | 已合入，源分支改动文件与合并结果一致 |
| `fix/patch-range-validation` | [#40](https://github.com/3316891527/Operit/pull/40) | `e0f8f171` | 已合入，源分支改动文件与合并结果一致 |
| `fix/voice-transcription-prompt` | [#41](https://github.com/3316891527/Operit/pull/41) | `4d657807` | 已合入，源分支改动文件与合并结果一致 |
| `fix/expired-media-tags` | [#42](https://github.com/3316891527/Operit/pull/42) | `96b44e58` | 已合入，源分支改动文件与合并结果一致 |
| `feat/voice-context-usage` | [#43](https://github.com/3316891527/Operit/pull/43) | `3a232219` | 已合入，源分支改动文件与合并结果一致 |
| `fix/duplicate-tool-errors` | [#44](https://github.com/3316891527/Operit/pull/44) | `76336d49` | 已合入，源分支改动文件与合并结果一致 |
| `fix/expired-media-alignment` | [#45](https://github.com/3316891527/Operit/pull/45) | `4385d4aa` | 已合入，源分支改动文件与合并结果一致 |
| `fix/mcp-manual-start` | [#46](https://github.com/3316891527/Operit/pull/46) | `fc74dbf7` | 已合入，源分支改动文件与合并结果一致 |

</details>

八个分支的本地和远端引用均已删除；四个对应的干净临时工作区已清理，保留 dev 和 main。

## 发布输入

- 分支：main
- 工作流：android-build.yml
- gradle_task：`:app:verifyCiPreviewRelease`
- publish_release：true
- previous_tag：`v1.12.2+12`
- release_notes：本目录的中文发行说明

工作流检查正式签名、包名、版本和资源打包，生成完整 APK、从 +12 到 +13 的增量包及元数据，并逐字节还原后比对目标 APK 的 SHA-256。文件齐备后公开 Release。派发受理与正式发布完成分别记录。

## 已确认验证

- dev 提交 `fc74dbf78a939f1c77a858d2abefe2c79e4b44d5` 的 [Debug 构建](https://github.com/3316891527/Operit/actions/runs/40199253010)通过
- 与该 dev 提交文件内容一致的 `4461c1a12078df6adbce7fb7f7860ac8a4a2f801` 的 [完整 JVM 测试](https://github.com/3316891527/Operit/actions/runs/40198980173)通过
- 本次仅更新版本和发行文档，静态检查后在工作分支派发仓库构建；正式发行由 main 工作流执行

完整回归材料继续使用[语音与累计统计](../../feat/voice-context-usage/verification.md)、[工具错误与终端](../../fix/duplicate-tool-errors/1-implementation.md)、[过期媒体布局](../../fix/expired-media-alignment/1-implementation.md)和[模型上下文协议](../../fix/mcp-manual-start/verification.md)记录。

版本与发行说明准备 [DONE]，正式产物结果以发布工作流和 Release 为准。
