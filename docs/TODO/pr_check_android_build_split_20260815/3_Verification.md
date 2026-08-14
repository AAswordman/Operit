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
