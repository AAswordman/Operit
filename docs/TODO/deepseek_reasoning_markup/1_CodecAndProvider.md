---
title: Codec、canonical grammar 与 Provider 历史投影
status: draft
document_type: implementation-step
step: 1
depends_on: []
fork_repository: https://github.com/CATMIAOZHI/Operit
last_reviewed: 2026-07-18
---

# Codec、canonical grammar 与 Provider 历史投影

- 新增 DeepSeek 专用、版本化的 `xml-text-v1` codec
- 编码顺序固定为 `&`、`<`、`>`，解码为严格、非递归的单次扫描
- OpenAI Provider 只提供默认无行为变化的 reasoning emission seam
- DeepSeek 流式与非流式输出统一编码，响应输出 token 统计使用编码前原文
- DeepSeek 历史只对精确版本 marker 解码
- 工具子轮次逐字符保留 reasoning，不 trim、不插入换行
- 原生工具调用保持在已关闭的 think block 外
- `enableToolCall` 只决定本轮是否暴露新工具，不改变历史 reasoning 或既有原生工具 exchange 的协议投影
- v1 reasoning 具有 producer provenance；普通正文中的同形 XML 始终是正文
- Provider 序列化和 token 计数消费同一个请求级投影

## Canonical wire format

新格式只接受以下精确结构：

```xml
<think data-operit-content-encoding="xml-text-v1">ENCODED_BODY</think>
```

- opening tag 必须与上述字符串完全一致：属性名、双引号、属性顺序和空格均固定，不允许附加属性
- closing tag 固定为 `</think>`；canonical producer 在正常结束、流结束和工具调用边界都必须闭合
- `ENCODED_BODY` 按 `&` → `&amp;`、`<` → `&lt;`、`>` → `&gt;` 的顺序编码
- 解码器只识别 `&amp;`、`&lt;`、`&gt;`，并以非递归单次扫描恢复；未知实体逐字符保留
- 未闭合的 v1 marker 属于 malformed canonical data：不得解码，并隔离其整个 reasoning segment
- 未知版本、额外属性或被编辑过的 reserved marker 不按 v1 解码；它们按下述 grammar 作为 opaque think block 隔离
- 相邻 v1 block 按源顺序逐字符拼接，不插入分隔符
- 混合历史中，共享 projector 只处理具有 `ReasoningV1` 来源的 segment；旧无 marker `<think>`/`<thinking>` 文本逐字符保留并继续交给目标 Provider 的既有逻辑，不由共享 projector trim、拼接或插入分隔符
- visual 编辑器只编辑已隔离的 body，不暴露 marker；raw 编辑保留 segment 来源，导致 marker 不再精确匹配时，按 opaque reasoning segment 处理

### Source provenance

仅靠扁平字符串无法区分 DeepSeek reasoning 生成的精确 v1 block 与普通 assistant 正文中的同形 XML。canonical assistant 数据因此必须保留有序、可持久化的 segment 来源，至少区分普通 `Text` 与内部 `ReasoningV1`；`PromptTurn` 还必须保留原始角色，不能只保留后续映射出的 wire role。

- DeepSeek Provider 只把 `reasoning_content` 产生的编码 block 标记为 `ReasoningV1`；普通 `content` 始终产生 `Text`，即使其文本与精确 v1 wire format 完全相同
- `ReasoningV1` 保存完整编码态 wire text 及其所属 assistant response/tool exchange；扁平 `content` 是兼容展示，不是 projector 判断来源的依据
- segment 来源必须随消息、变体、rollback/replay、`PromptTurn` 和编辑结果持久化；不能只放在一次流式请求的内存对象中
- raw 编辑在 `Text` 中插入精确 v1 文本时仍保持 `Text`；编辑已有 `ReasoningV1` 时保持其来源，非精确结果按 opaque 隔离
- hook 未修改某 segment 时保留其来源；替换或新建的纯文本没有权限通过 marker 字符串伪造 `ReasoningV1`
- v1 格式尚未进入上游发布历史，不为缺少 provenance 的 v1-looking 字符串推断来源；无 provenance 的同形文本按普通正文处理
- 旧无 marker `<think>`/`<thinking>` 仍按既有文本兼容规则处理，不要求补造 v1 provenance

该来源标识只消除 v1 内部 marker 与普通正文的歧义，不建立通用 XML provenance，也不改变普通 XML ToolCall 协议。

实现只增加 v1-specific 附属 metadata，用于标记 DeepSeek `reasoning_content` 产生的范围、source assistant turn 和 source native tool exchange。现有扁平 `content` 仍是主存储正文；metadata 不解析、分类或记录其他正文 XML，也不得把 `Text` 按 marker 内容升级为 reasoning。编辑和流式 revision 必须同步更新 v1 范围，不能靠请求时重新扫描全文恢复来源。

### Reserved marker-shaped grammar

共享 projector 只在具有 `ReasoningV1` 来源的 segment 内按以下确定性规则验证 marker；它不得扫描 `Text` 并据 marker 字符串推断来源：

