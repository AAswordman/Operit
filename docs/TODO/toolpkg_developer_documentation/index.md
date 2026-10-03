---
title: ToolPkg 开发者文档重建
status: complete
personal_repository: https://github.com/3316891527/Operit
upstream_target: dev
related_discussion: https://github.com/AAswordman/Operit/discussions/1328
---

# ToolPkg 开发者文档重建

## 当前情况

- Discussion #1328 指出插件教程对 JavaScript 基础讲解过多，没有系统说明宿主提供的 API。
- 当前文档入口主要位于 `docs/doc-src/toolpkg_developer_documentation/`，另有 `docs/TOOLPKG_FORMAT_GUIDE.md`、`docs/SCRIPT_DEV_GUIDE.md` 和 `docs/SCRIPT_DEV_SKILL.md` 作为格式、脚本和安装说明。
- 原 `docs/doc-src/package-dev/` 的 API 页面已迁移并扩展到新文档的 `04_modules/`，本次完成引用迁移后删除旧目录。
- `examples/types/*.d.ts` 描述静态类型；运行时实现位于 `app/src/main/java/.../core/tools/packTool/`、`core/tools/javascript/` 和 `plugins/toolpkg/`。
- ToolPkg API 兼容性实现目前支持 `1.0.0`；`1.0.1` 从 Operit `1.12.1+4` 起支持。包版本、宿主 API 版本和归档格式版本是不同字段。
- 旧文档在新文档完整后再单独审阅兼容入口、引用迁移和删除范围；本次已将 `package-dev` 的引用迁移到新目录，并删除旧目录。

## 目标

在 `docs/doc-src/toolpkg_developer_documentation/` 建立新的单一完整参考入口。文档以 JS/TS 开发者为目标，不重复教授通用语言基础；以声明文件、运行时实现和验证过的行为为依据，覆盖 ToolPkg 格式、运行模型、全部公开 API、版本门槛、Hook 契约、类型、示例和调试发布流程。

每个公开方法都应说明完整签名、参数约束、异步行为、返回结构、错误语义、状态/副作用、调用前置条件和可运行示例。Hook 还应写明触发时机、执行顺序、payload 可变性、返回值对宿主的影响、超时/取消/异常处理和版本要求。不能从类型声明推断未证实的运行时行为。

## 范围

- 纳入 ToolPkg manifest、子包脚本、全局运行时、工具模块、Android/Java Bridge、UI/Compose DSL、Hook、公共结果类型、内置库和 WASM 接口。
- 对每个版本化字段或方法标注 ToolPkg API `@since` 要求，并区分 Operit 最低支持版本、包自身版本和格式版本。
- 为公开符号建立声明文件与文档页面的覆盖清单，并记录声明与运行时不一致或尚无证据的项目。
- 新文档已完成模块 API、运行时、Hook、UI、结果类型、类型/库和兼容性参考；旧 `package-dev` 页面已迁移、引用已更新并删除。
- 不扩写通用 JavaScript/TypeScript 教程，不虚构 API，不修改运行时实现。

## 阶段

1. 完成 API、类型、版本和 Hook 的源码盘点。
2. 建立分类目录、总索引、版本/兼容说明和开发者快速路径。
3. 撰写格式、运行时、模块 API、Hook、UI、结果类型与库参考。
4. 逐符号核对声明/实现/文档，检查链接、示例和旧文档引用；确认新文档覆盖旧 API 页面后，完成引用迁移并删除重复旧目录。
5. 继续维护新文档的版本、兼容性和实现差异记录。

## 分支与 PR

