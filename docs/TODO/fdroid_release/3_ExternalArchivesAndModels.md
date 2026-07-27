---
title: 后续步骤：外部归档、FFmpeg/JNI 与模型
status: planned
document_type: implementation-step
depends_on: 2_BinaryAndBuildInputInventory.md
---
# 外部归档、FFmpeg/JNI 与模型

## 目标
移除未哈希 Google Drive 构建前置；以固定自由源码构建 FFmpeg/JNI；只打包来源、许可、哈希闭环的模型。

## 输入证据
以 [`2_BinaryAndBuildInputInventory.md`](./2_BinaryAndBuildInputInventory.md) 固定于 `a5785726` 的审计清单为基线。未知项保持 `BLOCKED`。

## 最小执行单元
1. 在仓库外审计三个历史归档，记录归档及逐文件哈希。
2. 为 FFmpeg、ripgrep 和所需 JNI 建立固定源码、版本、许可证与 Linux 构建命令。
3. 核实 Silero 与 Sherpa 模型许可；无法闭环的模型从 F-Droid 变体排除。
4. 建立产物唯一所有者表，拒绝重复 GIF SO、无消费者 JAR 和未声明文件。

## 执行记录

### 3.1 历史归档可获取性核查

2026-07-27 在仓库外使用临时目录 `/tmp/operit-fdroid-archive-audit` 尝试建立与 CI 相同的 `gdown==6.1.0` 下载环境；当前终端挂载层在创建 venv 时超时，未产生可审计归档，也未向工作树解包任何文件。

随后分别测试 Google Drive 官方下载入口：

- `https://drive.usercontent.google.com/download?...`：TCP/HTTPS 连接 15 秒超时；
- `https://drive.google.com/uc?...`：`ERR_CONNECTION_TIMED_OUT`。

因此本环境无法取得 `libs.zip`、`subpack.zip` 和 `jniLibs.zip`，无法真实计算归档 SHA-256 或逐文件清单。该执行结果不能证明归档内容合规，也不能把本单元标记完成。决策如下：

- 不把未知归档下载或解包到仓库；
- 不为未知成员补写推测的版本、许可证或哈希；
- 三个归档继续保持 `BLOCKED`，不得进入 F-Droid 构建输入；
- 正式版现有 CI 暂不在本单元直接删除，避免在替代源码构建尚未接通时破坏现有发布流程；完成 FFmpeg/JNI/子包替代后再移除其调用。

### 3.2 模型远端许可核查

首次访问固定 revision 的 Hugging Face API 与网页时连接超时；负责人启用可用网络路径后于 2026-07-27 重新核查成功。

Silero VAD 固定 revision `8a63e2e86cf654d7ba19fbedbccce5ff55de3c60` 的证据：

- API 返回 `cardData.license = mit`，且响应 revision 与请求一致；
- 固定 README 声明模型文件来自官方 Silero VAD 项目；
- 固定 `LICENSE` 响应头 `x-repo-commit` 与 revision 一致，正文是标准 MIT License，版权为 `Copyright (c) 2020-present Silero Team`；
- 已建立 `docs/licenses/models/silero-vad.md`，记录来源、文件 SHA-256、许可证和再分发要求。

Sherpa-NCNN 固定 revision `05945efc40afe4b572542f01104ca5c413a9f6e1` 的证据：

- API 标签与 `cardData.license` 均声明 `apache-2.0`；
- 固定 README 的 front matter 声明 Apache-2.0，并记录 TorchScript 原始模型仓库和 Icefall 训练代码路径；
- 被引用的原始模型仓库当前元数据同样声明 Apache-2.0；
- 已建立 `docs/licenses/models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13.md`；
- 已把 `app/config/stt-model-assets.properties` 中七项错误的“上游未声明许可证”改为 Apache-2.0，并链接本地溯源文档。

许可证问题已经澄清，但 Sherpa 模型仍未完成确定性生成链闭环：固定 README 未记录转换时使用的原始模型 revision，训练代码链接跟随 Icefall `master`，Operit 也尚未固定 pnnx/ncnn 工具链与完整导出命令。因此其状态调整为“Apache-2.0 已声明，生成链和训练来源待闭环”，不是直接判定完全合规。

