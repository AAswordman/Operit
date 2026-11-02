---
title: ToolPkg Manifest 字段
status: complete
---

# ToolPkg Manifest 字段

宿主接受 ToolPkg 归档中的 `manifest.json` 或 `manifest.hjson`。字段名按 manifest 的 JSON/HJSON schema 使用 snake_case；其中 ToolPkg API 的 `api_version` 必须单独于包自身版本维护。

## 顶层字段

### `schema_version: number`

Manifest schema 标识，解析模型缺省为 `1`。这是 manifest 数据格式字段，不等于 ToolPkg API 版本，也不等于归档 `formatVer`。当前文档只确认仓库解析模型默认值为 1；不要把 schema 版本的存在解释为宿主支持任意更高值。

### `toolpkg_id: string`

必填且去空白后不得为空。它是容器包的稳定 ID，也是依赖声明、Hook 所属上下文、资源解析与 UI 路由命名的重要身份字段。改 ID 会影响引用该包 ID 的配置和依赖关系。

### `version: string`

包自身发布版本，解析模型缺省为空字符串。它不决定 ToolPkg API 能力；依赖范围中的 `min_version`/`max_version` 比较的是目标包的此字段。

### `api_version: string`

宿主 ToolPkg API 版本，字段缺省为 `1.0.0`。解析器要求严格的 `major.minor.patch` 且当前 Operit 支持该版本，否则拒绝加载。当前支持矩阵和各成员最低版本见[API 版本与兼容](../09_compatibility/api_versions.md)。

### `requires: ToolPkgManifestRequirement[]`

声明启用本包前必须加载的其他包。它描述包依赖和版本范围，不是 JavaScript API。依赖 ID、描述和版本边界按[包格式完整说明](./package_format.md)定义；`min_version`/`max_version` 约束依赖包 `version`，不是 `api_version`。

### `main: string`

主入口相对 manifest 所在目录的路径。必填，解析出的归档路径必须存在并可读取。入口模块必须导出 `registerToolPkg()`。

### `display_name: LocalizedText`

包显示名。可为单个字符串或语言代码到字符串的映射；缺省为空文本。

### `description: LocalizedText`

包说明文本，支持同一 `LocalizedText` 形状；缺省为空文本。

### `logo: string`

可选资源 key，指向 `resources[]` 中声明的图片资源。解析模型只把字符串 `JsonPrimitive` 作为 logo key；Logo 文件类型和展示要求见[包格式完整说明](./package_format.md)。

### `author: string | string[]`

作者信息。解析器接受单个字符串或字符串列表，内部统一表示为列表；缺省为空列表。

### `enabled_by_default: boolean`

启用默认值，解析模型缺省为 `true`。用户/包管理器的当前启用状态仍由宿主配置决定。

## 子包与资源

### `subpackages: Array<{ id: string; entry: string }>`

默认空数组。每项声明一个普通脚本工具子包：

- `id`：子包 ID，去空白后不得为空。
- `entry`：相对 manifest 的脚本入口，必须存在并能解析为普通 JS 工具包。

单个无效子包会记录对应包加载错误并跳过；当 manifest 声明了子包但没有任何子包成功加载时，容器包解析失败。

### `resources: Array<{ key: string; path: string; mime?: string }>`

默认空数组。资源项：

- `key`：ToolPkg 内部稳定资源 key，供 `ToolPkg.readResource(key)` 使用。
- `path`：相对 manifest 的归档路径；必须指向存在的文件或目录内容。
- `mime?`：资源 MIME 类型，缺省为空字符串；目录资源的 MIME 会影响资源导出处理。

同一资源 key 的约束、路径规范化、目录打包、Logo 和大文件建议见[包格式完整说明](./package_format.md)。

## WASM 模块

### `wasm_modules: Array<ToolPkgManifestWasmModule>`

默认空数组。模块字段：

- `id`、`path`：模块标识和归档相对路径。
- `exports?`：允许调用的导出名列表，缺省为空列表。
- `source_language?`：源语言标识，默认 `assemblyscript`。
- `abi?`：调用 ABI，默认 `assemblyscript`。

目前 JS 侧 `ToolPkg.wasm.call()` 的数值类型和序列化见[注册 API](../03_runtime/registry.md)；支持的 ABI/验证条件见[包格式完整说明](./package_format.md)。

## 工作流与工作区模板

### `workflow_templates: ToolPkgManifestWorkflowTemplate[]`

默认空数组。每项通过 `id`、本地化展示信息和 `resource_key` 引用资源中的工作流模板；完整导入行为见[包格式完整说明](./package_format.md)。

### `workspace_templates: ToolPkgManifestWorkspaceTemplate[]`

默认空数组。每项通过 `id`、本地化展示信息、`resource_key` 和项目类型关联工作区模板；完整字段及资源目录行为见[包格式完整说明](./package_format.md)。

## 最小示例

```json
{
  "schema_version": 1,
  "toolpkg_id": "com.example.hello",
  "version": "0.1.0",
  "api_version": "1.0.0",
  "main": "dist/main.js",
  "display_name": {
    "zh": "问候插件",
    "en": "Hello ToolPkg"
  },
  "description": "演示 ToolPkg manifest。",
  "subpackages": [],
  "resources": []
}
```

## 权威实现

- `ToolPkgParser.kt`：manifest 数据模型、入口路径、资源/子包和 API 版本解析。
- `ToolPkgManifestRequirement.kt`：`requires` 版本约束。
- `ToolPkgMainRegistrationScriptParser.kt`：主入口注册定义解析。

相关页面：[包结构与入口](./package_structure.md)、[包格式完整说明](./package_format.md)、[API 版本规则](../09_compatibility/api_versions.md)。