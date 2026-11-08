---
fork: https://github.com/3316891527/Operit
status: 版本与发行说明已准备，正式产物由仓库工作流生成
---

# 1.12.2+11 发布同步

本次合入已确认的 86 个旧字符串键清理，并发布 1.12.2+11。资源清理基于已包含完整补译的 dev；先将资源清理和版本准备合入 dev，再将 dev 同步到 main，最后从 main 派发正式发布。

已发布的 1.12.2+10 保持不变。本次 versionName 为 `1.12.2+11`，versionCode 为 `67`，使用现有独立发行签名和增量包发布脚本。

本次发行范围由 1.12.2+10 对应提交之后的实际变更确定，包括记忆工作台、语音条、模型工作区与上下文总结、密钥池冷却恢复、工具调用处理，以及多语言补译与旧资源清理。面向用户的内容见 [发行说明](release-notes.md)。

资源清理范围见 [工具箱与旧用户偏好字符串清理](../unused-string-resources/index.md)。本次保留 Kotlin 源码和其他 2,546 个字符串候选。

## 发布输入

- 分支：main
- 工作流：android-build.yml
- gradle_task：`:app:verifyCiPreviewRelease`
- publish_release：true
- previous_tag：`v1.12.2+10`
- release_notes：本目录的中文发行说明

Android Build 会完成现有签名核对、发行 APK 打包和外部资源核对，再发布 APK、发行元数据及从 +10 到 +11 的增量包。构建工作流的派发受理不等同于正式产物已发布。

## 验证范围

86 个资源键在九种语言中同步删除，共 774 条定义；XML、语言键集合及未删除资源内容核对通过。版本准备只改动 versionName 与 versionCode，并补充本目录和维护计划入口。分支提交后派发 Android Debug，正式发行使用 main 上的发行工作流，不在本地启动 Gradle 构建或单测。

版本与发行说明准备 [DONE]。合并和派发结果以实际 PR 与工作流记录为准。
