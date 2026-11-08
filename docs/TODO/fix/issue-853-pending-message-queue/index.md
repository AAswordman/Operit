---
fork: https://github.com/AAswordman/Operit.git
issue: https://github.com/AAswordman/Operit/issues/853
---

# Issue 853 待发送消息队列

## 现状

待发送消息列表、列表展开状态和自动发送控制状态全部由 `AIChatScreen` 以
`remember(currentChatId)` 保存。选择其他会话会销毁原会话对应的组合状态，返回后
界面只能获得新的空队列，用户已提交到队列的文本因此丢失。

长会话切换不再使用全量历史加载。`ChatHistoryDelegate` 在 IO 调度器中按时间倒序读取
最近展示页，最多保留两页；`ChatArea` 使用惰性列表渲染当前展示窗口。该路径不需要为
本 Issue 另行替换加载策略。

## 目标

- 待发送消息按会话 ID 归属到 `ChatViewModel`，切换会话和界面重组不得清空它
- 自动发送和用户手动发送始终使用队列所属的会话 ID
- 保持已有的队列编辑、删除、取消当前回复后发送和自动发送行为

## 作用域

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/`
- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/screens/AIChatScreen.kt`
- `app/src/test/java/com/ai/assistance/operit/ui/features/chat/viewmodel/`
- 本目录中的实施记录

## PR

待创建

## 完成状态

- 队列状态归属与原会话投递已完成
- 长会话切换路径已完成审计，未修改既有分页策略
