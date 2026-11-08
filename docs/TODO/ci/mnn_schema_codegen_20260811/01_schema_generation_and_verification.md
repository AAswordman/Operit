# 步骤 1：schema 生成与验证

## 原本实现

`llm/mnn/CMakeLists.txt` 直接把 FetchContent 下载的 MNN 加入构建。MNN 上游没有在其顶层 CMake 中生成 `schema/current/*.h`，所以当 schema 与 C++ 源码在上游提交之间短暂不同步时，干净 archive 会直接编译失败。

## 修正意图

在 MNN 子目录加入父构建前，构建 MNN 自带的 FlatBuffers `flatc` 宿主工具，清理并重新生成公共 schema 头文件。Linux 使用 `gcc` 与 `g++` 编译该宿主工具，避免旧 FlatBuffers CMake 将 Clang 已知警告提升为错误；Android 目标编译器只负责最终 native 库，不会被当作宿主工具执行。

## 验证记录

- [x] `:mnn:assembleDebug --no-build-cache --no-daemon`：本地 Windows Android NDK 构建成功
- [x] `:app:testDebugUnitTest --no-build-cache --no-daemon`：本地 JVM 单测成功
- [ ] 云端 `Candidate checks` 全绿
- [ ] PR #926 使用普通 merge 合并到 `main`
