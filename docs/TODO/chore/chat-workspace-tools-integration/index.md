---
fork: https://github.com/3316891527/Operit
status: awaiting-ci
---

# 工具、文件操作与聊天目录联合验证

本分支从 `dev` 的 `ffb78d30` 创建，供 Android 集成包验证使用。分支名为 `ci/chat-workspace-tools-integration`，PR 目标为 `dev`。

## 集成来源

- [PR #92：工具目录与三档权限整理](https://github.com/3316891527/Operit/pull/92)，来源提交 `528a8070cb29774133234c353949943b79e640a5`
- [PR #93：工作区文件操作内存和进度](https://github.com/3316891527/Operit/pull/93)，来源提交 `9b9bb75d93b96b72204067fa576db30e5279d960`
- [PR #94：聊天和工作区职责目录整理](https://github.com/3316891527/Operit/pull/94)，来源提交 `d4e90bd1c02e703240fbc15b27f71c5c1188a734`

按上述顺序合入并保留来源历史。#94 以 #93 为基线，合并时按共同祖先处理，不重复应用文件操作改动。三份来源提交均已核对为集成分支祖先。

## 最终代码归属

工具入口使用 `core.tools.runtime.AIToolHandler`、`core.tools.packTool.runtime.PackageManager` 和 `core.tools.system.terminal.Terminal`，调用方同时使用新的聊天和工作区包路径，避免将任意一侧的旧路径带回集成结果。

聊天根层为 `foundation`、`modules`、`screens`、`viewmodel`；工作区位于平级的 `conversation.workspace`。流式复制、复制与移动进度、删除进度和既有工具权限路由继续保留。

此集成分支没有新增运行规则或回退入口。源码合并交叠处仅协调导入，文档索引保留各自功能计划。

## 已完成的静态核对

- 三份来源提交的祖先关系通过检查，工作树没有未解决的合并项
- 按工具迁移清单核对目标文件与旧路径，旧聊天包引用和冲突标记扫描无结果
- 对文件传输核心、进度实现、删除状态、相关用例、浏览器操作和新增语言资源共 20 个文件核对声明体，除工具路径外与目录整理分支保持一致
- 完整候选差异通过空白检查，声明迁移和原功能验证记录继续保留

本地未执行编译、构建或测试。集成包通过仓库 `Build` 的 `assembleDebug` 任务生成，并派发 `Android Tests`；实际受理与结果记录在对应集成 PR 和 Actions 页面，不将派发受理视为验证通过。

## 完整验证范围

联合验证继续使用三份既有记录中的全部范围，包含此前已有用例：

- [工具与权限验证](../tools-permissions-layout/verification.md)
- [聊天目录迁移、全部聊天回归类和界面范围](../chat-responsibility-layout/01-validation.md)
- [工作区目录迁移](../conversation-workspace-layout/01-validation.md)
- [复制、剪切、删除完整验证及可填写结果表](../../fix/workspace-copy-streaming-progress/01-validation.md)

[DONE] 集成来源和静态核对记录；CI 结果与 Android 界面测试仍待确认。