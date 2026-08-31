# V2 包与运行时契约

## 旧问题

V1 manifest 只声明 `chat.main` 和少数 token。`app.shell` 没有宿主，主题参数只有主色和背景图，Native component catalog 也没有由已安装包驱动。

## V2 契约

`operit-theme.json` schema version 为 `2`。包解析成不可变 `LinkedThemeRuntimeV2`，其中包括：

- 精确包坐标和显式 base package 坐标
- 完整 Material 颜色、排版和形状投影
- 通用组件皮肤：app bar、navigation、page、section、list item、button、icon button、input、message、dialog、sheet、menu、snackbar、status
- required daily surface 的实现清单
- page scene、host shell scene、资源、文本、状态样式和参数

主题仅在安装与激活阶段解析、链接和校验。运行时只读取 `LinkedThemeRuntimeV2`，不在 Compose composition 中重新解析 manifest。

## 严格规则

- 主题必须通过自有实现或显式 base package 实现覆盖 required surface 和组件皮肤
- 激活时拒绝 coverage 不完整、资源无效、schema 不匹配或基底坐标不精确的包
- 基底是 manifest 的确定性依赖，不是运行时视觉分支
- 组件状态必须显式提供 normal/disabled/focused/pressed/selected/error 中契约要求的状态
- 主题包不得访问业务模型、导航器、权限、网络或任意 Compose/Kotlin 代码

## Material 投影

V2 manifest 提供完整 `material` palette、typography 和 shapes。Operit 的既有 Material 组件仍作为交互与无障碍底座，但它们的颜色、排版、形状和 content alpha 一律来自 active package projection。新的 Operit theme components 为异形/九宫格/自定义状态提供包级 skin。

## 进展

[DONE] V2 manifest、严格归档校验器、内容寻址发布、安装器、链接器（`ThemePackageRuntimeLinkerV2`）、全局选择（V2 DataStore）与进程内 `ThemeRuntimeRepositoryV2` 已落地；V1 包代码与测试已全部删除。

[DONE] 场景 DSL 新增按内容测量的 `scaffold` 节点与宿主槽位 `rowWeight`；九宫格在有子内容时按子内容包裹。

[TODO] 参数→token/场景联动（当前参数机制已就绪但默认主题未声明参数）。
