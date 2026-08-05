---
fork: https://github.com/tuxKOH/Operit.git
scope: ToolPkg 聊天输入、Prompt、摘要 Hook 的同步超时门禁与显示设置
---

# ToolPkg Hook 超时门禁

当前同步 ToolPkg Hook 会逐个等待执行完成。单个脚本卡住时，用户消息会停留在输入处理或 Prompt 组装阶段。

本次新增显示与行为设置，默认将一条 Hook 分发链限制为 10 秒。链内多个 Hook 共享同一截止时间，到期时中断正在执行的 QuickJS 调用并保留已有上下文继续处理。

变更范围：

- `DisplayPreferencesManager` 与显示与行为设置页
- `JsEngine`、`PackageManager` 与 ToolPkg facade 的毫秒级超时传递
- 聊天输入、Prompt、摘要 Hook bridge
- ToolPkg 开发文档

详细实现见 [1_前置_Hook_超时门禁.md](1_%E5%89%8D%E7%BD%AE_Hook_%E8%B6%85%E6%97%B6%E9%97%A8%E7%A6%81.md)。