1. candidate 必须以字面量 `<think` 开始，且 `think` 后紧跟 ASCII whitespace（U+0009、U+000A、U+000C、U+000D 或 U+0020）。
2. opening-tag lexer 从 candidate 起逐字符扫描，分别跟踪单引号和双引号状态；只有引号外的 `>` 才结束 opening tag，不能信任第一个 `>`。
3. 只有在引号外属性名位置、大小写完全一致且 token 边界完整的 `data-operit-content-encoding` 才是保留属性；属性值中的同名字面量不得触发 reserved 分类。
4. opening tag 与 v1 opening tag 完全一致时才寻找固定 closing tag。由于精确 v1 body 中的 `<` 已编码，body 内不可能出现字面量 `</think>`，此时第一个字面量 closing tag 才可安全结束并触发一次非递归解码。
5. 含保留属性但使用未知版本、单引号、额外属性、不同空格或其他非精确写法时，整个 `ReasoningV1` segment 作为 opaque，不执行实体解码；不能用其 body 中第一个 `</think>` 提前结束隔离。
6. opening tag、属性引号或 closing tag 未闭合时，整个 `ReasoningV1` segment 作为 opaque；projector 不得越过 segment 边界寻找补充 closing tag。
7. opaque segment 内的全部字符，包括内部字面量 `</think>` 之后的 `<tool>`、`<tool_result>` 或其他 markup，均不得进入普通正文、工具解析输入或 Provider reasoning 字段。
8. `Text` 中不含 provenance 的精确 v1、`<think class="foo">` 或属性值中的保留名字面量均保持普通正文；精确旧 `<think>`/`<thinking>` 继续走现有旧历史逻辑。

该 lexer 不推断嵌套 XML，也不搜索 reasoning segment 之外的 closing tag。

## Storage contract

`streamBuffer`、`PromptTurn`、数据库和工具解析输入始终保持编码态，并携带 v1 segment 来源。普通 `content` 不编码，以免改变现有 XML 工具协议。

## Cross-provider history projection

`xml-text-v1` 是共享 canonical 历史格式，不是 DeepSeek 请求私有格式。DeepSeek 负责在响应边界编码并标记 segment 来源，但所有 Provider 必须通过同一个请求级 projector 解释 canonical turns。

### Projection order and request context

projector 必须运行在最终 prompt hook 之后、任何角色物化、assistant/tool 合并、XML ToolCall 解析和 Provider 序列化之前，顺序固定为：

1. 从持久化数据恢复 canonical turns、原始角色和有序 segments。
2. 计算并记录目标 wire role 和角色前缀，但不修改 canonical source role、segments 或正文；其他角色的 assistant 仍保留 `sourceRole=assistant` 和 segment 来源，不能先降成无来源的 user 文本。
3. 执行最终 prompt hooks，并按 segment mutation 规则保留或撤销来源。
4. 以目标 Provider、已注册的历史 reasoning 能力、`enableThinking`、`preserveThinkInHistory`、本轮有效 ToolCall 状态和目标 wire role 构造一次请求级 projection context。
5. 共享 projector 依据 source role 和 segment 来源先隔离 reasoning，输出普通正文、可选 reasoning 字段和协议必需状态；目标 role 为 user/system/tool 时不得携带 reasoning 字段。
6. projection 完成后才物化目标 roles 和角色前缀；工具编译只解析仍有资格作为 assistant/tool 协议输入的普通正文，再生成原生 `tool_calls` 和配对的 `tool_call_id`。
7. Provider 序列化与 token 计数共同消费同一个不可变 projected request；不得再次从 canonical `PromptTurn.content` 独立推导。

该投影必须位于所有 Provider 共用的请求边界，不能只放在 `OpenAIProvider` 或只判断投影后的 `PromptTurn.kind == ASSISTANT`。现有 preflight token API 缺少历史 reasoning 能力、`enableThinking`、`preserveThinkInHistory` 和完整请求上下文，实施时必须改为上下文完备的 request projection/counting API。

### Orthogonal controls and Provider capability

三个现有概念必须正交，不能互相代替：

- `enableThinking` 只控制目标模型本轮是否生成新的 reasoning；关闭后仍要投影目标协议允许或要求的历史 reasoning/state
- `enableToolCall` 与当前可用工具列表只控制本轮是否发送新工具定义、允许模型产生新调用；关闭后仍要保持历史原生工具 exchange 的协议结构
- `preserveThinkInHistory` 继续交给旧无 marker think 的既有逻辑；对有 provenance 的 v1，目标已注册安全文本历史通道时始终投影，未注册时始终剥离，不能把协议安全交给用户开关

历史 reasoning 能力按具体目标 Provider 显式注册：

- textual history reasoning：目标有经过协议文档、fixture 或 API 验证的 assistant 专用文本字段；所有真实 `ReasoningV1` 均可逐字符解码到该字段，且不受本轮 thinking 开关影响
- provider-native opaque state：目标要求 signature、encrypted item 或其他 Provider 自有状态；DeepSeek v1 不兼容该通道，必须剥离且不得填入这些字段，目标 Provider 既有的同源状态行为不由本任务修改
- unsupported/unverified：没有安全历史通道或尚未验证；剥离 `ReasoningV1`，不得降成普通 `content`

