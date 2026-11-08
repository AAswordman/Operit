---
repo: https://github.com/3316891527/Operit
branch: refactor/package-layout
base: 6a4b9bfb399c670321518089592ae3e02e38897e
status: implemented-awaiting-removal-ci
---
# 应用包职责与路径整理

本分支分两轮整理 Android 应用源码。第一轮覆盖原 API 的 117 个 Kotlin 文件；第二轮覆盖功能 UI、util 与 Android 集成的 677 个源文件，并按维护者要求删除确认废弃的工具箱首页与孤立文件管理器。随后按维护者接受兼容变化的新要求，完成废弃命名、插件界面 API 与九份 Android 包声明收尾。继续使用同一工作分支和 PR #67，目标为 dev。

- [原始职责分析](analysis.md)
- [第一轮 API 实施记录](01-api-migration.md)、[路径清单](api-migration-candidates.csv)、[机器映射](api-migration.json)与[历史验证](verification.md)
- [废弃工具箱清理](02-toolbox-cleanup.md)
- [第二轮功能、工具与 Android 集成整理](03-ui-utilities-migration.md)、[路径清单](ui-utilities-migration.csv)、[机器映射](ui-utilities-migration.json)与[第二轮验证](verification-round2.md)

- [命名与组件包名收尾](04-layout-completion.md)、[命名收尾时的路径快照](ui-utilities-migration-final.csv)、[受控变更清单](completion-migration.json)与[收尾验证](verification-completion.md)

- [固定内置扩展资源同步](05-offline-resource-sync.md)
- [旧界面与虚拟形象清理](06-legacy-ui-avatar-removal.md)、[清理及保留摘要](removal-migration.json)与[当前完整回归记录](verification-removal.md)

## 已实施

1. [DONE] 第一轮移动 74 个 API 文件，保留 43 个位置，完成五处类名调整和 37 个既有测试路径更新。
2. [DONE] 删除空工具箱首页、孤立文件管理器和空导航聚合，保留有调用的日志、诊断和导出；当时保留的旧原生工具页面已在后续清理。
3. [DONE] 第二轮将功能 UI 归入六组，按职责移动 43 个根部工具文件，将 DocumentsProvider 与 Widget 源码归档到 integrations。
4. [DONE] 第二轮核对 677 个范围文件：643 个移动、34 个保留位置；同步 95 个既有测试路径及全项目调用引用。
5. [DONE] 第二轮 1,072 个改动源码正文核对、1,350 个范围外源码摘要、完整源码集合、3,020 处隐式引用与 43 份入口摘要检查通过。
6. [DONE] 整理仍有效的工具箱模块、插件 UI API、导航面和路由命名；旧工具箱 UI 注册接口保留为空操作，默认 Compose DSL 继续支持。九份 Android 文件包声明全部对齐路径，更新 Manifest 与 Widget XML。
7. [DONE] 同步 40 个扩展指纹与 7 个插件载荷，发布并核对新的固定资源版本，更新应用目录。
8. [DONE] 已对资源同步提交 29972a8a 派发 Android Build 和 Android Tests，两项均受理；执行结果未查询。
9. [DONE] 删除 AutoGLM、FFmpeg、Shell、SQL 的旧原生界面和虚拟形象，保留执行器、Waifu 分段打字、图片头像与语音交互。
10. [PENDING] 对本轮清理后的最新提交派发 Android Build 和 Android Tests，结果由维护者反馈。

## 验证状态与边界

第一轮静态核对已通过，维护者反馈第一轮测试工作流已绿；没有据此断言第一轮 Release 构建或第二轮工作流通过。两轮均未运行本地 Gradle 编译、构建、JVM 或设备测试。

路径迁移不修改业务算法。工具箱清理是额外批准的删除范围，其导航行为与保留页面见清理记录。保留根包前缀、namespace、applicationId、authority、存储字段、JNI 和未纳入本轮的 Java 桥入口。此前保留的九份 Android 包声明已统一，有效插件界面采用新 API，旧界面注册 API 按空操作兼容，不增加旧 Android 组件转发；旧桌面实例可能需要重新添加。

旧界面及虚拟形象的最终删除范围见本轮清理记录；Waifu 和图片头像保留，语音调用不再注入形象动作协议。

三个 MCP 文件的路径与包声明整理、根包品牌和其他未纳入映射的目录继续留在后续任务。本分支未合入 dev/main，未发布应用版本。
