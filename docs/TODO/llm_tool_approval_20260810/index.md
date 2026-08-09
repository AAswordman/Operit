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
- 审批提示词随 FunctionalPrompts 统一维护，提供中英双语，不对用户开放编辑；decision 为必填，approve 放行、deny 拒绝、ask 表示模型无法自主判断转人工
- 审批模型看到的是工具调用原文 <tool name=""><param name=""></param></tool>，无需再拼装参数结构
- 审批接口异常或输出无法解析时转交询问弹窗，超时行为与询问档一致

## 步骤

1. [权限档位与审批调用](./1_PermissionLevelAndJudge.md) [DONE]
2. [功能模型与设置界面](./2_FunctionTypeAndSettingsUi.md) [DONE]
