---
title: 第 2 步：二进制与构建输入清单
status: done
document_type: audit-step
For_Agent: 本文结论固定于 a5785726；未知项不得推测为合规
baseline: a57857263f3291b41cf306a95ebcbe0e5c2b1373
last_reviewed: 2026-07-27
---
# 第 2 步：二进制与构建输入清单

## 范围与方法

本次只做静态审计，未下载 Google Drive 归档，未执行 Gradle、npm、pnpm、Rust、CMake 构建或测试。使用了 `git ls-files`、文件魔数扫描、`sha256sum`、`readelf`、ZIP/JAR/APK 列表检查和 `rg` 引用检索。工作树中的 `.backup/` 和本 TODO 目录在审计开始前已是未跟踪内容，不作为基线输入。

结论仅对应提交 `a57857263f3291b41cf306a95ebcbe0e5c2b1373`。字段中的“进入 APK”是由 Android source set、项目依赖或显式复制关系静态推导；标成“待验证”的项目必须在后续干净构建中用依赖图和 APK 内容复核。

处置词限定为：`从固定自由源码构建`、`从 F-Droid 变体编译排除`、`替换`、`许可/来源待核实`、`允许保留`。本步骤没有把任何预编译 APK/JAR/SO/AAR 判为“允许保留”。

## 结论摘要

当前干净检出**不能完成完整 Android 构建**：`app/libs` 和 `app/src/main/assets/subpack` 只有占位文件，`app/src/main/jniLibs` 为空，而 CI 仍从三个无内容哈希的 Drive 文件 ID 下载归档。与此同时，仓库已跟踪并会被打包的 APK、JAR、ELF、AAPT2 和密钥库也缺少完整的来源—版本—许可证—可重建闭环。

最直接的阻塞项为：

1. `ffmpeg-kit-local.aar` 缺失但被 `implementation(files(...))` 强制引用；
2. `subpack/android.apk`、`subpack/windows.zip` 缺失且存在运行时消费者；
3. `jniLibs.zip` 的真实成员、哈希和许可证未知；
4. Sherpa-NCNN 模型七个文件的上游模型许可证未声明；
5. ML Kit 五个 OCR 依赖仍直接进入 app，需按 `NonFreeDep` 风险处理；
6. 多个源码原生依赖默认跟随 `master`/`main`，构建输入不固定；
7. 私有 SSH 发布子模块会破坏递归干净检出；
8. Maven 传递产物及最终 APK 内容尚未在授权的干净环境验证。

## A. 历史 Google Drive 输入

下载入口为 `ci/script/download_android_dependencies.sh`，解包和过滤规则为 `ci/script/prepare_android_dependencies.py`。当前脚本只固定 Drive file ID，不固定归档 SHA-256；README 和构建指南明确把三者作为构建前置条件。

| 归档 / Drive ID | 输出根目录 | 基线可见内容 | 已知消费者/成员证据 | 进入 APK | 状态与处置 |
|---|---|---|---|---|---|
| `libs.zip` / `1Va1os7PRpCF3xtTwfx5kO11D7eIAvARG` | `app/libs` | 仅 `.keep` | `app/build.gradle.kts:389` 要求 `ffmpeg-kit-local.aar`；解包器排除 `arsc.jar` 和两个 Smart Exception JAR | AAR：是；其余待真实归档确认 | **BLOCKED**：归档哈希、真实清单、来源和许可证未知。FFmpeg 从固定自由源码构建；`arsc.jar` 删除；Smart Exception 已改 Maven，不再接收归档副本 |
| `subpack.zip` / `1SQs_dVPD6ldvwteqoUVjvBLjTWvr5Fpv` | `app/src/main/assets/subpack` | 仅 `.keep` | `ExportDialogs.kt:782` 打开 `subpack/android.apk`；`:893` 打开 `subpack/windows.zip`。历史说明还提到 EXE/DLL/WebView2，但基线不能证明成员清单 | source-set assets，若存在则是 | **BLOCKED**：归档、成员版本、哈希、许可证、源码均未知。从 F-Droid 变体编译排除，除非取得完整自由源码并建立 Linux 可重建流程 |
| `jniLibs.zip` / `1-W4fjjUwoShnB8Rh9RT5Gl8sHiGyQUaM` | `app/src/main/jniLibs` | 目录无文件（连 `.keep` 也未检出） | Java/Kotlin 加载 `sherpa-ncnn-jni`、`operit_ripgrep` 等；解包器排除四 ABI 的重复 `libpl_droidsonroids_gif.so` | 若存在则通常是 | **BLOCKED**：真实 SO 清单、ABI、哈希、来源和许可证未知。从固定自由源码构建；重复 GIF SO 不接收；最终所有权待 APK 对照 |

