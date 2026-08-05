---
fork: local workspace
status: complete
---

# 外部原生库构建校验

## 现状

Android Gradle Plugin 可以在缺少构建链外部生成的 native 输入时继续生成 APK 或 AAB。当前 `liboperit_ripgrep.so` 由 Rust 工具链单独编译后复制到应用 JNI 目录，FFmpegKit 则由独立脚本构建为本地 AAR；缺失时会在运行时加载失败。

## 目标

- 在应用打包开始前检查 `liboperit_ripgrep.so` 和本地 FFmpegKit AAR
- 仅覆盖 Gradle 之外构建并复制到应用 JNI 目录的产物
- 对缺失或零长度 native 文件立即使打包失败，并指出生成脚本

## 作用域

- `app/build.gradle.kts`
- `ci/README.md`
- 本目录中的实施记录

## 步骤

1. [外部原生库输入校验](./01-external-native-library-input-check.md) [DONE]

## PR

待创建

## 完成状态

应用打包现在依赖外部原生库输入校验。未运行 Gradle 构建或测试命令。
