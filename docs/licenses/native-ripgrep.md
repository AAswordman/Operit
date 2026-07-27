# Operit Native Ripgrep 构建与许可证记录

## 产物与消费者

- 源码：`tools/native_ripgrep/src/lib.rs`
- Rust 包：`operit_ripgrep 0.1.0`
- 构建产物：`app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so`
- ABI：`arm64-v8a` / AArch64
- Android API：26
- 消费者：`NativeRipgrep.kt` 通过 `System.loadLibrary("operit_ripgrep")` 加载
- 最终 APK：由 Android `jniLibs` source set 打包；实际归属将在干净 APK 审计中确认

## 固定构建输入

- Rust：`1.88.0`，由 `tools/native_ripgrep/rust-toolchain.toml` 固定；
- Rust target：`aarch64-linux-android`；
- Android NDK：`25.1.8937393`；
- Cargo 依赖：由 `tools/native_ripgrep/Cargo.lock` 的版本、registry 来源和 checksum 固定；
- 构建入口：`bash tools/native_ripgrep/build_android.sh`；
- 构建使用 `cargo build --release --target aarch64-linux-android --locked`，不接受锁文件外解析结果；
- 构建脚本检查输出存在并在 `readelf` 可用时验证 AArch64 机器类型，然后输出 SHA-256。

生成的 `.so` 不提交到 Git；它必须由固定源码在 Android 构建环境中生成。CI 缓存键覆盖 Cargo.toml、Cargo.lock、Rust toolchain 文件和构建脚本，缓存不是构建输入的权威来源。

## 许可证状态

Operit 自有 Rust JNI 源码随仓库根许可证发布。直接和传递 Rust crates 的精确版本及 crates.io checksum 已记录在 `Cargo.lock`，但每个 crate 的 SPDX 许可证表达式和许可证原文仍需通过锁定 crate 源码生成第三方声明。

在第三方许可证清单生成并复核前，本记录不声称 Rust 依赖许可证闭环完成。F-Droid server build 后还需要把最终 `.so` 哈希、ELF NEEDED、符号与本源码构建结果对应起来。