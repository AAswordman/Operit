---
repo: https://github.com/3316891527/Operit
branch: feat/phone-agent-local-execution
base: e9bb154a631300ec6fd7d9a073b50d58792aca38
status: launch-resume-swipe-implemented-validation-pending
---

# PhoneAgent 控件感知与本地连续执行

现有直接 UI 工具支持无障碍页面树，PhoneAgent 则逐轮截图、等待 UI_CONTROLLER 完整输出并执行一个动作。固定等待和重复模型请求使简单的搜索、填写流程产生明显停顿。

继续使用用户配置的 UI_CONTROLLER 模型及现有工具参数。在主屏无障碍能力明确可用时，把同一显示区的节点快照提供给控制器；控制器可以选择短序列，本地逐步检查目标和结果。无障碍提供者现有 AIDL 没有 display 参数，不能把主屏节点用于后台虚拟屏。后台和没有无障碍授权的会话保持明确的图像观察模式。

## 实施顺序

- [01：节点观察与等待](01-observation.md)
- [02：受检查的连续操作与复用](02-sequences.md)
- [03：耗时记录与验证](03-validation.md)
- [04：抖音首页停滞与 Android 参数解析修复](04-device-regressions.md)
- [05：可见操作反馈与通用控制器命名](05-visible-feedback.md)
- [06：输入进程生命周期与坐标证据](06-input-lifetime.md)
- [07：通用节点摘要与操作后快照交接](07-compact-node-handoff.md)
- [08：启动观察分离、点击派发与反馈动画](08-startup-input-feedback.md)

## 边界

不修改外部无障碍提供者协议，不增加权限或切换用户选择的模型，不复制外部仓库实现，不扩大原缓存与 BLE PR。序列不包含跨页旧坐标，不自动重复已送达的动作，不把超时当作动作未发生。已有 do()/finish() 单步指令继续可用。

运行效率以同一设备、同一模型、同一任务的端到端耗时及成功率为准。减少的固定等待或模型轮数不等于已经取得真机提速数据。本轮未执行本地编译、构建或测试。

初版测试包已在设备上暴露参数解析崩溃、首页停滞及截图服务长时间阻塞。修复源码现包含 46 项 JVM 和 3 项 Android 回归用例；本次增量的静态检查、远程 CI 和修复后设备复测分别记录，尚无真机提速数据。


通用节点整合的源码实现与静态审阅已完成，详见第 07 项。当前主屏无障碍会话默认发送紧凑节点摘要，Inspect 与 Observe 显式切换观察方式；纯启动任务确认前台后直接结束。第 07 项的新增 JVM 用例尚未运行，尚无对应构建及真机验证结果。


- [09 能力对齐与观察成本](09-contract-and-observation-cost.md)：第一阶段与第三阶段实现、计时口径和显式设备基准。
- [10：前台页面继续与真实滑动时序](10-launch-resume-and-swipe.md)：整合 PR #155，保留接管后的前台页面并修复滑动时序与失败反馈。
