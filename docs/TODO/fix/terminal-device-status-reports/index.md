---
status: implemented-pending-validation
fork: https://github.com/3316891527/Operit
---

# 终端状态与光标位置应答

GitHub CLI 的确认输入会通过 Survey 查询终端尺寸和光标位置。TerminalCore 没有回复 CSI 6 n，程序可能停在读取答案之前并保持光标隐藏。此问题属于终端协议兼容性缺陷，影响依赖相同查询的程序；普通输入提示不一定使用这些协议。

本次在独立 TerminalCore 分支实现标准状态报告与光标位置报告，由主仓库更新 terminal 子模块引用。保留现有会话生命周期修复，协议应答写回原会话输入队列。

- [实现、接入与验证](01-protocol-replies.md)
- 协议实现与回归源码 [DONE]
- 主仓库接入与远程验证记录分别确认
