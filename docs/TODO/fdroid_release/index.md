---
For_Agent: Operit F-Droid 上架工作的任务章程与持续入口
Status: PLANNING
Upstream: https://github.com/AAswordman/Operit
Fork: 待确认
ApplicationId: com.ai.assistance.operit（默认保持，除非负责人明确要求独立共存版）
---

# Operit F-Droid 上架任务章程

> 本文档是后续 F-Droid 上架工作的持续任务入口。执行过程中必须持续更新本目录下的分步文档、状态、证据和验证结果；未通过干净的 F-Droid server build 前，不得将任务标记为完成。

你是本项目的 F-Droid 上架负责人。不要只输出分析或建议，要实际完成上游改造、验证、发布准备和 fdroiddata 提交。全程遵守仓库 `AGENTS.md`、F-Droid Inclusion Policy 和最新 fdroidserver scanner 规则。

## 目标

使 `com.ai.assistance.operit` 能从干净源码、在没有 Google Drive 文件和本地缓存的环境中构建，并通过 F-Droid 的 lint、scanner 和 build。

## 已知初步扫描结果（需要重新核实）

- 构建文档要求从 Google Drive 下载 `models.zip`、`subpack.zip`、`jniLibs.zip`、`libs.zip`。目前部分依赖已经存放到 Hugging Face，请核对上游最新 commit 记录。
- 解压目录被 `.gitignore` 忽略，F-Droid 干净检出不会获得这些文件。
- `models.zip` 约 143 MB，包含 Silero VAD、Sherpa-NCNN 等模型，当前缺少完整的来源、版本、SHA-256 和模型许可证记录。
- `subpack.zip` 包含预编译 `android.apk`、Windows ZIP、EXE/DLL/WebView2 文件，尚未发现完整可重建源码。
- `jniLibs.zip` 包含多个预编译 `.so`，包括 ripgrep；部分已有 Rust/CMake 源码或与 Maven/NDK 产物重复。
- `libs.zip` 包含 `ffmpeg-kit-local.aar`、Smart Exception JAR 和 `arsc.jar`，由 `app/build.gradle.kts` 的 `fileTree` 整体加载；其中 `arsc.jar` 可能未使用。
- 仓库内另有 terminal 预编译运行时、`libsherpa-mnn-jni.so`、`shower-server.jar`、辅助 APK/JAR、ARM64 aapt2 等可执行二进制，需要逐个审计。
- `com.google.mlkit` OCR 依赖受 ML Kit 条款约束，极可能构成 F-Droid `NonFreeDep`，不能只声明 Anti-Feature 后继续打包。
- 应用包含自更新/APK 安装、外部 DEX/JAR 加载、脚本和插件市场、pnpm/pip 安装能力，需要按 F-Droid 动态代码政策逐项处理。
- 项目使用远程商业 AI 提供 API 服务，预计需要准确声明 `NonFreeNet`，但这不等于可以保留非自由编译依赖。
- npm/pnpm 锁文件目前被忽略，Gradle wrapper 缺少分发校验值，并存在 JitPack、失效 Bintray、snapshot 仓库。
- `.gitmodules` 含私有 SSH 子模块 `tools/hotbuild/OperitNightlyRelease`，F-Droid 递归初始化会失败。
- 当前代码为 `versionName 1.12.0+4`、`versionCode 44`，已有标签可能复用了同一 versionCode，必须修正发布策略。

## 执行要求

### 1. 发布身份与版本确认

先确认当前版本已正式发布、F-Droid 是否继续使用原 applicationId，以及最终版本号和标签。默认保持 `com.ai.assistance.operit`，除非负责人明确要求独立共存版。

### 2. 构建输入清单

建立完整的构建输入清单：记录每个模型、AAR、JAR、SO、APK、可执行文件和生成资产的来源仓库、固定 commit/tag、SHA-256、许可证、ABI、构建命令、使用位置及是否进入最终 APK。把结果写入项目文档和第三方声明。

### 3. 取消 Google Drive 构建前置条件

- FFmpeg、ripgrep、Sherpa/MNN JNI、terminal、Shower、辅助 APK/JAR、aapt2 等必须从固定版本的自由源码确定性构建，或者从 F-Droid 版本中彻底移除对应功能和产物。
- 删除未使用的 `arsc.jar`、重复 JNI 库及无来源二进制。
- 模型只有在来源、再分发许可和哈希全部明确时才能打包；否则改为用户明确操作的自由模型下载/导入流程，F-Droid APK 不携带这些模型。
- `subpack` 必须找到并发布完整源码及 Linux 构建流程；无法重建时，F-Droid APK 不得包含或暴露依赖它的功能。
- 不得使用 `scanignore`、`scandelete` 或运行时空实现掩盖真正使用的预编译代码。

### 4. 非自由依赖与行为处理

