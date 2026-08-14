# 03 Delivery

## 原状

`sync_example_packages.py` 会编译 `examples/` 内含 `manifest.json` 的 TypeScript ToolPkg 并生成 `.toolpkg`。

## 修改

- 添加示例 manifest、TypeScript 配置与运行时文件。
- 让 Runtime 目录和网络监听规则在 package 描述中明确可见。

## 预期结果

示例可进入既有 ToolPkg 打包链路，且没有改动 OpenCode 容器。

