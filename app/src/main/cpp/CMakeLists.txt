cmake_minimum_required(VERSION 3.22.1)

project(streamnative)

include("${CMAKE_CURRENT_LIST_DIR}/../../../../cmake/operit_git_source.cmake")

set(TOOLPKG_PROTECTION_SECRET_VALUE "$ENV{OPERIT_TOOLPKG_PROTECTION_SECRET}")

if("${TOOLPKG_PROTECTION_SECRET_VALUE}" STREQUAL "")
        set(TOOLPKG_PROTECTION_ENV_FILES
                "${CMAKE_CURRENT_LIST_DIR}/../../../../.env.local"
                "${CMAKE_CURRENT_LIST_DIR}/../../../../.env"
                "${CMAKE_CURRENT_LIST_DIR}/../../../.env.local"
                "${CMAKE_CURRENT_LIST_DIR}/../../../.env"
        )
        foreach(TOOLPKG_PROTECTION_ENV_FILE IN LISTS TOOLPKG_PROTECTION_ENV_FILES)
                if("${TOOLPKG_PROTECTION_SECRET_VALUE}" STREQUAL "" AND EXISTS "${TOOLPKG_PROTECTION_ENV_FILE}")
                        file(STRINGS "${TOOLPKG_PROTECTION_ENV_FILE}" TOOLPKG_PROTECTION_ENV_LINES
                                REGEX "^[ \t]*(export[ \t]+)?OPERIT_TOOLPKG_PROTECTION_SECRET[ \t]*="
                        )
                        list(LENGTH TOOLPKG_PROTECTION_ENV_LINES TOOLPKG_PROTECTION_ENV_LINE_COUNT)
                        if(TOOLPKG_PROTECTION_ENV_LINE_COUNT GREATER 0)
                                list(GET TOOLPKG_PROTECTION_ENV_LINES 0 TOOLPKG_PROTECTION_ENV_LINE)
                                string(REGEX REPLACE "^[ \t]*(export[ \t]+)?OPERIT_TOOLPKG_PROTECTION_SECRET[ \t]*=[ \t]*" "" TOOLPKG_PROTECTION_SECRET_VALUE "${TOOLPKG_PROTECTION_ENV_LINE}")
                                string(REGEX REPLACE "[ \t]*#.*$" "" TOOLPKG_PROTECTION_SECRET_VALUE "${TOOLPKG_PROTECTION_SECRET_VALUE}")
                                string(STRIP "${TOOLPKG_PROTECTION_SECRET_VALUE}" TOOLPKG_PROTECTION_SECRET_VALUE)
                                string(REGEX REPLACE "^\"(.*)\"$" "\\1" TOOLPKG_PROTECTION_SECRET_VALUE "${TOOLPKG_PROTECTION_SECRET_VALUE}")
                                string(REGEX REPLACE "^'(.*)'$" "\\1" TOOLPKG_PROTECTION_SECRET_VALUE "${TOOLPKG_PROTECTION_SECRET_VALUE}")
                        endif()
                endif()
        endforeach()
endif()

set(TOOLPKG_PROTECTION_SECRET_MASK "0xA7")
set(TOOLPKG_PROTECTION_SECRET_BYTES_LITERAL "")
string(HEX "${TOOLPKG_PROTECTION_SECRET_VALUE}" TOOLPKG_PROTECTION_SECRET_HEX)
string(LENGTH "${TOOLPKG_PROTECTION_SECRET_HEX}" TOOLPKG_PROTECTION_SECRET_HEX_LENGTH)
math(EXPR TOOLPKG_PROTECTION_SECRET_SIZE "${TOOLPKG_PROTECTION_SECRET_HEX_LENGTH} / 2")
if(TOOLPKG_PROTECTION_SECRET_SIZE GREATER 0)
        math(EXPR TOOLPKG_PROTECTION_SECRET_LAST_INDEX "${TOOLPKG_PROTECTION_SECRET_SIZE} - 1")
        foreach(TOOLPKG_PROTECTION_SECRET_INDEX RANGE 0 ${TOOLPKG_PROTECTION_SECRET_LAST_INDEX})
                math(EXPR TOOLPKG_PROTECTION_SECRET_HEX_OFFSET "${TOOLPKG_PROTECTION_SECRET_INDEX} * 2")
                string(SUBSTRING
                        "${TOOLPKG_PROTECTION_SECRET_HEX}"
                        ${TOOLPKG_PROTECTION_SECRET_HEX_OFFSET}
                        2
                        TOOLPKG_PROTECTION_SECRET_HEX_BYTE
                )
                math(EXPR TOOLPKG_PROTECTION_SECRET_OBFUSCATED_BYTE
                        "0x${TOOLPKG_PROTECTION_SECRET_HEX_BYTE} ^ ${TOOLPKG_PROTECTION_SECRET_MASK}"
                        OUTPUT_FORMAT HEXADECIMAL
                )
                if(TOOLPKG_PROTECTION_SECRET_BYTES_LITERAL STREQUAL "")
                        set(TOOLPKG_PROTECTION_SECRET_BYTES_LITERAL "${TOOLPKG_PROTECTION_SECRET_OBFUSCATED_BYTE}")
                else()
                        string(APPEND TOOLPKG_PROTECTION_SECRET_BYTES_LITERAL ", ${TOOLPKG_PROTECTION_SECRET_OBFUSCATED_BYTE}")
                endif()
        endforeach()
