package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

private val hiddenProtocolMetaProvider = Regex(
    """\bprovider\s*=\s*["'](?:gemini:thought_signature|openai:responses_reasoning|openai:responses_output_item)["']""",
    RegexOption.IGNORE_CASE,
)

/** 分块与 XML 绘制共用可见性规则，避免隐藏节点仍占用气泡首尾和留白。 */
internal fun isVisibleChatXmlContent(
    content: String,
    showThinkingProcess: Boolean,
    showStatusTags: Boolean,
): Boolean {
    val tagName = ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(content))
    return when (tagName) {
        "meta" -> !hiddenProtocolMetaProvider.containsMatchIn(content)
        "think", "thinking" -> showThinkingProcess
        "status" -> showStatusTags ||
            ChatMarkupRegex.typeAttr.find(content)?.groupValues?.get(1) !in
                listOf("completion", "complete", "wait_for_user_need")
        else -> true
    }
}

/** 仅去除不绘制内容的节点；普通正文和代码中的标签示例仍按原样展示。 */
internal fun isVisibleChatMarkdownNode(
    node: MarkdownNodeStable,
    showThinkingProcess: Boolean,
    showStatusTags: Boolean,
): Boolean = when (node.type) {
    MarkdownProcessorType.XML_BLOCK ->
        isVisibleChatXmlContent(node.content, showThinkingProcess, showStatusTags)
    MarkdownProcessorType.PLAIN_TEXT -> node.content.isNotBlank() ||
        node.children.any { isVisibleChatMarkdownNode(it, showThinkingProcess, showStatusTags) }
    else -> true
}
