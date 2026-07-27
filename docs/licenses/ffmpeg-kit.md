# FFmpegKit Android 构建来源与许可证审计

## 构建身份

- 上游：`https://github.com/arthenica/ffmpeg-kit`
- 固定 tag：`v6.0`
- 固定 commit：`d6be56d7aec286eb3c292d6b23ff07a6b70d8693`
- FFmpeg：`n6.0` / `ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2`
- Android ABI：`arm64-v8a`
- Android API：26
- Android NDK：`25.1.8937393`
- 构建入口：`tools/ffmpeg/build_android.sh`
- 产物：`app/libs/ffmpeg-kit-local.aar`
- 消费者：`app/build.gradle.kts` 的本地 AAR 依赖，以及 `com.arthenica.ffmpegkit` API 调用

`tools/ffmpeg/source-lock.properties` 固定 ffmpeg-kit、配置工具、cpu_features、FFmpeg、显式启用库及其由 ffmpeg-kit 规则隐式启用的源码，共 38 个输入。`tools/ffmpeg/apply_source_lock.py` 在构建前验证 ffmpeg-kit HEAD，并把上游 `scripts/source.sh` 的 37 个对应来源改成锁定 commit；缺失、重复或格式漂移会直接失败。

该 commit 是 Operit 新建立的明确源码构建基线。由于历史 `ffmpeg-kit-local.aar` 无哈希和构建证明，不能声称新基线就是旧 AAR 的已验证来源。

## 启用组件

构建启用：fontconfig、freetype、fribidi、gmp、gnutls、lame、libass、libiconv、libtheora、libvorbis、libvpx、libwebp、libxml2、opencore-amr、shine、speex、dav1d、kvazaar、libilbc、opus、snappy、soxr、twolame、vo-amrwbenc、zimg 和 Android system zlib。

构建命令没有启用 `--enable-gpl` 或 `--enable-nonfree`，也没有启用 x264、x265、xvidcore、OpenH264 或 OpenSSL。这个事实只描述当前参数，不能替代逐库许可证核查。

## 当前许可证结论

- ffmpeg-kit 仓库由 GitHub 标识为 LGPL-3.0；
- FFmpeg 仓库的顶层许可证是组合说明，最终许可证取决于配置；必须以实际 `ffmpeg -buildconf`、生成配置和打包内容复核；
- 已取得的上游元数据包括 cpu_features Apache-2.0、FriBidi LGPL-2.1、libass ISC、libvorbis BSD-3-Clause；
- GitHub 对 GMP、GnuTLS 和 libiconv 的顶层单许可证识别会显示 GPL，但这些项目对库和工具可能使用不同条款，不能据此直接判定最终链接产物；
- GitHub 匿名 API 在批量核查过程中触发限流，剩余组件及多许可证项目必须读取固定 commit 的许可证原文后再下最终结论。

`tools/ffmpeg/license-lock.properties` 已为 38/38 个输入登记许可证表达式、构建/运行时角色、审核状态、固定 commit 下的证据路径与证据 SHA-256。`tools/ffmpeg/verify_license_lock.py` 强制源码锁与许可证锁组件集合完全一致，并在每次 FFmpeg 构建前运行。

固定原文澄清了几个容易误判的项目：GMP library 为 `LGPL-3.0-or-later OR GPL-2.0-or-later`；GnuTLS core 为 `LGPL-2.1-or-later`，但与 Nettle/GMP 静态组合仍需按 LGPL-3.0-or-later 路径审查；libiconv library 为 `LGPL-2.1-or-later`，GPL 条款针对命令行程序和文档；GNU config 脚本为 GPL-3.0-or-later 且带 Autoconf exception，仅作为构建工具。

重新访问 2026-07-27 的 F-Droid Inclusion Policy 后确认：F-Droid 以 DFSG、FSF、GNU、OSI 和 SPDX 等权威标准判断 FLOSS；所有本地二进制依赖仍必须在 F-Droid 构建中从公开源码生成，不能因许可证自由而直接保留未知 AAR。SPDX 收录 `WTFPL`，Debian 的 DFSG 许可证页面也将 WTFPL 列为 DFSG-compatible，因此 zimg 的许可证不构成排除理由。

固定原文进一步关闭了四项静态审查：fontconfig 对应 SPDX `HPND-sell-variant`；libjpeg-turbo 为 `IJG AND BSD-3-Clause AND Zlib`，需保留 IJG 归属；libsndfile 为 LGPL-2.1-or-later，并含 Apache-2.0 ALAC 与需保留声明的 GSM 6.10 代码；zimg 为 WTFPL。详细归属和分发要求写入 `docs/licenses/FFMPEG-NOTICE.md`。

当前只剩三项产物验证门：

- FFmpeg：最终许可证取决于 Fork Builder 实际 configure/buildconf，必须确认没有 GPL/nonfree 开关；
- ffmpeg-kit：LGPL-3.0-only 的组合、对应源码和可替换/再链接义务要以真实 AAR 结构复核；
- GnuTLS：与 Nettle/GMP 静态组合后按 LGPL-3.0-or-later 路径履约，并以真实 ELF/构建日志确认实际链接内容。

这三项均是自由许可证组件，不构成 `NonFreeDep`；但未通过 Fork Builder 的 AAR、ELF、buildconf 和对应源码验证前，FFmpeg AAR 的许可证状态仍是 **未闭环**，不得标记为 F-Droid 合规。

## 构建约束与待验证项

- 构建脚本不设置默认代理，不包含个人 WSL/磁盘路径；调用环境如需代理，应显式提供标准网络环境变量；
- 脚本强制固定 NDK、API、JDK 路径和 ffmpeg-kit HEAD；
- 上游源码下载表被改写为固定 commit，但源码归档 SHA-256、嵌套子模块 commit 和下载后补丁仍需输出成构建审计报告；
- GnuTLS 子模块更新不再吞掉失败；缺失输入会终止构建；
- 构建后必须记录 AAR SHA-256、ZIP 成员、每个 SO 的 ABI/ELF NEEDED、FFmpeg build configuration、许可证与 NOTICE；
- 两次独立干净构建需要比较未签名 AAR/APK，并解释 ZIP 时间戳或其他非确定性差异。

本地静态核查未执行 FFmpeg/Gradle 构建。真实 AAR 只能在提交推送后的 Operit Builder 或独立 F-Droid 构建环境生成。