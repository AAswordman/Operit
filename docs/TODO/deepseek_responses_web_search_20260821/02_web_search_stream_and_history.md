# 搜索状态与历史协议

## 请求

DeepSeek Responses 且搜索开关开启时，在现有函数工具之外声明 `{"type":"web_search"}`。关闭搜索时不声明该工具，也不改变函数工具。

## 流式状态

`response.web_search_call.in_progress`、`searching` 和 `completed` 转换成通用服务端工具流事件，并携带 `toolType=web_search`。开始事件驱动当前输入处理状态显示“正在搜索...”，完成事件恢复为接收回复；这些事件不写入最终可见正文。

## 历史

服务端返回的 `web_search_call` 输出项以隐藏元数据保存在 assistant 消息中。下一轮 Responses 请求恢复为原始 input item；普通展示、复制和非 Responses 请求移除该元数据。

## 终止事件

Responses 流同时处理 `response.completed`、`response.incomplete` 和 `response.failed`。其中 incomplete 是服务端已确认的终止状态，不按网络中断处理。

[DONE]
