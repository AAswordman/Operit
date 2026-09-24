# 08 插入总结保留 previousSummary

## 旧实现情况

插入总结的窗口装载用 loadMessagesAfterLatestSummaryInRange，起点是最近一次总结的时间戳，终点是用户长按的那条消息；ChatViewModel 又把结果过滤成只剩 user 与 ai。自动总结走 getRuntimeChatHistory，传的是含 summary 消息的运行时快照。

AIMessageManager.summarizeMemory 从传入列表里取最后一条 summary 作为 previousSummary，再取其后的消息作为待总结消息。因此两条路径虽然素材相同，模型侧却不一致：自动总结有上一次摘要可融合，任务描述完整；插入总结的列表里没有任何 summary 消息，previousSummary 恒为 null，模型收到的总结任务只剩 system prompt 末尾那段自定义规则，于是把规则原样复述，总结正文消失。

## 意图修正

插入总结与自动总结是同一件事的两种触发方式，必须给 summarizeMemory 同一份输入构造。summary 消息不是待总结内容，而是 previousSummary 的来源，装载窗口必须保留它。

## 期待的新实现情况

新增 loadRuntimeChatMessagesForSummaryInsertion，返回长按位置之前的全部消息，保留 summary 消息，不做总结锚点裁切，由 summarizeMemory 自己按 lastSummaryIndex 取 previousSummary 并截取待总结消息。ChatViewModel 不再过滤 sender，预检改为窗口内存在 user 或 ai 消息，把完整窗口交给 insertSummaryAtMessage。

失去调用方的 loadMessagesAfterLatestSummaryInRange、loadMessagesForSummaryInsertion 与 MessageDao.getLatestSummaryTimestampBefore 一并删除，不留死代码。

## 细化作用域

- 修改 data/repository/ChatHistoryManager.kt，新增 summarizeMemory 输入构造所需的窗口装载方法
- 修改 services/core/ChatHistoryDelegate.kt 的转发包装，删除旧包装
- 修改 ui/features/chat/viewmodel/ChatViewModel.kt 的插入总结装载与预检
- 删除 data/dao/MessageDao.kt 中失去引用的查询
- 不动 summarizeMemory、summarizeHistory、insertSummaryAtMessage 的逻辑与签名
- 插入位置语义不变：长按 ai 消息总结落在其前，长按 user 消息落在其后

## 调试注释要求

在新装载方法处注释 summary 消息为何必须保留，以及过滤掉它为何会让模型只剩自定义规则可复述。

## 实际落地记录

- loadRuntimeChatMessagesForSummaryInsertion 用 getMessagesForChatInRangeAsc 加载范围全部消息，afterTimestampExclusive 恒为 null，保留区间内所有 summary 消息
- ChatViewModel 装载改用新方法并删除 sender 过滤，预检改为 none { user || ai } 判定，summaryWindow 原样传入
- loadMessagesForSummaryInsertion 与 loadMessagesAfterLatestSummaryInRange 删除，MessageDao.getLatestSummaryTimestampBefore 随之删除
- GetDiagnostics 对四个改动文件零告警，:app:compileDebugKotlin 通过

[DONE]
