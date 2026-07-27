#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
readonly TARGET="aarch64-linux-android"
readonly ABI="arm64-v8a"
readonly API_LEVEL="26"
readonly NDK_VERSION="25.1.8937393"
readonly OUTPUT="${REPOSITORY_ROOT}/app/src/main/jniLibs/${ABI}/liboperit_ripgrep.so"

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ndk_home="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [[ -z "${ndk_home}" && -n "${android_home}" ]]; then
  ndk_home="${android_home}/ndk/${NDK_VERSION}"
fi

if [[ -z "${ndk_home}" ]]; then
  echo "ANDROID_NDK_HOME/ANDROID_NDK_ROOT is required, or set ANDROID_HOME with NDK ${NDK_VERSION} installed." >&2
  exit 2
fi

readonly LINKER="${ndk_home}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${API_LEVEL}-clang"
if [[ ! -x "${LINKER}" ]]; then
  echo "Pinned Android linker was not found: ${LINKER}" >&2
  exit 2
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo is required; rust-toolchain.toml pins Rust 1.88.0 and the Android target." >&2
  exit 2
fi

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="${LINKER}"
export CARGO_INCREMENTAL=0
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-0}"

# Run from the crate directory so rustup honors the committed rust-toolchain.toml.
cd "${SCRIPT_DIR}"
cargo build \
  --manifest-path "${SCRIPT_DIR}/Cargo.toml" \
  --release \
  --target "${TARGET}" \
  --locked

readonly BUILT_LIBRARY="${SCRIPT_DIR}/target/${TARGET}/release/liboperit_ripgrep.so"
if [[ ! -s "${BUILT_LIBRARY}" ]]; then
  echo "Rust build did not produce ${BUILT_LIBRARY}" >&2
  exit 1
fi

install -Dm755 "${BUILT_LIBRARY}" "${OUTPUT}"

if command -v readelf >/dev/null 2>&1; then
  readelf -h "${OUTPUT}" | grep -q 'Machine:.*AArch64' || {
    echo "Unexpected ABI for ${OUTPUT}; expected AArch64." >&2
    exit 1
  }
fi

sha256sum "${OUTPUT}"
echo "Built locked native ripgrep source -> ${OUTPUT}"