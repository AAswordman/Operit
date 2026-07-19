cmake_minimum_required(VERSION 3.22.1)

project("mnn_jni")

include("${CMAKE_CURRENT_LIST_DIR}/../cmake/operit_git_source.cmake")

operit_prepare_git_source(
    MNN_SOURCE_DIR
    OPERIT_MNN_BINARY_DIR
    mnn
    "https://github.com/alibaba/MNN.git"
    "master"
)

operit_prepare_git_source(
    KLEIDIAI_SRC_DIR
    OPERIT_KLEIDIAI_BINARY_DIR
    kleidiai
    "https://github.com/ARM-software/kleidiai.git"
    "v1.16.0"
)

# 使用 CMake Policy CMP0077 来让 set() 覆盖 option()
if(POLICY CMP0077)
    cmake_policy(SET CMP0077 NEW)
endif()

# 设置标志以避免 MNN 的 POST_BUILD 命令问题
set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "Build from command" FORCE)

# 启用 LLM 支持及相关优化
set(MNN_BUILD_LLM ON CACHE BOOL "Build LLM support" FORCE)
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

