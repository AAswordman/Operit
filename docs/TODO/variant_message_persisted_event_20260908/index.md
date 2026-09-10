---
fork: local workspace
branch: fix/variant-message-persisted-event
status: in-progress
---

# 变体消息持久化事件修复

## 原本状况

PR #1122 为重新生成的消息变体补发 `message_persisted` 事件，但事件 payload 由生成中的消息对象构造。消息变体重新加载时会继承基础消息的显示状态；因此 payload 可能与数据库最终消息不一致。PR 的测试只直接调用桥接器，未覆盖变体落库路径。

## 修改意图

合并 PR #1122，并使变体落库事件使用最终持久化的消息属性。补充覆盖 `addMessageVariant` 的回归测试，验证事件的消息标识、变体索引、收藏状态和显示状态。

## 期待结果

- 每次成功新增变体只派发一次 `message_persisted`
- 事件 payload 与重新加载后的选中变体一致
- 外部同步可用时间戳和变体索引准确定位消息

## 作用域

- PR #1122
- `ChatHistoryDelegate.addMessageVariant`
- ToolPkg 消息持久化事件测试

## 进度

- [DONE] PR 审查与缺陷定位
- [DONE] 合并 PR #1122
- [DONE] 以持久化消息构造变体事件
- [DONE] 补充变体落库路径测试
- [DONE] 静态检查并合并回 `dev`

## 验证记录

- 已检查所有 `addMessageVariant` 调用方适配持久化结果类型
- 已覆盖变体实体转换与事件派发：基础消息的时间戳、显示状态、收藏状态必须保留，生成消息的内容与变体索引必须更新
- 依照仓库工作约束，本次未执行构建或测试命令
