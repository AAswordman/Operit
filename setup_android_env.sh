#!/usr/bin/env bash
set -e

log() {
  echo "[android-setup] $*" >&2
}

fail() {
  log "ERROR: $*"
  exit 1
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

speed_to_int() {
  local speed="$1"
  if [[ ! "$speed" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    echo 0
    return
  fi
  local speed_int="${speed%.*}"
  if [[ -z "$speed_int" ]]; then
    speed_int=0
  fi
  echo "$speed_int"
}

GRADLE_VERSION="9.1.0"
GRADLE_ROOT="${GRADLE_ROOT:-$HOME/gradle}"
GRADLE_DIST="gradle-${GRADLE_VERSION}"
GRADLE_ZIP="${GRADLE_ROOT}/${GRADLE_DIST}-bin.zip"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
SCRIPT_DIR=""
export GRADLE_USER_HOME
APT_UPDATED=0

ping_host() {
  local host="$1"
  local ping_cmd=""
  if command_exists ping; then
    ping_cmd="ping"
  elif command_exists busybox; then
    ping_cmd="busybox ping"
  fi
  if [[ -z "$ping_cmd" ]]; then
    log "ping not found; skipping mirror checks"
    return 2
  fi
  if $ping_cmd -c 1 -W 2 "$host" >/dev/null 2>&1; then
    log "Ping OK: $host"
    return 0
  fi
  log "Ping fail: $host"
  return 1
}

select_download_url() {
  local label="$1"
  local default_url="$2"
  local default_host="$3"
  shift 3
  local mirror_args=("$@")

  log "Selecting fastest mirror for $label"

  if command_exists curl; then
    local best_url="$default_url"
    local best_speed=0

    local probe_dir
    probe_dir=$(mktemp -d)

    {
      local speed
      speed=$(measure_download_speed "$default_url") || speed="0"
      printf '%s\t%s\n' "$(speed_to_int "$speed")" "$default_url" > "$probe_dir/default"
    } &

    local i=0
    local probe_idx=0
    while (( i < ${#mirror_args[@]} )); do
      local url="${mirror_args[$((i + 1))]}"
      local key="mirror_${probe_idx}"
      {
        local speed
        speed=$(measure_download_speed "$url") || speed="0"
        printf '%s\t%s\n' "$(speed_to_int "$speed")" "$url" > "$probe_dir/$key"
      } &
      i=$((i + 2))
      probe_idx=$((probe_idx + 1))
    done

    wait

    local result_file
    while IFS= read -r -d '' result_file; do
      local speed_int
      speed_int=$(cut -f1 "$result_file")
      local url
      url=$(cut -f2- "$result_file")
      if [[ -n "$speed_int" && "$speed_int" -gt "$best_speed" ]]; then
        best_speed="$speed_int"
        best_url="$url"
      fi
    done < <(find "$probe_dir" -type f -print0)

    rm -rf "$probe_dir"

    if [[ "$best_speed" -gt 0 ]]; then
      log "Fastest mirror selected for $label: $best_url (speed=${best_speed}B/s)"
      echo "$best_url"
      return 0
    fi

    log "Speed test failed for all mirrors; fallback to ping selection"
  fi

  if ping_host "$default_host"; then
    echo "$default_url"
    return 0
  fi

  local i=0
  while (( i < ${#mirror_args[@]} )); do
    local host="${mirror_args[$i]}"
    local url="${mirror_args[$((i + 1))]}"
    i=$((i + 2))
    if ping_host "$host"; then
      log "Using mirror for $label: $host"
      echo "$url"
      return 0
    fi
  done

  log "No reachable mirror; fallback to default URL for $label"
  echo "$default_url"
}

measure_download_speed() {
  local url="$1"
  # Download a small range to /dev/null and use curl's measured speed.
  # Use short timeouts to keep selection fast.
  curl -L \
    --range 0-524287 \
    --output /dev/null \
    --silent \
    --show-error \
    --connect-timeout 3 \
    --max-time 8 \
    -w "%{speed_download}" \
    "$url" 2>/dev/null
}

download_file() {
  local url="$1"
  local dest="$2"
  local max_retries=3
  local retry_count=0

  while [[ $retry_count -lt $max_retries ]]; do
    if command_exists curl; then
      if curl -L --connect-timeout 30 --max-time 120 --retry 2 --retry-delay 3 "$url" -o "$dest"; then
        return 0
      fi
    elif command_exists wget; then
      if wget --timeout=30 --tries=3 --waitretry=3 -O "$dest" "$url"; then
        return 0
      fi
    else
      log "curl or wget is required to download files."
      exit 1
    fi

    retry_count=$((retry_count + 1))
    log "Download failed, retrying ($retry_count/$max_retries)..."
    sleep 2
  done

  log "Failed to download file after $max_retries attempts: $url"
  return 1
}

install_packages() {
  local packages=("$@")
  if command_exists apt-get; then
    local sudo_cmd=""
    if command_exists sudo; then
      sudo_cmd="sudo"
    fi
    log "Installing packages: ${packages[*]}"
    if [[ "$APT_UPDATED" -eq 0 ]]; then
      $sudo_cmd apt-get update
      APT_UPDATED=1
    fi
    $sudo_cmd apt-get install -y "${packages[@]}"
  else
    log "apt-get not found; please install: ${packages[*]}"
  fi
}

ensure_ping() {
  if command_exists ping || command_exists busybox; then
    return
  fi
  if command_exists apt-get; then
    install_packages iputils-ping
  fi
  if ! command_exists ping && ! command_exists busybox; then
    log "ping still unavailable; mirror selection will be skipped"
  fi
}

ensure_java() {
  if command_exists java; then
    local version
    version=$(java -version 2>&1 | sed -n 's/.*version "\(.*\)".*/\1/p')
    local major=${version%%.*}
    if [[ "$major" == "1" ]]; then
      major=$(echo "$version" | cut -d. -f2)
    fi
    if [[ -n "$major" && "$major" -ge 17 ]]; then
      log "Java $version detected"
      return
    fi
    log "Java version $version is below 17; upgrading"
  else
    log "Java not found; installing OpenJDK 17"
  fi
  install_packages openjdk-17-jdk
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then
    return
  fi
  if command_exists java; then
    local java_path
    java_path=$(readlink -f "$(command -v java)")
    JAVA_HOME=$(dirname "$(dirname "$java_path")")
    export JAVA_HOME
  fi
}

ensure_android_tools() {
  ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android}}"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"

  if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "Downloading Android command line tools"
    install_packages unzip
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    local tmp_dir
    tmp_dir=$(mktemp -d)
    local zip_path="$tmp_dir/cmdline-tools.zip"
    local cmdline_url
    cmdline_url=$(select_download_url \
      "Android command line tools" \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "dl.google.com" \
      "mirrors.tuna.tsinghua.edu.cn" "https://mirrors.tuna.tsinghua.edu.cn/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "mirrors.bfsu.edu.cn" "https://mirrors.bfsu.edu.cn/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "mirrors.aliyun.com" "https://mirrors.aliyun.com/android/repository/commandlinetools-linux-11076708_latest.zip")
    download_file "$cmdline_url" "$zip_path"
    unzip -q "$zip_path" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp_dir"
  fi

  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
  log "Installing Android SDK packages"
  yes | sdkmanager --licenses >/dev/null || true
  sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
}

ensure_gradle() {
  install_packages unzip
  mkdir -p "$GRADLE_ROOT"
  if command_exists gradle; then
    local installed_version
    installed_version=$(gradle --version 2>/dev/null | grep -oP 'Gradle \K[0-9.]+' | head -1 || true)
    if [[ -n "$installed_version" ]]; then
      log "System Gradle detected: $installed_version (still preparing local Gradle zip for wrapper cache)"
    else
      log "System Gradle detected (version parse failed); preparing local Gradle zip for wrapper cache"
    fi
  fi

  if [[ ! -f "$GRADLE_ZIP" ]]; then
    log "Downloading Gradle ${GRADLE_VERSION}"
    local gradle_url
    gradle_url=$(select_download_url \
      "Gradle distribution" \
      "https://services.gradle.org/distributions/${GRADLE_DIST}-bin.zip" \
      "services.gradle.org" \
      "mirrors.cloud.tencent.com" "https://mirrors.cloud.tencent.com/gradle/${GRADLE_DIST}-bin.zip" \
      "mirrors.aliyun.com" "https://mirrors.aliyun.com/gradle/${GRADLE_DIST}-bin.zip")
    download_file "$gradle_url" "$GRADLE_ZIP"
  else
    log "Gradle zip already present: $GRADLE_ZIP"
  fi

  if [[ ! -d "$GRADLE_ROOT/$GRADLE_DIST" ]]; then
    log "Extracting Gradle ${GRADLE_VERSION}"
    unzip -q "$GRADLE_ZIP" -d "$GRADLE_ROOT"
  else
    log "Local Gradle already extracted: $GRADLE_ROOT/$GRADLE_DIST"
  fi

  export GRADLE_HOME="$GRADLE_ROOT/$GRADLE_DIST"
  export PATH="$GRADLE_HOME/bin:$PATH"
}

update_gradle_wrapper_properties() {
  local wrapper_file="gradle/wrapper/gradle-wrapper.properties"
  if [[ ! -f "$wrapper_file" ]]; then
    return
  fi
  if [[ ! -f "$GRADLE_ZIP" ]]; then
    log "Gradle zip not found; keeping existing wrapper distributionUrl"
    return
  fi

  local gradle_zip_abs="$GRADLE_ZIP"
  if command_exists readlink; then
    gradle_zip_abs=$(readlink -f "$GRADLE_ZIP" 2>/dev/null || echo "$GRADLE_ZIP")
  fi
  local file_url="file\\://$gradle_zip_abs"

  if grep -q '^distributionUrl=' "$wrapper_file"; then
    sed -i "s|^distributionUrl=.*|distributionUrl=$file_url|" "$wrapper_file"
  else
    echo "distributionUrl=$file_url" >> "$wrapper_file"
  fi
  log "Wrapper distributionUrl set to local file: $gradle_zip_abs"
}

warmup_gradle_wrapper_cache() {
  if [[ ! -x "./gradlew" ]]; then
    log "gradlew not found; skipping wrapper cache warm-up"
    return 0
  fi
  if [[ ! -f "gradle/wrapper/gradle-wrapper.properties" ]]; then
    log "gradle-wrapper.properties not found; skipping wrapper cache warm-up"
    return 0
  fi
  log "Warming Gradle wrapper cache"
  if ! ./gradlew --version --no-daemon >/dev/null; then
    log "Wrapper cache warm-up failed; continuing"
    return 1
  fi
}

restore_gradle_properties() {
  cat > gradle.properties <<'EOF'
# Project-wide Gradle settings.
# IDE (e.g. Android Studio) users:
# Gradle settings configured through the IDE *will override*
# any settings specified in this file.
# For more details on how to configure your build environment visit
# http://www.gradle.org/docs/current/userguide/build_environment.html
# Specifies the JVM arguments used for the daemon process.
# The setting is particularly useful for tweaking memory settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. For more details, visit
# https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
# org.gradle.parallel=true
# AndroidX package structure to make it clearer which packages are bundled with the
# Android operating system, and which are packaged with your app's APK
# https://developer.android.com/topic/libraries/support-library/androidx-rn
android.useAndroidX=true
# Kotlin code style for this project: "official" or "obsolete":
kotlin.code.style=official
# Enables namespacing of each library's R class so that its R class includes only the
# resources declared in the library itself and none from the library's dependencies,
# thereby reducing the size of the R class for that library
android.nonTransitiveRClass=true

# Proot/Termux Compatibility Settings
# Disable AAPT2 daemon mode to prevent "Daemon startup failed" errors in proot environment
android.aapt2.process.daemon=false
EOF
}

restore_gradlew_bat() {
  cat > gradlew.bat <<'EOF'
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
EOF
}

update_local_properties() {
  local sdk_dir="$ANDROID_HOME"
  cat > local.properties <<EOF
## This file is automatically generated by Android Studio.
# Do not modify this file -- YOUR CHANGES WILL BE ERASED!
#
# This file should *NOT* be checked into Version Control Systems,
# as it contains information specific to your local configuration.
#
# Location of the SDK. This is only used by Gradle.
# For customization when using a Version Control System, please read the
# header note.
sdk.dir=$sdk_dir
EOF
}

configure_env_persistence() {
  local bashrc="$HOME/.bashrc"
  touch "$bashrc"
  if ! grep -q "operit android env" "$bashrc"; then
    cat >> "$bashrc" <<EOF
# >>> operit android env >>>
export JAVA_HOME=$JAVA_HOME
export ANDROID_HOME=$ANDROID_HOME
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$JAVA_HOME/bin:\$PATH
export GRADLE_USER_HOME=$GRADLE_USER_HOME
export GRADLE_HOME=${GRADLE_HOME:-$HOME/gradle/gradle-9.1.0}
export PATH=\$GRADLE_HOME/bin:\$PATH
# <<< operit android env <<<
EOF
    log "Environment variables appended to ~/.bashrc"
  else
    log "Environment variables already configured in ~/.bashrc"
  fi
}

warmup_gradle_cache_for_aapt2() {
  local gradle_cmd="$GRADLE_HOME/bin/gradle"
  if [[ ! -x "$gradle_cmd" ]]; then
    log "Local Gradle not found: $gradle_cmd"
    return 1
  fi
  log "Running warm-up Gradle task to resolve and execute AAPT2"
  if ! "$gradle_cmd" --no-daemon --rerun-tasks :app:processDebugResources; then
    log "AAPT2 pre-replace warm-up failed; continuing to patch aapt2"
    return 1
  fi
}

warmup_gradle_cache_after_aapt2_replace() {
  local gradle_cmd="$GRADLE_HOME/bin/gradle"
  if [[ ! -x "$gradle_cmd" ]]; then
    log "Local Gradle not found: $gradle_cmd"
    return 1
  fi
  log "Running post-replace warm-up to ensure patched AAPT2 is used"
  if ! "$gradle_cmd" --no-daemon --rerun-tasks :app:processDebugResources; then
    log "AAPT2 post-replace warm-up failed; setup will still continue"
    return 1
  fi
}

replace_aapt2() {
  local bundled_aapt2="$SCRIPT_DIR/tools/aapt2/aapt2-arm64-v8a"
  local expected_sha256="e5b5ff7f0d4f6ecd7fa5d05d77fed3f09f6f1bf80f078b8aada82bc578848561"
  if [[ ! -f "$bundled_aapt2" ]]; then
    fail "Bundled ARM64 aapt2 not found: $bundled_aapt2"
  fi

  local actual_sha256
  actual_sha256=$(sha256sum "$bundled_aapt2" | awk '{print $1}')
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    fail "Bundled ARM64 aapt2 checksum mismatch: expected $expected_sha256, got $actual_sha256"
  fi

  local tmp_dir
  tmp_dir=$(mktemp -d)
  local aapt2_path="$tmp_dir/aapt2"
  log "Using bundled ARM64 aapt2 from template"
  cp "$bundled_aapt2" "$aapt2_path"
  chmod +x "$aapt2_path"

  if [[ -d "$ANDROID_HOME/build-tools/35.0.0" ]]; then
    cp "$aapt2_path" "$ANDROID_HOME/build-tools/35.0.0/aapt2"
    log "Replaced SDK build-tools aapt2"
  fi

  local gradle_cache_root="$GRADLE_USER_HOME/caches"
  local gradle_aapt_dir="$gradle_cache_root/modules-2/files-2.1/com.android.tools.build/aapt2"
  if [[ -d "$gradle_aapt_dir" ]]; then
    local updated_jar_count=0
    while IFS= read -r -d '' jar_path; do
      local jar_dir
      jar_dir=$(dirname "$jar_path")
      cp "$aapt2_path" "$jar_dir/aapt2"
      (cd "$jar_dir" && zip -q -f "$(basename "$jar_path")" aapt2)
      updated_jar_count=$((updated_jar_count + 1))
    done < <(find "$gradle_aapt_dir" -name "aapt2-*-linux.jar" -print0)
    log "Updated Gradle cache aapt2 jars: $updated_jar_count"
  else
    log "Gradle aapt2 module cache not found: $gradle_aapt_dir"
  fi

  local updated_transform_count=0
  while IFS= read -r -d '' transforms_dir; do
    while IFS= read -r -d '' transformed_aapt2; do
      cp "$aapt2_path" "$transformed_aapt2"
      updated_transform_count=$((updated_transform_count + 1))
    done < <(find "$transforms_dir" -name "aapt2" -type f -print0 2>/dev/null || true)
  done < <(find "$gradle_cache_root" -maxdepth 1 -type d -name "transforms-*" -print0 2>/dev/null || true)
  if [[ "$updated_transform_count" -gt 0 ]]; then
    log "Updated transformed aapt2 binaries: $updated_transform_count"
  else
    log "No transformed aapt2 binaries found under: $gradle_cache_root"
  fi

  rm -rf "$tmp_dir"
}

# ------------------------------------------------------------
# ARM64 NDK: inlocuim linkerele x86-64 cu LLVM 18 nativ
# Rezolva: ld/ld.lld/lld + llvm-ar/llvm-ranlib crash pe ARM64,
#          --record-libdeps (flag LLVM vs GNU ar)
# ------------------------------------------------------------
ensure_ndk_arm64_toolchain() {
  local ndk_root
  ndk_root=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)
  if [[ -z "$ndk_root" ]]; then
    log "NDK not found; skipping ARM64 toolchain fix"
    return
  fi
  local ndk_bin="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin"
  local llvm_bin="/usr/lib/llvm-18/bin"
  if [[ ! -d "$llvm_bin" ]]; then
    log "LLVM 18 not found; installing llvm-18 lld-18"
    install_packages llvm-18 lld-18
  fi
  local sudo_cmd=""
  if command_exists sudo; then
    sudo_cmd="sudo"
  fi
  log "NDK: $ndk_root"
  if [[ -f "$llvm_bin/ld.lld" ]]; then
    $sudo_cmd ln -sf "$llvm_bin/ld.lld" "$ndk_bin/ld.lld"
    $sudo_cmd ln -sf "$llvm_bin/ld.lld" "$ndk_bin/ld"
    $sudo_cmd ln -sf "$llvm_bin/ld.lld" "$ndk_bin/lld" 2>/dev/null || true
    log "Replaced NDK linker (ld/ld.lld/lld) with ARM64 LLVM 18"
  fi
  for tool in llvm-ar llvm-ranlib llvm-strip; do
    if [[ -f "$llvm_bin/$tool" ]]; then
      $sudo_cmd ln -sf "$llvm_bin/$tool" "$ndk_bin/$tool"
    fi
  done
  $sudo_cmd chmod +x "$ndk_bin"/ld* "$ndk_bin"/lld* "$ndk_bin"/llvm-* 2>/dev/null || true
  log "Replaced NDK llvm-ar/llvm-ranlib/llvm-strip with ARM64 LLVM 18"
}

# ------------------------------------------------------------
# ARM64 NDK sysroot: stub-uri libgcc/libunwind/libatomic
# Rezolva: bad_array_new_length, operator new(align_val_t)
#          NDK 27 cauta aceste lib-uri; lipsa lor => link fail
# ------------------------------------------------------------
patch_ndk_sysroot_libs() {
  local ndk_root
  ndk_root=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)
  if [[ -z "$ndk_root" ]]; then
    log "NDK not found; skipping sysroot lib patch"
    return
  fi
  local sysroot="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
  if [[ ! -d "$sysroot" ]]; then
    log "NDK sysroot not found; skipping sysroot lib patch"
    return
  fi
  local sudo_cmd=""
  if command_exists sudo; then
    sudo_cmd="sudo"
  fi
  local patched=0
  for abi_dir in "$sysroot"/usr/lib/*/; do
    [[ -d "$abi_dir" ]] || continue
    for lib in libgcc.a libunwind.a libatomic.a; do
      if [[ ! -f "$abi_dir/$lib" ]]; then
        if $sudo_cmd touch "$abi_dir/$lib" 2>/dev/null; then
          patched=$((patched + 1))
        fi
      fi
    done
  done
  log "NDK sysroot stub libs patched: $patched"
}

# ------------------------------------------------------------
# AIDL x86-64 -> wrapper C + qemu-x86_64 (daca e cazul)
#   + genereaza IAccessibilityProvider (AGP nu il genereaza)
# ------------------------------------------------------------
fix_aidl_arm64() {
  local bt_dir
  bt_dir=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)
  if [[ -z "$bt_dir" ]]; then
    log "build-tools not found; skipping AIDL fix"
    return
  fi
  local aidl_bin="$bt_dir/aidl"
  if [[ ! -f "$aidl_bin" ]]; then
    log "aidl not found in $bt_dir; skipping AIDL fix"
    return
  fi
  if ! command_exists gcc; then
    install_packages gcc
  fi
  if ! command_exists qemu-x86_64; then
    install_packages qemu-user
  fi
  if file "$aidl_bin" | grep -q "x86-64"; then
    local sudo_cmd=""
    if command_exists sudo; then
      sudo_cmd="sudo"
    fi
    if [[ ! -f "$aidl_bin.orig" ]]; then
      $sudo_cmd cp "$aidl_bin" "$aidl_bin.orig"
      log "Backup AIDL original: $aidl_bin.orig"
    fi
    local tmp_c
    tmp_c=$(mktemp)
    cat > "$tmp_c" <<EOF
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
int main(int argc, char **argv) {
    const char *bin = "$aidl_bin.orig";
    char **args = malloc((argc + 3) * sizeof(char *));
    int n = 0;
    args[n++] = "qemu-x86_64";
    args[n++] = (char *)bin;
    for (int i = 1; i < argc; i++) args[n++] = argv[i];
    args[n] = NULL;
    execv("/usr/bin/qemu-x86_64", args);
    perror("execv qemu");
    return 127;
}
EOF
    local tmp_wrap
    tmp_wrap=$(mktemp)
    if gcc -O2 -o "$tmp_wrap" "$tmp_c"; then
      $sudo_cmd cp "$tmp_wrap" "$aidl_bin"
      $sudo_cmd chmod +x "$aidl_bin"
      log "Replaced AIDL with qemu wrapper: $aidl_bin"
    else
      log "gcc wrapper build failed; keeping original AIDL"
    fi
    rm -f "$tmp_c" "$tmp_wrap"
  else
    log "AIDL already ARM64 native: $aidl_bin"
  fi

  # Genereaza IAccessibilityProvider + IAccessibilityEventCallback (AGP nu le
  # genereaza din .aidl - atat pentru cloneRelease cat si pentru variantele
  # standard/release). Fisierele sunt surse persistente in app/src/main/java si
  # nu dispar la clean. Folosim aidl ARM64 nativ cu -I import path.
  local aidl_native=""
  local ver
  for ver in 36.0.0 35.0.0 34.0.0; do
    local cand="$ANDROID_HOME/build-tools/$ver/aidl"
    if [[ -f "$cand" ]] && file "$cand" | grep -qE "ARM aarch64|ARM64"; then
      aidl_native="$cand"
      log "aidl ARM64 nativ gasit: $cand"
      break
    fi
  done
  if [[ -z "$aidl_native" ]] && [[ -f "$ANDROID_HOME/build-tools/36.0.0/aidl" ]]; then
    aidl_native="$ANDROID_HOME/build-tools/36.0.0/aidl"
    log "Folosesc aidl 36.0.0 (poate fi wrapper)"
  fi

  local aidl_src_dir="app/src/main/aidl"
  local aidl_dst_dir="app/src/main/java"
  if [[ -n "$aidl_native" ]] && [[ -d "$aidl_src_dir" ]]; then
    log "Generare IAccessibilityProvider + IAccessibilityEventCallback ..."
    "$aidl_native" --lang=java -I "$aidl_src_dir" -o "$aidl_dst_dir" \
      "$aidl_src_dir/com/ai/assistance/operit/provider/IAccessibilityProvider.aidl" || \
      log "IAccessibilityProvider generation failed (non-fatal)"
    "$aidl_native" --lang=java -I "$aidl_src_dir" -o "$aidl_dst_dir" \
      "$aidl_src_dir/com/ai/assistance/operit/provider/IAccessibilityEventCallback.aidl" || \
      log "IAccessibilityEventCallback generation failed (non-fatal)"
    if [[ -f "$aidl_dst_dir/com/ai/assistance/operit/provider/IAccessibilityProvider.java" ]]; then
      log "OK: IAccessibilityProvider.java generat ($(stat -c%s "$aidl_dst_dir/com/ai/assistance/operit/provider/IAccessibilityProvider.java") bytes)"
    fi
    if [[ -f "$aidl_dst_dir/com/ai/assistance/operit/provider/IAccessibilityEventCallback.java" ]]; then
      log "OK: IAccessibilityEventCallback.java generat ($(stat -c%s "$aidl_dst_dir/com/ai/assistance/operit/provider/IAccessibilityEventCallback.java") bytes)"
    fi
  else
    log "!! Nu am gasit aidl ARM64 - generare manuala SKIP (build-ul va pica daca lipsesc .java-urile)"
  fi
}

# ------------------------------------------------------------
# Librarii native locale: dragonbones AAR prebuilt v1.1.0
#   - offline: copia din tools/dragonbones/ (inclus in repo)
#   - fallback: download din release-ul oficial GitHub
#   + ffmpeg-kit-local.aar (optional, dar verificat)
# ------------------------------------------------------------
ensure_native_libs() {
  mkdir -p app/libs
  local bundled_dragonbones="$SCRIPT_DIR/tools/dragonbones/dragonbones-release.aar"
  local expected_sha256="639238ed2d6d2c68fb7f4954c809475aaee25e4c5eeaebfcc690f53d7f775807"
  if [[ ! -f "app/libs/dragonbones-release.aar" ]]; then
    if [[ -f "$bundled_dragonbones" ]]; then
      local actual_sha256
      actual_sha256=$(sha256sum "$bundled_dragonbones" | awk '{print $1}')
      if [[ "$actual_sha256" == "$expected_sha256" ]]; then
        cp "$bundled_dragonbones" "app/libs/dragonbones-release.aar"
        log "dragonbones-release.aar copiat din tools/dragonbones (offline, SHA256 OK)"
      else
        log "dragonbones bundled checksum mismatch: $actual_sha256 != $expected_sha256"
      fi
    fi
  fi
  if [[ ! -f "app/libs/dragonbones-release.aar" ]]; then
    local dragonbones_url="https://github.com/Davinci198/dragonbones-aar-prebuilt/releases/download/v1.1.0/dragonbones-release.aar"
    log "Downloading dragonbones-release.aar v1.1.0 (2.49MB)"
    if download_file "$dragonbones_url" "app/libs/dragonbones-release.aar"; then
      log "dragonbones-release.aar downloaded from GitHub release"
    else
      log "dragonbones download failed; foloseste AAR-ul din repo-ul dragonbones-aar-prebuilt"
    fi
  else
    log "dragonbones-release.aar already present"
  fi
  if [[ ! -f "app/libs/ffmpeg-kit-local.aar" ]]; then
    log "ffmpeg-kit-local.aar missing - build-ul merge si fara el (FFmpeg optional)"
    log "Pentru suport FFmpeg complet, copiaza ffmpeg-kit-local.aar in app/libs/"
  else
    log "ffmpeg-kit-local.aar already present"
  fi
}

main() {
  SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  cd "$SCRIPT_DIR"

  if [[ -f "./gradlew" ]]; then
    chmod +x "./gradlew"
  fi

  install_packages wget curl unzip zip
  ensure_ping
  ensure_java
  resolve_java_home
  ensure_android_tools
  ensure_gradle
  update_gradle_wrapper_properties
  if ! warmup_gradle_wrapper_cache; then
    log "Ignoring wrapper warm-up error and continuing"
  fi
  restore_gradle_properties
  restore_gradlew_bat
  update_local_properties
  ensure_native_libs
  ensure_ndk_arm64_toolchain
  patch_ndk_sysroot_libs
  fix_aidl_arm64
  if ! warmup_gradle_cache_for_aapt2; then
    log "Ignoring pre-replace warm-up error and continuing to patch aapt2"
  fi
  replace_aapt2
  if ! warmup_gradle_cache_after_aapt2_replace; then
    log "Ignoring post-replace warm-up error and continuing"
  fi
  configure_env_persistence

  log "Android environment setup complete"
  log "Reload shell or run: source ~/.bashrc"
}

main "$@"
