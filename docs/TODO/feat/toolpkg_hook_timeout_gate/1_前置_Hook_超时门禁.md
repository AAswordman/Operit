# 前置 Hook 超时门禁

## 原有行为

- ToolPkg 聊天输入、Prompt 和摘要 Hook 在各自 bridge 内顺序执行
- 每次脚本调用使用 QuickJS 的主执行超时，默认 1,800 秒
- 多个 Hook 会累积等待时间

## 实现意图

- 在显示与行为中提供 1 至 60 秒的设置，默认 10 秒
- 每次 bridge 分发创建一个总预算，链内每个 Hook 仅可使用剩余时间
- 到期中断 QuickJS，跳过超时 Hook 的返回值，并让宿主继续使用当前上下文
- 保持 `message_processing` 回复接管链独立，不接入该门禁

## 变更结果

- `JsEngine` 接受精确的毫秒超时并在等待到期时中断 QuickJS
- 聊天输入、Prompt、摘要 bridge 都会传递同一分发链的剩余预算
- 日志记录阶段、包名、Hook ID 与耗时，便于定位卡住的插件
- 用户主动提交消息时，聊天输入 Hook 超时会通过聊天内自定义 Toast 提示一次；Prompt 与摘要阶段仍只记录日志

[DONE]
