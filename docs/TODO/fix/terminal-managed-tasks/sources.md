# 终端代码参考与适配来源

参考版本为 CATMIAOZHI/Operit 的 `b7a179c5abc68323ad34d0b1b03536f5a9a63687` 及其实际引用的终端核心 `5e4c6a0bcae786184faa231f0c59c379b5c8c5ba`。个人应用基线 `559633f959da872e82e22eaa6235c9cf4604c700` 固定终端核心 `aad54161c1b5e10ae9c5e393735119e01dd8e40a`，本轮沿该版本适配，保留宿主初始化、UTF-8 连续解码、真实退出码、命令完成标记、规范模式交互判断、输入顺序与环境维护。

## 应用参考

- [0009633：订阅握手和调用取消](https://github.com/CATMIAOZHI/Operit/commit/00096337fdfaf746027c69f47b9feb7de65e45cb)：先建立订阅再提交，避免快速完成事件丢失；收集器结束按具体命令 ID 清理
- [daac831：托管任务生命周期](https://github.com/CATMIAOZHI/Operit/commit/daac8315713efc9906a0495aa8ea6a081fa4eeb5) 与 [3e8337e：任务接口收敛](https://github.com/CATMIAOZHI/Operit/commit/3e8337ec30fbf38d5dc05acfbb251ebe170d78b6)：借鉴独立任务作用域、ID 查询和单次等待分离；在个人项目新增兼容入口，保留旧 exec 结果

## 核心参考

- [10d3541：关闭时结束所有等待命令](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/10d3541f0fbb2003246d772c11535f88b6f06335)：关闭、读取故障和主进程退出统一通知运行及排队任务
- [63fd584：初始化原子写入](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/63fd58471e4978d44e8ad0fc9728d535afbc39ac) 与 [67f03e6：初始化清理](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/67f03e63ba44927baf2f7a4fe306f29332bc9890)：同目录临时文件原子替换，启动取消后清理晚发布的进程
- [cd5d53c：按命令 ID 取消](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/cd5d53cb2c99b7913d01c7919e1d93bb55ca76c4)：取消排队项与中断运行项分别处理，取消期间暂停队列推进
- [0c238c9：独立退出监控](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/0c238c9bc8bbf581688af721da75be8bde2ce3c4)：主进程退出后限制输出排空时间，后代持有文件描述符不再永久阻止收尾
- [362a726：PTY 进程身份](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/362a726631060732a8bd1c5dec673180eb5ac2c9) 与 [afb8b35：任务清理及 waitpid](https://github.com/CATMIAOZHI/OperitTerminalCore/commit/afb8b35780a17a4c4a9da8499f1ef229e11d69ef)：核对 PID 启动时间，识别僵尸状态，缓存回收结果；JNI 仅处理 EINTR、状态初始化和返回 PID 校验

## 个人实现边界

核心沿现有输出解析器和进程封装增量修改，没有整体覆盖参考分支。每个会话使用独立输入消费者，阻塞写入不占生命周期锁；任务注册表、兼容入口及结果类型按重构后的目录编写。JNI 工具链保持 CMake 3.22.1。

本轮没有卡死当时的线程栈或完整生命周期日志，因此不能把用户经历的某一次卡住断言归因于某一提交。SSH 取消仍使用远程终端与连接关闭语义，本地 `/proc` 身份清理只适用于本地 PTY；脱离原 SID 的进程不属于同会话清理保证。
