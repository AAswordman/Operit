---
title: DeepSeek Harness node-pty 模块解析
status: in_progress
---

# DeepSeek Harness node-pty 模块解析

## 原状

`node-pty` 的本地 `node-gyp` 编译已成功完成，但加载检查将
`node_modules/.pnpm/.../node-pty` 直接传给 Node 的 `require()`。该值不以 `./` 或
`/` 开头，Node 将它当作包名而不是文件路径，导致 `MODULE_NOT_FOUND`。

## 修改

- 将加载检查的参数改为以 `./` 开头的运行时相对路径。
- 保留现有的本地编译、符号链接解引用和加载失败诊断。

## 验收标准

- `gyp info ok` 后，`require()` 能加载编译后的 `node-pty`。
- 不再出现 `Cannot find module 'node_modules/.pnpm/.../node-pty'`。
