---
status: done
For_Agent: 服务端工具状态是流式旁路事件，不进入模型正文或聊天历史
---

# 服务端工具状态展示

## 原实现问题

服务端搜索状态使用 `WEB_SEARCH_STARTED` 与 `WEB_SEARCH_COMPLETED` 专用事件。该命名把 UI 状态通道绑定到一种工具，后续增加其他服务端工具时需要重复扩展事件和消费逻辑。

## 修正意图

- 使用 `SERVER_TOOL_STARTED` 与 `SERVER_TOOL_COMPLETED` 表达通用服务端工具生命周期
- 事件携带 `toolType`，由 UI 状态层决定本地化文案
- `web_search` 显示“正在搜索...”，其他服务端工具显示工具类型
- 完成最后一个活动服务端工具后恢复“正在接收回复”
- 状态只显示在既有输入处理指示器和悬浮窗状态区，不写入 assistant 正文或聊天历史

## 当前协议映射

DeepSeek Responses 的 `response.web_search_call.in_progress`、`searching` 与 `completed` 映射到通用服务端工具事件，`toolType` 固定为 `web_search`。客户端函数工具继续使用原有工具执行状态，不混入服务端工具事件。

## 验证范围

- 工程中不再引用搜索专用流事件枚举
- 服务端工具开始事件携带 `toolType`
- Web Search 使用本地化搜索文案
- 未识别工具类型使用通用服务端工具文案
- 完成事件恢复当前响应阶段对应的接收状态
- 静态差异、资源占位符和 XML 解析检查

## 完成记录

- 流式旁路事件已改为通用服务端工具开始与完成事件，并携带 `toolType`
- DeepSeek Web Search 已映射为 `toolType=web_search`，输入处理指示器显示“正在搜索...”
- 其他服务端工具类型使用通用本地化文案展示
- 并行 Web Search 保持首个调用开始显示、全部调用完成后恢复接收状态
- Classic、Agent 与悬浮窗继续复用既有 `InputProcessingState.Receiving` 展示状态
- 已增加服务端工具事件数据契约测试代码
- 按工程执行准则，本轮未运行 Gradle 编译、构建或测试

[DONE]
