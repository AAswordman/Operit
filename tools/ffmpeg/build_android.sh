#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
readonly FFMPEG_KIT_DIR="${1:-${FFMPEG_KIT_DIR:-}}"
readonly REQUIRED_NDK_VERSION="25.1.8937393"
readonly OUTPUT_AAR="${REPOSITORY_ROOT}/app/libs/ffmpeg-kit-local.aar"

if [[ -z "${FFMPEG_KIT_DIR}" ]]; then
  echo "usage: $0 <ffmpeg-kit-checkout>" >&2
  exit 2
fi

prepare_sources() {
  export BASEDIR="${FFMPEG_KIT_DIR}"
  export FFMPEG_KIT_BUILD_TYPE="android"

  # shellcheck source=/dev/null
  source "${BASEDIR}/scripts/variable.sh"
  # shellcheck source=/dev/null
  source "${BASEDIR}/scripts/function-${FFMPEG_KIT_BUILD_TYPE}.sh"

  enable_default_android_architectures
  enable_default_android_libraries
  enable_main_build
  optimize_for_speed
  no_link_time_optimization

  disable_arch arm-v7a
  disable_arch arm-v7a-neon
  disable_arch x86
  disable_arch x86-64

  enable_library android-zlib

  local enabled_libraries=(
    fontconfig
    freetype
    fribidi
    gmp
    gnutls
    lame
    libass
    libiconv
    libtheora
    libvorbis
    libvpx
    libwebp
    libxml2
    opencore-amr
    shine
    speex
    dav1d
    kvazaar
    libilbc
    opus
    snappy
    soxr
    twolame
    vo-amrwbenc
    zimg
  )

  local library
  for library in "${enabled_libraries[@]}"; do
    enable_library "${library}"
  done

  echo "Preparing sources"
  download_gnu_config
  downloaded_library_sources "${ENABLED_LIBRARIES[@]}"
}

if [[ ! -d "${FFMPEG_KIT_DIR}/.git" ]]; then
  echo "ffmpeg-kit Git checkout not found: ${FFMPEG_KIT_DIR}" >&2
  exit 2
fi

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "${ANDROID_HOME}" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required." >&2
  exit 2
fi
export ANDROID_SDK_ROOT="${ANDROID_HOME}"

export ANDROID_NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME}/ndk/${REQUIRED_NDK_VERSION}}}"
export ANDROID_NDK_HOME="${ANDROID_NDK_ROOT}"
if [[ "$(basename -- "${ANDROID_NDK_ROOT}")" != "${REQUIRED_NDK_VERSION}" ]]; then
  echo "Android NDK ${REQUIRED_NDK_VERSION} is required: ${ANDROID_NDK_ROOT}" >&2
  exit 2
fi
if [[ ! -x "${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]]; then
  echo "Pinned Linux NDK was not found at ${ANDROID_NDK_ROOT}" >&2
  exit 2
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is required; Operit Builder uses JDK 21." >&2
  exit 2
fi
if [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "JAVA_HOME does not contain an executable Java runtime: ${JAVA_HOME}" >&2
  exit 2
fi

python3 "${SCRIPT_DIR}/verify_license_lock.py"
python3 "${SCRIPT_DIR}/apply_source_lock.py" --ffmpeg-kit "${FFMPEG_KIT_DIR}"

cd "${FFMPEG_KIT_DIR}"

mkdir -p "${FFMPEG_KIT_DIR}/android"
cat > "${FFMPEG_KIT_DIR}/android/local.properties" <<EOF
sdk.dir=${ANDROID_SDK_ROOT}
ndk.dir=${ANDROID_NDK_ROOT}
EOF

prepare_sources

# Some bundled build scripts pin automake 1.16 binary names even on newer distros.
TOOLS_COMPAT_DIR="${FFMPEG_KIT_DIR}/.tools-compat"
mkdir -p "${TOOLS_COMPAT_DIR}"
ln -sf "$(command -v aclocal)" "${TOOLS_COMPAT_DIR}/aclocal-1.16"
ln -sf "$(command -v automake)" "${TOOLS_COMPAT_DIR}/automake-1.16"
export PATH="${TOOLS_COMPAT_DIR}:${PATH}"

# ffmpeg-kit's libiconv bootstrap misses two m4 files for libcharset on newer distros.
if [[ -d "${FFMPEG_KIT_DIR}/src/libiconv" ]]; then
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/autogen.sh" ]]; then
    sed -i 's/for file in codeset.m4 fcntl-o.m4 lib-ld.m4 relocatable.m4 relocatable-lib.m4 visibility.m4;/for file in codeset.m4 fcntl-o.m4 lib-ld.m4 build-to-host.m4 host-cpu-c-abi.m4 relocatable.m4 relocatable-lib.m4 visibility.m4;/g' "${FFMPEG_KIT_DIR}/src/libiconv/autogen.sh"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/build-to-host.m4" ]]; then
    cp -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/build-to-host.m4" "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/m4/"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/host-cpu-c-abi.m4" ]]; then
    cp -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/host-cpu-c-abi.m4" "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/m4/"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/Makefile.devel" ]]; then
    sed -i 's/aclocal-1\.16/aclocal/g' "${FFMPEG_KIT_DIR}/src/libiconv/Makefile.devel"
    sed -i 's/automake-1\.16/automake/g' "${FFMPEG_KIT_DIR}/src/libiconv/Makefile.devel"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/Makefile.devel" ]]; then
    sed -i 's/aclocal-1\.16/aclocal/g' "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/Makefile.devel"
  fi
