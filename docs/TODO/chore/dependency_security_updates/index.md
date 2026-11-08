---
For_Agent: https://github.com/AAswordman/Operit
---

# 依赖安全更新

## 现状

GitHub Dependabot 在默认分支报告五个开放告警：web-chat 的 Vite 开发依赖链包含三个 Vite 告警和一个 esbuild 告警，MCP bridge 的 uuid 运行时依赖包含一个告警。

## 意图

- 将 web-chat 升级到包含 Vite 与 esbuild 修复的版本
- 将 MCP bridge 的 uuid 升级到具备边界检查修复的版本
- 保持现有构建工具和运行时接口，不引入功能改动

## 作用域

- web-chat/package.json
- web-chat/package-lock.json
- tools/mcp_bridge/package.json

`tools/mcp_bridge/pnpm-lock.yaml` is intentionally ignored by the repository, so it is regenerated locally from package.json and is not part of this change.

## 完成标准

- Vite 锁定版本不再命中四个开发服务器告警
- esbuild 锁定版本不再命中 CORS 告警
- uuid 锁定版本不再命中缓冲区边界告警

[DONE]
