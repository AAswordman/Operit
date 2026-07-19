---
title: 步骤 3：工作流与构建分层
status: completed
---

# 步骤 3：工作流与构建分层

## 旧实现

一个翻译文件会触发完整 Android 构建。PR 构建还继承 secret、初始化已经删除的 submodule、调用迁移前的 ToolPkg 路径，并在失败时上传构建产物。

## 新实现

PR 只产生一个 `Candidate checks` job，并按作用域顺序执行：

1. 候选契约、门禁单元测试和快速检查
2. 翻译资源的 AAPT2 syntax compile
3. WebChat 和 ToolPkg 专项检查
4. Kotlin/Java 改动的 JVM test 与 Android lint
5. native、Gradle 和构建输入改动的完整 assemble、JVM test 与 lint

快速检查失败后不会启动耗时阶段。PR workflow 只有 `contents: read` 权限，不读取 secret，不上传 APK/AAB。可信的 main 和手工 Android 构建保留独立 workflow。

## 验收

- PR 页面最多出现一个技术红灯
- 纯翻译改动不启动完整 assemble
- fork PR 日志和产物中不存在仓库 secret
- Android 构建只初始化当前存在且实际需要的公共 submodule
- ToolPkg 使用 `tools/sync_example_packages.py`
- WASM ToolPkg 按独立 lockfile 执行 AssemblyScript、TypeScript 和归档构建
- ToolPkg manifest 声明的入口和 WASM 文件必须存在、非空并实际写入归档
- GitHub TypeScript 示例必须重建且与提交的 `examples/github.js` 一致
- YAML 使用 AST 语法解析和 actionlint，合法标量不会触发对象反序列化误报
- 可信 Android 构建按事件隔离并发，并在 Gradle 前强制校验 OAuth 输入
- Gradle Wrapper 下载内容必须匹配 Gradle 8.13 官方 SHA-256

[DONE]