历史 `models.zip` 已退出当前流程，不再是当前构建输入。未取得上述归档，因此没有把历史描述中的文件名或大小伪装成已验证事实，也没有把归档导入工作树。

## B. 构建期下载的默认 STT 模型

清单位于 `app/config/stt-model-assets.properties`；`syncSttModelAssets` 下载并校验大小和 SHA-256，`syncMainAssets` 将生成目录替换为主 assets，因此八项都会进入常规 APK。ABI 不适用。

| APK 相对路径（`models/` 下） | 固定来源 revision | 大小 | SHA-256 | 许可证 / 处置 |
|---|---|---:|---|---|
| `silero_vad.onnx` | `safestack/silero-vad@8a63e2e86cf654d7ba19fbedbccce5ff55de3c60` | 1,289,603 | `7ed98ddbad84ccac4cd0aeb3099049280713df825c610a8ed34543318f1b2c49` | 清单声明 MIT；来源许可证仍应归档，暂为许可/来源待核实 |
| `sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/decoder_jit_trace-pnnx.ncnn.bin` | `csukuangfj/...@05945efc40afe4b572542f01104ca5c413a9f6e1` | 6,412,296 | `dc4df2d8e1ddee1b90ac72a2de982eb1d320ee6c9a70e1dee4d23d9acfc8b978` | 上游模型元数据未声明许可证；**BLOCKED** |
| `.../decoder_jit_trace-pnnx.ncnn.param` | 同上 | 439 | `cb88f5894978fd3e85369d2f8ea55621809fceb2b5158243fb0cd025eb4f1aaf` | 同上；**BLOCKED** |
| `.../encoder_jit_trace-pnnx.ncnn.bin` | 同上 | 127,364,056 | `4ed65f05b78c0106d3d176018ab01e26a15c200604490d3d49b08cc75a122dd0` | 同上；**BLOCKED** |
| `.../encoder_jit_trace-pnnx.ncnn.param` | 同上 | 161,888 | `97ad0954fb2cb4730f87a7eb66401b024f756752ece246e4b2063f870ebf3e18` | 同上；**BLOCKED** |
| `.../joiner_jit_trace-pnnx.ncnn.bin` | 同上 | 7,350,724 | `0e6c4370017394de5d74128756233d2e4451209e63ac2abd525da3b089e8bee1` | 同上；**BLOCKED** |
| `.../joiner_jit_trace-pnnx.ncnn.param` | 同上 | 490 | `46c339f3869136c2f6d9d9d6983a6cbc2bfbcd0e3dab0f76ae25e9477f00a360` | 同上；**BLOCKED** |
| `.../tokens.txt` | 同上 | 56,317 | `a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3` | 同上；**BLOCKED** |

应用内 MNN 模型下载界面所列模型属于**运行时用户下载**，不是静态默认 APK 输入；其来源选择逻辑见 `MnnModelDownloadScreen.kt`。其网络下载政策和逐模型许可应在功能审计中另行处理。

## C. 仓库已跟踪、会进入 app 的二进制/归档

