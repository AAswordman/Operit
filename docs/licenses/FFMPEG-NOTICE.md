# FFmpegKit Android 第三方 NOTICE

本文件对应 `tools/ffmpeg/source-lock.properties` 中的固定源码输入和 `tools/ffmpeg/license-lock.properties` 中的许可证证据。最终发布 APK/AAR 必须保留适用的许可证、版权与归属声明；本摘要不能替代许可证原文。

## 构建和组合方式

Operit 使用固定 ffmpeg-kit v6.0 源码构建 arm64-v8a AAR。第三方媒体库以静态方式链接进 FFmpeg 共享库，FFmpeg/FFmpegKit 以共享 `.so` 进入 AAR。对应源码、Operit 修改脚本和构建命令由本仓库提供。

## Copyleft 组件

- FFmpeg：`LGPL-2.1-or-later`，最终配置不得启用 GPL/nonfree 部分；
- FFmpegKit：`LGPL-3.0-only`；
- FriBidi、libiconv、SoXR、TwoLAME：`LGPL-2.1-or-later`；
- LAME、Shine：`LGPL-2.0-or-later`；
- GMP、Nettle：`LGPL-3.0-or-later OR GPL-2.0-or-later`，本组合选择 LGPL 路径；
- GnuTLS core：`LGPL-2.1-or-later`，与 GMP/Nettle 的静态组合按 LGPL-3.0-or-later 义务处理；
- libsndfile：`LGPL-2.1-or-later`，另含下述内嵌 codec 许可。

分发时必须提供精确对应源码、修改说明和构建脚本，并允许使用者重建和替换 LGPL 覆盖的共享库。构建后 AAR/ELF 报告用于证明实际成员和链接关系。

## 归属和宽松许可证组件

- cpu_features、OpenCORE-AMR、VisualOn AMR-WB、libsndfile 内嵌 ALAC：Apache-2.0；
- dav1d：BSD-2-Clause；
- libass：ISC；
- Expat、giflib、HarfBuzz、libxml2：MIT；
- Kvazaar、libiLBC、libogg、libtheora、libvorbis、libvpx、libwebp、Opus、Snappy、Speex：BSD-3-Clause；
- libpng：Libpng-2.0；
- libtiff：libtiff license；
- FreeType：FTL OR GPL-2.0-or-later，本组合选择 FTL；
- fontconfig：HPND-sell-variant；
- zimg：WTFPL；
- libjpeg-turbo：IJG AND BSD-3-Clause AND Zlib；
- GNU config scripts：GPL-3.0-or-later WITH Autoconf-exception-3.0，仅在构建期使用。

### libjpeg-turbo 必需归属

二进制分发文档必须包含：

> This software is based in part on the work of the Independent JPEG Group.

并保留 libjpeg-turbo `LICENSE.md` 中的 IJG、Modified BSD 和 Zlib 条款，不得使用 IJG、libjpeg-turbo Project 或贡献者名称进行背书。

### libsndfile 内嵌 codec

当前 libsndfile 构建禁用外部库，但源码仍包含：

- ALAC：Apache-2.0；
- GSM 6.10：Jutta Degener 与 Carsten Bormann 的宽松许可，要求保留版权及免责声明；
- 其他实际编入对象必须以 Fork Builder 的符号/构建日志和最终 AAR 审计结果复核。

## 模型和专利提醒

自由软件许可证结论不构成专利法律意见。AMR、MP3、AAC、H.264、HEVC、AV1 等格式在不同司法辖区可能涉及专利。F-Droid 上架前需要根据项目分发地区和当前专利状态进行独立评估。

## 权威证据

每项固定证据文件及 SHA-256 见：

- `tools/ffmpeg/source-lock.properties`
- `tools/ffmpeg/license-lock.properties`
- `docs/licenses/ffmpeg-kit.md`

许可证原文应由固定 commit 的对应源码随构建源包提供，不能仅依赖远端网页长期可用。