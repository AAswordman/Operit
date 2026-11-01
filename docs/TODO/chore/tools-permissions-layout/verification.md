# 验证记录

本次交付分为权限实现收口和工具目录迁移两部分。目录迁移以 [files.json](files.json) 为依据，当前共 117 个文件，其中迁移 111 个，保留 6 个。

## 静态核对

- 权限收口逐项比较 61 个原操作体及辅助方法，以及 17 个管理员文件操作路由，保留原操作实现和只读标记
- 目录迁移比较 264 份受影响 Kotlin 文件的声明内容，除包名、导入和清单对应的显式路径引用外未发现其他变化
- 核对清单全部目标文件、目标包名、迁移符号导入及工具导入名称冲突
- 共享注册常量的跨包导入同时覆盖普通引用和字符串插值，保留注册校验及错误提示的现有内容
- 静态语法扫描未发现新增问题；最终类型检查与编译结果以仓库工作流为准
- 核对变更 XML、Python 语法、Markdown 本地链接与 Git 差异空白
- 安装、刷新与 UI DSL 调试广播保持原公开组件名，现有 ADB 和编辑器调用仍使用原入口

## 仓库验证

- [Build](../../../../.github/workflows/build.yml)：工作分支执行 `assembleDebug`
- [Android Tests](../../../../.github/workflows/android-tests.yml)：工作分支执行 Android JVM 单测
- PR 的 Candidate checks 根据实际差异选择技术检查

本地未执行编译、构建或测试。工作流派发受理与最终运行结果分别记录在 PR 中，派发成功不代表构建或测试已经通过。

## 对应回归范围

- `PermissionCapabilitiesTest` 覆盖三档用户模式与 16 种授权状态组合，共 48 组能力选择，以及显式显示器和 Shell 身份选择
- `PermissionOperationRouterTest` 与 `PermissionCapabilitySessionTest` 覆盖原有操作路由及会话能力缓存
- `AndroidPermissionModeMigrationTest` 覆盖旧偏好转换为管理员，以及 Root 和其他偏好的保留
- 插件包、内置工具和 JS 测试随目录迁移同步包名、引用和资源路径，具体运行结果以仓库工作流为准