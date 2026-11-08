---
status: 开发方案
pr: https://github.com/AAswordman/Operit/pull/1278
---

# 思考片段的 token 边界

OpenAI 兼容接口的独立 reasoning 字段、Responses 推理事件、Gemini thought parts 和 Claude thinking blocks 在适配层进入同一条消息流时，统一包裹为带随机 token 的 think 片段。每次开启新的思考片段都用 SecureRandom 生成 4 到 5 位字母数字 token，该 token 不加入当前请求。

```text
<think token="aBc4">思考正文里也可以出现 </think> 和 </thinking>。</think token="aBc4">回答正文
```

这是 Operit 内部的文本标记协议，结束标记上的 token 由适配层写入。它由 Kotlin 和原生 XML 分段器识别，不使用标准 XML 文档解析器解析。适配层规范生成使用双引号；为兼容导入内容和历史文本，静态解析以及 Kotlin/C++ 流式分段器也接受成对的单引号。

## 配对与流式显示

开始和结束标记使用同一个 token，且 token 区分大小写。普通结束标签、不同 token 的结束标记、其他属性里的 token 文本，都只作为思考正文。工具调用、普通正文切换和正常响应结束时，由适配层发出匹配的结束标记。

MessageSectionCodec、ChatUtils、复制清理、记忆候选清理，以及思考气泡和结构化内容解析共用 ThinkingMarkup 的边界逻辑。带 token 的思考片段尚未收到真实结束标记时，剩余文字继续归为思考，复制、历史提取和 Waifu 的思考过滤不会把内部结束标签后的文字当成正文。

Kotlin StreamXmlPlugin 和原生 StreamXmlPlugin 都匹配适配层生成的完整结束标记，逐字收到 token 和最后一个大于号之后才结束该 XML 块。新片段和解析器重置时清除上一段的边界。

展开思考卡片的流式正文由 ThinkingMarkdownStream 读取，完整保留开始标记并复用 ThinkingMarkup 判断候选结束标记。普通结束标签、错误 token、思考正文中的同名标签示例都保留在当前思考正文；真正配对的结束标记被移除，后续回答不进入该正文流。结束标记跨多个数据块到达时继续等待完整配对；上游结束但仍未闭合时，不完整候选作为正文保留。普通正文实时发送，只有可能的结束标记暂存。该读取逻辑位于原有的聊天组件包，供 CustomXmlRenderer 的展开显示使用。

## 记录和兼容

Thinking section 持久化仍只包含 content。运行时的原始片段保存在 Transient 字段中，解析后保留原 token 和未闭合状态；从 sections 重建片段时只生成一次边界，所以同一片段重复渲染不会改变 token。归档、CSV 和数据库 sections 的字段保持既有格式。

思考名称与上游 main、dev 一致，仅为 think 和 thinking。程序生成及 Thinking section 重建统一使用 think token，thinking 保留现有解析和渲染兼容。历史无 token 的 think、thinking 标签继续按各自原有闭合标签读取。该规则用于一般运行时解析，不按数据库版本、迁移状态或消息来源区分，因此新版本里的旧式标签也能识别为思考。随机 token 保护的是客户端包装的独立思考字段；如果接口只在 content 中返回无 token 的混合文字，客户端仍按旧标签解释，纯文本本身没有额外的结构边界。

思考卡片的类型由思考标签名决定，显示还受“显示思考过程”设置控制。标签配对决定正文边界和闭合状态；流式思考片段可以在结束标记到达前显示，不要求先闭合才出现卡片。

operit_thinking 按普通 XML 内容处理，不归入 Thinking section、不参与思考卡片或思考工具分组；编辑器为它保留普通 XML 的结束标签语法。该名称未出现在本次核对的上游 main、dev 中，本分支不增加它的内建思考识别。

## 可视化消息编辑器

“修改记忆”与消息编辑共用的 MessageEditor，在添加标签时填写标签名 think、点击标签保存后，自动为没有 token 的思考片段生成一次 4 到 5 位随机字母数字 token，并把同一个 token 写入开始和结束标记。上游已有的 thinking 别名也使用同样的配对规则。已有 token 继续使用，普通 XML 标签仍使用标准结束标记。

MessageEditorContent 负责解析、保存和重组编辑片段，思考解析复用 ThinkingMarkup 的边界。思考正文中的普通结束标签或错误 token 不会拆出后续思考；切换纯文本/可视化模式、修改正文和重复重组时保持已有 token。未闭合思考在仅切换模式时保持未闭合状态。

读取旧式标签仍保留原有格式；在标签对话框中明确保存一个没有 token 的思考标签时补上随机 token。文本片段之间的空白、自闭合标签和普通 XML 的格式继续保留。

## 回归范围

- 思考正文包含普通结束标签、同名开始标签、不同 token 和不完整结束标签
- 思考名称仅保留上游已有的 think、thinking；operit_thinking 在提取、清理、sections、编辑器和原生分段中按普通内容处理
- 展开正文流过滤真实 token 边界，保留内部标签示例，并覆盖每个数据块切分位置、逐字到达与中断后的不完整候选
- 展开正文流兼容历史标签、思考别名和属性配对，普通正文在上游完成前即可显示
- 真实结束标记之前每个流式前缀均留在思考中，真实结束之后保留回答
- 复制、思考提取、原生 XML 分段和结构化显示共享同一思考正文
- 新 token 格式、历史标签、多个思考片段、解析器重置与保存后重建
- 编辑器新建 think 时自动生成匹配 token，附加属性与已有 token 保持
- 思考正文包含普通结束标签和错误 token 时，编辑器仍保留完整正文及后续回答
- 修改正文、重复重组和切换编辑模式不改变 token 或空白
- 历史思考读取、明确保存后的 token 生成、未闭合状态保留及普通 XML 编辑

验证通过 Android Build 的 assembleDebug 和 Android Tests 工作流执行。ThinkingTokenNativeTest 属于 Android 仪器测试，可在具备设备的环境执行；JVM 工作流不执行这组原生分段测试。
