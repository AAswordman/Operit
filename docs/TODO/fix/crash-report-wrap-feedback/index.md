---
fork: https://github.com/3316891527/Operit
branch: fix/crash-report-wrap-feedback
status: 已实现，待仓库工作流验证
---
# 崩溃报告自动换行开关反馈

报告页展开堆栈后的自动换行控件由轨道和圆点自绘，外层可点击行使用 Compose 默认 indication，按压时会出现整行灰色涟漪。

在 CrashReportStackPanel 的该行显式设置交互源并关闭 indication，继续使用 toggleable 的点击处理、Switch 角色和状态语义。轨道位置与配色继续显示启用状态，堆栈自动换行及横向滚动行为保持原有实现。实现完成 [DONE]。

验证包括 Kotlin 临时副本语法解析和差异检查；不执行本地 Android 编译、构建或测试，Debug 构建与 JVM 测试交由仓库工作流。设备按压视觉效果尚未实测。
