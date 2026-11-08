---
fork: https://github.com/3316891527/Operit
status: implemented-user-verified
---
# 个人发行版离线资源外置

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

现有 APK 随包携带 Sherpa-NCNN 模型、Ubuntu 安装归档和 Android/Windows 导出模板，完整安装包接近 400 MB。

本轮保留各功能，把上述大资源放到个人仓库的固定资源 Release。共用的 Silero VAD 仍随 APK 分发。新增独立资源模块和设置入口，支持校验后下载、ZIP 导出和离线导入。已安装的终端环境及原有模型路径继续使用。

作用域是宿主语音初始化、终端核心的归档准备接口、网页应用导出、资源管理和 CI 依赖准备。A2UI 和设置搜索不在本轮范围。

步骤及验收见 [资源外置实现](01-implementation.md)。
