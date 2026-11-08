---
status: in_progress
---

# 2. 新目录与文档契约

## 旧实现

原 `package-dev/` 以 API 模块文件平铺，ToolPkg 格式说明、开发教程、类型定义和 Hook 文档入口彼此分散。部分高级声明没有对应的独立说明；旧文件还存在固定 URL 和交叉链接引用。新资料已集中到 `docs/doc-src/toolpkg_developer_documentation/`。

## 意图修正

新资料集中到 `docs/doc-src/toolpkg_developer_documentation/`，目录按主题拆分。总索引先标清阅读路径、权威声明和版本规则。每个模块页面采用一致的逐方法结构，不把单独一行的签名当作完整说明。

每个方法条目应按适用情况包含：

- 符号全名、签名、所属命名空间、`@since` 与最低 Operit 版本
- 调用前置条件、必要 capability/权限、执行上下文和线程/异步性质
- 参数逐字段说明，包含类型、可空性、默认值、边界、枚举、序列化形状与相互约束
- 返回值逐字段说明、成功/失败判定、异常或错误对象
- 可观察副作用、持久化、取消、超时和重入行为
- 最小可运行示例、典型错误示例和相关 API 链接

Hook 条目额外描述注册时机、宿主触发点、Hook 之间的次序、输入快照/可变性、返回值合并策略、阻断/替换语义、异常传播、超时和取消信号。

## 期待目录

```text
toolpkg_developer_documentation/
	index.md
	01_getting_started/
	02_package_model/
	03_runtime/
	04_modules/
	05_hooks/
	06_ui_and_compose/
	07_types_and_libraries/
	08_examples/
	09_compatibility/
```

目录名和页面最终按实际 API 数量调整，避免为了分类制造空文件夹。

## 完成情况

- [ ] 创建新文档目录和逐主题索引。
- [ ] 提供逐方法、逐字段和逐 Hook 的统一说明模板。
- [ ] 为所有公开声明建立页面映射；缺失内容不能以泛化“见类型定义”代替。
- [x] 旧文档继续保留，后续只记录引用迁移/删除候选，不在本 PR 清理。