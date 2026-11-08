# 升级有告警的依赖

## 旧实现

web-chat 使用 Vite 5.4.21 与 esbuild 0.21.5。MCP bridge 使用 uuid 9.x。

## 修正

- 将 Vite 更新到 6.4.3 线，使用其修复后的开发服务器和 esbuild 依赖链
- 将 uuid 更新到至少 11.1.1

## 预期

保留现有 Vite React 插件和 MCP bridge 的 UUID 调用方式，同时移除 Dependabot 告警。

## 实现结果

- Vite 更新到 6.4.3，锁定 esbuild 0.25.12
- MCP bridge 没有 UUID 调用，移除了 uuid 与 @types/uuid 依赖
- web-chat 的官方 npm 锁文件审计结果为零漏洞

[DONE]
