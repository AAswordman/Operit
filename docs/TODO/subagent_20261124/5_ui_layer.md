# 步骤 5：UI 层

[返回总计划](index.md)

## 旧实现情况

`CustomXmlRenderer` 不认识 `subagent` 标签，信封会走 fallback 渲染成原始 XML。`renderToolRequest` 只按工具名做文件类特殊处理，没有实时卡片。`renderToolResult` 把内层信封当纯文本。

## 意图

三种渲染形态，一套详情组件：

| 形态 | 组件 | 数据源 |
| --- | --- | --- |
| 已完成 | `RenderSubagentRun(content)` | 信封属性 + evidence JSON（补任务简报与账本） |
| 运行中 | `SubagentRunLiveCard(toolTitle, agentName)` | 订阅 `service.jobsFlow` |
| 详情 | `SubagentDetailDialog(data)` | 两者共用，运行中订阅 flow 实时刷新 |

## 新实现

### `ui/features/chat/components/part/SubagentRunCard.kt`

`SubagentRunRenderer.parse`：正则解析 `<subagent><final ...>...</final></subagent>`，属性转 `Envelope`。

`displayBody` 做一次展示层兼容：旧版本在 `no_final_declaration` 时把子任务原始终局 JSON 当正文写入，卡片会直接显示 `{"status":...}`。剥离 think 块后若能解析出 `summary` 就显示 summary，解析失败原样显示，不改协议数据。

`SubagentRunLiveCard` 的任务匹配规则：

```kotlin
val job = remember(jobs, toolTitle) {
    val active = jobs.filter { it.status == RUNNING || it.status == QUEUED }
    if (toolTitle.isNullOrBlank()) active.lastOrNull()
    else active.firstOrNull { it.toolTitle == toolTitle }   // 不能用 lastOrNull 兜底
}
if (job == null) return   // 空壳修复
```

两条都是实测出来的：回退到 `lastOrNull()` 会让 8 路并行时每张卡片显示同一个任务；空壳不 `return` 会留下"执行中 0/0"的无内容卡片。

### `CustomXmlRenderer` 四处改动

1. `builtInTags` 加 `subagent`（保留本仓库的 `mood`）
2. `RenderXmlContent` 开头 `splitConcatenatedToolBlocks`：并行委派时模型常把多个 `<tool>` 标签连续输出（甚至只闭合最后一个），XML 分块会把它们当成一个超长块，渲染成原始参数文本
3. `"subagent" -> RenderSubagentRun(...)` 分派
4. `renderToolResult` 优先识别内层信封：`if (content.contains("<subagent>", ignoreCase = true))`
5. `renderToolRequest` 命中委派时转 `SubagentRunLiveCard`，`package_proxy`/`proxy` 要解析内层 `tool_name` + `params` JSON 才能拿到 `tool_title`/`agent`

### 路由与设置入口

`OperitScreens.kt`：

```kotlin
data object SubagentConfig : Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_subagent_config)
data class SubagentEditor(val profileId: String) : Screen(navItem = NavItem.Settings, titleRes = R.string.screen_title_subagent_editor)
```

带参数的 `data class` 由 `ScreenRouteRegistry` 反射构造，路由可直达。

`SettingsScreen` 加 `navigateToSubagentConfig` 参数与一个 Section 入口。

### 权限系统

按既定决策**完全不动**：`ToolPermissionSystem` 继续走 `PermissionRequestOverlay`，`ToolPermissionDialog.kt` 保持现有枚举桩。子代理委派与子任务内的工具调用沿用同一套确认链路。

## 验收

- 同步委派完成后 `tool_result` 里出现任务卡片，不是原始 XML
- 阻塞委派期间工具请求行显示实时卡片，工具调用数与当前工具名随执行刷新
- 8 路并行：每张卡片显示各自的任务标题，不串台
- 已完成的历史消息里委派请求行不渲染空壳
- 点击卡片打开详情，显示任务简报、状态、工具账本、摘要

[DONE] 实现已完成，编译、单测与真机验证均通过（详见 [index.md](index.md) 的实施状态）。
