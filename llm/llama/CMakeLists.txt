cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/operit_git_source.cmake")

set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# ===========================================================================
# [PATCH 0025 / gpu7] 后端切换：Vulkan -> OpenCL (Adreno 740)
#
# 背景（HANDOFF 第十五节）：gpu6 已真机实测 Vulkan 后端「注册可用、执行不可用」——
#   dev_count=2 / gpu_devices=1 / 28 层 offload / 进入 graph_compute，但驱动在
#   vk::Device::createComputePipeline 返回 ErrorUnknown -> 未捕获 vk::SystemError
#   -> std::terminate -> SIGABRT（另有加载期 SIGSEGV）。而 llama.cpp 官方 OpenCL
#   后端在 Adreno 上是 Stable 路径：有专用 Adreno kernels、Q4_K 现已支持、
#   实测 Q4_0 --pure 全卸载 75.02 t/s prefill / 11.85 t/s decode。
#
# 「Android 没有公开的 OpenCL 头/库」的判断已作废：只需 Khronos 开源的
# OpenCL-Headers + OpenCL-ICD-Loader（Apache-2.0），交叉编译后放进 NDK sysroot。
# 上游 llama.cpp 的 OPENCL.md 里 Android 章节即为此方案。
#
# 依赖已就位：<NDK>/toolchains/llvm/prebuilt/linux-x86_64/sysroot/
#   usr/include/CL/          (OpenCL-Headers)
#   usr/lib/aarch64-linux-android/libOpenCL.so  (ICD Loader, arm64-v8a)
# 使 ggml/src/ggml-opencl/CMakeLists.txt 的 find_package(OpenCL REQUIRED) 可用。
#
# GGML_VULKAN=OFF：彻底不编 Vulkan，APK 内也不再放 libggml-vulkan.so，
#   从源头消除 q4_k compute pipeline 崩溃路径。
# GGML_BACKEND_DL=ON 保留：OpenCL 编成 libggml-opencl.so MODULE，运行时由
#   ggml_backend_load_best("opencl") dlopen；加载失败只返回 nullptr、不 abort。
# ===========================================================================
# ===========================================================================
# [REVIEW P1] OpenCL 改为显式开关，默认关闭。
#
# 原实现无条件 GGML_OPENCL=ON，而 ggml-opencl 用 find_package(OpenCL REQUIRED)：
# 头文件和交叉编译的 libOpenCL.so 并不在仓库里，需要手工放进 NDK sysroot。
# 结果是「干净检出 + 官方 NDK + CI」全都构建失败，只有作者本机能编。
#
# 现在：默认纯 CPU 构建（任何人 clone 下来就能编），要用 OpenCL 时显式打开：
#   -DOPERIT_ENABLE_OPENCL=ON
# 并在 NDK sysroot 里准备好 CL 头文件与 libOpenCL.so（见下方校验处的提示）。
# ===========================================================================
option(OPERIT_ENABLE_OPENCL
       "Build the ggml OpenCL backend (needs OpenCL headers + ICD loader in the NDK sysroot)"
       OFF)

if (OPERIT_ALLOW_CPU_ONLY OR NOT OPERIT_ENABLE_OPENCL)
    set(GGML_OPENCL                   OFF CACHE BOOL "" FORCE)
    message(STATUS "[operit] OpenCL backend disabled (CPU-only build)")
else()
    set(GGML_OPENCL                       ON  CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS    ON  CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS         ON  CACHE BOOL "" FORCE)
    set(GGML_OPENCL_TARGET_VERSION        "300" CACHE STRING "" FORCE)
    set(GGML_BACKEND_DL                   ON  CACHE BOOL "" FORCE)
endif()
set(GGML_VULKAN                   OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_VALIDATE          OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_CHECK_RESULTS     OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_SHADER_DEBUG_INFO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP                   OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE                OFF CACHE BOOL "" FORCE)

