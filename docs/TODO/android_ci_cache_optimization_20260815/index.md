---
title: Android CI 缓存与 ARM64 构建优化
repo: https://github.com/luojiaping/Operit
upstream: https://github.com/AAswordman/Operit
status: verified
---

# Android CI 缓存与 ARM64 构建优化

## 原本状况

Android CI 显式使用 `--no-build-cache`，JDK setup 没有启用 Gradle 依赖和 Wrapper 缓存。CMake 依赖 source 位于各模块的 `.cxx/operit_deps`，每个 runner 都重新下载。native ripgrep 的缓存 key 只包含系统和 `Cargo.lock`，没有绑定 NDK、Rust、Android API 或 runner 架构。

DragonBones native module 没有声明 ABI 过滤器，而应用和其他 native module 只支持 `arm64-v8a`。

## 修改意图

- 让 Gradle 复用依赖、Wrapper 和可复用的 build cache
- 让 CMake 只复用带工具链和源码配置 key 的 source tree，不复用带绝对路径的 binary tree
- 让 native ripgrep cache key 覆盖实际编译环境
- 让 DragonBones 的 native 编译范围与应用支持的 `arm64-v8a` 一致

## 作用域

- `.github/workflows/android-build.yml`
- `.github/workflows/android-tests.yml`
- `.github/workflows/pr-check.yml`
- `avator/dragonbones/build.gradle.kts`
- `app/build.gradle.kts`
- 本 TODO 目录

## 验证

- [x] 通过 workflow YAML、仓库门禁和 diff 检查
- [x] 推送 `ci/android-cache-optimization` 到 fork
- [x] 在 fork PR [#16](https://github.com/luojiaping/Operit/pull/16) 中验证 Android build 与 Android JVM tests
- [x] 连续运行确认 Gradle、CMake source 和 native ripgrep cache 命中
- [x] 构建日志中的 native packaging 路径仅使用 `lib/arm64-v8a/`

## 结果

Fork run `31875454511` 的两次尝试均通过 `Fast checks`、`Android JVM tests`、`Android build` 和 `Candidate checks`。

- 首次 Android build：Gradle `24m31s`，`329` 个 actionable tasks 中 `29` 个来自 cache；CMake source cache 首次 miss，完成后保存约 `337 MB`
- 第二次 Android build：Gradle `15m46s`，job 总耗时约 `17m51s`，`329` 个 actionable tasks 中 `116` 个来自 cache
- 第二次日志确认 Gradle wrapper/dependency、CMake source 和 native ripgrep cache 均命中
- Android JVM tests 从首次约 `11m04s` 降至第二次约 `2m28s`
