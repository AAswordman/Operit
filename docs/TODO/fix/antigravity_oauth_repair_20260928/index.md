---
topic: Antigravity OAuth repair, remove Vertex AI login, and fingerprint spoofing
status: done
fork_repository: https://github.com/3316891527/Operit.git
working_branch: feat/antigravity-oauth
pr_url: https://github.com/AAswordman/Operit/pull/1257
---

# Antigravity OAuth 修复、移除 Vertex AI 登录与指纹伪装

## 背景

PR #1257 引入了 Antigravity 与 Vertex AI 的 OAuth 登录，但实际真机测试（见日志）暴露以下关键问题：
- Antigravity OAuth 请求的 Scope 缺少 `https://www.googleapis.com/auth/cloud-platform`，导致 Google `cloudcode-pa.googleapis.com` 网关将配额查询及所有对话推理接口拦截并返回 HTTP 403 `ACCESS_TOKEN_SCOPE_INSUFFICIENT`。
- 登录时获取 `projectId` 失败被静默忽略，并使用邮箱 SHA-1 生成伪造的本地 UUID 兜底，违反了禁止回退和兜底的规范，且未接入真实账号的 `onboardUser` 激活流程。
- 缺少原生 Antigravity 客户端的指纹伪装（包含 HTTP/1.1 连接规范、User-Agent、会话标识 `request.sessionId`、`.requestType`、`.requestId` 等）。
- 用户指示彻底移除本次 PR 中引入的 Vertex AI 登录代码，仅保留 Antigravity。

## 意图

- 彻底清除 PR #1257 中新增的 Vertex AI 登录相关文件与配置，恢复相关通用类结构。
- 补充 Antigravity 所需的 `cloud-platform` OAuth Scope，修复 UserInfo 接口版本。
- 接入真实的 GCP 项目发现与新账号 `onboardUser` 激活轮询，彻底删除本地伪造 UUID 兜底。
- 对齐 CLIProxyAPI 实现全套指纹伪装（HTTP/1.1、User-Agent、清理多余请求头、信封参数对齐）。
- 修复配额查询参数（带上 `project`）与非流式响应外层解包。

## 步骤

- [01_remove_vertex_ai_login.md](./01_remove_vertex_ai_login.md) [DONE] 清理 Vertex AI 登录相关全部代码与资源
- [02_antigravity_oauth_and_fingerprint_repair.md](./02_antigravity_oauth_and_fingerprint_repair.md) [DONE] 修复 Antigravity OAuth Scope、项目发现/激活流程、请求指纹伪装与配额查询