fi

export RECONF_libiconv="${RECONF_libiconv:-1}"
export RECONF_gnutls="${RECONF_gnutls:-1}"

# cpu-features tries to download googletest during configure; disable tests for offline/reproducible builds.
if [[ -f "${FFMPEG_KIT_DIR}/scripts/android/cpu-features.sh" ]]; then
  if ! grep -q -- '-DBUILD_TESTING=OFF' "${FFMPEG_KIT_DIR}/scripts/android/cpu-features.sh"; then
    sed -i 's~$(android_ndk_cmake) || return 1~$(android_ndk_cmake) -DBUILD_TESTING=OFF || return 1~' "${FFMPEG_KIT_DIR}/scripts/android/cpu-features.sh"
  fi
fi

# gnutls bootstrap drags optional openssl submodule checks into the Android build path.
if [[ -d "${FFMPEG_KIT_DIR}/src/gnutls" ]]; then
  if [[ -f "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap.conf" ]]; then
    perl -0pi -e 's/ devel\/openssl//g' "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap.conf"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap" ]]; then
    sed -i "/git submodule | grep '\\^-'/c\\if \$use_git && git submodule | grep '^-' | grep -v ' devel/openssl\$' >/dev/null; then" "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap"
    sed -i "/^ >\\/dev\\/null; then$/,+3d" "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap"
  fi

  git -C "${FFMPEG_KIT_DIR}/src/gnutls" submodule sync --recursive
  git -C "${FFMPEG_KIT_DIR}/src/gnutls" submodule update --init --depth 1 \
    gnulib \
    devel/libtasn1 \
    devel/openssl \
    devel/nettle \
    devel/abi-dump \
    cligen \
    tests/suite/tls-fuzzer/python-ecdsa \
    tests/suite/tls-fuzzer/tlsfuzzer \
    tests/suite/tls-fuzzer/tlslite-ng \
    tests/suite/tls-interoperability

  if [[ -d "${FFMPEG_KIT_DIR}/src/gnutls/devel/libtasn1" ]]; then
    git -C "${FFMPEG_KIT_DIR}/src/gnutls/devel/libtasn1" restore --source=HEAD --worktree --staged .
  fi
fi

# Match the currently vendored ffmpeg-kit feature set as closely as possible,
# but keep LTO disabled because the current arm64 libavfilter crashes inside
# avfilter_inout_free while parsing complex filter graphs.
./android.sh \
  -f \
  -s \
  --api-level=26 \
  --disable-arm-v7a \
  --disable-arm-v7a-neon \
  --disable-x86 \
  --disable-x86-64 \
  --enable-fontconfig \
  --enable-freetype \
  --enable-fribidi \
  --enable-gmp \
  --enable-gnutls \
  --enable-lame \
  --enable-libass \
  --enable-libiconv \
  --enable-libtheora \
  --enable-libvorbis \
  --enable-libvpx \
  --enable-libwebp \
  --enable-libxml2 \
  --enable-opencore-amr \
  --enable-shine \
  --enable-speex \
  --enable-dav1d \
  --enable-kvazaar \
  --enable-libilbc \
  --enable-opus \
  --enable-snappy \
  --enable-soxr \
  --enable-twolame \
  --enable-vo-amrwbenc \
  --enable-zimg

built_aar="$(find "${FFMPEG_KIT_DIR}/prebuilt/bundle-android-aar" "${FFMPEG_KIT_DIR}/android/ffmpeg-kit-android-lib/build/outputs/aar" -maxdepth 1 -type f -name '*.aar' -print 2>/dev/null | sort | head -n 1)"
if [[ -z "${built_aar}" || ! -s "${built_aar}" ]]; then
  echo "ffmpeg-kit build did not produce an Android AAR." >&2
  exit 1
fi

install -Dm644 "${built_aar}" "${OUTPUT_AAR}"
python3 "${SCRIPT_DIR}/audit_android_aar.py" \
  --aar "${OUTPUT_AAR}" \
  --report "${REPOSITORY_ROOT}/app/build/reports/ffmpeg-kit-aar-audit.txt"
sha256sum "${OUTPUT_AAR}"
echo "Built locked ffmpeg-kit source -> ${OUTPUT_AAR}"
