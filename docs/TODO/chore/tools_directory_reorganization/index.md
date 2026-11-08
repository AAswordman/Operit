---
feature: tools_directory_reorganization
scope: assistance
---

# Tools 目录重整

## 现状

`tools/` 根目录同时放置 ToolPkg 调试、ADB 脚本执行、示例包同步、Shower 辅助脚本、Compose DSL 调试与 native-ripgrep 构建入口，职责相近的文件彼此分散。

## 意图

按工具职责将根目录入口迁入已有或新增子目录，并更新脚本相对路径、代码、测试与开发文档。旧入口不保留。

## 预期结果

- `tools/` 根目录只保留目录与固定的公开安装脚本
- 每个工具入口与其配置、状态文件和说明位于同一职责目录
- 仓库内所有调用使用新路径
- 不增加旧路径转发脚本

## 步骤

1. [分类布局与入口迁移](./1_CategoryLayoutAndEntrypoints.md)
