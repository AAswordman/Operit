---
fork: https://github.com/3316891527/Operit
branch: feat/chat-continue-generation
status: verified
---
# 手动继续生成

参考 [上游 PR #1223](https://github.com/AAswordman/Operit/pull/1223)，将 USER、CONTINUE 与 AUTO_CONTINUE 三种发送模式接入当前 Android 聊天链路。

附件面板在“当前位置”后增加“继续生成”。当前聊天存在有效历史且未生成、未总结时可用。继续生成只读取当前会话历史，不新增用户气泡、不读取或清空草稿和附件、不修改会话标题。正常发送、自动总结后的续写保持各自原有行为，请求配置按会话保存。

改动覆盖消息协调与处理、模型请求、PromptTurn 处理、ViewModel、Agent 与 Classic 输入界面和九种语言文案。迁移上游已有 JVM 回归，由仓库工作流执行；不在本地编译或运行测试。

当前分支的发送流程已协程化，手动续写沿用现有错误处理入口，角色与历史读取在 IO 中完成，读取后再次检查聊天和生成状态。保留 A2UI 的编辑器消费控制。

[DONE]

## 本轮验收结论

2026-10-20，用户确认本轮测试全部通过；统一验收提交为 `1576d70ae`，构建与 JVM 回归均已通过。完整来源与发布准备见 [统一验收记录](../../chore/chat-experience-release/index.md)。本地没有重复执行编译、构建或测试。
