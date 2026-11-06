---
title: 已验收稳定性修复集成
status: ready-for-review
fork: https://github.com/luojiaping/Operit
branch: fix/pr-stability-and-review-fixes
date: 2026-11-06
---

# 已验收稳定性修复集成

[返回总计划](../README.md)

## 问题和范围

将已人工验收的稳定性修复提交到上游 dev，并补齐复查发现的消息身份、服务生命周期、终端阻塞与流式订阅边界。

- PR #1396：限制 ANR 堆栈字符串和采集频率
- PR #1403：朗读先提取正文，再执行用户正则
- PR #1395、#1397：A2A 文本 Part 与 1.0 状态事件兼容
- PR #1400：终端输入／中断定向到指定会话，后台取消不切换可见终端
- PR #1398：裸网址可点击；补齐 Unicode、IPv6 和正文括号边界
- Web Chat：按会话保留临时消息交接记录，补齐单轮超过 24 条的连续历史，避免提示重复或消失
- 功能模型：实际请求持有租约；创建失败不留下已释放缓存；请求取消仍归还租约
- A2A：终态前发布最终正文替换，订阅快照与增量发布保持一致顺序

虚拟屏 PR #1402 与 Issue #1371 继续单独处理。Issue #1277 的表格显式链接点击问题不在本次修复范围。

## 终端依赖

`terminal` 指向公开提交 `fcdc73da7ddb93dd7bb49da4ae3fee3baaee0b8d`，对应 [TerminalCore PR #14](https://github.com/AAswordman/OperitTerminalCore/pull/14)。该 PR 保留 #13 的原作者提交，并增加三秒输入写入期限、中断绕开文本 writer 和取消未完成输入的修正。
已从上游仓库地址的空白浅仓库按 SHA 成功获取该提交；其 tree 为 `0037f7a139cde8985228f1de97a085528866af76`。主仓库的子模块检出不依赖终端 PR 先合并；终端 PR 仍需由其仓库维护者审查。

## 验证边界

集成包中的对应共享修改已完成 Android Release 编译、V2/V3 签名和维护者人工验收。Web Chat 类型检查已通过。
本贡献分支基于上游 dev `5a8f293fb8822831aa7737caa93b666adbc90292` 导出，保留原作者和来源记录；导出过程中没有手工冲突。
本轮发布贡献分支未重新运行编译、构建或自动测试，新增回归用例也未因此记录为已执行。

## 首次上游 CI 的环境修正

首次 Candidate checks 的 Android build 与 JVM tests 都在 SDK 准备阶段失败，日志为 `Failed to find package 'tools'`，没有进入应用编译或测试。
固定版本 setup-android 的 action.yml 默认额外安装 `tools platform-tools`，其中旧 `tools` 包已经无法获取。PR workflow 三个 SDK 初始化入口显式传入空 packages，与现有独立 Android workflow 一致，后续 sdkmanager 步骤继续安装已声明的 SDK／NDK 工具链。

## 手工复核

1. 新对话发送一次只保留一条用户消息，真实重复发送保留两条；历史同步失败后恢复及长 Waifu 回复不会留下重复或中间缺口。
2. 终端 A、B 的定向输入与 Ctrl+C 互不影响；工作区取消正常结束，输入堵塞能报告超时。
3. 翻译、总结、记忆等请求执行期间修改参数并发起下一请求，旧服务直到实际请求结束才释放。
4. A2A 客户端遵循 append 字段：追加增量，替换最终正文；最终结果与 GetTask 一致，输出中订阅不重复片段。
5. 思考和工具块不被朗读，正常 Markdown／裸网址点击与 ANR 诊断继续可用。

[DONE] 共享源码与回归用例完成，维护者人工验收通过；等待上游代码审查和手动合并。
