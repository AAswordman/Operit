---
For_Agent: ToolPkg 发布期 AST 压缩改造记录
---

# ToolPkg AST 压缩

## 现状

ToolPkg 和独立脚本以普通文件形式发布、下载和导入。

## 意图

- 发布时可选择使用 AST minifier 压缩可执行 JavaScript
- 保留 METADATA 块、外部调用接口和 export 名
- ToolPkg 的 manifest 与资源文件保持原始内容
- 压缩后的 ToolPkg 继续作为普通 ZIP 使用，不改变市场下载或外部导入

## 作用域

- `ToolPkgArtifactMinifier` 发布期压缩器
- `ToolPkgJsAstMinifier` AST minifier
- 插件市场发布开关与说明文案

## 完成标准

- 脚本和 ToolPkg 可执行 JavaScript 条目会在发布时压缩
- 压缩产物可以由现有市场路径、文件选择器和调试安装直接加载

[DONE]
