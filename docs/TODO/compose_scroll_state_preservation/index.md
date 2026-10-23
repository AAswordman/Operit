---
fork: https://github.com/yoruuuchan/Operit
issue: 709
---

# Compose DSL 与设置页滚动位置保持

## 旧实现

- `ToolPkg Compose DSL` 的 `LazyColumn` 在渲染器内部直接 `rememberLazyListState()`。JS 状态变化后整棵树会重建，列表节点一旦离开组合，滚动状态随之丢失。
- `LazyRow` 甚至没有显式传入 `LazyListState`。
- 角色卡编辑页、标签编辑页使用 `rememberScrollState()`；页签内容由 `when` 分支替换，离开组合后列表回到顶部。
- 角色卡列表与标签列表的 `items` 没有稳定 key，数据刷新时会按位置重新绑定条目。

## 本次意图

让普通状态更新、页签切换与对话框重排不再把滚动位置丢回顶部，同时保留正常导航、主动定位和列表数据更新的滚动行为。

## 作用域

- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/`
- `app/src/main/java/com/ai/assistance/operit/ui/features/settings/`
- `examples/worldbook` 的列表状态键
- `tools/compose_dsl/` 生成器与模板
- 对应 JVM 单元测试

不修改聊天主界面、文件管理器等已有独立滚动状态管理的页面。

## 步骤

1. [LazyListState 持久化](1_LazyListStatePreservation.md) [DONE]
