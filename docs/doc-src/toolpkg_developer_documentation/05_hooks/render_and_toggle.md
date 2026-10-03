---
title: XML 渲染与输入菜单 Hook
status: draft
---

# XML 渲染与输入菜单 Hook

本页说明 `registerXmlRenderPlugin()` 和 `registerInputMenuTogglePlugin()`。两者都由 `ToolPkgCommonBridgePlugin` 汇总已启用包的注册项，但返回值消费和缓存行为不同。

## XML 渲染插件

### 注册

```ts
ToolPkg.registerXmlRenderPlugin({
  id: string,
  tag: string,
  function: (event: ToolPkg.XmlRenderHookEvent) => ToolPkg.XmlRenderHookReturn
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- `id`：包内插件 ID。
- `tag`：插件声明负责的 XML tag。宿主注册时去除首尾空格并转为小写；空 tag 不会加入可匹配列表。
- `function`：必须能映射为已导出的 ToolPkg 函数。

### 事件

事件类型为 `xml_render`，payload 为：

- `xmlContent?`：当前 tag 对应的 XML 文本。
- `tagName?`：当前请求的 tag 名。

只有注册 tag 匹配的 Hook 会执行。每个 tag 下按容器加载顺序、包名和插件 ID 排序；宿主依次尝试，直到某个结果被消费。

### 返回值与消费规则

可以返回字符串、`null`、`void`、Promise，或 `XmlRenderHookObjectResult`。

- 非空字符串：等价于 `{ handled: true, text: value }`。
- 对象未提供 `handled` 时按 `true`；`handled: false` 时继续下一个插件。
- `text` 非空时优先作为渲染文本；否则尝试 `content`。
- `text` 与 `content` 都为空时，该结果不会生成文本响应，继续尝试后续插件。
- `composeDsl` 有效时优先返回 Compose DSL screen，而不是文本。

`composeDsl` 字段：

- `screen`：必填非空 screen 路径；空路径使该 composeDsl 结果无效。
- `state?`、`memo?`：对象映射，其他类型被规范化为空对象。
- `moduleSpec?`：对象映射；空对象会被省略。

Hook 执行、返回解析失败只记日志并继续下一个匹配 tag 的 Hook。第一个成功返回文本或 DSL screen 的插件结束该 tag 的处理。

## 输入菜单开关

### 注册

```ts
ToolPkg.registerInputMenuTogglePlugin({
  id: string,
  function: (event: ToolPkg.InputMenuToggleHookEvent) => ToolPkg.InputMenuToggleHookReturn
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- `id`：包内 Hook ID。
- 返回值可为 `InputMenuToggleDefinitionResult[]` 或 `{ toggles: [...] }`。

### `create` 事件与定义验证

宿主为当前 `runtime` 和 `chatId` 请求开关列表时发送 `action: 'create'`。payload 还含 `chatId?` 和 `runtime?`。

单个 toggle 的字段：

- `id`、`title`：必填且去空白后不得为空；否则该项被丢弃。
- `description?`：可选说明。
- `icon?`：可选图标名。
- `isChecked?`：默认值；如果当前宿主 feature state 有同 ID 状态，则该状态覆盖此默认值。
- `slot?`：`thinking`、`memory`、`model`、`tools`、`general` 或 `default`。

所有 ToolPkg toggle Hook 依容器加载顺序串行执行，合法项目合并进同一菜单。Hook 返回的其他字段不会传入菜单定义。

### `toggle` 事件与状态刷新

用户点击一个非宿主内建 feature toggle 时，宿主向其所属 Hook 发送：

```ts
{
  action: 'toggle',
  toggleId: string,
  chatId?: string,
  runtime?: string
}
```

该回调返回值被忽略；Hook 完成后宿主重新请求 `create` 列表刷新菜单。若 `toggleId` 已存在于宿主 `featureStates`，点击将直接调用宿主 feature-state handler，不会进入 ToolPkg 的 `toggle` 回调。

### 缓存与首次加载

菜单定义按 `runtime|chatId` 缓存，并在 Hook 注册表变化时失效。异步刷新期间若还没有旧结果，宿主先显示禁用的 loading toggle；读取失败记录日志并缓存空列表。UI 刷新通过 toggle registry 的 changed 通知完成。

## 权威实现

- `plugins/toolpkg/ToolPkgCommonBridgePlugin.kt`：tag 匹配、渲染结果消费、输入 toggle 缓存和 create/toggle 调度。
- `core/tools/packTool/ToolPkgParser.kt`、`ToolPkgMainRegistrationScriptParser.kt`：注册定义解析。
- `examples/types/toolpkg.d.ts`：公开 payload、定义和返回类型。

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[Compose DSL](../06_ui_and_compose/compose_dsl.md)、[Hook 总索引](./index.md)。