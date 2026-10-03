---
title: 模块 API 索引
status: complete
---

# 模块 API 索引

本节以 `examples/types/` 中实际公开的运行时命名空间为入口。所有 `04_modules/*.md` 页面都已完成声明、facade、运行时实现和结果 DTO 的逐方法审计；声明与运行时的差异、未暴露入口和无法由实现证实的行为均在对应页面及兼容性覆盖索引中明确记录。

## 运行时基元与 ToolPkg

- [全局核心类型与调用约定](./core.md)：脚本工具调用、上下文函数和核心运行时类型。
- [工具参数与调用类型](./tool-types.md)：工具名、参数映射与类型化返回。
- [ToolPkg 注册对象](./toolpkg.md)：UI、生命周期、聊天、Prompt、摘要、AI Provider、IPC、WASM 和资源注册。
- [公共结果类型](./results.md)：宿主工具结果及字段结构。

## 聊天与记忆

- [Chat](./chat.md)：聊天记录、消息、模型调用及上下文相关接口。
- [Memory](./memory.md)：记忆搜索、读写、关系和结果结构。

## 文件与网络

- [Files](./files.md)：文件读写、目录、搜索、编辑与转换接口。
- [Net](./network.md)：HTTP、网页访问、搜索和网络结果类型。
- [OkHttp](./okhttp.md)：OkHttp Java Bridge 的请求构造和响应处理。

## 系统与设置

- [System](./system.md)：终端、设备、应用、自动化和系统操作接口。
- [SoftwareSettings](./software_settings.md)：模型、语音、包、环境变量和应用设置管理接口。

## 自动化与编排

- [Workflow](./workflow.md)：工作流查询、创建、执行和结果数据。
- [Tasker](./tasker.md)：Tasker Runtime API。
- [Android](./android.md)：Intent、PackageManager、ContentProvider、SystemManager、DeviceController 和 AdbExecutor。

## UI 与工具库

- [UI](./ui.md)：页面结构、节点定位和 UI 自动化操作。
- [FFmpeg](./ffmpeg.md)：媒体转码、编码器和转码参数。
- [CryptoJS](./cryptojs.md)：加密、摘要与编码库接口。
- [Jimp](./jimp.md)：图像处理库接口。

## 逐方法审计进度

已完成 `04_modules/` 全部模块页面的声明、facade、运行时实现和结果结构逐项核对：

- `core.d.ts` 的 NativeInterface 全方法契约、工具调用结果分支和全局调用约定已完成核验。
- `results.d.ts` 的全部公开结果类型、字段映射和 `BaseResult` 包装关系已完成核验。
- Android、Chat、Core、CryptoJS、FFmpeg、Files、Jimp、Memory、Net、OkHttp、SoftwareSettings、System、Tasker、Tool Types、ToolPkg、UI 和 Workflow 页面已完成逐方法对照。
- Compose DSL、Material 3、Material Icons 以及 Java Bridge 的公开声明、renderer/bridge 行为和字段归一规则已完成核验。
- 全局 `Tools`、辅助对象与 exports 注册入口的调用时序、错误路径和当前未暴露能力已完成记录。

已知声明/运行时差异、未实现入口和无法由当前实现证实的行为不会被隐去，而是保留在各模块页面与 `09_compatibility/coverage.md` 中。模块索引本身的审计状态现为 `complete`。