在 F-Droid 审核确认可接受固定上游预训练模型，或生成链补齐前，后续 F-Droid 变体仍不默认携带 Sherpa 模型；不能使用 `scanignore`、`scandelete` 或运行时空文件。

### 3.3 Native ripgrep 固定源码构建入口

现有代码已经包含 `tools/native_ripgrep` Rust JNI 源码和 `Cargo.lock`，Android Build 与 PR Check 也都从源码构建，而不是消费 `jniLibs.zip` 中的 ripgrep SO。本单元将其整理成一个权威 Linux 构建入口：

- 新增 `tools/native_ripgrep/rust-toolchain.toml`，固定 Rust `1.88.0` 和 `aarch64-linux-android` target；
- 新增 `tools/native_ripgrep/build_android.sh`，固定 NDK `25.1.8937393`、API 26、ABI `arm64-v8a`、`--locked` 和唯一输出路径；
- Android Build 与 PR Check 删除重复的内联 Cargo 命令，统一调用该脚本；
- CI 缓存键现在覆盖 Cargo.toml、Cargo.lock、toolchain 文件和构建脚本，代码或工具链变化不会复用旧 key；
- 新增 `docs/licenses/native-ripgrep.md`，记录源码、消费者、构建命令、最终 APK 结论和尚未完成的 crate 许可证闭环。

自主决策：生成的 `liboperit_ripgrep.so` 继续不提交到 Git，必须在构建环境中从锁定源码生成；没有把历史归档 SO 作为任何构建路径。Windows 旧脚本暂未删除，因为正式开发者接口仍在使用，后续会让它转调同一参数契约或在文档迁移后删除。

静态验证：

- `bash -n tools/native_ripgrep/build_android.sh` 通过；
- Cargo.toml 与 rust-toolchain.toml 可由 TOML 解析器读取；
- Cargo.lock 为版本 4，包含本地 `operit_ripgrep` 包；
- 两个工作流各且仅各调用一次统一脚本，已无内联 `cargo +1.88.0 build`；
- 清除所有 Android SDK/NDK 环境变量后运行脚本，按设计以退出码 2 快速失败并指出固定 NDK 要求；
- `git diff --check` 通过。

依照仓库规则，本地未执行 Cargo/NDK/Gradle 构建；实际 ELF 产物验证留给提交并推送后的 Operit Builder 和后续 F-Droid server build。

### 3.4 FFmpegKit 固定源码基线与 Linux 构建入口

仓库已有 FFmpegKit 构建脚本和功能调用，但历史 `ffmpeg-kit-local.aar` 没有来源哈希或 checkout 记录。根据现有脚本所用的组件名、参数和版本表，新建立如下明确基线，不伪称它已被证明等同于历史 AAR：

- ffmpeg-kit `v6.0` / `d6be56d7aec286eb3c292d6b23ff07a6b70d8693`；
- FFmpeg `n6.0` / `ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2`；
- NDK `25.1.8937393`、Android API 26、ABI `arm64-v8a`；
- 26 个显式启用媒体/字体/加密库，加上 `gnu-config` 和 `cpu_features`，全部解析为固定 40 位 commit。

实际改造：

- 新增 `tools/ffmpeg/source-lock.properties`，登记 ffmpeg-kit 和 28 个源码输入的仓库、上游 ref 与 resolved commit；
- 新增 `tools/ffmpeg/apply_source_lock.py`：构建前验证 ffmpeg-kit HEAD，并将上游 `scripts/source.sh` 对应来源改写为固定仓库、commit 和 `SOURCE_TYPE=COMMIT`；缺失、重复或格式异常直接失败；
- 新增通用 Linux 入口 `tools/ffmpeg/build_android.sh`；移除默认代理、个人 WSL 路径、个人 JDK 路径和 NDK 22，要求调用环境显式提供 Android SDK 与 JDK；
- 旧 `build_ffmpeg_kit_wsl.sh` 保留为兼容转发，避免破坏现有开发者命令；
- GnuTLS 子模块命令不再使用 `|| true` 吞掉失败；
- 成功构建后只导入一个非空 AAR 到 `app/libs/ffmpeg-kit-local.aar` 并输出 SHA-256；
- 新增 `ci/test/test_ffmpeg_source_lock.py`，覆盖锁文件必填、非法 commit、重复组件、成功改写和缺失源码块；
- 新增 `docs/licenses/ffmpeg-kit.md`，记录构建身份、启用组件、当前许可证证据和未闭环项。

