---
fork: https://github.com/yoruuuchan/Operit.git
issue: https://github.com/AAswordman/Operit/issues/1180
---

# 测试与兼容性 [DONE]

## 覆盖范围

- 正常 hiddenExec 成功完成
- 进程启动失败返回明确错误
- READY 缺失、reader 异常、shell 退出等 executor 异常及时结束
- 创建 mutex、进程启动、同 key 排队、pipe write 或结果读取达到 timeout 后返回并清理
- queued timeout 不终止 active owner；owner timeout 后 queued 调用在新 shell 上继续
- cancellation 传播并清理 active shell
- END marker 分片时等待完整 exit-code 行
- disconnect 后拒绝新调用且不创建进程
- timeout 后相同与不同 executorKey 可再次执行
- QQbot、code_runner 与 linux_ssh 调用参数和结果字段保持兼容

## 自动化结果

- `:terminal:testDebugUnitTest`：Java 21，13 个测试全部通过。
- `:app:compileDebugKotlin`：Java 21，通过；验证 ToolPkg/Kotlin 入口与 terminal submodule 集成。
- `:terminal:assembleDebug`：作为全量 assemble 依赖执行并通过。
- `git diff --check`：父仓库与 terminal submodule 均通过。
- Android JVM test 工作流和 PR check 现同时执行 `:app:testDebugUnitTest` 与
  `:terminal:testDebugUnitTest`，避免 submodule 测试只存在于磁盘而不进入 CI。

## 基线与环境边界

- `:app:testDebugUnitTest` 在当前分支编译既有测试源码时失败；独立干净工作树中的最新
  `upstream/dev@5948dff9` 复现完全相同的 7 条错误，涉及
  `DeepseekProviderMediaRoleTest` 和 `XaiProviderReasoningTest`，与本次 diff 无关。
- `assembleDebug` 已通过本次改动涉及的 Kotlin、terminal AAR、native/FFmpeg/STT 资产校验，
  随后在 `:mnn:configureCMakeDebug[arm64-v8a]` 因本机没有 host `nmake`/C++ compiler 而停止。
- 当前没有连接的 adb 设备或可用 AVD，也没有 QQbot 凭证，未声称完成带凭证的 QQbot 设备重放。
  QQbot、code_runner 和 linux_ssh 的调用点已逐一审计；它们继续使用相同参数和结果字段，
  自动化测试直接覆盖 QQbot 所经过的同一通用 hidden executor。
