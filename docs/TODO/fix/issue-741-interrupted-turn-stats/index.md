---
fork: https://github.com/luojiaping/Operit
issue: https://github.com/AAswordman/Operit/issues/741
branch: fix/issue-741-interrupted-turn-stats
---

# 中断回合统计修复

## 原本状况

取消模型输出前，消息处理层已经读取当前 Token 与耗时快照。取消任务结束后，收尾逻辑会重新从 Room 加载消息，并通过只存在于内存中的 `contentStream` 查找流式消息。Room 不保存该字段，因此查找无法命中，部分回复、Token、耗时和完成时间都不会写回。

## 修改意图

由每个会话的运行态对象持有当前可持久化的流式 AI 消息。取消操作在终止任务前取得该消息引用，任务停止后直接完成并持久化同一条消息，不再从持久化模型推断运行态身份。

## 期待结果

- 用户中断普通聊天输出后，已生成内容继续保留。
- 消息信息中的输入 Token、输出 Token、等待耗时和输出耗时反映中断时的快照。
- Waifu 模式下已经形成的分段消息获得相同的回合统计。
- 正常完成、非持久化回合和单条重新生成的现有行为不变。

## 作用域

- `MessageProcessingDelegate` 的每会话运行态消息所有权
- 中断回合消息收尾与运行态清理
- Web 删除会话与丢弃式取消的执行顺序
- 针对消息完成规则的单元测试

## 验证记录

- 已添加 `MessageProcessingDelegateTest`，覆盖中断消息载荷中的部分内容、Token、耗时、发送时间、完成时间和流引用清理。
- 依照仓库本地工作约束，本次未执行构建或测试命令。
