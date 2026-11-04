---
fork: https://github.com/yoruuuchan/Operit
branch: fix/issue-1399-memory-document-dialog-overflow
issue: https://github.com/AAswordman/Operit/issues/1399
status: completed
---

# Memory 文档弹窗 Constraints 溢出修复

## 原状

Memory 图谱中的文档节点会打开 `DocumentViewDialog`。弹窗把每个文档区块放进
`LazyColumn` 的 `OutlinedTextField` 中，文本域未设置任何高度上限。

`LazyColumn` 按设计以无界高度测量子项。当文档导入时没有空行分隔，整篇文本会成为
一个超大区块；文本域按全文高度布局，超过 Compose `Constraints` 可表示的最大尺寸
(262143 px) 后，Material3 的 `OutlinedTextFieldMeasurePolicy` 在构造内部容器约束时抛出
`IllegalArgumentException: Can't represent a width of 952 and height of 523922 in Constraints`。

## 修改意图

保留现有区块编辑、文档内搜索和整体保存行为，只补齐文本编辑组件的尺寸约束：
每个区块编辑框获得有限的高度视口并在内部滚动，区块列表负责文档级滚动。

## 作用域

- `DocumentViewDialog`
  - 区块编辑框使用 120dp 至 280dp 的高度范围，避免无界高度测量。
  - 文档标题改为单行输入，避免超长标题撑高弹窗。
  - 搜索框保留固定高度，区块列表改用 `weight` 占据剩余空间。
- 新增 `DocumentViewDialogAndroidTest`，覆盖超长单区块和短区块列表的打开、滚动、编辑与保存。
- 不改变文档导入分块、存储格式、检索索引或保存接口。

## 验证

- 超长单区块弹窗可以打开，编辑框高度不超过 280dp，编辑内容可以回写并触发保存。
- 短区块文档可以滚动到末尾区块，编辑与保存回调正常。
- `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin` 通过。
- `:app:testDebugUnitTest` 通过。
- 在 API 35 x86_64 模拟器（`system-images;android-35;google_apis;x86_64`，含
  `arm64-v8a` 翻译）上执行
  `DocumentViewDialogAndroidTest`：`OK (2 tests)`，耗时约 11–13 秒。
- 同一超长用例在修复前的 APK 上复现
  `IllegalArgumentException: Can't represent a width of 748 and height of 825143 in Constraints`，
  栈位于 `OutlinedTextFieldMeasurePolicy.measure()`，确认测试覆盖了原始崩溃路径。
