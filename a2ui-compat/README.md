# AndroidX A2UI 适配层

来源为 Google Maven 的 AndroidX A2UI `1.0.0-alpha01` sources JAR：`a2ui-model`、`a2ui-engine`、`compose-runtime`、`compose-ui`。共 105 个源文件，保留 Android Open Source Project 的 Apache-2.0 文件头，完整许可证见 `LICENSE`。

官方四个二进制模块的 AAR 元数据要求 SDK 37 / AGP 9.1，并传递引入 Compose 1.13.0-alpha03。本目录将对应的官方协议、引擎和 Compose 源码一起编译到应用现有的 SDK 36 / AGP 8.13 / Compose BOM，避免调整整套构建工具，也不忽略 AAR 元数据检查。

保留官方 A2UI v0.9 模型与处理行为。应用在 `ui/common/a2ui/` 提供自己的组件目录、Surface 和主题，不引入完整 Material A2UI 组件及其内置图标集。兼容层使用应用已有的协程、JSON 序列化和 AndroidX Core 依赖，Collection 保持在 1.5.0。

上游版本更新时，必须同时核对这四个模块的接口及协议，并由云端构建与官方引擎回归用例验证。未执行本地构建，不应仅凭源码导入判断兼容性已经通过。

本地适配：`A2uiComponentScopeImpl.kt` 和 `A2uiBasicCatalogV1.kt` 的带键 `SideEffect` 改为 `LaunchedEffect`，按相同输入键在提交后上报错误，避免重复上报。`sources.json` 的 `sha256` 保留官方原始来源值，`adapted_sha256` 记录适配后的文件值。