endif()

set(TOOLPKG_PROTECTION_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated/toolpkgprotect")
file(MAKE_DIRECTORY "${TOOLPKG_PROTECTION_GENERATED_DIR}")
file(WRITE
        "${TOOLPKG_PROTECTION_GENERATED_DIR}/toolpkg_protection_secret.h"
        "#pragma once\n#include <array>\n#include <cstdint>\n\nconstexpr uint8_t kToolPkgProtectionSecretMask = ${TOOLPKG_PROTECTION_SECRET_MASK};\nconstexpr std::array<uint8_t, ${TOOLPKG_PROTECTION_SECRET_SIZE}> kToolPkgProtectionSecretBytes = {${TOOLPKG_PROTECTION_SECRET_BYTES_LITERAL}};\n"
)

function(force_release_flags_for_debug target_name)
    if(TARGET ${target_name})
        # Keep app variant as Debug, but compile heavy native libs with release-like optimization.
        target_compile_definitions(${target_name} PRIVATE $<$<CONFIG:Debug>:NDEBUG>)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Debug>:-O3 -g0>)
    endif()
endfunction()

function(enable_16kb_page_alignment target_name)
    if(TARGET ${target_name})
        target_link_options(${target_name} PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
endfunction()

function(harden_toolpkg_protection_target target_name)
    if(TARGET ${target_name})
        target_compile_definitions(${target_name} PRIVATE NDEBUG)
        target_compile_options(${target_name} PRIVATE
                -O3
                -g0
                -fvisibility=hidden
                -fvisibility-inlines-hidden
                -ffunction-sections
                -fdata-sections
        )
        target_link_options(${target_name} PRIVATE
                "-Wl,--gc-sections"
                "-Wl,--strip-all"
                "-Wl,-z,relro"
                "-Wl,-z,now"
        )
    endif()
endfunction()

function(resolve_wamr_android_target output_var)
    if("${ANDROID_ABI}" STREQUAL "arm64-v8a")
        set(${output_var} "AARCH64" PARENT_SCOPE)
    elseif("${ANDROID_ABI}" STREQUAL "armeabi-v7a")
        set(${output_var} "ARMV7A" PARENT_SCOPE)
    elseif("${ANDROID_ABI}" STREQUAL "x86")
        set(${output_var} "X86_32" PARENT_SCOPE)
    elseif("${ANDROID_ABI}" STREQUAL "x86_64")
        set(${output_var} "X86_64" PARENT_SCOPE)
    else()
        message(FATAL_ERROR "Unsupported Android ABI for ToolPkg WAMR: ${ANDROID_ABI}")
    endif()
endfunction()

if(ANDROID)
    # Build sherpa-ncnn as part of Android Studio externalNativeBuild.
    operit_prepare_git_source(
            OPERIT_SHERPA_NCNN_SOURCE_DIR
            OPERIT_SHERPA_NCNN_BINARY_DIR
            sherpa_ncnn
            "https://github.com/k2-fsa/sherpa-ncnn.git"
            "master"
    )

    list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
    list(APPEND CMAKE_MODULE_PATH "${OPERIT_SHERPA_NCNN_SOURCE_DIR}/cmake")
    include_directories("${OPERIT_SHERPA_NCNN_SOURCE_DIR}")

    set(SHERPA_NCNN_ENABLE_PORTAUDIO OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_BINARY OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_TEST OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_C_API OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_JNI ON CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_PYTHON OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_FFMPEG_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_GENERATE_INT8_SCALE_TABLE OFF CACHE BOOL "" FORCE)

    # Keep sherpa/ncnn static inside libsherpa-ncnn-jni and avoid OpenMP abort issues.
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(NCNN_OPENMP OFF CACHE BOOL "" FORCE)

    add_subdirectory(
            "${OPERIT_SHERPA_NCNN_SOURCE_DIR}"
            "${OPERIT_SHERPA_NCNN_BINARY_DIR}"
    )

    force_release_flags_for_debug(ncnn)
    force_release_flags_for_debug(sherpa-ncnn-core)
    force_release_flags_for_debug(sherpa-ncnn-jni)
endif()

add_library(
        streamnative
        SHARED
        streamnative/native_xml_splitter.cpp
        streamnative/native_markdown_splitter.cpp
        streamnative/StreamOperators.cpp
        streamnative/plugins/StreamXmlPlugin.cpp
        streamnative/plugins/BaseJsonPlugin.cpp
        streamnative/plugins/StreamJsonPlugin.cpp
        streamnative/plugins/StreamPureJsonPlugin.cpp
        streamnative/plugins/StreamMarkdownPlugin.cpp
        streamnative/StreamBuilders.cpp
        streamnative/HotStream.cpp
        streamnative/StringExtensions.cpp
)

target_include_directories(
        streamnative
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(
        log-lib
        log
)

if(ANDROID)
    resolve_wamr_android_target(TOOLPKG_WAMR_BUILD_TARGET)
    operit_prepare_git_source(
            OPERIT_WAMR_SOURCE_DIR
            OPERIT_WAMR_BINARY_DIR
            wamr
            "https://github.com/bytecodealliance/wasm-micro-runtime.git"
            "main"
    )

    set(WAMR_ROOT_DIR "${OPERIT_WAMR_SOURCE_DIR}")
    set(WAMR_BUILD_PLATFORM "android")
    set(WAMR_BUILD_TARGET "${TOOLPKG_WAMR_BUILD_TARGET}")
    set(WAMR_BUILD_INTERP 1)
    set(WAMR_BUILD_FAST_INTERP 1)
    set(WAMR_BUILD_AOT 0)
    set(WAMR_BUILD_JIT 0)
    set(WAMR_BUILD_FAST_JIT 0)
    set(WAMR_BUILD_LIBC_BUILTIN 1)
    set(WAMR_BUILD_LIBC_WASI 0)
    set(WAMR_BUILD_LIBC_UVWASI 0)
    set(WAMR_BUILD_LIBC_EMCC 0)
    set(WAMR_BUILD_MULTI_MODULE 0)
    set(WAMR_BUILD_LIB_PTHREAD 0)
    set(WAMR_BUILD_LIB_PTHREAD_SEMAPHORE 0)
    set(WAMR_BUILD_LIB_WASI_THREADS 0)
    set(WAMR_BUILD_THREAD_MGR 0)
    set(WAMR_BUILD_SHARED_HEAP 0)
    set(WAMR_BUILD_WASI_NN 0)
    set(WAMR_BUILD_WASM_CACHE 0)
    set(WAMR_BUILD_DEBUG_INTERP 0)
    set(WAMR_BUILD_COPY_CALL_STACK 0)
    set(WAMR_BUILD_MINI_LOADER 0)
    set(WAMR_BUILD_SIMD 0)
    set(WAMR_BUILD_REF_TYPES 0)

    include("${WAMR_ROOT_DIR}/build-scripts/runtime_lib.cmake")

    add_library(
            toolpkg_wamr_vmlib
            STATIC
            ${WAMR_RUNTIME_LIB_SOURCE}
    )

    target_include_directories(
            toolpkg_wamr_vmlib
            PUBLIC
            "${WAMR_ROOT_DIR}/core/iwasm/include"
    )

    force_release_flags_for_debug(toolpkg_wamr_vmlib)
endif()

add_library(
        toolpkgprotect
        SHARED
        streamnative/native_toolpkg_protection.cpp
)

if(ANDROID)
    add_library(
            toolpkgwasm
            SHARED
            streamnative/native_toolpkg_wasm.cpp
    )

    target_include_directories(
            toolpkgwasm
            PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}
            "${WAMR_ROOT_DIR}/core/iwasm/include"
    )
endif()

target_include_directories(
        toolpkgprotect
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${TOOLPKG_PROTECTION_GENERATED_DIR}
)

target_link_libraries(
        streamnative
        ${log-lib}
)

target_link_libraries(
        toolpkgprotect
        ${log-lib}
)

if(ANDROID)
    target_link_libraries(
            toolpkgwasm
            toolpkg_wamr_vmlib
            ${log-lib}
            android
            m
            dl
    )
endif()

enable_16kb_page_alignment(streamnative)
enable_16kb_page_alignment(toolpkgprotect)
harden_toolpkg_protection_target(toolpkgprotect)
if(ANDROID)
    enable_16kb_page_alignment(toolpkgwasm)
    harden_toolpkg_protection_target(toolpkgwasm)
endif()
