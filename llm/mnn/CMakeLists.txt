cmake_minimum_required(VERSION 3.22.1)

project("mnn_jni")

include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/operit_git_source.cmake")

# Pin MNN to the commit behind the latest stable 3.6.1 release. Using the
# release commit keeps every native configure reproducible across machines.
set(OPERIT_MNN_STABLE_COMMIT "d407447ed56c4121a11ccbd266dc184ca1ead0c2")
# Migrate every existing cache value, including configurations created from
# master, so Android Studio cannot reuse a different MNN snapshot.
set(OPERIT_MNN_GIT_REF "${OPERIT_MNN_STABLE_COMMIT}" CACHE STRING "Git ref used to fetch mnn" FORCE)

operit_prepare_git_source(
    MNN_SOURCE_DIR
    OPERIT_MNN_BINARY_DIR
    mnn
    "https://github.com/alibaba/MNN.git"
    "${OPERIT_MNN_GIT_REF}"
)

operit_prepare_git_source(
    KLEIDIAI_SRC_DIR
    OPERIT_KLEIDIAI_BINARY_DIR
    kleidiai
    "https://github.com/ARM-software/kleidiai.git"
    "v1.16.0"
)

# MNN can update schema/default/MNN.fbs and its C++ sources in separate commits.
# Regenerate schema/current from the fetched revision before adding MNN so a
# clean archive never compiles source files against stale generated symbols.
set(OPERIT_MNN_FLATBUFFERS_SOURCE_DIR "${MNN_SOURCE_DIR}/3rd_party/flatbuffers")
set(OPERIT_MNN_FLATC_BUILD_DIR "${OPERIT_MNN_BINARY_DIR}/flatc-host")
set(OPERIT_MNN_FLATC_OUTPUT_DIR "${OPERIT_MNN_FLATC_BUILD_DIR}/bin")
set(OPERIT_MNN_FLATC_CMAKE_ARGS
    "-S" "${OPERIT_MNN_FLATBUFFERS_SOURCE_DIR}"
    "-B" "${OPERIT_MNN_FLATC_BUILD_DIR}"
    "-DFLATBUFFERS_BUILD_TESTS=OFF"
    "-DFLATBUFFERS_BUILD_FLATLIB=OFF"
    "-DFLATBUFFERS_BUILD_FLATHASH=OFF"
    "-DFLATBUFFERS_BUILD_GRPCTEST=OFF"
    "-DFLATBUFFERS_BUILD_SHAREDLIB=OFF"
    "-DFLATBUFFERS_INSTALL=OFF"
    "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${OPERIT_MNN_FLATC_OUTPUT_DIR}"
    "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${OPERIT_MNN_FLATC_OUTPUT_DIR}"
    "-DCMAKE_BUILD_TYPE=Release"
)

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Linux")
    find_program(OPERIT_MNN_HOST_C_COMPILER NAMES gcc NO_CMAKE_FIND_ROOT_PATH)
    find_program(OPERIT_MNN_HOST_CXX_COMPILER NAMES g++ NO_CMAKE_FIND_ROOT_PATH)
    if(NOT OPERIT_MNN_HOST_C_COMPILER OR NOT OPERIT_MNN_HOST_CXX_COMPILER)
        message(FATAL_ERROR "MNN schema generation requires the host gcc and g++ compilers on Linux.")
    endif()
    list(APPEND OPERIT_MNN_FLATC_CMAKE_ARGS
        "-DCMAKE_C_COMPILER=${OPERIT_MNN_HOST_C_COMPILER}"
        "-DCMAKE_CXX_COMPILER=${OPERIT_MNN_HOST_CXX_COMPILER}"
    )
endif()

if(NOT CMAKE_HOST_WIN32)
    # MNN's bundled FlatBuffers enables -Werror for Clang despite known warnings.
    # The schema compiler is a host-only generator, so retain diagnostics without
    # promoting upstream warnings to errors in its Release configuration.
    list(APPEND OPERIT_MNN_FLATC_CMAKE_ARGS "-DCMAKE_CXX_FLAGS_RELEASE=-Wno-error")
endif()

