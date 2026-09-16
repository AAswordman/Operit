---
fork: https://github.com/yoruuuchan/Operit.git
issue: https://github.com/AAswordman/Operit/issues/1180
---

# Executor 生命周期修复 [DONE]

## 全生命周期 deadline

`TerminalManager` 从环境初始化前开始计时，并把剩余预算传给 provider。manager 使用自身的
长生命周期 IO scope 承载可能不响应取消的 native 工作；调用方只 await 结果，到 deadline 后
立即取消该工作并返回明确 `TIMEOUT`，不会为了 join 阻塞 IO 而继续挂起。外部 cancellation
保留为 cancellation，并有 key、阶段和 timeout 日志。

## 本地 executor 状态机

- deadline 覆盖 provider 可用性、全局创建 mutex、进程启动、READY、同 key 排队、
  stdin write/flush 和结果 marker 读取。
- 进程启动与 pipe write 在 provider scope 中执行；调用 deadline 不需要等待不可中断 IO。
  启动完成时若调用已经取消，未发布的进程会立即销毁。
- 每个 shell 有原子 closed 状态、reader job、exit watcher 和命令 mutex。shell 退出时由 watcher
  主动关闭结果 channel，即使后代仍持有 stdout，调用也会得到 `PROCESS_EXITED`。
- active 调用超时、取消、reader 异常、marker 异常或进程退出时，shell 会先从 key map 原子移除，
  再终止命令进程组和 shell。销毁进程先于关闭 buffered writer，避免 close 再次阻塞 flush。
- 仅仅在 mutex 中排队的调用超时，不会关闭另一个 active owner。若 owner 关闭旧 shell，仍有预算的
  排队调用会重新解析同一 key、创建健康 shell 并继续执行。
- 启动失败不会把半初始化 executor 发布到 map；相同或不同 key 的下一次调用均可重新创建。

## SSH 路径

SSH exec channel 的打开、connect、stdout/stderr drain 和完成检查共享同一剩余预算；失败、超时
与 cancellation 都在 `finally` 中关闭本次 channel。`executorKey`、结果字段和调用接口保持不变。

## 可诊断结果

成功、启动失败、执行错误、进程退出、timeout 与 cancellation 都有明确状态或异常；日志包含
`executorKey`、命令 token、当前阶段、timeout、最终 state 和 exit code。
