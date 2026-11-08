# 异常退出会话回收

## 旧实现

`TerminalManager.handleTerminalSessionExit` 只向 `OutputProcessor` 写入退出消息，没有从 `SessionManager` 删除已退出会话。由于工具层按会话名称查找已有会话，后续调用会得到同一个失效 Session ID。

## 修改意图

完成退出命令事件后调用 `SessionManager.closeSession`，让状态表、读写资源、provider 会话和输出处理状态一起释放。下一次 `super_admin:terminal` 使用原会话名创建时，会进入正常的新会话创建流程。

## 预期结果

退出命令仍能收到终端退出消息；旧 Session 不再被复用；后续同名终端调用获得新的可用 Session。现有工具名称、参数和 AIDL 接口保持不变。
