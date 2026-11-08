---
fork: https://github.com/3316891527/Operit
branch: fix/package-proxy-json-diagnostics
status: 已实现，待仓库工作流验证
---

# 包工具 JSON 参数诊断

原 package_proxy 和 proxy 在 params 解析失败时仅返回通用错误，模型难以区分缺引号、括号和对象类型错误。本次在 core/tools/parameters 中集中生成英文原因、行列及偏移，缺少引号时提示字符串起点与转义方法，并明确目标工具尚未执行。

实际解析继续使用 Android 原有 JSONObject，不自动补引号或改写终端命令。诊断只提取结构、坐标与已知原因，不回显命令或解析器附带的完整参数。

新增七项 JVM 回归，覆盖缺引号、悬空转义、括号错配、字符串内括号、顶层类型、Android/JVM 两种坐标，以及合法命令内容。未执行本地编译或测试；仓库工作流结果另记。

实现完成 [DONE]。
