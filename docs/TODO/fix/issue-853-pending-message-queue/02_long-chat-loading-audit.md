---
fork: https://github.com/AAswordman/Operit.git
issue: https://github.com/AAswordman/Operit/issues/853
---

# 长会话加载审计

## 检查结果

会话选择调用 `ChatHistoryDelegate.loadChatMessages`。该方法经 `collectNewestDisplayPages`
按时间倒序分批读取数据库，并仅将最近一至两页放入界面状态。每一页按五条用户或摘要消息
划分，界面消息列表采用惰性渲染。

## 结论

Issue 中的第二个现象与队列丢失没有共享的状态根因，也不存在此路径上的全量历史加载。
本次不以未经复现的猜测替换现有分页策略。若后续仍有卡顿，应结合具体会话的消息大小、
数据库查询耗时和渲染 trace 另行定位。

[DONE]
