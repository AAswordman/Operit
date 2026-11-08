---
fork: https://github.com/tuxKOH/Operit
branch: token-statistics-long
---

# Token 统计 Long 迁移

## 原本状况

模型响应中的 usage、回合快照、消息和会话持久化、归档以及统计展示并未统一使用同一种数值类型。累计统计已部分使用 `Long`，但单轮计数、供应商解析和持久化字段仍使用 `Int`，大值在进入累计统计前可能发生截断或溢出。

## 修改意图

让所有 Token 统计计数从供应商响应解析到界面展示统一使用 `Long`。统计范围包括输入、输出、缓存输入和当前上下文窗口计数，不包括鉴权凭据、分词器内部索引或模型参数配置。

## 期待结果

- 供应商 usage 能以 `Long` 读取输入、输出和缓存 Token。
- 运行期的单轮与累计 Token 统计保持 `Long`。
- Room 实体、聊天归档、偏好统计和 HTTP 桥接模型不再以 `Int` 表示 Token 统计。
- 现有数据库值和 JSON 数字载荷可继续读取，统计页正确显示大值。

## 作用域

- AI provider 的 usage 解析与 Token 计数接口
- 回合统计、消息处理和会话历史保存
- Room 实体、归档模型、偏好累计和 HTTP/ToolPkg 桥接
- 聊天与设置页的统计状态和展示

## 验证记录

- 静态检索覆盖 37 个 Kotlin 源码与测试文件。输入、输出、缓存输入、当前窗口和单次统计字段不存在 `Int` 声明。
- 供应商 usage 已使用 `optLong`。剩余的 4 个 token 相关 `optInt` 仅用于模型 `max_tokens` 或本地模型容量配置，不是使用量统计。
- 已移除 Token 统计在运行期、保存回调和 ToolPkg 解析处的 `Int.MAX_VALUE` 截断。
- 依照仓库本地工作约束，本次不执行构建或测试命令。
