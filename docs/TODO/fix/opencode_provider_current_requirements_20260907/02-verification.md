# 验证记录

本次按仓库执行准则不运行构建、Gradle 或测试命令。

静态检查项目：

- OpenCode 模型列表 URL 包含 `/v1/models`。
- Zen 与 Go 的 MiniMax 协议按端点区分。
- OpenCode 专用请求头只在 OpenCode 路由中注入。
- 公共 provider 没有新增 OpenCode 条件分支。

静态检查已完成：`git diff --check` 无错误。未执行构建、Gradle 或测试命令。
