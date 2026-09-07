# 共享 JS runtime

## 目标

ToolPkg 注册桥和 `Tools` facade 共享同一个 JS 版本比较与版本变体选择工具。

## 预期行为

- runtime 提供 `namespace(publicName, members)` 和 `method().since(version, implementation)`
- `namespace` 用属性键构造公开 API 名称，方法在调用时检查当前 manifest API 版本并选择实现
- API facade 只保留自己的参数校验和自己的版本变体
- 新增同名 API 多版本实现时，在该 API 的本地链式调用追加 `.since(version, implementation)`
