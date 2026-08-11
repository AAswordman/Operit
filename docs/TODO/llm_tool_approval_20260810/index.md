---
title: 工具权限智能审批（LLM 审批档位）
fork: https://github.com/Aizosa/Operit
branch: feat/llm-auto-approval-permission
status: complete
---

# 工具权限智能审批

## 当前状况

工具权限只有三档：允许、询问、禁止。询问档每次都要用户手动确认，允许档则完全放行。对于既不想每次点确认、又不放心完全放行的工具，缺少一个中间档位。

## 修改意图

在 PermissionLevel 中新增 LLM 档位：由一个可配置的审批模型阅读工具名称与参数后自动决定允许或拒绝。模型无法给出明确决定、接口出错或输出无法解析时，转交现有的询问弹窗流程由用户确认，不改变任何已有档位的行为。

## 预期结果

- 权限设置的全局开关与按工具分组出现第四档「智能审批」，存储沿用 tool_permission_<name> 键，旧值不受影响
- 功能模型配置中新增「工具审批」功能类型，用户可为审批绑定独立模型，默认使用默认配置
- 审批策略通过独立 SYSTEM 消息下发；USER 消息只承载父会话、工作区与最终规范化工具动作等不可信证据
- 审批模型返回带 review_id 的严格结构化结果：decision、risk_level、user_authorization 与 reason；字段缺失、类型非法或解析失败时转人工
- host 强制拒绝 critical 风险、无用户授权及未明确授权的 high 风险动作，不能由 reviewer 的 approve 覆盖
- 审核前绑定 host 注入的 package context，审核与执行复用同一份最终参数
- 同一消息执行上下文共享动作指纹与拒绝计数；重复已拒动作或连续拒绝达到阈值时打开 circuit breaker 并中断本模型回合
- 审批接口异常或输出无法解析时转交询问弹窗，超时行为与询问档一致

## 步骤

1. [权限档位与审批调用](./1_PermissionLevelAndJudge.md) [DONE]
2. [功能模型与设置界面](./2_FunctionTypeAndSettingsUi.md) [DONE]
