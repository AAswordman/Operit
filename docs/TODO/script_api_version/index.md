---
title: 脚本包 API 版本与市场展示
status: completed
repository: https://github.com/AAswordman/Operit
---

# 脚本包 API 版本与市场展示

独立脚本原先没有宿主 API 版本，发布和市场展示仅处理 ToolPkg。

沿用脚本 METADATA 的 `api_version` 和市场具体版本的 `apiVersion`，缺失声明按 `1.0.0` 解释。保留旧发布请求和响应结构，OP1 对不支持的显式声明阻止安装。

作用域包括 OP1 脚本解析、本地导入、发布、列表和版本详情，OP2 的脚本 API 展示，以及市场后端校验和网页版本对应下载。现有市场目录和协议路径保持不变。

实施细节见 [1_implementation.md](1_implementation.md)。
