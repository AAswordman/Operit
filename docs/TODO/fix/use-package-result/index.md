---
fork: https://github.com/3316891527/Operit
status: implemented
---

# use_package 激活失败状态

原工具包装器把 usePackage 返回的所有文字一律包装为 success=true，即使内容为 Package not found。本次让激活分支产生结构化结果；不存在时返回 success=false、PACKAGE_NOT_FOUND 及包名，普通外部包、外置内置包、Skill 和 MCP 共用失败协议。停用、加载失败、缺少必填环境变量、隐藏 Skill、MCP 连接失败也明确失败，成功内容继续为原字符串。

usePackage 和 useMCPServer 的字符串方法签名保持，工具调用使用结构化结果，不通过匹配提示文字推断状态。不会把失败包注册为已激活。

回归包含任意来源的不存在名称、所有错误代码、成功文本含有错误字样但仍为成功、提示与错误一致，以及结构化错误 JSON 的类型与包名。未在本地执行构建或测试，派发和 CI 结果分别记录。

[DONE] 实现与回归源码完成，发行集成和验证状态另存交付收据。
