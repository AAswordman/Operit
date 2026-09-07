# DTS 与文档

## 目标

- 在新增或现有 ToolPkg API 的 DTS 声明上标注对应的 ToolPkg API 引入版本。
- 补充 manifest 字段、API 支持范围、依赖和加载顺序说明。
- 保持格式文档和开发文档的字段命名一致。

## 维护约定

- `examples/types/toolpkg.d.ts` 保持一份最新版声明，不手写多份按版本拆开的 DTS。
- 新增公开能力时，在相关类型、注册函数和底层 NativeInterface 声明处标注 `@since ToolPkg API x.y.z`。
- manifest 的 `api_version` 和宿主注册桥负责实际可用性；DTS 只负责开发期提示和文档来源。

[DONE]
