---
title: 终端与子代理反馈修正
status: terminal_wrapper_verified_sdk_subagent_pending
fork: https://github.com/luojiaping/Operit
date: 2026-11-07
---

# 终端与子代理反馈修正

[返回总计划](../README.md) · [主仓库 PR #1469](https://github.com/AAswordman/Operit/pull/1469) · [终端配套 PR #15](https://github.com/AAswordman/OperitTerminalCore/pull/15)

## 问题与范围

整合后的终端超时虽然已在宿主标记失败，SDK 和包工具却会丢失输出、会话及超时详情；PTY 完成事件也没有传递真实退出码。子代理把非空摘要当成成功，预算和递归约束只存在于提示词中，异步 Java 调用缺少按执行归属的取消生命周期。

本次在原 PR 追加终端、SDK、子代理与相关回归用例；其他已整合项目维持原范围。

## 实现

1. 终端超时按明确结果类型保留既有 SDK 返回约定，模型侧仍为失败。非零退出和失败数据经过包工具与结果聚合层完整传递。
2. 子模块传递实际退出码、单次回收 PTY 子进程并修正存活判断，使用命令 ID 区分控制报文和普通输出。应用普通／流式路径共用终态判定，hidden 非零退出同样为失败。
3. 新增 sessionAlive，旧 context_preserved 取自真实进程状态；保留原 shell 的工作目录与变量，主动退出后创建新会话。完成时明确通知状态流。
4. 子任务执行层记录真实工具结果并执行 1～64 次预算和递归拒绝，独立工具会话范围保留父聊天／角色身份。完成状态由宿主终态、模型声明及本次证据共同决定。
5. Java suspend 子树绑定到每次 JS 执行，捕获固定执行上下文；取消、超时和结束时清理相应任务及回调。共享 QuickJS 的同步脚本中断仍是 engine 级机制。
6. 子代理示例为 0.1.2，声明 ToolPkg API 1.2.0，新增真实证据归档和完成／受阻／失败渲染。保留历史 XML 兼容；数百字摘要可完整返回，超出内联范围的正文通过归档提供。

## 配套依赖与合并

terminal gitlink 指向 `f581eb59e2067bf43bc78eba58d0cfb3ed77d8b5`，源码 tree 为 `e29761c503323bfc4184f38a47faba672fda76f5`，由终端 PR #15 提交。底层 PR 的目标沿用 #14 的 `feat/performance-terminal-pid`，主仓库 PR 仍以 dev 为目标，由维护者手动合并。建议先合入终端配套 PR。

GitHub 贡献不包含 CNB 专有配置或历史，.gitmodules 的 URL 没有改动。来源为本地共享修复 `86d23f206`、`b3cfaeda8`，保留原 PR 的全部历史提交。

## 验证证据与边界

- TypeScript 5.9.3 已编译子代理与 super_admin；source/dist、两份内置 JS 和接口文档同步。
- [Release 构建](https://cnb.cool/luojiaping/Operit/-/build/logs/cnb-0h8-1k6p83nvq)成功，Gradle 用时 12 分 9 秒，APK V2/V3 签名通过。已核对 APK 内置脚本、DEX 和原生库包含本轮实现。
- 维护者提供的 14 条普通终端／后台等待记录均符合预期：退出码 1／7／3／0 如实返回，超时保留部分输出、同一会话及 /tmp／环境变量，退出后的会话状态为 false，新建会话使用新的 ID。
- 形似提示符的文本、引号和无换行输出完整保留；后台等待本次为 3024 毫秒，第二次等待为 17 毫秒。这是单次设备记录，不是统一的延迟保证。
- 其余第三批项目按维护者此前整体反馈记为基本通过；悬浮窗关闭与隔离按指定的代码审阅方式通过。
- 新增 Node／JVM 行为用例未在本地运行；本次 head 的 GitHub Checks 单独给出自动检查结果。直接 SDK、流式／hidden、子代理预算／证据／递归／取消等未完整真机验证，不写为通过。

真实退出码报告用于 Bash；非 Bash 的自动工具执行明确报错。sessionAlive 表示原 PTY 宿主进程存活，不等于取消已确认或任意环境值没有变化。子代理示例需单独导入配套包，未加入 APK 内置名单。

## 关联状态

主 PR 承接的十个来源 PR 已按被整合取代关闭。关联 issue #1125、#1346、#1292、#1195 仍等待修复进入上游；终端 #4 还包含本轮未覆盖的其他内容，保持开放。

[DONE] 本轮修复与验收证据已追加准备，等待维护者手动合并。
