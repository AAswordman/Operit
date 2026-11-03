---
repo: https://github.com/3316891527/Operit
branch: feat/distribution-a2ui
source: https://github.com/CATMIAOZHI/Operit/tree/9902726bec086d2950e6472c14f6acb9cca85413
status: implemented-user-verified
---

# 原生 A2UI 与包体优化

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

本轮以包含 11 项设置及原生翻译调整的个人分支提交 `6803712c` 为基础。在本分支的 MessageSection 和长消息分片路径接入原生卡片，并减少完整 Material 图标库带来的安装包开销。

模型可直接输出完整有效的 `a2ui` 围栏，也可通过五个原生工具编译 Express、修订卡片和更新 live 数据。语法与用法由原生 `a2ui_catalog` 提供；外部示例引导脚本、旧内置生成脚本及对应 JS 生成桥接均已移除。

宿主、工具与测试统一使用 a2ui 包路径，类型和文件名使用 A2ui 前缀，字段、资源键与私有状态目录使用 a2ui 命名。普通卡片状态和 live 快照保存在应用私有 `files/a2ui/<聊天指纹>/`。功能尚未正式发布，直接使用新目录，不迁移旧调试数据。

配套保留 128 个 JVM 用例，A2UI 完整实机清单已扩充到 48 项并覆盖 55 种组件，原发行版整体回归清单继续保留。命名与目录统一版本 `0dfbea95` 的云端 Debug 和 Android Tests 已通过；本轮补充 live 输入校验、错误分类及说明后重新派发两条工作流，结果以本轮实际运行和用户实机验收为准。未执行本地构建或测试。

- [依赖、来源与包体输入](01-dependencies-and-size.md)
- [消息、交互与状态接入](02-chat-integration.md)
- [完整回归清单及结果表](03-regression.md)
- [调试反馈修正](04-debug-feedback.md)
- [原生工具与外部可选引导](05-native-tools-and-optional-guide.md)
- [命名与私有状态目录统一](06-a2ui-naming-and-storage.md)
- [live 校验与说明修复](07-live-validation-and-guide.md)
- [完整 A2UI 实机回归](08-a2ui-device-regression.md)
- [发行图标依赖修复](09-release-icon-dependency.md)
- [A2UI 工具说明](../../../agent/a2ui.md)

开发验证只使用 Operit Debug。用户已授权本轮正式发行，展示版本设为 `1.12.2+2`，内部 versionCode 递增至 `58`，Kong 应用显示名、包名和签名保持一致。通过个人仓库 `main` 的 Android Build 工作流执行 `:app:verifyCiPreviewRelease` 并启用 `publish_release`，构建验证成功后由工作流发布正式 Release；工作流已派发不代表构建或发布已完成，不轮询 CI。

低于 `+2` 的同基础版本只有在已接入个人更新源、存在正式基础 APK 且补丁哈希匹配时才能增量更新；当前没有历史正式 APK Release，本次建立完整包基线。此前展示为 `+2/+3/+4` 的旧包使用完整 APK 覆盖安装，Android 通过递增的 versionCode 接受升级；既有客户端按展示版本比较，需从 Release 或分发渠道获取完整包，不能承诺旧客户端会自动提示这个同名或较低展示版本。
