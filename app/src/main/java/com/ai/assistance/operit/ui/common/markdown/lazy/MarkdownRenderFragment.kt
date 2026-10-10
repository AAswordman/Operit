package com.ai.assistance.operit.ui.common.markdown.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.ai.assistance.operit.ui.common.markdown.MarkdownNodeGrouper
import com.ai.assistance.operit.ui.common.markdown.XmlContentRenderer
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** 解析由消息持有；可见条目只负责绘制已经准备好的内容。 */
internal interface MarkdownRenderFragment {
    @Composable
    fun Render(
        modifier: Modifier,
        textColor: Color,
        fontSize: TextUnit,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        nodeGrouper: MarkdownNodeGrouper,
        enableDialogs: Boolean,
        fillMaxWidth: Boolean,
    )
}

internal val LocalMarkdownRenderFragment = staticCompositionLocalOf<MarkdownRenderFragment?> { null }

/** 按节点类型和同类型序号标识内容；过滤协议元数据不会改变工具或思考的身份。 */
internal fun markdownNodeIdentityKeys(nodes: List<MarkdownNodeStable>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return nodes.map { node ->
        val kind = if (node.type == MarkdownProcessorType.XML_BLOCK) {
            val tag = node.content.trimStart().drop(1).takeWhile { it.isLetterOrDigit() || it == '_' }
            "xml-${if (tag == "thinking") "think" else tag}"
        } else {
            node.type.name
        }
        val occurrence = occurrences[kind] ?: 0
        occurrences[kind] = occurrence + 1
        "$kind-$occurrence"
    }
}
