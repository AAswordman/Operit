---
fork_repository: local
---

# 终端异常退出后的会话回收

## 背景

可见终端的 PTY 进程异常退出后，退出提示会写入画面，但旧会话仍保留在会话状态表中。同名 `terminal.create` 会继续复用该会话，后续命令无法写入已关闭的 PTY。

## 目标

- 终端退出时结束当前命令的等待
- 立即释放死会话及其 PTY 资源
- 保持现有工具和 AIDL 契约不变
- 下一次同名终端调用自动创建新会话

## 作用域

- `terminal/src/main/java/com/ai/assistance/operit/terminal/TerminalManager.kt`
- `docs/TODO/fix/terminal_session_auto_recovery_20260808/`

## 步骤

1. [完成] 在异常退出处理完成当前命令后移除死会话
2. [完成] 记录同名会话下一次调用时重新创建的行为
3. [完成] 检查工作区变更，未执行构建和测试

[DONE]
