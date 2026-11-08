# 验证与 fork 测试

## 静态验证

- 检查 workflow YAML 和 actionlint
- 运行 CI Python 单元测试
- 检查 scope 分类、job 条件和 `Candidate checks` 聚合逻辑

## 线上验证

- 先推送到 fork 的 `ci/split-android-pr-check` 分支
- 在 fork 创建指向 fork `main` 的测试 PR，观察 build/test 两个 job 是否独立
- 确认构建 job 失败或单测 job 失败时，另一项仍保留自己的结果
- 确认无 Android 改动的 PR 不会被错误阻断
- 验证通过后再由用户决定是否提交 upstream

## 已完成验证

- Fork PR #15 的 Fast checks 通过，包含 46 个 CI 门禁单元测试和 actionlint
- Fork PR #15 的 `Android build` job 通过，`assembleDebug` 和构建报告完成
- Fork PR #15 的 `Android JVM tests` job 独立启动并执行到 Gradle 测试编译
- `Candidate checks` 正确记录 `Android build=success`、`Android JVM tests=failure`
- 首轮 JVM 测试暴露了 upstream 既有测试源码错误，包括 `ProviderUsageCancellationTest.kt`、
  `RecordingSQLiteDriver.kt`、`TokenCanonicalTotalsTest.kt` 和 `TokenCostCalculatorTest.kt`；
  已补齐导入和参数、删除过期测试夹具，并修正日志与 Gemini usage 测试夹具
- 修复后 run `31835091374` 的 `Android build` 通过，JVM 测试仅剩
  `ProviderUsageCancellationTest.kt` 中 3 个跨 IO dispatcher 的 cancellation 对象身份断言；
  已改为验证取消消息语义，避免把协程实例身份当成 provider 接口契约
- Fork PR #14 的 run `31839888120` 已全部通过：`Fast checks`、`Android JVM tests`、
  `Android build` 和 `Candidate checks` 均为 success
- 可信 `Android Build` 手动运行通过，并上传 Android 报告和成功产物

该验证确认 Android 编译已经在单测失败时独立完成，且最新 run 已验证 JVM 测试和
`Candidate checks` 聚合状态。
