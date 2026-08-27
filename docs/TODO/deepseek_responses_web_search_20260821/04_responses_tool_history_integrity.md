---
status: done
For_Agent: 保持 DeepSeek 无状态历史中的函数调用、结果和可见消息边界完整
---

# Responses 工具历史完整性

## 直接现象

DeepSeek Responses 在包含客户端函数工具的多轮历史上返回 `400 No tool output found for tool call`。客户端又把确定性的 400 重试五次，最终错误被描述为连接超时。

设备请求日志显示，assistant 的可见文本位于 `function_call` 与 `function_call_output` 之间。并行工具结果按完成时间写入消息时，原实现还会按位置把结果绑定到调用，导致调用名与结果内容错配。

## 修正意图

- Responses input 中先写 assistant 可见消息，再写该轮全部 `function_call`，使后续结果不被消息边界隔开
- 将服务端 `call_id` 写入内部工具标记，并由工具执行结果原样带回
- 构建下一轮历史时按 `call_id` 绑定并行结果；已发布历史没有该字段时维持原有位置绑定
- 确定性的 4xx 请求校验错误立即结束，不重复发送同一个无效请求；408 与 429 继续使用既有重试和多密钥轮询链路

## 验证

- 两个并行调用的 Responses item 顺序与 ID 对应测试
- 工具调用和结果标记的 ID 往返测试
- 旧工具结果标记不含 ID 时的兼容测试
- Kotlin 编译和 debug 打包

## 完成记录

- 设备请求日志确认旧顺序为 `function_call`、assistant message、`function_call_output`，服务端在该边界返回 400。
- Responses 转换已调整为 assistant message、该轮全部 `function_call`、对应 `function_call_output`。
- 新工具调用的 `call_id` 已贯穿 XML 标记、并行执行与结果标记；旧标记继续按位置读取。
- `NonRetriableException` 按状态码分类：确定性 4xx 不再进入重试循环，408 与 429 保持可重试。
- 已增加 408、429 与确定性 4xx 的状态分类回归测试代码；本轮未运行 Gradle 测试或构建。
- 针对性协议测试与完整 `:app:testDebugUnitTest` 通过。
- `:app:compileDebugKotlin` 与 `assembleDebug` 通过。

以上通过记录产生于本次 HTTP 状态码分类调整之前。

[DONE]
