# 外部原生库输入校验

## 原有行为

`liboperit_ripgrep.so` 不由 Gradle 或 CMake 生成，而是由 `tools/native_ripgrep/build_native_ripgrep.ps1` 使用 Rust 工具链编译并复制到应用 JNI 目录。FFmpegKit 也由 `tools/ffmpeg/build_ffmpeg_kit_wsl.sh` 独立构建，再导入为 `app/libs/ffmpeg-kit-local.aar`。这些输入缺失时，AGP 仍可继续打包。

## 修改意图

让 `preBuild` 依赖一个专用校验任务。该任务检查 Rust 编译产物
`app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so`，并读取 FFmpegKit AAR，确认其中 10 个 arm64 native 库均为非空条目。

## 预期结果

- `liboperit_ripgrep.so`、FFmpegKit AAR 或其 arm64 native 条目缺失或大小为零时，应用打包失败
- 错误消息指向相应生成或导入脚本
- 不约束 CMake、AAR 或子模块提供的 native 库

## 完成状态

[DONE] 已在应用 Gradle 脚本中注册外部 native 输入校验，并接入 `preBuild`。
