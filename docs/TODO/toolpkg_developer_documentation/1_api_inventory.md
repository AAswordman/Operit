---
status: in_progress
---

# 1. API 盘点与权威来源

## 旧实现

- 声明集中于 `examples/types/*.d.ts`，但当前 API 文档与声明文件分散在不同目录。
- ToolPkg 注册、版本门禁和运行时代理分布在 `JsToolPkgApiRuntime.kt`、`ToolPkgApiVersion.kt`、`ToolPkgParser.kt`、`PackageManager.kt`、`ToolPkg*Bridge.kt` 等实现。
- Hook 的模型、桥接、超时与取消逻辑分散在 `plugins/toolpkg/` 和 `core/tools/packTool/`。

## 意图修正

- 以公开类型声明建立候选 API 清单，再逐项定位 JS facade、宿主注册和执行桥接。
- 只有声明与运行时都可确认的行为写为契约；对无法由实现确认的项显式记录待核实状态。
- 版本注记以 `@since ToolPkg API x.y.z` 为接口最低 API 版本要求；宿主 Operit 最低版本单独列出。
- 区分 `manifest.api_version`、`manifest.version`、`formatVer` 和市场版本对象中的 `apiVersion`。

## 期待结果

- 清单覆盖 `examples/types/index.d.ts` 导出的模块、全局 API、类、函数、Hook 注册和结果类型。
- 每个有版本门槛的符号都记录 API 版本和实现中的可用性检查位置。
- 记录声明与实现的偏差、弃用项及当前无法确定的运行时语义。

## 完成情况

- [ ] 汇总全部声明文件和公开符号。
- [ ] 核实 API 版本解析、比较、支持矩阵和错误条件。
- [ ] 核实全部 Hook 触发点、桥接顺序、返回值消费、超时和取消。
- [ ] 标出需后续维护者确认的差异。
## 已完成的局部核对
- `core.d.ts` 的 `NativeInterface` 含 33 个函数；33 个声明名均匹配 `JsEngine.kt` 中的 `@JavascriptInterface` 绑定，并在 `04_modules/core.md` 逐项列出。
- 核实了同步工具调用返回 JSON、异步流结果分发、注册入口 API 版本、图片注册实际返回值、`logDebug` 空实现及 Java bridge JSON 返回形状；已确认的声明/运行时差异登记在覆盖索引。
- `results.d.ts` 共 162 个接口、742 个顶层字段签名；本轮逐字段核对 30 个接口/134 个字段，覆盖基础 Chat/ChatCall、Memory 查询/链接和 CharacterCard 数据。其余 132 个接口/608 个字段及其他结果类别仍待逐项审计。
