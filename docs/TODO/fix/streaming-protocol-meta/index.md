---
repo: https://github.com/3316891527/Operit
status: IMPLEMENTED_PENDING_VALIDATION
---

# 流式协议元数据隐藏

流式 Markdown/XML 解析可能仍在处理前一个未闭合节点，导致后续协议 meta 被当作该节点的正文展示。完整消息按 sections 绘制时跳过 Protocol，出现流式露出、结束后消失的不一致。

在聊天显示流进入 Markdown 解析前处理已知协议 meta，覆盖分片、长负载及未闭合外层节点。已闭合的代码示例保留，未闭合代码中的协议负载在结束时继续隐藏；静态正文采用同一显示规则。原始流、存档、请求历史和保存点继续保留完整协议内容，回滚重新投影显示前缀。

范围：纯 Kotlin 显示投影、聊天共享显示流入口、静态正文显示入口、对应回归与文档。使用独立修复分支，以 dev 为 PR 目标；验证交由仓库 Debug 和完整 JVM 工作流，本地不构建或运行测试。

实施及完整回归见[实施记录](1-implementation.md)和[回归记录](verification.md)。