# ===========================================================================
# [PATCH 0018] ★★ 真正的性能根因修复：强制 Release 级优化 ★★
#
# 症状：0.5B Q4_K_M 在骁龙 8 Gen 2 上 prefill 仅 2.5 tok/s；
#       PATCH 0015（dotprod，sdot 482 条）实测后仍为 2.54 tok/s，
#       手机持续满载发热。dotprod 不是主因。
#
# 根因：App 一直以 assembleDebug 构建，Gradle 向 CMake 传入
#       -DCMAKE_BUILD_TYPE=Debug。本工程的 CMAKE_C_FLAGS_DEBUG /
#       CMAKE_CXX_FLAGS_DEBUG 为空，Android toolchain 也不补 -O，
#       于是 clang 采用默认 -O0。实测 build.ninja 里 ggml-cpu 的 FLAGS：
#         -g ... -fstack-protector-strong ... -D_FORTIFY_SOURCE=2 ...
#         -march=armv8.2-a+dotprod      （全程无任何 -O 选项）
#       ggml / llama.cpp 的全部量化 kernel 以 -O0 编译：函数不内联、
#       NEON intrinsic 每次都走栈往返，还叠加栈保护与 FORTIFY 检查，
#       比 Release 慢 10~40 倍。
#       对照：本地 x86 复现为 Release + -march=native，同参数 264 tok/s。
#       基线函数大小 ggml_vec_dot_q5_0_q8_0 = 700 条指令（-O0）。
#
# 修复：在 add_subdirectory(llama.cpp) 之前注入目录级编译选项。
#       add_compile_options 生成的参数排在 toolchain 的 CMAKE_C_FLAGS
#       之后，可覆盖默认 -O0。
#       [REVIEW P2] 这里只保留 -O3 -DNDEBUG。早期版本还加了
#       -fno-stack-protector / -U_FORTIFY_SOURCE / -g0，但那会把栈保护与
#       FORTIFY 从整个原生库上摘掉，且没有实测证明它们在这一层有性能收益，
#       因此去掉 —— 安全加固保持默认。
#       必须放在 add_subdirectory 之前，否则对 llama.cpp 子目录的目标无效。
# ===========================================================================
add_compile_options(
    -O3
    -DNDEBUG
)

# ===========================================================================
# [PATCH 0015] ★ 关键性能修复：启用 ARM dotprod 指令集 ★
#
# 症状：Qwen2.5-0.5B Q4_K_M 在骁龙 8 Gen 2 上 prefill 只有 2.13 tok/s，
#       6 个线程 100% 满载跑了 52 秒才处理 119 个 token。
#       而同样参数在 x86 上有 264 tok/s，相差 124 倍。
#
# 根因：Android 是交叉编译，ggml/CMakeLists.txt:104 检测到
#       CMAKE_CROSSCOMPILING 后把 GGML_NATIVE_DEFAULT 设为 OFF，
#       于是 GGML_NATIVE=OFF。
#       再看 ggml/src/ggml-cpu/CMakeLists.txt 的 ARM 分支：
#         if (GGML_NATIVE)            → 运行时探测 dotprod/i8mm/sve，加 -march
#         elseif (GGML_CPU_ARM_ARCH)  → 用指定架构
#         elseif (GGML_CPU_ALL_VARIANTS) → 构建多份
#         (以上都不满足)              → ARCH_FLAGS 保持为空
#       我们三项都没设，所以编译成 clang 的默认基线 armv8-a，
#       不带 +dotprod 扩展。
#
# 后果：ggml/src/ggml-cpu/arch/arm/quants.c 里所有量化点积优化都包在
#       #if defined(__ARM_FEATURE_DOTPROD) 里，没有该宏就全部退化成
#       ggml-quants.c 的通用标量 C 实现，慢 10~20 倍。
#
# 修复：显式指定 armv8.2-a+dotprod。
#   骁龙 8 Gen 2（Cortex-X3 + A715 + A510）全核支持 dotprod（ARMv8.2 特性）。
#   不加 i8mm 是为了兼容：A510 小核不支持 i8mm，而 6 线程可能被调度到小核，
#   若编译带 i8mm 会触发 SIGILL。dotprod 是全核通用的安全上限。
#
# 验证方法（构建日志里应该出现）：
#   "Checking for ARM features using flags: -march=armv8.2-a+dotprod"
#   且 HAVE_DOTPROD 为真。
# ===========================================================================
# [REVIEW P1] 不再强制 -march=armv8.2-a+dotprod。
# dotprod 是 ARMv8.2 特性；Cortex-A73/A53 这类跑 Android 9 的老核心（如骁龙 835）
# 不支持，而 ggml 的量化 kernel 是编译期用 #if defined(__ARM_FEATURE_DOTPROD) 选的，
# 一旦编进去，这些设备首次推理就 SIGILL。作者机器全核支持不代表所有设备支持。
#
# 现在默认不覆盖架构（= 上游默认基线，任何 arm64 都能启动）。要性能上限时显式打开：
#   -DOPERIT_ARM_ARCH=armv8.2-a+dotprod    # 仅当确认目标设备全核支持 dotprod
option(OPERIT_ARM_ARCH
       "Override GGML_CPU_ARM_ARCH for arm64-v8a builds (empty = upstream default, no ISA floor)"
       "")

