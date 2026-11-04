---
repo: https://github.com/3316891527/Operit
branch: fix/send-summary-anr
base: 38d7a1aa806e21eb2f8939fa56158603e2e53c1c
status: implemented-ci-pending
---

# 发送触发总结时的主线程 ANR 修复

## 背景与目标

长对话中取消请求、将上下文总结阈值调整为 80% 后再次发送，会进入自动总结。已保存日志显示，主线程停留在 `summarizeMemory → condenseAssistantForReview → ProtocolMetaDisplayFilter.literalContext / xmlBlockEnd`，并持续报告 ANR。手动插入总结的调用方已经使用后台上下文，发送自动总结和超限总结的调用方没有同样的调度边界。

目标是让自动总结的历史清洗、对话回顾组装和结果整理统一在后台执行，减少长 XML 文本扫描时的原生匹配器分配，并让取消在历史处理和模型请求前生效。

## 已实现范围

- `AIMessageManager.summarizeMemory` 使用 `Dispatchers.Default`，覆盖发送触发、超限触发和手动调用；保留原有协程父子关系及返回值，群聊和普通对话都在逐条处理历史时检查取消状态。
- `ProtocolMetaDisplayFilter` 在一次 XML 扫描内复用 `Matcher`，不再通过 Kotlin `Regex.findAll` 为每个 XML token 创建后续匹配器；代码边界扫描同样复用匹配器，直接读取可见缓冲区。
- 新增真实总结入口的后台调度与取消回归；扩展长嵌套 XML、注释、CDATA、内部反引号和分块输出回归。

总结阈值、配置格式、历史原文、协议负载保存、代码示例保留、总结插入位置和网络接口均未改动。原有包预热的 I/O 调度继续使用。

## 验证

[完整回归清单与结果表](./verification.md) 包含此前已有的协议过滤用例、新增回归及本次复现路径。新增 JVM 回归已放入仓库现有测试目录，由 Android Tests 工作流执行。

当前尚未运行本地构建、单元测试或设备复现；静态检查与工作流派发结果在交付时另行记录，不能由源码或静态检查推定为通过。

## 实现状态

[DONE] 后台总结边界、取消检查、匹配器复用和回归源码已实现；CI 与设备结果待记录。