- 用真正自由且可审计的 OCR 实现替换 ML Kit，或在编译层面从 F-Droid APK 完整排除 OCR 模块及相关代码和依赖。
- F-Droid 版本移除自更新、APK 下载/安装入口以及相关权限和组件。
- 对外部 DEX/JAR、插件市场、MCP、pnpm/pip 安装逐项对照官方政策；不符合时必须从 F-Droid 二进制中编译排除，不能只隐藏 UI。
- 审查未受权限保护的导出 receiver 和内联 JavaScript 执行入口。
- 对仍存在的商业 AI API、Tasker 集成等准确填写 Anti-Features，不夸大也不遗漏。

### 5. F-Droid 构建变体

保持正式版现有接口和构建流程可用，增加独立、非 debuggable 的 F-Droid 构建变体，例如 `assembleFdroidRelease`。F-Droid 专属源码集和 Manifest 必须在编译阶段排除不合规代码；不要增加兜底或回退逻辑。

### 6. 许可证闭环

检查 iTextG/AGPL、FFmpeg 及启用编解码器、模型、所有原生库和内嵌工具的许可证兼容性；补齐 SPDX 标识、NOTICE、源码提供方式和修改说明，不要继续无差别删除依法必须保留的 LICENSE/NOTICE。

### 7. 固定全部构建输入

提交 npm/pnpm 锁文件，固定 Node/pnpm/Rust/NDK/CMake/JDK 版本和子模块 commit；Gradle wrapper 使用官方固定 URL 和 SHA-256；移除无用或失效仓库；为必要依赖增加验证信息。Web Chat、ToolPkg 和其他 assets 必须从锁定源码确定性生成。

### 8. 清理私有子模块

将发布工具移出 F-Droid 源码依赖，或改成公开 HTTPS 仓库。确保干净检出只初始化构建必需且固定 commit 的公共子模块。

### 9. Fastlane/F-Droid 展示资料

在上游补齐 Fastlane/F-Droid 展示资料：中英文名称、摘要、完整描述、图标、截图、更新日志、源码地址、Issue 地址、许可证和隐私说明。

### 10. fdroiddata 元数据

在独立 fdroiddata fork 创建 `metadata/com.ai.assistance.operit.yml`，包含准确的 License、Categories、AntiFeatures、源码与 Issue 链接、固定 tag/commit、唯一递增 versionCode、NDK 版本、F-Droid Gradle 任务、源码准备步骤和仅针对无关测试二进制的删除规则。配置可靠的标签更新检查，不引用 Google Drive。

### 11. 干净环境验证

验证必须基于精确发布标签和全新环境，且不存在 ignored 依赖：

- 运行 `fdroid lint`。
- 运行 `fdroid scanner`，逐项解决结果。
- 使用官方 fdroidserver 环境执行 server build。
- 检查 APK 中不存在 ML Kit、自更新安装能力、无来源 DEX/JAR/SO/APK/EXE/DLL。
- 对两个独立干净构建的未签名 APK 使用哈希和 diffoscope 检查可复现性。
- 输出最终 APK 内每个原生库和模型的来源对应表。

## 构建与安全约束
遵守本仓库关于构建的特殊规则：不要直接本地执行 Gradle、Android、FFmpeg、Cargo/NDK 或 APK 构建。所有真实构建必须先将可审查改动提交并推送到可控 Fork 的专用分支，再按 Operit Builder 流程触发；F-Droid lint/scanner/server build 放在独立 fdroiddata/fdroidserver Fork 或 CI 环境中。不得提交令牌、签名文件、本地配置、`.backup/` 或其他工作区状态。当前上游远端只读，在可推送 Fork 建立前只执行源码修改和静态验证。

## 最终交付

- 一个可审查的上游 PR，包含代码、构建脚本、许可证和文档。
- 一个唯一 versionCode 的正式发布标签。
- 一个通过 lint、scanner 和 server build 的 fdroiddata MR。
- 一份简短报告，列出删除/替换的二进制、许可证结论、Anti-Features、验证命令与结果。
- 未通过干净 server build 前，不得声称任务完成。

## 推进方式

先阅读仓库并形成初步计划，再按 `docs/TODO/README.md` 细化和持续更新任务。除版本号、applicationId、发布权限等必须由负责人决定的事项外，自主推进到可提交状态。

## 当前决策门

以下事项需要负责人确认，未确认前可以继续审计和准备，但不得擅自发布：

- [x] 最新正式发布版本已核实为 `v1.12.0`；当前 `main` 的 `1.12.0+4 (44)` 尚未正式发布。
- [ ] 最终 `versionName`、唯一递增的 `versionCode` 与发布标签。
- [ ] 是否继续使用 `com.ai.assistance.operit`（默认：是，待负责人明确确认）。
- [ ] 上游 fork、fdroiddata fork 与对应推送/PR/MR 权限。
## 状态
- [x] 建立 F-Droid 上架任务章程。
- [x] 完成基线、上游同步、正式发布与历史 versionCode 初步核查，详见 `1_BaselineAndReleaseGate.md`。
- [x] 完成二进制与构建输入清单审计，详见 `2_BinaryAndBuildInputInventory.md`。
- [x] 按审计结果补齐后续分步执行文档（步骤 3 至 8）。
- [ ] 完成上游改造与提交准备。
- [ ] 完成独立 fdroiddata 提交准备。
- [ ] 通过干净环境 lint、scanner、server build 与可复现性验证。
