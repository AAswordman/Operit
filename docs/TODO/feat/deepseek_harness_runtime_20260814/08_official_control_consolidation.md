---
title: DeepSeek Harness official control consolidation
status: in_progress
---

# DeepSeek Harness 官方控制能力整合

## 原状

- 工作区示例使用 `@deepseek-ai/dsh@latest`，验证 pnpm 生命周期构建和
  `node-pty` 原生模块，但没有 AI 可调用的服务控制入口。
- 官方协作实现 `com.operit.sidebar_dsh` 提供状态查询、启动、重启和停止服务的
  ToolPkg 子包，以及让安装后台任务脱离终端会话的实现。
- 社区成员 `zjxdzh` 的投稿与本示例使用同一运行包 ID。为尊重其独立投稿的版权，
  本次整合不纳入其源码；发布说明会感谢其及时跟进 DeepSeek Harness 的社区探索。

## 修改

- 在 `sidebar_deepseek_harness` 中加入服务控制子包：查询状态、启动、重启、停止。
- 复用现有显式安装结果和 `node-pty` 加载校验；服务启动改为会话脱离的
  `setsid` 后台进程。
- 在更新 DSH 依赖前准备 `build-essential`，避免 `node-pty` 生命周期脚本因缺少
  `make` 而中断。
- 已有 DSH PID 尚在运行时等待其 Web 服务就绪，不在侧边栏重入或普通重试时
  中断进程并重新安装。
- 保持 DSH 凭据和 profile 配置仅由 DSH 原生 Web UI 管理，不向 ToolPkg 工具返回
  `.credentials.yaml`、`.env` 或其他凭据原文。
- 将示例版本升级为 `0.2.1`，生成可由发布者自行上传的 `.toolpkg`。

## 验收标准

- 控制子包能返回实际的 Web 服务健康状态，并可启动、重启和停止运行时。
- 冷启动或显式重启时更新 DSH 到 `@latest`，并在 pnpm、依赖构建或 `node-pty` 加载失败时返回
  明确诊断。
- 打出的归档包含 manifest、主入口、控制子包、共享运行时和 Compose DSL 页面。
- [DONE] 已添加无凭据访问的控制子包，并完成 `pnpm exec tsc -p
  examples/sidebar_deepseek_harness/tsconfig.json`。
- [DONE] 已生成 `build/deliverables/sidebar_deepseek_harness-0.2.1.toolpkg`，校验
  manifest、`dist/main.js` 和 `dist/packages/deepseek_harness_control.js` 均在归档内。
  SHA-256 为 `BF4373FAF28C314CA3E73F83D3911D45E559F2026C27D5C9884318383B47E2E4`。