验证记录：

1. 第一次固定 checkout 测试失败：锁文件组件名写成仓库名 `gnu-config`，而 ffmpeg-kit 源码表键为 `config`。修正组件键后重新测试成功；
2. 在 `/tmp` 的固定 ffmpeg-kit checkout 上执行锁定脚本，28/28 个源码块均改为 40 位 commit，源码表 diff check 通过；
3. 新单元测试第一次因 Python 3.12 动态导入未登记 `sys.modules` 而失败；修正测试装载后 5/5 通过；
4. 两个 Shell 入口 `bash -n` 通过，Python 文件可编译；
5. 源码锁共 29 个唯一输入，全部为四字段和 40 位小写 commit；
6. 构建入口确认不含默认代理、`/mnt/d`、NDK 22、`|| true`、`--enable-gpl` 或 `--enable-nonfree`；
7. `git diff --check` 通过。

许可证尚未闭环：GitHub 匿名 API 在批量核查时触发限流，且 GMP、GnuTLS、libiconv 等项目不能以 GitHub 顶层单许可证识别代替 library 实际条款。FFmpeg 单元当前完成“固定源码和构建入口”，没有将 AAR 判为 F-Droid 合规；逐库许可证原文、嵌套子模块、补丁、最终 AAR 内容和可复现性仍需后续完成。

### 3.5 FFmpeg 隐式源码闭包与许可证证据锁

复核固定 ffmpeg-kit 的 `scripts/function.sh` 后发现，前一单元的 29 项只覆盖显式输入，不是完整依赖闭包。当前启用规则还会引入 expat、libpng、Nettle、HarfBuzz、libogg、giflib、libjpeg-turbo、libtiff 和 libsndfile。已将这九项全部按上游 ref 解析到 40 位 commit 并加入源码锁；其中 expat 先单独加入，随后加入剩余八项。一次结构化编辑误插入裸 `ffmpeg|` 行，已立即删除，并由四字段校验确认清单恢复有效。

现在源码锁共 38 项：ffmpeg-kit 主 checkout 加 `scripts/source.sh` 中 37 个源码块。固定 checkout 验证结果为 37/37 成功改写成 `SOURCE_TYPE=COMMIT`。

新增 `tools/ffmpeg/license-lock.properties`：为 38/38 项记录许可证表达式、构建/运行时角色、审核状态、固定 commit 下证据文件路径和证据 SHA-256。许可证原文在仓库外临时 checkout/HTTP 审计，不把临时上游源码复制进工作树。固定原文纠正了 GitHub 顶层许可证识别可能造成的误判：

- GMP library：`LGPL-3.0-or-later OR GPL-2.0-or-later`；
- GnuTLS core：`LGPL-2.1-or-later`，与 Nettle/GMP 静态组合仍需按 LGPL-3.0-or-later 路径复核；
- libiconv library：`LGPL-2.1-or-later`，GPL 针对程序和文档；
- GNU config：`GPL-3.0-or-later WITH Autoconf-exception-3.0`，仅构建期使用。

新增 `tools/ffmpeg/verify_license_lock.py`，强制 source/license 两个锁组件集合相等、字段数正确、commit/SHA-256 格式规范、角色和状态受控、证据路径不能越界。FFmpeg 构建入口会先运行该检查。新增 `ci/test/test_ffmpeg_license_lock.py`，覆盖仓库完整覆盖、缺失组件、越界路径和未知状态；与源码锁测试合计 9/9 通过。

验证器仍主动报告七项待审查：FFmpeg 配置相关许可证、ffmpeg-kit LGPL-3.0 组合义务、fontconfig SPDX 变体、GnuTLS 静态组合、libjpeg-turbo 组合条款、libsndfile 内嵌 codecs、zimg WTFPL 政策接受性。因此许可证证据覆盖已完成，但 FFmpeg AAR 许可证闭环尚未完成。