| 路径（同哈希项合并） | 大小 / SHA-256 | 类型/ABI | 来源、版本、许可证证据 | 引用及进入 APK | 处置 |
|---|---|---|---|---|---|
| `app/src/main/assets/accessibility.apk` | 2,763,980 / `b6bac271c33a41b6cc39840d122286488cebc57be2e74b39aed5bb0d0b1c5212` | APK；内含 4 ABI SO | 内嵌 VCS revision `1221cf08…`，但未找到对应公开源码、tag、许可证和构建命令 | `UIHierarchyManager.kt` 安装；assets 必入 APK | **BLOCKED**；从固定自由源码构建或从 F-Droid 变体编译排除 |
| `app/src/main/assets/desktop.apk` | 6,489,542 / `76f6eb10db6e909ce4274f8738ee6fe25320fa35a46aa7ba911069b5e433320e` | APK；内含 4 ABI SO | APK 明示无受支持 VCS；`desktop_version.txt` 为 `1.0`；无源码/许可证闭环 | 无文件名直接引用，但 assets 必入 APK；代码引用包 `com.ai.assistance.operit.desktop` | **BLOCKED**；从 F-Droid 变体编译排除，除非补齐自由源码构建 |
| `app/src/main/assets/shizuku.apk` | 2,571,773 / `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f` | APK；内含 adb/rish/shizuku 四 ABI SO | `shizuku_version.txt` 为 `13.6.0`；仓库说明要求手工复制下载 APK，未记录 APK 来源 URL/哈希/构建对应 | `ShizukuInstaller.kt` 安装；assets 必入 APK | 预编译 APK 不保留；F-Droid 变体移除内置安装，改用已安装的自由应用交互 |
| `showerclient/src/main/assets/shower-server.jar` | 1,133,303 / `4fc349a6ea9722d2ba2431811de1b44276a5a13901cad751463df021be0fb5e8` | Android dex JAR；内含 4 ABI SO | 仓库有 `tools/shower` 源码，但仅有 Windows `.bat` 复制流程，未证明该 JAR 与源码提交对应 | `ShowerServerManager.kt` 复制后以 `app_process` 执行；`:showerclient` 被 app 依赖 | **BLOCKED**；建立固定源码的 Linux 确定性构建，不能仅凭“有源码”保留 |
| `mnn/src/main/jniLibs/arm64-v8a/libsherpa-mnn-jni.so` | 3,755,168 / `4285a62b38c54ca714076bd69007306408c00509568b893eef3fdbf141cef5bb` | ELF64 AArch64 SO；依赖 `libMNN.so` 等 | UI 声明 sherpa-mnn Apache-2.0，但二进制版本和构建对应未知 | `com.k2fsa.sherpa.mnn.*` 调用 `System.loadLibrary`；`:mnn` 被 app 依赖，预计进入 APK | **BLOCKED**；从固定 sherpa-mnn/MNN 自由源码构建或编译排除 |
| `app/src/main/assets/operit_shell_exec` | 21,496 / `40befdfb8e98a050330a1d91b2d17792539618784bb5c11d942bb2acabf221e9` | ELF64 AArch64 PIE | 有 `tools/shell_identity_launcher/native-lib.cpp` 与 CMake，但无可核查版本/产物对应和 Linux/CI 复制流程 | `RootShellExecutor.kt` 从 assets 使用；必入 APK | **BLOCKED**；从仓库固定源码确定性构建 |
| 两份 `templates/**/tools/aapt2/aapt2-arm64-v8a` | 各 4,706,040 / `e5b5ff7f0d4f6ecd7fa5d05d77fed3f09f6f1bf80f078b8aada82bc578848561` | ELF64 AArch64 EXEC | 来源、Build Tools 版本、许可证、构建方式未登记 | 模板 setup 脚本使用；作为 assets 必入 APK | **BLOCKED**；替换为可接受的固定自由源码构建方案，或从 F-Droid 变体编译排除设备内 Android 构建模板 |
| `app/src/main/assets/jks.jks` | 2,656 / `b152b228ca8eddf1f78fe7fbc2fa6fa53204007def49dee1ca8ee000a4458711` | JKS 密钥库 | 生成者、用途和公开测试密钥说明缺失 | `KeyStoreHelper.kt` 复制使用；assets 必入 APK | **BLOCKED**；替换为构建/运行时生成的非秘密测试密钥方案，并确认无发布凭据 |

表中记录完整 SHA-256；关键审计仍应以路径重新执行 `sha256sum`。除根 `LICENSE`（自定义标题、正文 LGPL-3.0）外，仓库未发现与上述制品逐项对应的 LICENSE/NOTICE。

## D. 已跟踪但是否进入 app 取决于源码生成流程的 JAR

