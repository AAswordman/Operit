---
fork: https://github.com/yoruuuchan/Operit.git
issue: https://github.com/AAswordman/Operit/issues/1180
base: https://github.com/AAswordman/Operit/tree/dev
---

# Issue 1180 hiddenExec 生命周期 [DONE]

## 现状

`Tools.System.terminal.hiddenExec` 从 ToolPkg JavaScript API 进入 Kotlin 工具执行器后，
最终交给 `terminal` 子模块中的隐藏 shell executor。Issue 1180 显示 QQbot 在启动后台
网关时可能永久等待；调用只有在上层生成被用户取消后才结束，传入的 timeout 没有约束完整
生命周期。

当前需要区分初始化、executor 创建与复用、同 key 排队、进程启动、输出解析和结果回传，
找到真实没有完成的等待路径，而不是在 QQbot 调用方添加专用绕行。

## 目标

- 所有 hiddenExec 路径最终明确成功、失败、取消或超时
- timeout 覆盖初始化、排队、进程启动和结果读取
- 失效或超时的 executor 不污染后续同 key 或不同 key 调用
- 保持 QQbot、code_runner 与 linux_ssh 的既有调用接口

## 作用域

- ToolPkg hiddenExec JavaScript bridge 与 Kotlin 工具入口的调用链审计
- `terminal` 子模块中的 hidden executor 生命周期修复与单元测试
- `examples/types/system.d.ts` 中与实际 timeout 和复用行为一致的 API 文档
- 本目录中的定位与验证记录

## PR

- 父仓库：https://github.com/AAswordman/Operit/pull/1201（Draft，目标 `AAswordman/Operit:dev`）
- terminal submodule：https://github.com/AAswordman/OperitTerminalCore/pull/6（目标 `master`，需先合并，父 PR 的 submodule 指针指向它）

## 完成状态

- 已同步最新 `upstream/dev`，确认 Issue 仍开放且无人分配或提交关联 PR
- 已在 Issue 下留言认领
- 已在最新 dev 基线上复现 executor 准备、创建 mutex 和阻塞 writer 三条永久等待路径
- 已完成通用生命周期修复、13 项回归测试、调用方兼容审计和 API 文档更新
- 已把 terminal 测试接入 Android JVM test 与 PR check 工作流
- 已完成相关测试、应用 Kotlin 集成编译和构建边界核验
- 已推送 fork 分支并创建父仓库 Draft PR 与 terminal submodule PR，已在 Issue 下同步链接
