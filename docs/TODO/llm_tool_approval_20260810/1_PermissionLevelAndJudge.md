# 权限档位与审批调用

## 旧实现

ToolPermissionSystem 的 PermissionLevel 仅有 ALLOW、ASK、FORBID 三个值，checkToolPermission 按 覆盖值或全局开关 三分支返回。

## 修正意图

新增 LLM 档位。checkToolPermission 命中该档位时调用 requestLlmApproval：取 FunctionalPrompts 中按应用语言选择的审批提示词，在末尾附上本次工具调用原文，经 FunctionType.TOOL_APPROVAL 绑定的模型发起一次非流式请求，从输出中提取首个 JSON 对象并读取 decision 字段。

## 新实现结果

decision 为必填字段：approve 返回放行，deny 返回拒绝，ask 表示模型无法自主判断；ask、接口异常或输出不符合约定时按产品设计调用 requestPermission 转交用户确认。fromString 对旧存储值保持原样解析，未知值仍回到 ASK。

[DONE]