| 路径 | 大小 / SHA-256 | 生成/引用关系 | APK 结论 | 处置 |
|---|---|---|---|---|
| `examples/apktool/resources/apktool/android-framework.jar` | 4,488,013 / `5dd984016ed5a5eb0eef866e2c6e8cd352e1427828ec23adb18005cf5648f3d7` | `examples/apktool/manifest.json` 资源；内容为 Android framework manifest/resources | `sync_example_packages.py` 会将示例包同步到 app assets，待干净生成后确认 | 来源/平台版本待核实；需要固定、可再生成 |
| `.../apk-reverse-helper-runtime-android.jar` | 3,176,318 / `5f9acea136d2d02a25eccb8d1541e876a07d84d73130be035e84b21b34849372` | 同上；PowerShell 聚合构建脚本 | 同上 | **BLOCKED**：改为 Linux 固定依赖构建，并保留完整许可证 |
| `.../apktool-runtime-android.jar` | 5,190,028 / `f043f4e72f3dbad65a6676814d775144654e41c58e6bc00c61e8fb1ff9eea94d` | 同上；内嵌 Android `aapt2` | 同上 | **BLOCKED**：固定源码构建；审计内嵌 AAPT2 |
| `.../jadx-runtime-android.jar` | 14,332,003 / `cda7b7c8da5d5db51703113fd096be66ec5a4aa65c6673e0b1d65c7a22e62987` | 同上；带本地 patch 和裁剪 | 同上 | **BLOCKED**：固定源码、补丁、锁定依赖的 Linux 可重建流程 |
| 根及 Android 模板 `gradle-wrapper.jar` | 各 59,203 / `e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637` | 根 wrapper 用于仓库构建；模板 wrapper 会进 app assets | 根分发为 Gradle 8.13 且有 SHA-256；模板分发为 9.1.0、无 distribution hash | wrapper JAR 来源版本仍需验证；模板项必入 APK |
| `tools/shower/gradle/wrapper/gradle-wrapper.jar` | 45,457 / `76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3` | Shower 子工程 wrapper | 不直接进入 app；用于重建 server JAR | wrapper/distribution 都需补哈希和版本验证 |

`tools/example_packages/sync_example_packages.py` 是 app 构建前 CI 步骤；仓库根和 `web-chat` 已跟踪 `package-lock.json`，CI 固定 pnpm `10.34.5` 并使用 `npm ci`。同步后的 JS/ToolPkg 与 WebChat 属于源码生成资产，仍须在后续确认：输入全集、输出差异为零、无下载二进制、F-Droid 构建内可重现。

## E. Gradle/Maven 依赖

直接依赖的权威清单是 `app/build.gradle.kts:373-636`、各模块 `build.gradle.kts` 与 `gradle/libs.versions.toml`。本次逐项静态枚举了所有 `implementation`、`coreLibraryDesugaring` 和项目模块，但未运行 Gradle，因此**传递依赖、AAR 内 SO/模型、解析仓库及最终 APK 归属均待验证**。

重点风险分组：

- **本地文件依赖**：`app/libs/ffmpeg-kit-local.aar`，基线缺失且强制引用，确定阻塞构建；来源、FFmpeg 配置、codec 许可证和 hash 全未知。
- **OCR / NonFreeDep 风险**：`com.google.mlkit:text-recognition` 及 Chinese/Japanese/Korean/Devanagari 五项，版本均 `16.0.0`，由 app 直接依赖并有 OCR 源码引用。第 2 步不替换；后续必须替换为自由实现或从 F-Droid 编译图完整排除。
- **可能携带原生库/模型的直接依赖**：Filament `1.69.2`、TensorFlow Lite `2.10.0`、MediaPipe tasks-text `0.10.11`、ONNX Runtime Android `1.17.1`、android-gif-drawable `1.2.28`、PDFBox Android、ObjectBox `5.3.0` 等。不能在无依赖图/APK 的情况下判定其二进制清单或 F-Droid 安全性。
- **JitPack/非中央仓库依赖**：Sable AXML、zipalign-java、libsu、RenderX 等；仓库还配置了 JitPack、失效 Bintray Shizuku、Xposed 和 Sonatype snapshots。必须逐项确认实际解析来源并删除无消费者仓库。
- **许可证重点**：iTextG `5.5.10`、FFmpeg、模型和全部 native 传递产物需单独闭环；根许可证不能替代第三方声明。
- **构建插件/工具**：AGP `8.13.2`、Kotlin `2.2.0`、ObjectBox Gradle plugin `5.3.0`、Gradle wrapper 也是构建输入，需固定仓库、校验和及 F-Droid 环境可得性。

