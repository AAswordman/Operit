# 分发模型与宿主迁移

## 旧实现

- `ThemePackageBuiltInReferenceV1` 在 Kotlin 中构造 `operit.reference@1.0.0` manifest
- `ThemePackageReferenceV1.BuiltIn` 用没有归档摘要的特殊选择记录表示默认主题
- `ThemePackageBundledSamplesV1` 从 APK assets 导入 `cyber-grid.otheme`，并阻止卸载
- 主题页为 BuiltIn 分支单独渲染默认主题和参数编辑区

## 新模型

默认主题的包 ID 为 `operit.default`。它是一个真实、已验证的 `.otheme` release artifact，APK 将其作为 `theme-packages/operit-default.otheme` 交付。应用代码固定该 release 的包 ID、版本和 SHA-256，并将它安装到普通内容寻址目录。默认主题不允许卸载。

全局选择只允许带精确坐标的 `ThemePackageReferenceV1`。DataStore schema 升级时无条件重置为默认主题固定坐标，删除未发布阶段的 `builtin` 选择编码。

赛博主题不出现在主仓 assets、启动任务、卸载限制或测试中。它作为常规已安装包通过主题页导入，用户可以在未激活时卸载。

## 完成条件

- 主应用不再包含 `ThemePackageReferenceV1.BuiltIn`、`ThemePackageBuiltInReferenceV1`、`ThemePackageBundledSamplesV1`、`cyber-grid.otheme` 或 `cyber-grid-source/`
- 默认主题 manifest 不在 Kotlin 中构造
- 默认主题可被同一归档校验器验证、安装、选中和渲染
- 主应用源码只保留默认主题 release artifact 与固定的 release lock 信息
- 主题页把默认主题显示为内置且不可卸载，其余导入包均按照普通安装包处理

[DONE] 宿主已删除旧选择分支和赛博 bundled 路径；默认归档锁定为 `operit.default@1.0.2`。
