---
repo: https://github.com/3316891527/Operit
status: IMPLEMENTED_CI_PENDING
---

# Gemini 空 Part 与空请求

## 问题与来源

Gemini 原生端点要求每条 Content 至少有一个 Part。空文本无法初始化 Part 的 data 成员，空 parts 的消息也会导致整轮请求 400。纯思考的历史助手消息、失效媒体链接和空白系统提示均可能产生这种请求，重复发送相同内容不能解决问题。

本次引入上游 [PR #1428](https://github.com/AAswordman/Operit/pull/1428)，对应 [Issue #1413](https://github.com/AAswordman/Operit/issues/1413)，来源为 [f8ef319](https://github.com/AAswordman/Operit/commit/f8ef319bc693d2797cc6199fd904ef7e777edb63)。

## 实施范围

1. 纯文本只有非空白正文才生成 Part，写入的正文保持原文，不修剪有效文本。
2. 所有 Content 写入点统一检查 parts，空系统消息不产生 systemInstruction。
3. 没有任何可发送 Content 时抛 EmptyContentsException，按已有错误处理入口直接上抛，不进行无意义重试。
4. 增加八个 locale 的本地错误文案；保留工具调用和结果配对、思考保留选项、签名与 token 计数流程。
5. 引入 13 个序列化回归用例，适配个人版本 data/config/model、api/chat/keypool 包以及现有七参数请求构建方法；新增注释使用中文。

## 行为边界

仅含失效媒体链接且没有正文的消息会被跳过，包括最新用户消息；只要还有其他有效历史，剩余历史仍可能发出。这是本次引入保留的行为，需要调用方区分媒体失效与空内容。全空请求在本地报错，不发送到端点。

普通非空正文、有效图文和工具 Part 保持既有语义，没有数据库或持久化格式变化。宿主的思考档位与 API Key 池保持现有接口。

## 验证

本地仅执行差异、仓库卫生、XML 和文档链接静态检查。13 个功能用例交由工作分支 Android Tests 与 PR Candidate checks 执行；不把上游作者的本地记录当成本分支通过证据。Android 实机与真实 Gemini 端点验证尚未执行。

完整覆盖与待填写结果见[回归清单](regression.md)。

[DONE]
