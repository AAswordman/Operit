# LazyListState 持久化

## 旧实现

`ToolPkgComposeDslGeneratedRenderers.kt` 中：

- `renderLazyColumnNode` 使用 `rememberLazyListState()`。
- `renderLazyRowNode` 没有状态参数，使用 `LazyRow` 的默认内部状态。

`rememberLazyListState()` 本身虽然是 saveable 的，但 saveable 只在组合仍然存活或宿主保存了组合状态时生效。Compose DSL 的树由 JS 每次状态更新后重建，条件分支、页签和对话框重建会让列表离开组合。

`ModelPromptsSettingsScreen` 的 `when (currentTab)` 同样会替换整个页签内容；`CharacterCardDialog` 与 `TagDialog` 的 `rememberScrollState()` 只存在于对话框当前的组合里。

## 新实现

- 新增 `ComposeDslScrollStateStore`，由 DSL 页面、对话框宿主和嵌入式渲染入口持有，生命周期长于界面树。
- 新增 `rememberComposeDslLazyListState(nodePath, nodeType, key)`：优先从 store 按稳定身份取状态，没有宿主时退回 `rememberSaveable(LazyListState.Saver)`。
- 稳定身份优先使用 DSL 节点的 `key`，否则使用节点路径与规范化类型；世界书为列表页、编辑页、分组页、导入页分别声明 key，避免不同视图互相串用滚动位置。
- `LazyRow` 也显式接收同一个状态。
- 角色卡编辑页与标签编辑页的滚动状态改为 `rememberSaveable(ScrollState.Saver)`；角色卡列表与标签列表补上 `key = { it.id }`。
- 设置页三个页签统一包在 `SaveableStateHolder.SaveableStateProvider(currentTab)` 中，切换页签后返回时列表状态可以恢复。

## 验证

- JVM 单元测试：`ComposeDslScrollStateStoreTest`。
- 手工验证：世界书列表切换条目状态、编辑页输入、角色卡与标签编辑页输入、设置页页签往返，均保持滚动位置；列表重新加载后仍能按用户滚动位置显示。
