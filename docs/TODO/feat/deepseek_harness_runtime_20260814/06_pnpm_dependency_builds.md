---
title: DeepSeek Harness pnpm 依赖构建
status: in_progress
---

# DeepSeek Harness pnpm 依赖构建

## 原状

Debug Linux 容器中的 pnpm 拒绝执行 DSH 依赖的生命周期脚本，并报告
`ERR_PNPM_IGNORED_BUILDS`。脚本在 `pnpm add` 返回失败后退出，后续的 `node-pty`
本地重建没有机会执行。安装失败标记只包含概括性文本，界面没有显示 pnpm 原始错误。

## 修改

- 在 DSH 运行时目录写入 `pnpm-workspace.yaml` 的 `allowBuilds` 配置。
- 仅允许日志中列出的 DSH 依赖执行构建脚本。
- 安装 DSH 后执行 `pnpm install --prod`，让先前半安装状态也应用该配置。
- 将失败标记和最后的 pnpm 输出一起作为界面诊断。

## 验收标准

- 不再出现 `ERR_PNPM_IGNORED_BUILDS`。
- `node-pty` 本地重建和加载检查实际执行。
- 仍然只有收到安装成功标记才启动 DSH Web。