execute_process(
    COMMAND "${CMAKE_COMMAND}"
        ${OPERIT_MNN_FLATC_CMAKE_ARGS}
    RESULT_VARIABLE OPERIT_MNN_FLATC_CONFIGURE_RESULT
    OUTPUT_VARIABLE OPERIT_MNN_FLATC_CONFIGURE_OUTPUT
    ERROR_VARIABLE OPERIT_MNN_FLATC_CONFIGURE_ERROR
)
if(NOT "${OPERIT_MNN_FLATC_CONFIGURE_RESULT}" STREQUAL "0")
    message(FATAL_ERROR
        "Failed to configure the host FlatBuffers compiler for MNN schema generation.\n"
        "${OPERIT_MNN_FLATC_CONFIGURE_OUTPUT}\n"
        "${OPERIT_MNN_FLATC_CONFIGURE_ERROR}"
    )
endif()

execute_process(
    COMMAND "${CMAKE_COMMAND}"
        "--build" "${OPERIT_MNN_FLATC_BUILD_DIR}"
        "--target" "flatc"
        "--config" "Release"
        "--parallel"
    RESULT_VARIABLE OPERIT_MNN_FLATC_BUILD_RESULT
    OUTPUT_VARIABLE OPERIT_MNN_FLATC_BUILD_OUTPUT
    ERROR_VARIABLE OPERIT_MNN_FLATC_BUILD_ERROR
)
if(NOT "${OPERIT_MNN_FLATC_BUILD_RESULT}" STREQUAL "0")
    message(FATAL_ERROR
        "Failed to build the host FlatBuffers compiler for MNN schema generation.\n"
        "${OPERIT_MNN_FLATC_BUILD_OUTPUT}\n"
        "${OPERIT_MNN_FLATC_BUILD_ERROR}"
    )
endif()

if(CMAKE_HOST_WIN32)
    set(OPERIT_MNN_FLATC_EXECUTABLE "${OPERIT_MNN_FLATC_OUTPUT_DIR}/flatc.exe")
else()
    set(OPERIT_MNN_FLATC_EXECUTABLE "${OPERIT_MNN_FLATC_OUTPUT_DIR}/flatc")
endif()
if(NOT EXISTS "${OPERIT_MNN_FLATC_EXECUTABLE}")
    message(FATAL_ERROR
        "The host FlatBuffers compiler was built but not found at "
        "${OPERIT_MNN_FLATC_EXECUTABLE}."
    )
endif()

set(OPERIT_MNN_SCHEMA_DIR "${MNN_SOURCE_DIR}/schema")
set(OPERIT_MNN_SCHEMA_CURRENT_DIR "${OPERIT_MNN_SCHEMA_DIR}/current")
file(GLOB OPERIT_MNN_SCHEMA_HEADERS "${OPERIT_MNN_SCHEMA_CURRENT_DIR}/*.h")
if(OPERIT_MNN_SCHEMA_HEADERS)
    file(REMOVE ${OPERIT_MNN_SCHEMA_HEADERS})
endif()
file(GLOB OPERIT_MNN_SCHEMA_FILES "${OPERIT_MNN_SCHEMA_DIR}/default/*.fbs")
if(NOT OPERIT_MNN_SCHEMA_FILES)
    message(FATAL_ERROR "MNN schema/default does not contain any FlatBuffers schema files.")
endif()

execute_process(
    COMMAND "${OPERIT_MNN_FLATC_EXECUTABLE}"
        "-c"
        "-b"
        "--gen-object-api"
        "--reflect-names"
        ${OPERIT_MNN_SCHEMA_FILES}
    WORKING_DIRECTORY "${OPERIT_MNN_SCHEMA_CURRENT_DIR}"
    RESULT_VARIABLE OPERIT_MNN_SCHEMA_GENERATE_RESULT
    OUTPUT_VARIABLE OPERIT_MNN_SCHEMA_GENERATE_OUTPUT
    ERROR_VARIABLE OPERIT_MNN_SCHEMA_GENERATE_ERROR
)
if(NOT "${OPERIT_MNN_SCHEMA_GENERATE_RESULT}" STREQUAL "0")
    message(FATAL_ERROR
        "Failed to generate MNN schema/current headers.\n"
        "${OPERIT_MNN_SCHEMA_GENERATE_OUTPUT}\n"
        "${OPERIT_MNN_SCHEMA_GENERATE_ERROR}"
    )
endif()

# 使用 CMake Policy CMP0077 来让 set() 覆盖 option()
if(POLICY CMP0077)
    cmake_policy(SET CMP0077 NEW)
endif()

# 设置标志以避免 MNN 的 POST_BUILD 命令问题
set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "Build from command" FORCE)

