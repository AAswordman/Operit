# 验证记录

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

## 已完成

- 核对完整差异、数据库版本和迁移注册，保留 v21/v22 历史升级链已有断言并追加 v27
- 核对内置包白名单、同步脚本未计划产物清理与 PackageManager 的缺失记录清理；手动安装来源继续保留
- 核对修改及新增 Kotlin 的字符串引用、中英文 XML 和重复资源键，执行 `git diff --check`
- 新增 12 项 JVM 回归用例：哈希 3 项、翻译迁移及级联删除 2 项、退役提供商 4 项、功能思考请求及取消 3 项

## 云端验证

在 `3316891527/Operit` 的 `feat/integrated-distribution` 分支推送完成后派发：

```sh
gh workflow run android-build.yml --repo 3316891527/Operit --ref feat/integrated-distribution -f gradle_task=assembleDebug -f publish_release=false
gh workflow run android-tests.yml --repo 3316891527/Operit --ref feat/integrated-distribution
```

Android Build 仅构建使用 Android debug 签名的 Operit Debug，包名为 `com.ai.assistance.operit.debug`，不覆盖现有 Kong 发行版；Android Tests 执行应用 JVM 用例。具体运行链接随本轮提交交付；派发后不等待、不轮询。未确认结果前，不标记构建或测试通过，不创建公开 Release。

## 未运行项

本地未执行编译、构建或测试。Android 界面、外部翻译模型及网络重试按完整清单验收。调试包具有独立数据目录，不能代替 Kong 发行版覆盖安装验收；正式 +5 发布前另核对已分发 +2/+3/+4 的 APK 哈希及增量链。

[DONE] 代码审查与验证方案已整理；功能验收已按用户确认补录为通过；CI 状态见验证结果补录。
