---
repo: https://github.com/3316891527/Operit
branch: fix/upstream-a2a-tts
status: implemented-awaiting-validation
---

# TTS 正文核验与 A2A 协议同步

## 本轮范围

先核验 TTS 问题，确认缺陷后修正现有入口；同步上游 #1466 的 A2A 文本 Part、流式输出和订阅交接修复。保留本地目录重构、语音队列、分句与播放时序。终端实现及依赖不变。

模型服务生命周期下一轮在现有 #147 继续完善。ANR、网页历史和裸网址识别保留在后续同步清单，本轮不混入。

## TTS 核验依据

核对了当前代码，而非以 issue 描述作为缺陷结论：

- 修改前的消息朗读按钮将运行时 `message.content` 传入 `ChatViewModel.speakMessage`；数据库与备份实际保存 `sections`，不是该字符串
- 主聊天和悬浮语音的消息播放先调用 `TtsCleaner.clean` 执行用户正则，再调用 `WaifuMessageProcessor.cleanContentForWaifu` 提取纯正文
- 设置界面的“去除 XML 标签”模板实际写入 `<[^>]+>`；默认正则只有中英文括号过滤，不包含该模板
- 因而只有启用这类先移除标签的规则、且原始消息含非正文块时，标签会先消失，块内文本留下；之后的正文识别无法恢复块边界

例如，原始消息包含思考块和答复，先运行 XML 标签过滤会把二者拼成普通文本，之后清洗不会再识别思考块。工具结果、状态和协议元数据同样受这一处理顺序影响。这是配置相关的真实缺陷，不声明默认配置或所有朗读必现，也不声明已完成真机复现。

修复仅将非正文块提取放在用户正则之前；Markdown 标记继续留给用户规则匹配，之后沿用原来的 Markdown 展平。自由输入的语音合成测试及普通正则清洗接口保持原语义。

## A2A 范围

- 根据 Part 内容成员识别文本，允许合法附带字段，拒绝混合内容成员
- 状态事件遵循 1.0 schema，移除协议之外的 final，用任务终态结束 SSE
- 终态前发布 append=false 的最终正文，保证流式订阅与 GetTask 一致
- 快照注册与增量发布共用同步边界，防止订阅交接重复或漏内容
- 适配本地包路径并同步相关回归源码和协议说明

来源：[上游 #1466](https://github.com/AAswordman/Operit/pull/1466)。仅移植上述修复，不整体合并上游分支。

## 验证

进行代码链路核验、上游差异复查和静态检查。补充能区分修复前后行为的 JVM 回归源码；本地不运行编译、构建或测试。远程结果与真机验收分别记录。

## 实现与回归记录

本轮完成主聊天和悬浮语音的调用顺序修正。通用 `TtsCleaner.clean`、自由文本语音合成测试、预取队列、分句和播放生命周期保持原逻辑。新增旧模板第一步擦除思考边界的反例断言；没有把源码审阅称为设备实测。

A2A 文本 Part 校验、标准 JSON 序列化、最终替换事件和快照交接修复已适配本地代码。保留本地请求执行器、重试后正文归并及包路径；未替换整个上游实现。

新增 39 项 JVM、3 项 Android 回归用例源码。JVM 用例覆盖条件相关的 TTS 正文、用户正则、协议元数据，以及 A2A 文本附带字段、互斥内容成员、终态 JSON、空最终正文替换和并发订阅交接。Android 源码验证既有 Markdown/分句管线，尚未执行设备用例。

实现 [DONE]。本地未运行编译、构建或测试，静态检查及远程验证结果在交付记录中保存。

## 消息结构复核与修正

复核确认数据库 `MessageEntity.sections`、回答版本和备份序列化均使用分段结构。`MessageEntity.toChatMessage()` 与备份反序列化在运行时渲染 `content`；该字段仍承担协议、编辑及流式边界的字符串表示，不是持久化正文列。

首次移植的 TTS 提取函数沿用了上游成对标签正则，适配不充分。本地思考块使用 `ThinkingMarkup` 的同名同 token 边界；上游正则会把内部普通 `</think>` 误判为结束，删掉外层起始边界并留下后半段思考。它还按供应商名称删除协议元数据，没有遵循所有 `Protocol` 分段均不可朗读的规则。

修正为：

- 手动朗读回调传递 `ChatMessage`，由 `resolvedSections()` 取得当前片段，保留编辑后的同步规则
- TTS 只从 `MessageSection.Text` 提取正文，再执行用户正则；非正文分段不先渲染成字符串
- 自动朗读和悬浮语音仍接收运行时字符串，在语音边界使用本地 `MessageSectionCodec.parse`，共用 token 识别和分段判定
- 情绪标记仍在用户正则之前清除；Markdown 展平、队列和播放时序沿用现有处理

A2A 链路为 `ExternalChatRequestExecutor` → `StandardChatManagerTool.startMessageToAIStream` → `core.getResponseStream()` → `ExternalChatResponseSanitizer`。最终输出继续由 `collectRevisionAppliedText()` 应用本地修订历史，再发布替换事件。本次协议修复不读取旧数据库正文列，也不更改数据库、备份或回答版本结构。

补充 9 项 JVM、2 项 Android 回归源码，覆盖 token 块内伪闭合、未闭合 token 块、其他 token 的闭合标签、全部非正文分段、非预设供应商协议、数据库与备份往返、编辑后的片段，以及正文内代码示例。保留已有搜索片段覆盖。

[DONE] 消息结构链路核查与源码修正完成。静态检查、远程编译和设备验证分别记录，不将新增用例源码视为已执行结果。
