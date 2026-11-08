# 发行版 Material 图标依赖修复

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

## 问题与原因

发行提交 `387ee3e8` 在 `mergeDexRelease` 遇到重复的 `androidx.compose.material.icons.automirrored.filled.ArticleKt`。应用已改用源码子集，但固定提交 `105ed5e4` 的 `terminal` 子模块仍依赖完整 `material-icons-extended`；该库经项目依赖传递进入应用，与子集提供同名类。

## 实现

- 在应用 `:terminal` 项目依赖上排除完整扩展图标库及 Android 制品，保留终端自身的独立编译依赖。最终 APK 使用图标子集与 `material-icons-core:1.7.8`。
- 核对固定版本终端全部 16 个图标引用，仅 `filled/GetApp.kt` 缺失；从项目已锁定 SHA-256 的官方 1.7.8 sources JAR 添加原始源码，静态扩展图标增至 304 个，未改变终端界面行为。
- 图标生成及引用检查显式扫描固定版本终端源码；未初始化子模块时明确报错。使用源码归档时可通过 `--terminal-source` 指定目录。
- Android Build 在初始化终端后检查图标引用，避免终端新增引用后遗漏子集；图标模块和生成器改动纳入 main 的构建触发范围。

## 验证边界

源码归档 SHA-256 与锁定值一致。引用覆盖与静态差异检查以执行记录为准；未运行本地编译或测试，Release 的编译、DEX 合并、签名和发布结果由修复后派发的 GitHub Actions 验证，派发成功不代表已通过。

本轮保持 `versionCode=58`、`versionName=1.12.2+2`、固定包名、签名和 `app-kong.apk` 名称不变。
