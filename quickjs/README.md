# quickjsjni

这个目录现在放的是可接入的 QuickJS JNI 模块本体。

目录结构：
- `src/main/cpp/CMakeLists.txt`：原生库构建脚本，并通过 CMake 获取上游 QuickJS C 源码
- `src/main/cpp/quickjs_jni.cpp`：QuickJS JNI Runtime
- `src/main/java/com/ai/assistance/operit/core/tools/javascript/QuickJsNativeRuntime.kt`：Kotlin Runtime 封装
- `src/main/java/com/ai/assistance/operit/core/tools/javascript/QuickJsNativeHostDispatcher.kt`：默认 HostBridge，实现 console 和 timer
- `src/main/java/com/ai/assistance/operit/core/tools/javascript/QuickJsNativeCompatScriptBuilder.kt`：JS 兼容层

当前这部分只落在 `quickjs` 目录里，还没有接到 `app` 模块。