能从响应读取 reasoning、拥有本轮 thinking 参数或继承某个 Provider 实现，都不足以自动注册 textual history reasoning 能力。

本任务只使用该能力分类决定 DeepSeek v1 的目标投影，不新增或修复 Claude、Gemini、OpenAI Responses 等 Provider 自有 opaque-state 持久化协议。

### Target projection rules

- DeepSeek → DeepSeek：所有真实 v1 body 始终解码到各自 source assistant 的 `reasoning_content`，不受本轮 `enableThinking`、`enableToolCall`、可用工具或 `preserveThinkInHistory` 影响；普通正文进入 `content`
- DeepSeek → Kimi/MiMo：只有在目标 Provider 明确验证并注册 textual history reasoning 能力后，thinking-enabled 与 thinking-disabled 两条路径才都将全部 v1 body 解码到 assistant `reasoning_content`；本轮 thinking 开关不能作为历史能力判断
- DeepSeek → 其他显式注册 textual history reasoning 能力的 Provider：全部 v1 body 解码到其 assistant 专用字段，不受本轮生成开关影响
- DeepSeek → 使用 signature/encrypted item 等 opaque reasoning 的 Provider：剥离 DeepSeek v1，不得填入 signature、encrypted item、signed thinking 或普通 `content`；该 Provider 既有的同源状态行为保持不变
- DeepSeek → 通用 OpenAI：v1 block 从 wire `content` 剥离；除非该 Provider 明确定义了安全 reasoning 通道，否则不发送 reasoning
- DeepSeek → 不支持 reasoning 的 Provider：v1 block 从请求历史剥离，不转换成普通正文
- 对 v1 block，`preserveThinkInHistory` 不覆盖目标 Provider 能力决策：安全 textual history 通道存在时 true/false 都投影，不存在时 true/false 都剥离
- 其他角色的 assistant 被角色隔离映射为 user 时，无论 preserve 值如何都只发送 `Text` 正文；不发送 marker、encoded body 或 reasoning 字段
- 普通 `Text` 中的精确 v1 wire format 逐字符保留为正文，不触发 v1 解码或剥离
- 旧无 marker `<think>`/`<thinking>` 历史不受上述 v1 开关规则影响，继续委托给各 Provider 的现有兼容逻辑；共享 projector 不 trim、重排或按 v1 解码旧实体文本
- DeepSeek 响应输出 token 估算使用编码前的原始 reasoning；历史输入 token 估算使用目标 Provider 的最终 wire projection，不能使用投影前 canonical 字符串
- ToolCall 开关两种状态必须复用同一 reasoning 投影；工具解析只能接收已移除 think block 的普通正文，历史 native exchange 投影不能由本轮开关降成 XML 文本
- Kimi/MiMo 的 thinking-enabled 与 thinking-disabled 请求路径必须先调用同一共享 projector，并让请求 JSON 与 token 计数消费同一个 projected request；现有 legacy think extractor 只处理 projector 保留的旧无 marker 文本
- 安全 history reasoning capability 按具体 Provider 和本次请求 context 显式注册，不能因字段名称相似或继承了某个 Provider 实现而自动启用

### DeepSeek historical native ToolCall protocol state

[DeepSeek Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode) 规定：未发生工具调用的普通 assistant reasoning 在后续请求中可以省略，回传时会被忽略；发生过原生工具调用的 assistant reasoning 则必须在后续所有用户交互请求中完整回传，否则可能返回 400。为避免遗漏 mandatory reasoning，DeepSeek 目标统一回传所有真实 `ReasoningV1`，并保持历史 source native tool exchange 的完整协议结构。

- DeepSeek 目标的全部 source assistant `ReasoningV1` 均写入对应 `reasoning_content`；无需在发送阶段区分普通与 mandatory reasoning
- 只要历史中存在 source DeepSeek native tool exchange，其 assistant `tool_calls`、对应 `reasoning_content` 和 tool results 在以后每个 DeepSeek 请求中都按原生结构回传
- 上述历史投影不受本次 `enableThinking`、`preserveThinkInHistory`、`enableToolCall` 或当前可用工具列表影响；当前 ToolCall 开关只控制是否发送本轮的新工具定义和 `tool_choice`
- reasoning、assistant `tool_calls` 和后续 tool results 必须通过 canonical assistant/tool-call/tool-result exchange 边界显式关联并随历史持久化，不能用“最近的 `<think>`”或扁平字符串位置猜测
- 本任务仍不持久化上游 `tool_call_id`；projected request 可生成 ID，但 assistant calls 与对应 results 必须在同一个投影对象中一次生成并保持一致
- 所有回传 reasoning 只能进入 source assistant 的 `reasoning_content`，不得进入普通 `content`、工具解析输入或其他角色
- token 计数必须包含最终请求中实际回传的全部 reasoning 和历史原生工具结构

## Completion

状态：未完成。实现共享 projector 并完成授权验证后，在一级标题末尾添加 `[DONE]`。
