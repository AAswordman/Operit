package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.chat.protocol.protocolMetaDisplayText
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.ui.common.markdown.lazy.LocalMarkdownRenderFragment
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** 分块已有解析节点，不再为每块校验整条正文；独立消息按内容变化准备显示片段。 */
@Composable
internal fun rememberDisplayMessageSections(message: ChatMessage): List<MessageSection> {
    if (LocalMarkdownRenderFragment.current != null) return emptyList()
    return remember(message.content, message.sections) { messageSectionsForRendering(message) }
}

/** 协议只用于供应商历史续传，界面不生成对应节点。 */
internal fun messageSectionsForRendering(message: ChatMessage): List<MessageSection> = message.displaySections()
/** 已分类的工具、思考和状态片段直接生成 XML 节点，正文继续使用现有 Markdown 解析。 */
internal suspend fun parseMessageSectionsToNodes(
    sections: List<MessageSection>,
    parseText: suspend (String) -> List<MarkdownNode> = ::parseMarkdownToNodes,
): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    for (section in sections) {
        when (section) {
            is MessageSection.Text -> nodes.addAll(parseText(protocolMetaDisplayText(section.content)))
            is MessageSection.Protocol -> Unit
            else -> nodes.add(
                MarkdownNode(
                    type = MarkdownProcessorType.XML_BLOCK,
                    initialContent = MessageSectionCodec.render(listOf(section)),
                )
            )
        }
    }
    return nodes
}

/** 区分相同字符串的不同 section 边界，避免复用普通 Markdown 或其他分类的缓存。 */
internal fun messageSectionCacheKey(content: String, sections: List<MessageSection>): String {
    val boundaries = sections.joinToString(",") {
        it.type + ":" + MessageSectionCodec.render(listOf(it)).length
    }
    return "sections:$boundaries\u0000$content"
}
