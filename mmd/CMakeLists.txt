cmake_minimum_required(VERSION 3.22.1)

project("mmd_jni")

include("${CMAKE_CURRENT_LIST_DIR}/../cmake/operit_git_source.cmake")

operit_prepare_git_source(
    OPERIT_SABA_SOURCE_DIR
    OPERIT_SABA_BINARY_DIR
    saba
    "https://github.com/benikabocha/saba.git"
    "master"
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(USE_GRAPHICAL_BENCHMARK OFF CACHE BOOL "" FORCE)
set(BUILD_CPU_DEMOS OFF CACHE BOOL "" FORCE)
set(BUILD_BULLET3 OFF CACHE BOOL "" FORCE)
set(BUILD_OPENGL3_DEMOS OFF CACHE BOOL "" FORCE)
set(BUILD_BULLET2_DEMOS OFF CACHE BOOL "" FORCE)
set(BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
set(BUILD_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(INSTALL_LIBS OFF CACHE BOOL "" FORCE)

operit_prepare_git_source(
    OPERIT_BULLET3_SOURCE_DIR
    OPERIT_BULLET3_BINARY_DIR
    bullet3
    "https://github.com/bulletphysics/bullet3.git"
    "master"
)

add_subdirectory("${OPERIT_BULLET3_SOURCE_DIR}" "${OPERIT_BULLET3_BINARY_DIR}" EXCLUDE_FROM_ALL)

add_library(
    MmdWrapper
    SHARED
    src/main/cpp/android/AndroidAssetSupport.cpp
    src/main/cpp/android/MmdInspectorBridge.cpp
    src/main/cpp/android/MmdRendererBridge.cpp
    src/main/cpp/Saba/GL/GLShaderUtil.cpp
    src/main/cpp/Saba/GL/GLSLUtil.cpp
    src/main/cpp/Saba/GL/GLTextureUtil.cpp
    src/main/cpp/Saba/GL/Model/MMD/GLMMDModel.cpp
    src/main/cpp/Saba/GL/Model/MMD/GLMMDModelDrawContext.cpp
    src/main/cpp/Saba/GL/Model/MMD/GLMMDModelDrawer.cpp
    src/main/cpp/Saba/Viewer/Camera.cpp
    src/main/cpp/Saba/Viewer/CameraOverrider.cpp
    src/main/cpp/Saba/Viewer/Grid.cpp
    src/main/cpp/Saba/Viewer/Light.cpp
    src/main/cpp/Saba/Viewer/ModelDrawer.cpp
    src/main/cpp/Saba/Viewer/ShadowMap.cpp
    src/main/cpp/Saba/Viewer/Viewer.cpp
    src/main/cpp/Saba/Viewer/ViewerCommand.cpp
    src/main/cpp/Saba/Viewer/ViewerContext.cpp
    src/main/cpp/Saba/Viewer/VMDCameraOverrider.cpp
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Base/File.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Base/Log.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Base/Path.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Base/Singleton.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Base/Time.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Base/UnicodeUtil.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/SjisToUnicode.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDIkSolver.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDMaterial.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDModel.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDMorph.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDNode.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDPhysics.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/MMDCamera.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/PMDFile.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/PMDModel.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/PMXFile.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/PMXModel.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/VMDAnimation.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/VMDCameraAnimation.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/VMDFile.cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src/Saba/Model/MMD/VPDFile.cpp"
)

target_compile_definitions(MmdWrapper PRIVATE OPERIT_HAS_SABA=1)

target_include_directories(MmdWrapper PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp"
    "${OPERIT_SABA_SOURCE_DIR}/src"
    "${OPERIT_SABA_SOURCE_DIR}/external/glm/include"
    "${OPERIT_SABA_SOURCE_DIR}/external/spdlog/include"
    "${OPERIT_SABA_SOURCE_DIR}/external/stb/include"
    "${OPERIT_SABA_SOURCE_DIR}/external/tinyddsloader/include"
    "${OPERIT_BULLET3_SOURCE_DIR}/src"
)

target_link_libraries(
    MmdWrapper
    android
    log
    GLESv3
    BulletDynamics
    BulletCollision
    LinearMath
)

# 16KB page size support (Android 15+ requirement)
target_link_options(MmdWrapper PRIVATE "-Wl,-z,max-page-size=16384")
