# 权限档位与审批调用

## 旧实现

ToolPermissionSystem 的 PermissionLevel 仅有 ALLOW、ASK、FORBID 三个值，checkToolPermission 按覆盖值或全局开关三分支返回。

## 修正意图

新增 LLM 档位，并把审批模型作为独立权限 reviewer，而不是普通的单次文本分类调用：

- 审批策略使用独立 SYSTEM 消息，父会话、工具名、真实参数、路径及文件或网页内容只作为 USER 层的不可信证据；
- 审批请求包含近期父会话、当前助手内容、工作区及最终规范化动作，用于分别判断风险与用户授权；
- reviewer 必须返回带 review_id 的完整结构化结果，包括 decision、risk_level、user_authorization 与 reason；
- host 对 critical 风险、无授权动作及未明确授权的 high 风险执行强制策略，不能只依赖模型给出的 approve；
- 审核前先绑定 host 注入的 package context，审核与执行复用同一份最终参数；
- 权限检查直接返回包含拒绝来源与原因的结构化结果，不再通过 singleton 全局变量传递拒绝理由；
- 同一消息执行上下文共享动作指纹和连续拒绝计数；任一次审批通过都会清零连续拒绝计数；
- 连续两次拒绝后锁定本消息执行上下文的全部后续工具调用，但把拒绝结果回传主模型，由主模型停止调用工具并请求用户在新消息中书面授权。

## 新实现结果

LLM 档位支持 approve、deny、ask 三种决定。接口异常、严格 JSON 解析失败、review_id 不匹配或字段缺失/非法时转交用户确认；ask 同样转人工。自动拒绝会约束主 Agent 不得通过改写、拆分、编码、换工具或换路径绕过。fromString 对旧存储值保持原样解析，未知值仍回到 ASK。

普通工具、package_proxy 与 CLI proxy 均审核最终执行动作；审批结果和拒绝理由按单次调用结构化传递，并发聊天之间不会共享可变拒绝理由。

[DONE]
