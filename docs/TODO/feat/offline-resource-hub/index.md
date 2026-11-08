---
fork: https://github.com/3316891527/Operit
branch: feat/offline-resource-workbench
status: IMPLEMENTED_PENDING_CI
---
# 离线资源与本地模型

已按认可的 HTML 稿落实 Android 原生工作区，分为本地模型与其他资源。保留原有四组资源，加入五个 Sherpa ONNX 语音模型、九种工程模板、APK 逆向资源与 ML Kit 模型资产，并将语音设置接到同一安装记录。

- [设计稿](design.html)
- [资源与模型来源](resource-map.md)
- [原生实现与兼容](01-native-implementation.md)
- [验证与完整回归](02-verification.md)
- [资源准备的输入范围与日志](03-asset-preparation.md)

资源已扩展为 51 个资源包，目录与校验文件已[公开发布](https://github.com/3316891527/Operit/releases/tag/resources-v2-27aa8eec7b5c78a17629)。统一分支提交 `14e566ea776e9027300ef20a193011b2913916cf` 的 Debug 与 JVM 已通过；本轮清单和设置入口调整将在新提交上派发验证。设备验证记录按实际验收填写。

Ubuntu 开发环境预装及配置入口整理继续后置。分支继承已获认可的语音配置实现；未自行合并其他工作分支。

第二轮计划见 [资源反馈与内置扩展按需安装](04-builtin-downloads.md)。

本轮收尾见 [本地模型清单与设置入口](05-storage-and-settings.md)。
