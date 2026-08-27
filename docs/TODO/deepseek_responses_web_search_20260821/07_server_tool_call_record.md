---
status: done
For_Agent: 服务端工具记录只从供应商 metadata 派生，不得转换成客户端 tool 或 tool_result
---

# 服务端工具调用记录

## 原实现问题

Responses Web Search 执行期间只显示“正在搜索...”。调用完成后，`web_search_call` 仅作为隐藏 metadata 保存，用户无法在消息记录中确认模型是否调用过服务端工具。

普通 `<tool>` 记录会进入客户端工具解析、授权和执行流程，因此不能用它模拟服务端工具。服务端搜索也不提供与函数工具等价的 `function_call_output`，最终答案和引用属于 assistant 消息。

## 修正意图

- 保留原始 `openai:responses_web_search` metadata，继续用于下一轮 Responses 历史恢复
- 渲染时严格解码并校验 `web_search_call`，生成只读的服务端工具记录
- 主标题直接显示具体工具名，使用前置云端图标标识调用来源
- 标题展示协议工具标识 `web_search`，摘要展示搜索动作和状态，详情展示服务端返回的完整调用条目
- 不生成 `<tool>`、`AITool`、`ToolInvocation` 或 `<tool_result>`
- metadata 格式不合法时保持隐藏，不把内部协议内容暴露给用户

## 输出边界

默认 `web_search_call` 能提供调用 ID、状态和 action。搜索 action 通常包含 query 或 queries；最终回答中的引用仍由 assistant 文本承载。只有请求显式包含 `web_search_call.action.sources` 时，服务端调用条目才会包含完整来源列表，本步骤不改变请求字段。

## 完成记录

- Android 消息渲染已使用云端图标加具体工具名展示只读服务端调用
- Web Chat 消息渲染已使用相同规则展示只读服务端调用，点击记录行可通过 `ContentDetailDialog` 查看完整 raw JSON
- 修复运行时发现的协议差异：DeepSeek 批量搜索使用 `action.queries`，Web Chat 现已严格校验并汇总 `query` 与 `queries`
- 原始 metadata、复制隔离和 Responses 历史恢复路径保持不变
- 已增加 metadata 到服务端工具记录的解析测试代码
- Web Chat 类型检查与生产构建通过；当前子项目未配置测试脚本

[DONE]
