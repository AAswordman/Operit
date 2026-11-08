---
repo: https://github.com/3316891527/Operit
branch: feat/integrated-distribution
status: implemented-user-verified
---

# 个人发行版设置与消息体验整理

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

本轮实现用户提出的 11 项调整，兼容已经发布的个人发行版。源码完成后派发 Android Build 和 Android Tests；构建、JVM 用例和 Android 界面验收分别记录，不将已派发当作通过。

1. [设置、内置包与发行版界面](01-settings-and-distribution.md)：移除 3 个内置插件、密钥池 Toast、模型搜索、心流迁移、功能思考控制、角色卡入口、离线资源目录、存储加载态、关于页及消息时间
2. [原生消息翻译](02-native-translation.md)：按上游 PR #1036 接入长按翻译及持久缓存，适配本分支数据库 v26 → v27
3. [完整回归清单](03-regression.md)：保留前轮 61 项并补充本轮 32 项，共 93 项及独立结果表
4. [验证记录](04-validation.md)：静态检查、云端派发命令及未运行项

此前已经向粉丝分发 `1.12.2+2/+3/+4`，不能再将正式更新版本回退为 `+2`。本轮包含 11 项改动的源码版本设为 `1.12.2+4`、versionCode 57，只派发 `assembleDebug` 和 Android Tests。调试包名为 `com.ai.assistance.operit.debug`，名称为 Operit Debug，与固定签名的 `com.ai.assistance.operit.kong` 发行版并存。用户测试通过后，再将正式版本改为 `1.12.2+5`、versionCode 至少 58，并单独构建发布增量更新。后续开发验证统一使用调试包，本轮不创建 Release。
