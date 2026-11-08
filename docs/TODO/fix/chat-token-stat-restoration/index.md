---
feature: chat-token-stat-restoration
fork: https://github.com/3316891527/Operit
branch: fix/chat-token-stat-restoration
base: dev
---

# 会话累计 Token 统计恢复

旧会话的累计统计依赖侧边栏列表 Flow 已经到达。冷启动时若消息先加载，统计恢复可能被跳过；随后普通保存或刷新上下文会把初始零值写入会话元数据，使长期累计从头开始。

本次修复直接按会话 ID 恢复统计，区分尚未加载与真实零值，首轮累加前恢复基数，并将窗口更新与累计保存分开。普通累计快照在内存和数据库中均保持计数不减小。

作用域为聊天历史、运行时 Token 统计和 DAO 更新方法。数据库结构、消息存储格式和界面文案保持兼容。现有明确清空消息的数据库操作继续使用独立的元数据更新。

实施和回归范围见 [统计恢复与保存](./01-statistics-restoration.md)。Android 构建和 JVM 回归由工作分支的仓库工作流执行，运行结果单独记录。

[DONE]