# 启用 LLM 支持及相关优化
set(MNN_BUILD_LLM ON CACHE BOOL "Build LLM support" FORCE)
# The Android app links the LLM library but does not ship MNN command-line demos.
# Upstream's Qwen3 TTS demo has extra audio sources that are not part of this build.
set(MNN_LLM_BUILD_DEMO OFF CACHE BOOL "Build LLM demos" FORCE)
set(MNN_LOW_MEMORY ON CACHE BOOL "Use low memory mode" FORCE)
set(MNN_SUPPORT_TRANSFORMER_FUSE ON CACHE BOOL "Support transformer fuse" FORCE)
set(MNN_CPU_WEIGHT_DEQUANT_GEMM ON CACHE BOOL "CPU weight dequant gemm" FORCE)

# 启用 GPU 后端支持
set(MNN_VULKAN ON CACHE BOOL "Enable Vulkan backend" FORCE)
set(MNN_OPENCL ON CACHE BOOL "Enable OpenCL backend" FORCE)
set(MNN_OPENGL ON CACHE BOOL "Enable OpenGL backend" FORCE)
set(MNN_SUPPORT_RENDER ON CACHE BOOL "Enable render backend" FORCE)

# 禁用分离编译 - 将所有后端编入 libMNN.so (解决 "Can't Find type=3 backend" 问题)
set(MNN_SEP_BUILD OFF CACHE BOOL "Build backends separately" FORCE)

# 禁用不需要的功能以加快编译
set(MNN_BUILD_BENCHMARK OFF CACHE BOOL "Build benchmark" FORCE)
set(MNN_BUILD_TEST OFF CACHE BOOL "Build test" FORCE)
set(MNN_BUILD_TOOLS OFF CACHE BOOL "Build tools" FORCE)
set(MNN_BUILD_QUANTOOLS OFF CACHE BOOL "Build quantools" FORCE)
set(MNN_EVALUATION OFF CACHE BOOL "Build evaluation" FORCE)
set(MNN_BUILD_CONVERTER OFF CACHE BOOL "Build converter" FORCE)
set(MNN_BUILD_TRAIN OFF CACHE BOOL "Build train" FORCE)

# 添加 MNN 主项目
add_subdirectory(${MNN_SOURCE_DIR} ${OPERIT_MNN_BINARY_DIR})

# 包含 JNI 绑定代码（只包含我们需要的文件）
add_library(
    MNNWrapper
    SHARED
    src/main/cpp/mnnnetnative.cpp
    src/main/cpp/mnnmodulennative.cpp
    src/main/cpp/mnnllmnative.cpp
    # mnnportraitnative.cpp 使用的是不同的包名，这里不需要
)

# 包含 MNN 头文件
target_include_directories(MNNWrapper PRIVATE
    ${MNN_SOURCE_DIR}/include
    ${MNN_SOURCE_DIR}/source
    ${MNN_SOURCE_DIR}/express
    ${MNN_SOURCE_DIR}/transformers/llm/engine/include
    ${MNN_SOURCE_DIR}/transformers/llm/engine/src
    ${MNN_SOURCE_DIR}/3rd_party
)

# 添加编译选项以解决 TLS 问题
target_compile_options(MNNWrapper PRIVATE -fno-emulated-tls)
target_compile_options(MNN PRIVATE -fno-emulated-tls)
if(TARGET llm)
    target_compile_options(llm PRIVATE -fno-emulated-tls)
    if(EXISTS "${MNN_SOURCE_DIR}/transformers/llm/engine/src/minja/chat_template.hpp")
        target_compile_options(MNNWrapper PRIVATE -DLLM_USE_MINJA)
        target_include_directories(MNNWrapper PRIVATE
            ${MNN_SOURCE_DIR}/transformers/llm/engine/src
        )
    endif()
endif()

# 链接 MNN 库
target_link_libraries(
    MNNWrapper
    MNN
    android
    log
    jnigraphics
)

# 如果 LLM 库存在，也链接它
if(TARGET llm)
    target_link_libraries(MNNWrapper llm)
endif()

# 添加 16KB 页面大小支持（Android 15+ 要求）
# 这确保所有 LOAD 段对齐到 16KB 边界
target_link_options(MNNWrapper PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(MNN PRIVATE "-Wl,-z,max-page-size=16384")
if(TARGET llm)
    target_link_options(llm PRIVATE "-Wl,-z,max-page-size=16384")
endif()

