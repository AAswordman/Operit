# Claude 历史与响应边界

旧实现把 Claude 的所有工具调用名称直接放进待配对队列。`package_proxy` 的执行结果由
工具执行层改写为具体包工具名，因此严格名称匹配时会留下未完成调用。

修复后，队列保存 `StructuredToolCallBridge.toolCallName` 返回的实际执行名称。该解析同时
覆盖 OpenAI `arguments`、Gemini `args` 和 Claude `input`，工具结果仍然必须精确命中对应调用。

Claude 的流式和非流式解析也只在本次请求启用思考时转发 thinking block；关闭思考时直接忽略
服务端误返回的思考块，避免内部推理进入用户消息。

变更完成：[DONE]
