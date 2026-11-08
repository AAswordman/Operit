# Job 边界与状态契约

## 现状

`Candidate checks` 同时承担作用域规划、快速检查、JavaScript 专项检查、Android
资源编译、依赖准备和完整 Android Gradle 检查。

## 目标

- `Fast checks` 负责规划、快速门禁、资源 lane、WebChat 和 ToolPkg 专项检查
- `Android build` 只在 `android_full` 时运行 `assembleDebug`
- `Android JVM tests` 在 `android_jvm` 或 `android_full` 时运行 JVM 单测
- 两个 Android job 都基于相同的 merge candidate，但各自拥有干净 workspace
- 新增的 job 结果由最终的 `Candidate checks` 聚合

## 约束

- 不能让仅有快速门禁的 PR 因不存在 Android job 而失败
- Android build 必须自行生成 WebChat 和 ToolPkg 的 Gradle 输入
- JVM test job 必须自行准备 Gradle 所需的依赖和 native ripgrep
- 不在 job 之间传递未审计的 workspace；使用既有缓存和 candidate checkout