依照仓库规则，本地未执行 FFmpeg/Android/Gradle 构建。

### 3.6 FFmpeg 功能需求与构建后 AAR 审计契约

源码消费者核查确认 FFmpeg 不是孤立工具：内部用于媒体探测、音频转 16 kHz WAV/MP3、视频缩放与抽帧、H.264/MPEG-4/AAC 转码、GIF 和多种音视频输入；正式版还公开任意 `ffmpeg_execute` 命令，TypeScript 接口承诺 VP8/VP9/AV1/MP3/Opus/Vorbis 等能力。因此本阶段自主决定不缩减现有 FFmpeg 组件集，避免破坏已发布接口。只有明确不自由、不可再分发或无法从源码重建的组件才会从 F-Droid 变体编译排除。

发现既有接口准确性缺陷：TypeScript 声称支持 `libx265` 与 `libaom`，当前构建脚本并未启用 x265/libaom。该缺陷不是本次引入；当前不擅自加入 GPL 组件，也不在未确认发布兼容策略前删除公开类型，留待正式接口决策单元处理。

固定 ffmpeg-kit 的链接脚本显示：第三方库通过 `pkg-config --libs --static` 静态并入 FFmpeg，而 FFmpeg 本体以 `--disable-static --enable-shared` 生成共享库。因此 APK 最终可携带 FFmpeg `.so`，但每个 `.so` 内含静态第三方代码；LGPL 合规需要精确对应源码、补丁、构建脚本和可替换/再链接安排，不能只附一个 LICENSE。

新增 `tools/ffmpeg/audit_android_aar.py`，在 Fork/Builder 真实构建后自动：

- 拒绝空 AAR、路径越界、重复 ZIP 成员和非 arm64 ABI；
- 记录 AAR 与每个成员的大小和 SHA-256；
- 要求八个核心 FFmpegKit/FFmpeg SO 存在；
- 用 readelf 验证 AArch64，并记录每个 SO 的 ELF NEEDED；
- 输出确定性文本报告到 `app/build/reports/ffmpeg-kit-aar-audit.txt`。

`tools/ffmpeg/build_android.sh` 已接入该审计器。新增 `ci/test/test_ffmpeg_aar_audit.py` 覆盖绝对路径、父目录逃逸、缺失核心 SO、错误 ABI 和重复成员。FFmpeg 三组测试合计 14/14 通过；许可证验证器仍如实报告七项待审查。

负责人启用可用网络后重新读取 2026-07-27 的官方 F-Droid Inclusion Policy：F-Droid 以 DFSG、FSF、GNU、OSI 和 SPDX 等权威标准判断 FLOSS，并要求本地二进制依赖从公开源码生成；`scanignore` 不能用于掩盖自带 FFmpeg AAR。SPDX 收录 WTFPL，Debian 将其列为 DFSG-compatible，因此 zimg/WTFPL 已从 `policy-review` 更新为 `verified`。fontconfig 的 HPND-sell-variant、libjpeg-turbo 的 IJG/BSD/Zlib 组合、libsndfile 的 LGPL/ALAC/GSM 条款也已按固定原文关闭静态审核。新增 `docs/licenses/FFMPEG-NOTICE.md` 记录 copyleft、宽松许可证、IJG 归属和内嵌 codec 声明。当前只剩 FFmpeg buildconf、ffmpeg-kit LGPL 分发结构和 GnuTLS 静态组合三项 Fork Builder 产物验证门；它们均为自由许可证，不是 `NonFreeDep`。

依照负责人新增约束，未在本地执行 FFmpeg/NDK/Android/Gradle 构建。任何真实构建必须先提交并推送到可控 Fork，再由 Operit Builder 或独立 fdroidserver CI 执行。

## 验收
- 每项变更有固定来源、许可证、哈希/commit、生成命令、消费者和 APK 结论。
- 不使用 `scanignore`、`scandelete` 或运行时空实现。
- 未完成代码改造和验证前不添加 `[DONE]`。
