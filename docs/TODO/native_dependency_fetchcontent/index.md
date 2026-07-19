---
For_Agent: native dependency FetchContent migration notes
---

# Native Dependency FetchContent

## 旧状况

- `ufbx`、`Bullet3`、`Saba`、`ncnn`、`llama.cpp`、`sherpa-ncnn`、WAMR、QuickJS 和 MNN 由 Git 子模块固定到仓库路径
- MNN 的 KleidiAI 子依赖原本由 MNN CMake 在深层构建目录中下载
- `sherpa-ncnn` 内置的 `ncnn.cmake` 使用固定 zip 包

## 本次意图

- 将这些原生依赖改成 CMake `FetchContent`
- 默认跟随各自上游主分支
- 每次配置先解析远端 ref 的 commit，再下载对应 GitHub archive
- 保留 `OPERIT_*_GIT_REF` 参数，便于 CI 或发布构建指定 ref
- FetchContent 源码和构建目录落在各模块 `.cxx/operit_deps`，降低 Windows native build 路径长度

## 作用域

- `.gitmodules`
- `fbx/CMakeLists.txt`
- `mmd/CMakeLists.txt`
- `llama/CMakeLists.txt`
- `quickjs/src/main/cpp/CMakeLists.txt`
- `mnn/CMakeLists.txt`
- `cmake/operit_git_source.cmake`
- `app/src/main/cpp/cmake/ncnn.cmake`
- `app/src/main/cpp/CMakeLists.txt`
- 开发构建文档

## 状态

[DONE]
