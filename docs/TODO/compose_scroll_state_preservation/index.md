---
fork: https://github.com/yoruuuchan/Operit
issue: 709
---

# Compose DSL 与设置页滚动位置保持

## 旧实现

- `ToolPkg Compose DSL` 的 `LazyColumn` 在渲染器内部直接 `rememberLazyListState()`。JS 状态变化后整棵树会重建，列表节点一旦离开组合，滚动状态随之丢失。
- `LazyRow` 甚至没有显式传入 `LazyListState`。
- 角色卡编辑页、标签编辑页的外层 `rememberScrollState()` 本身已经可保存，但没有独立管理每个输入框的内部视口和选区；页签内容由 `when` 分支替换，缺少页签状态宿主。
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
2. [角色卡提示词编辑窗口补全](2_CharacterCardEditors.md)：实现及发布版构建完成，用户真机验收通过，Android instrumentation 用例待执行
