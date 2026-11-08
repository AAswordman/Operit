---
repo: https://github.com/3316891527/Operit
branch: fix/toolpkg-webview-focus
status: IMPLEMENTED_PENDING_VALIDATION
---

# 语音条 WebView 挂载焦点修复

反馈来自其他用户，附件为 voice_bar 0.5.5 toolpkg、崩溃报告与应用日志：AI 调用工具后在助手回复中渲染语音条时弹出 ComposeRuntimeError，错误为 pending composition has not been applied。栈包含 BoxWithConstraints 子组合、forceMeasureTheSubtree 与 LazyListMeasuredItemProvider。

插件通过 XML 渲染钩子返回 Compose DSL WebView 播放器。宿主 renderWebViewNode 的挂载效果无条件 post requestFocus，导致语音条刚显示就从聊天输入框抢焦点，并可能使 Android/Compose 焦点搜索进入惰性列表的额外子组合测量。

修复移除宿主挂载时的无条件焦点请求，保留网页输入、点击、显式聚焦、加载和销毁行为。范围为现有 DSL WebView 生命周期及针对性的 Android 界面回归，不修改附件中的插件、服务端合成配置或音频文件。

原始现场的完整焦点调用尾栈未保留，重启日志没有崩溃前事件。本次针对挂载不会抢焦点这一触发路径补充固定回归，不能将源码定位或工作流受理写成原始设备崩溃已复现通过。

- [实现与证据](1-implementation.md)
- [完整回归步骤与独立结果表](verification.md)
