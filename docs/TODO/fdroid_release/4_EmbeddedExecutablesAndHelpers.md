---
title: 后续步骤：内嵌 APK、JAR、ELF、AAPT2 与密钥库
status: planned
document_type: implementation-step
depends_on: 2_BinaryAndBuildInputInventory.md
---
# 内嵌 APK、JAR、ELF、AAPT2 与密钥库

## 目标
消除不可重建的内嵌执行制品；确需保留者必须由固定自由源码在构建中生成。

## 最小执行单元
1. 逐项处理 accessibility、desktop、Shizuku APK。
2. 为 Shower、shell launcher 和 APKTool/JADX 运行时建立 Linux 确定性构建，或从 F-Droid 编译排除。
3. 审计并处理两份 AAPT2、Gradle wrapper 与模板构建能力。
4. 移除内嵌未知 JKS，采用明确的非秘密生成流程。

## 执行记录

### 4.1 Shizuku 内嵌 APK 与安装入口

静态消费者核查确认：

- `ShizukuAuthorizer` 通过自由的 Shizuku API 连接用户自行安装的 Shizuku 或 Sui，负责检测 binder、请求权限和校验 UID；该交互本身可以保留；
- `ShizukuInstaller` 从 `app/src/main/assets/shizuku.apk` 提取 APK，通过 FileProvider 和 package installer 启动安装，并读取 `shizuku_version.txt` 比较内置/已安装版本；
- `ShizukuDemoScreen`、`ShizukuWizardCard` 和 `PermissionLevelCard` 提供内置安装、内置更新和版本比较入口；
- `app/src/main/assets/README.md` 要求开发者手工下载、改名和复制 Shizuku APK，没有固定来源 URL、commit、APK SHA-256 或源码构建对应；
- 该 APK 会作为 main assets 进入所有现有变体，不能用于 F-Droid。

处置决策：

- 正式版当前接口暂不修改，避免在独立 variant 尚未建立前破坏现有流程；
- F-Droid 变体不得携带、下载、提取、安装或更新 Shizuku APK；
- F-Droid 专属源码集不编译 `ShizukuInstaller` 及内置版本比较实现，UI 也不编译内置安装/更新入口；不能用“文件不存在后打开网页”等运行时回退掩盖；
- F-Droid 仍保留对用户自行安装的 Shizuku/Sui 的检测、打开、授权请求和官方安装说明；这不是内嵌可执行文件分发；
- `shizuku.apk` 与 `shizuku_version.txt` 必须从 F-Droid assets source set 的编译输入中排除，并在最终 APK 内容审计中验证不存在。

Manifest/权限边界：

- 主 Manifest 声明 `android.permission.REQUEST_INSTALL_PACKAGES`，但其消费者不只 Shizuku，还包括自更新、补丁安装、辅助 APK 和通用系统操作工具；本单元不能只删权限而留下其他安装代码；
- F-Droid variant 建立时必须在 Manifest 合并阶段移除该权限，并在编译阶段排除所有 APK 安装消费者；
- FileProvider 同时服务普通附件、图片和文件分享，不能因移除 APK 安装能力而整体删除；F-Droid 版应保留最小必要 FileProvider 路径并审计其 exported/grantUriPermissions 配置。

本单元只建立编译排除契约，实际 source set/Manifest 改造在 `8_FdroidVariantAndReproducibleBuild.md` 对应步骤统一完成，以避免先写临时 BuildConfig 分支或运行时空实现。

## 验收
- 建立源码、版本、许可证、哈希、构建命令、消费者及 APK 结果的对应表。
- F-Droid APK 不包含无法重建或来源不明的执行制品。
- 未完成代码改造和验证前不添加 `[DONE]`。
