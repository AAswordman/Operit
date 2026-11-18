---
fork: https://github.com/CATMIAOZHI/Operit
target: dev
---

# 消息过程折叠

对长回复，已完成的思考、工具活动和中间助手消息可以折叠，最终答复仍然可见。气泡、Cursor 样式和浮窗共用这套规则。

- 用户可展开过程；定位到隐藏消息时先展开，按会话记住展开状态。
- 生成中的回复保持可读，不改变持久化的聊天内容；设置页可关闭自动折叠。
- 识别范围和显示规则见[过程折叠协议](../../doc-src/feature-protocol/collapsed_response_activity.md)。
