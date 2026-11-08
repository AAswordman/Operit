# Workflow 实现

## PR Check

将现有单 job 改为快速门禁、Android build、Android JVM tests 和聚合 job。快速门禁
通过后两个 Android job 并行运行；`android_full` 不再触发快速 job 中的 WebChat
typecheck、WASM 打包和 ToolPkg 专项校验，但 Android build 仍执行生成 APK 所需的
assets 同步。

## Android Build

删除 JVM test 输入和 test step。保留编译所需的依赖、assets 生成、Gradle build 和
Android 产物上传。

## Android Tests

新增可信 main/manual workflow，复用 JVM test 所需的 SDK、依赖和 native ripgrep
准备步骤，不生成 APK/AAB。
