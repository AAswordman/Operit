---
status: completed
---

# Merge Package Implementation

## 旧实现

世界书数据保存在稳定包的配置目录。管理界面以平铺条目列表展示内容，
不维护分组记录，也不能在创建后复制条目。

## 变更

从下载的 `com.operit.worldbook_plus.toolpkg` 同步分组的源码和已编译产物，
保留稳定的包 ID。将归档内依赖 `com.operit.worldbook_plus` 的 UI 路由改为
`com.operit.worldbook`，避免安装后导航到不存在的路由。

## 预期结果

- 稳定包原地升级到 `1.2.0`
- 原有条目数据保持可读
- 分组数据额外写入同一配置目录的 `groups.json`
- 管理界面可管理分组并复制条目

## 验证

本次未执行编译或测试，遵循工作区的默认执行准则。需要在后续明确要求时，
再执行 ToolPkg 的类型检查、构建和设备端调试安装。

应用内置的 `worldbook.toolpkg` 由 `sync_example_packages.py` 在预构建阶段从
`examples/worldbook` 生成，本次未手动改写该生成物。

[DONE] 已同步分组实现、稳定包元数据与对应的 `dist` 文件。
