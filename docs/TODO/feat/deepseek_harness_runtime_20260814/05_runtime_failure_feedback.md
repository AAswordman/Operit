---
title: DeepSeek Harness 运行时失败反馈
status: completed
---

# DeepSeek Harness 运行时失败反馈

## 原状

DeepSeek Harness 的安装命令仅依赖终端 API 返回的 `exitCode`。在交互式 PTY 中，内层
`bash -lc` 即使以错误状态结束，终端 API 仍可能报告外层会话正常完成。插件因此把安装
失败当成成功，继续启动 Web 并等待 `127.0.0.1:3081`，用户只能看到连接拒绝。

安装失败结果也没有把 `installOutput` 传给 UI，错误文本最多显示三行，真实原因不可见。

## 修改

- 安装脚本打印运行时生成的成功标记，只有收到该标记才允许启动 DSH Web。
- 缺少 Node、缺少 pnpm、安装 DSH 失败、加载 node-pty 失败等路径都打印明确错误标记。
- 将具体安装错误作为结果诊断传给仪表盘。
- 失败时清空已缓存的 Web URL，并扩大错误信息的显示范围。

## 验收标准

- Debug Linux 容器缺少 Node 时，页面直接显示 `Node.js is required in the Linux runtime.`。
- 未接收到安装成功标记时，不执行 `dsh web`，也不等待 3081 端口。
- Retry 失败后不保留先前 WebView 内容。

## 完成记录

- [DONE] 将安装完成判定由终端 API 的 `exitCode` 改为显式成功标记，避免交互式 PTY 掩盖内层 shell 的失败状态。
- [DONE] 将安装失败标记解析为界面诊断，并同步到 ToolPkg 实际加载的 `dist` 产物。
- [DONE] 补充运行时失败行为说明；本次仅做静态检查，未执行构建或测试。
