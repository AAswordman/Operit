# 01 Parser And Market Decision

## 旧实现

`MCPLocalServer.mergeConfigFromJson` 先反序列化为只有 `command` 的 `ServerConfig`，没有命令的远程项被删除。`MCPRepository.checkConfigNeedsPhysicalInstallation` 又单独解析 JSON 并将缺失 `command` 的项判定为需要物理安装。

## 修正

增加无 Android 依赖的结构化解析器。每个服务器项按明确字段分类：

- `command` 表示 stdio，命令必须是非空字符串。
- `type=streamable_http` 要求非空 `url`，转换为 `connectionType=httpStream`。
- `type=sse` 要求非空 `url`，转换为 `connectionType=sse`。
- 其他传输类型、混合字段和错误字段直接报告输入错误。

解析器同时保留 args、env、disabled、autoApprove 和 headers 的类型校验，市场安装决策只消费解析结果，不再复制判断逻辑。

[DONE]