ML Kit 代码位置可由 `rg 'com.google.mlkit' app/src/main` 复核。直接版本完整值保留在 version catalog；本文不复制一份容易漂移的百项清单，而以固定基线文件和行区间作为可复核清单。

## F. 子模块与源码构建的原生输入

| 输入 | 基线固定值 | 构建/产物 | 问题与处置 |
|---|---|---|---|
| `terminal` 子模块 | `f85be57944b806de4d863dee8b10d80d04daa236`，公开 HTTPS | `:terminal`，预计携带 terminal runtime | commit 已固定，但当前工作区未初始化，许可证、其嵌套下载和 APK 内容待审计 |
| `tools/hotbuild/OperitNightlyRelease` | `350165237e89b370e45213dcd7ab02ee7e7a6361`，私有 SSH URL | 发布工具 | 不应是 app 输入，但递归初始化会失败；从 F-Droid 源码准备中移除/不初始化 |
| sherpa-ncnn | GitHub，默认 `master` | `libsherpa-ncnn-jni.so` | 浮动；固定自由源码 commit 并记录许可证/构建参数 |
| WAMR | GitHub，默认 `main` | `libtoolpkgwasm.so` | 浮动；固定 commit |
| MNN | GitHub，默认 `master` | `libMNN.so`、`libMNNWrapper.so` | 浮动；固定 commit，审计其三方源码 |
| KleidiAI | tag `v1.16.0` | MNN 优化代码 | tag 会被 helper 解析到 commit，但需在离线/F-Droid 准备阶段固定 SHA 与许可证 |
| llama.cpp | commit `720d7fa4097f76e5d0eade5a92c1df87c1faf9d9` | `libLlamaWrapper.so` 等 | 已固定 commit；许可证及实际产物待 APK 核对 |
| Saba / Bullet3 / ufbx | 分别默认 `master` / `master` / `main` | `libMmdWrapper.so` / `libFbxWrapper.so` | 全部浮动；固定 commit 和三方许可证 |
| OpenFST | tag archive `sherpa-onnx-2024-06-19`, SHA-256 `5c98e82cc509c5618502dde4860b8ea04d843850ed57e6d6b590b644b268853d` | Sherpa 相关静态代码 | URL hash 已固定；许可证和最终所有者仍需登记 |
| QuickJS / DragonBones | 仓库内源码（QuickJS 模块源码树、DragonBones `cpp/`） | 对应 JNI SO | 可从源码构建，但仍需源码版本来源、许可证和 APK 对照，不能自动把既有二进制视为同源 |
| ripgrep JNI | `tools/native_ripgrep`，Cargo.lock 已跟踪 | `liboperit_ripgrep.so` | 有源码锁文件；需接入固定 Android 构建并替代归档 SO |

`operit_git_source.cmake` 会先解析 ref 再下载 GitHub commit archive，但浮动 ref 在每次全新配置时仍可能解析成不同提交，不满足可重现输入固定要求。

## G. 进入 APK 与干净检出的判定矩阵

| 类别 | 干净检出可得 | 常规 APK | F-Droid 当前结论 |
|---|---|---|---|
| Drive 三归档 | 否（构建时外部下载） | 预期是 | 阻塞；不能依赖 Drive 未哈希制品 |
| 八个默认 STT 文件 | 构建时按固定 URL/hash 取得 | 是 | Sherpa 七项许可证阻塞 |
| `app/src/main/assets` APK/ELF/AAPT2/JKS | 是 | 是 | 均需替换、源码构建或编译排除 |
| `showerclient` JAR、MNN 预编译 SO | 是 | 预计是 | 源码—产物对应阻塞 |
| APKTool 示例 JAR | 是 | 同步生成后预计是 | 待生成资产/APK验证；当前不可保留 |
| Maven/Gradle AAR/JAR/SO | 缓存中否，仓库解析取得 | 直接与传递项通常是 | 待依赖图、scanner、许可证和 APK 验证 |
| CMake FetchContent | 否，配置时联网解析/下载 | 编译后 SO 是 | 浮动 ref 阻塞可重现构建 |
| 私有发布子模块 | 无权限时否 | 否 | 从 F-Droid 准备路径移除 |

