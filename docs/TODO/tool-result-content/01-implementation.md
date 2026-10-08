# 实现与验证

## 正文提取

新增 util/toolmarkup/ToolResultMarkup.kt。contentFromBody() 的输入限定为一个工具结果的外层 body，完整匹配外层 content 包裹并保留内部标签；contentFromBlock() 的输入限定为一个完整块。普通裸正文和自闭合结果继续保持原有读取语义，多个相邻工具结果独立处理。

CustomXmlRenderer、MessageSectionCodec、Claude、Gemini、OpenAI、StructuredToolCallBridge、ConversationService 和 AIMessageManager 共用该入口。删除旧 contentTag、xmlToolResultPattern 以及无调用的显示层别名。结果名称与状态从外层开始标签读取，正文中的同名属性不参与卡片状态判断。

MessageSectionStorage 读取已有 sections JSON 时，若 ToolResult.raw 完整，会恢复此前被截短的 content；raw 不改写。不改数据库版本、归档格式或 Provider 请求 schema。

## 静态与流式卡片

MessageSectionMarkdown.kt 将 Text 交给原 Markdown 解析，工具、思考、搜索、状态 section 直接生成 XML_BLOCK 节点；Protocol 不产生可见节点。Cursor 和 Bubble 的静态入口传 sections，继续使用原来的工具分组、展开、详情和复制组件。缓存 key 包含 section 类型及边界，避免同一字符串的不同分类混用缓存。

Codec 寻找结构标记时跳过行内代码、反引号或波浪号围栏，以及普通 XML 容器。已经进入工具或思考块后，块正文按外层边界读取，其中的代码标记不会吞掉后续 section。

Kotlin 和 C++ StreamXmlPlugin 允许合法 tool/tool_result 及其后缀标签紧接普通文字开始。普通 XML 继续使用既有行首和标点边界；Markdown 代码块仍由代码插件处理。工具结果等到真实外层闭合后显示，内部自闭合标签不算结果闭合。

## 全量回归清单

| 项目 | 操作或输入 | 预期 |
| --- | --- | --- |
| 普通结果 | 工具返回一般文本 | 显示完整正文，保持原成功或失败状态 |
| 内部结束标签 | 正文包含 content 结束标签及其后续文本 | 详情、保存和下一轮模型回传保留尾部 |
| 嵌套示例 | 正文包含多组 content 标签 | 只移除真正外层包裹 |
| 文件差异 | 返回 file-diff、CDATA 和 XML 实体 | 正文保持原样，继续由文件差异组件展示 |
| 相邻结果 | 同时产生多个工具结果 | 各自取值，不跨块合并 |
| 空与历史正文 | 空包裹、自闭合结果、旧裸正文 | 保持各自语义 |
| 流与历史一致 | OrderedToolResults 发布包含标签的结果 | 实时发布与模型历史正文一致 |
| 历史截短字段 | JSON 的 raw 完整、content 缺尾 | 读取后恢复完整字段并保留 raw |
| 普通文字相邻 | 正文直接连接工具调用或结果 | 静态产生工具节点，流式识别工具起始标签 |
| 代码示例 | 行内代码、反引号和波浪号围栏中的工具示例 | 保持正文，不变成卡片 |
| 普通 XML | details 内嵌工具示例或普通行内标签 | 保持容器及原起始规则 |
| 不完整结果 | 结果正文末尾为内部自闭合标签 | 等待外层结果闭合 |
| 隐藏协议 | sections 带 Protocol | 显示继续隐藏协议正文 |
| 缓存分类 | 相同字符串分别作为 Text 和 ToolCall | 使用独立的节点缓存 |
| 分包边界 | 原生解析在每个切分位置及逐字输入 | 工具结果节点保留完整外层块 |

| 验证项 | 结果 |
| --- | --- |
| 源码差异与调用点检查 | diff --check 通过；旧提取调用已清除，静态及流式入口已核对 |
| Android Tests / JVM 用例 | 已补充用例，工作流派发后等待结果 |
| Android Build / assembleDebug | 工作流派发后等待结果 |
| Android 原生仪器用例与界面复核 | 尚未执行 |

[DONE] 正文解析、section 节点入口、流式边界和回归用例实现完成；运行结果以仓库工作流和设备执行记录为准。