if (ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a" AND OPERIT_ARM_ARCH)
    set(GGML_CPU_ARM_ARCH "${OPERIT_ARM_ARCH}" CACHE STRING "" FORCE)
    message(WARNING
        "[operit] ARM arch override: GGML_CPU_ARM_ARCH=${GGML_CPU_ARM_ARCH} — "
        "devices lacking these ISA extensions will SIGILL on first inference")
endif()

# 确保 repack 开启（use_extra_bufts 关闭后，repack 仍可独立工作）
set(GGML_CPU_REPACK               ON  CACHE BOOL "" FORCE)

operit_prepare_git_source(
    OPERIT_LLAMA_CPP_SOURCE_DIR
    OPERIT_LLAMA_CPP_BINARY_DIR
    llama_cpp
    "https://github.com/ggml-org/llama.cpp.git"
    "720d7fa4097f76e5d0eade5a92c1df87c1faf9d9"
)

add_subdirectory("${OPERIT_LLAMA_CPP_SOURCE_DIR}" "${OPERIT_LLAMA_CPP_BINARY_DIR}" EXCLUDE_FROM_ALL)

add_library(
    LlamaWrapper
    SHARED
    src/main/cpp/llama_jni_stub.cpp
)

target_compile_definitions(LlamaWrapper PRIVATE OPERIT_HAS_LLAMA_CPP=1)
# [PATCH 0017] 构建标签：运行时日志自证身份（见 llama_jni_stub.cpp PATCH 0017）
target_compile_definitions(LlamaWrapper PRIVATE OPERIT_BUILD_TAG="gpu11-kqv-default")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/common")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/include")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/ggml/include")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/vendor")
target_link_libraries(LlamaWrapper llama llama-common)

target_link_libraries(
    LlamaWrapper
    android
    log
)

# 16KB page size support (Android 15+ requirement)
target_link_options(LlamaWrapper PRIVATE "-Wl,-z,max-page-size=16384")

# ===========================================================================
# [PATCH 0025 / gpu7] OpenCL 后端存在性校验
#
# ggml-opencl 的 CMakeLists 用 find_package(OpenCL REQUIRED)，缺头/库会直接
# FATAL_ERROR；但若某些路径把 GGML_OPENCL 静默关掉，构建会"成功"而产物只有
# CPU 后端。这里显式校验 target，避免重演 gpu5「以为 GPU 启用实际全程 CPU」。
# ===========================================================================
if (TARGET ggml-opencl)
    # BACKEND_DL 下 ggml-opencl 是 MODULE 库，运行时由 ggml 自己 dlopen；
    # 这里只声明构建顺序，不做静态链接（否则会把 libOpenCL.so 带进 libggml.so）。
    add_dependencies(LlamaWrapper ggml-opencl)
    # 让 JNI 横幅能自证 GPU 后端确实编进了这个 APK。
    target_compile_definitions(LlamaWrapper PRIVATE OPERIT_HAS_OPENCL=1)
    # OpenCL 安全初始化：启动时信号防护探测，失败静默回退 CPU。
    target_compile_definitions(LlamaWrapper PRIVATE OPERIT_OPENCL_SAFE_INIT=1)
    message(STATUS "[operit] OpenCL backend ENABLED for LlamaWrapper")
elseif (OPERIT_ENABLE_OPENCL AND NOT OPERIT_ALLOW_CPU_ONLY)
    # 只有「明确要 OpenCL」时才失败：默认构建（开关 OFF）必须能过。
    message(FATAL_ERROR
        "[operit] OpenCL backend was requested (-DOPERIT_ENABLE_OPENCL=ON) but was NOT built.\n"
        "  find_package(OpenCL REQUIRED) failed: the OpenCL headers and a cross-compiled\n"
        "  libOpenCL.so must exist in the NDK sysroot, e.g.\n"
        "    <NDK>/toolchains/llvm/prebuilt/<host>/sysroot/usr/include/CL/\n"
        "    <NDK>/toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/aarch64-linux-android/libOpenCL.so\n"
        "  Build them from Khronos OpenCL-Headers + OpenCL-ICD-Loader (both Apache-2.0).\n"
        "  For a plain CPU build, drop -DOPERIT_ENABLE_OPENCL=ON (it is OFF by default).")
else()
    # 默认路径：纯 CPU 构建，任何 stock NDK / CI 都能配置通过。
    message(STATUS "[operit] OpenCL backend not built — CPU-only build "
                   "(pass -DOPERIT_ENABLE_OPENCL=ON to enable)")
endif()