- 分支：`docs/toolpkg-developer-reference`
- PR 目标：`dev`
- 个人仓库：`https://github.com/3316891527/Operit`
- 本分支文档实现已完成，目标分支为上游 `dev`；PR：[AAswordman/Operit#1330](https://github.com/AAswordman/Operit/pull/1330)。

## 本轮进度

- 已按 `examples/types/*.d.ts` 补齐 QuickJS Runtime、Java Bridge、Pako、Compose DSL、Material 3、Material Icons 的新文档入口，并为 Compose/库分类建立索引。
- 已根据源码更正 `dataUtils`、Pako、`useState`/`useMemo`、QuickJS console/timer 等运行时契约；差异记录在[覆盖索引](../../doc-src/toolpkg_developer_documentation/09_compatibility/coverage.md)。
- 新文档树的相对 Markdown 文件链接检查已通过；本轮复核覆盖 45 篇 Markdown，断链为 0。NativeInterface 声明与 `JsEngine` 原生绑定均为 33 项，核心页逐项覆盖 33 项。
- `software_settings.d.ts` 的 26 个公开方法均已列出 TypeScript 签名；环境变量、沙箱/MCP、speech 更新、角色卡字段校验与工具访问解析、连接测试行为已有源码核对。本轮完成模型配置创建/更新/删除、字段归一、函数绑定/索引和连接探针行为审计，并记录 `ModelConfigUpdateOptions` 缺少 6 个运行时支持的 `llama_*` 字段这一声明差异。
- `workflow.d.ts` 的 10 个 `Runtime` 方法均已列出签名；本轮完成 CRUD、patch、节点/连接解析、启停/错误结果及 `WorkflowExecutor` 的触发选择、依赖调度、边条件、失败分支、Condition/Logic/Extract/Execute 节点行为源码审计。
- `network.d.ts` 的 34 个 `Net` 函数声明及 CookieManager 的 3 个方法已完成源码逐项核对；记录 7 个声明但未映射的浏览器方法、HTTP/visit 参数与结果字段差异、全局活动 tab 行为和 Cookie 返回类型差异。
- `okhttp.d.ts` 的 `OkHttp`、`OkHttpClientBuilder`、`OkHttpClient`、`RequestBuilder`、`HttpRequest`、`HttpStreamEvent`、`OkHttpExecuteOptions` 和 `OkHttpResponse` 已按 `OkHttp3.js`、宿主 HTTP 工具和启动模块完成逐项核对；记录全局加载、超时单位换算、同步拦截器、流式回调、响应 Base64/cookie 字段，以及 `retryOnConnectionFailure`、`formParam` 和 `multipartParam` 的当前实现差异。
- `memory.d.ts` 的 10 个公开方法及位置/对象重载已按 `JsTools.Memory`、`ToolRegistration`、`MemoryQueryToolExecutor`、`MemoryRepository` 相关调用和结果 DTO 完成逐项核对；记录 caller card profile 解析、查询快照、时间边界、通配查询、文档分块、CRUD 默认值、移动筛选、链接定位/歧义和 weight/limit 钳制行为。
- `ffmpeg.d.ts` 的 `Tools.FFmpeg.execute`、`info`、`convert` 已按 `JsTools`、`JsInitRuntimeScriptBuilder`、`ToolRegistration`、`StandardFFmpegTool`、`FFmpegResultData` 和内置包 wrapper 完成方法级核对；新参考页已补充命令构造、前置条件、异步失败语义、实际返回结构及声明差异。
- `jimp.d.ts` 的 `JimpWrapper`、读取/创建、裁剪/合成、尺寸读取、Base64 导出和资源释放已按 `Jimp.js`、`JsNativeInterfaceDelegates.imageProcessing`、`JsEngine` 与启动模块完成方法级核对；记录 wrapper 构造器未导出、释放后的 `id` 变化、整数参数、PNG/JPEG 编码规则、内部二进制句柄输入和 bitmap 生命周期差异。
- `cryptojs.d.ts` 的 `WordArray`、`MD5`、`AES.decrypt`、`enc.Hex.parse`、`enc.Utf8`、`pad.Pkcs7` 和 `mode.ECB` 已按 `CryptoJS.js`、`JsNativeInterfaceDelegates.crypto`、`JsEngine` 与启动模块完成方法级核对；记录 WordArray 运行时形状、Hex 不解码、AES key/cfg 归一、固定 ECB/NoPadding + 手动 PKCS7、错误转空结果及未导出构造器差异。
- `tasker.d.ts` 的 `TriggerTaskerEventParams` 和 `Runtime.triggerEvent` 已按 `JsTools`、`JsInitRuntimeScriptBuilder`、`ToolRegistration`、`AIAgentTasker` 与 Tasker Plugin Library 调用链完成方法级核对；记录 task_type 校验、arg1-arg5/args_json 归一、事件提交而非任务完成的异步语义、本地化状态文本和 Tasker 事件方向差异。
- `quickjs-runtime.d.ts` 页面现在区分 QuickJS 全局兼容层与 metadata 驱动的包工具参数转换；后者按 `JsToolManager` 实现记录类型转换、缺参和错误路径。
- `tool-types.d.ts` 的 `ToolResultMap` 151 个键及返回类型已逐项列出，并按 `core.d.ts`、`JsTools.kt`、`ToolRegistration.kt` 和 `results.d.ts` 完成静态核对；已记录 `combined_operation`/`execute_terminal` 仅存在于声明文本、当前 facade/注册入口未进入映射、`read_file` 与 `read_file_full` 的兼容入口差异，以及包工具和代理工具的动态名称边界。
- `system.d.ts` 的全部 System、Terminal、Bluetooth、Intent、Broadcast 和 Music 方法已按 `JsTools.kt`、`ToolRegistration.kt`、系统/蓝牙/终端/音乐/Intent 实现与结果 DTO 完成逐项核对；已记录权限门禁、系统设置命名空间、安装/卸载请求语义、Usage Access 默认值和筛选、位置超时/最近位置回退、蓝牙 UUID/会话/通知消费、Shell 危险命令过滤、Intent 实际字段、终端超时取消/流式事件、音乐输入校验及声明差异。
 - 当前已完成 `ui.d.ts`、`compose-dsl.d.ts`、`compose-dsl.material3.generated.d.ts` 和 `java-bridge.d.ts` 四个方法级声明组，以及 `results.d.ts` 的完整结果类型审计。`results.d.ts` 的 162 个 interface、742 个声明字段和 38 个 `BaseResult` 包装映射均已完成源码核对；此前剩余的 26 个接口/71 个字段已清零。`results.md` 同时记录了 `DateResultData`、Bluetooth/音乐开放字符串、终端会话、FFmpeg `mediaInfo`、`ConnectionResultData` 无当前构造路径，以及 `ToolResultDataClasses.kt` 中未进入声明文件的运行时数据类。`package-dev` 旧 API 页面已迁移到 `docs/doc-src/toolpkg_developer_documentation/04_modules/`，仓库内入口和新文档交叉引用已更新，旧目录可删除。