## H. 阻塞项与后续最小改造单元

后续已拆成独立文档：

- `3_ExternalArchivesAndModels.md`：Drive 输入、FFmpeg/JNI、模型许可；
- `4_EmbeddedExecutablesAndHelpers.md`：内嵌 APK/JAR/ELF/AAPT2/JKS；
- `5_OcrAndNonFreeDependencies.md`：ML Kit 和可能携带 native/model 的 Maven 依赖；
- `6_NativeSourcePinningAndSubmodules.md`：CMake source pin、terminal 和私有子模块；
- `7_DynamicCodeAndGeneratedAssets.md`：ToolPkg、外部 DEX/JAR、WebChat、脚本与生成资产；
- `8_FdroidVariantAndReproducibleBuild.md`：编译排除、依赖图、APK 对照、scanner/server build 和复现。

## 可重复静态命令

```bash
git checkout a57857263f3291b41cf306a95ebcbe0e5c2b1373
git ls-files -z | xargs -0 sha256sum
rg -n 'implementation|coreLibraryDesugaring|System.loadLibrary' app mnn llama mmd fbx quickjs showerclient
find app/src/main/assets mnn/src/main/jniLibs showerclient/src/main/assets examples/apktool/resources -type f
readelf -h -d mnn/src/main/jniLibs/arm64-v8a/libsherpa-mnn-jni.so
unzip -l app/src/main/assets/accessibility.apk
unzip -l showerclient/src/main/assets/shower-server.jar
git ls-tree HEAD terminal tools/hotbuild/OperitNightlyRelease
```

## 验证记录

2026-07-27 对本步骤执行了以下最小验证：

- `python3 -m unittest -v ci/test/test_android_dependencies.py`：通过，10/10；覆盖归档路径越界、符号链接、特殊文件、输出根和当前解包文件校验。
- 使用仓库 `ci/script/check_markdown_links.py` 的 `check_file` 检查本目录工作树，并检查 Markdown 表格、行尾空白和 front matter 闭合：通过，共 9 个文件。
- 逐项解析 `app/config/stt-model-assets.properties` 并与本文比对：8/8 个模型 SHA-256 一致。
- 对本文列出的 14 个仓库二进制重新计算 SHA-256 并比对：14/14 一致。验证后将原先为排版缩写的二进制哈希改为完整值。
- 核对 `HEAD`、缺失的 `ffmpeg-kit-local.aar`、Drive 输出占位目录、ML Kit 声明及 CMake 浮动 ref：均与本文结论一致。
- `git diff --check`：通过；另行逐行检查了未跟踪 TODO 文档的行尾空白。

首次自定义文档检查错误地要求旧有 `1_BaselineAndReleaseGate.md` 必须包含 front matter，因此失败；该文件本来就是普通 Markdown。调整为“仅对已有 front matter 检查闭合”后通过，这不是产品或第 2 步文档缺陷。

未执行 Gradle/Android 构建、lint、fdroid scanner 或 server build：本步骤仅修改审计文档，且已确认干净检出缺少强制引用的 `app/libs/ffmpeg-kit-local.aar`。这些验证由步骤 8 在外部输入和编译图完成改造后执行，当前不声称通过。

## 完成记录

已覆盖基线中通过扩展名和文件魔数可发现的 APK、JAR、SO、ELF、AAPT2、密钥库、默认模型、直接 Gradle 文件依赖、Maven 风险组、源码生成资产、CMake 网络源码和子模块；无法静态证明的传递产物和最终 APK 内容已明确列为后续验证，不作合规推断。本步骤未修改产品代码、依赖或构建配置，未下载缺失归档。[DONE]
