# JS API 声明语法糖

## 目标

版本声明写在 JS facade 的属性旁。Kotlin 不生成公开 API 描述符，也不维护 tool 名称、registration 名称或公开 API 名称的对应关系。

## 预期行为

- 调用点使用 `ToolPkgApi.namespace("Tools.Chat", { call: ToolPkgApi.method().since("1.0.1", implementation) })` 这类链式声明
- runtime 用 namespace 和属性键自动确定公开 API 名称
- 版本限制在公开 API 调用时检查，Kotlin 只验证 manifest 的 `api_version` 是否为宿主支持的版本
- 不维护 tool 名称、registration 名称到公开 API 的等价集中映射
