package com.ai.assistance.operit.util.markdown

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ai.assistance.operit.util.stream.*
import com.ai.assistance.operit.util.stream.plugins.*
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 字符串收集处理器 - 简单地将流收集为字符串 */
class StringCollectorProcessor : StreamProcessor<String, String> {
    override suspend fun process(stream: Stream<String>): String {
        val content = StringBuilder()
        stream.collect { content.append(it) }
        return content.toString()
    }
}

/** Markdown处理器类型枚举 */
enum class MarkdownProcessorType {
    // 块级处理器
    HEADER,
    BLOCK_QUOTE,
    CODE_BLOCK,
    ORDERED_LIST,
    UNORDERED_LIST,
    HORIZONTAL_RULE,
    BLOCK_LATEX, // LaTeX块级公式
    TABLE, // 表格支持
    XML_BLOCK, // XML块级元素
 
    // 内联处理器
    BOLD,
    ITALIC,
    INLINE_CODE,
    LINK,
    IMAGE,
    STRIKETHROUGH,
    UNDERLINE,
    INLINE_LATEX, // LaTeX行内公式

    // 纯文本
    PLAIN_TEXT,
    HTML_BREAK
}

/** 
 * Markdown数据模型 
 * 
 */
class MarkdownNode(val type: MarkdownProcessorType, initialContent: String = "") {
    val content: SmartString = SmartString(initialContent)
    val children: SnapshotStateList<MarkdownNode> = mutableStateListOf()
}

@Stable
data class MarkdownNodeStable(
    val type: MarkdownProcessorType,
    val content: String,
    val children: List<MarkdownNodeStable>
)

/** 将字符串转换为字符流 */
fun String.toCharStream(): Stream<Char> {
    return stream {
        for (c in this@toCharStream) {
            emit(c)
        }
    }
}

/** 将字符串流转换为字符流 */
fun Stream<String>.toCharStream(): Stream<Char> {
    val charStream = stream {
        this@toCharStream.collect { str ->
            for (c in str) {
                emit(c)
            }
        }
    }
    var result: Stream<Char> = charStream
    val carrier = this as? TextStreamEventCarrier
    if (carrier != null) {
        result = result.withTextEventChannel(carrier.eventChannel)
    }
    val rollbackPrefix = this as? StreamRollbackPrefix
    if (rollbackPrefix != null) {
        result = result.withRollbackPrefix(rollbackPrefix.rollbackPrefix)
    }
    return result
}

/** Markdown结果处理器 - 生成MarkdownNode模型 */
class MarkdownNodeProcessor(private val type: MarkdownProcessorType) :
        StreamProcessor<String, MarkdownNode> {
    override suspend fun process(stream: Stream<String>): MarkdownNode =
            withContext(Dispatchers.Default) {
                val contentBuilder = StringBuilder()
                stream.collect { contentBuilder.append(it) }
                MarkdownNode(type, initialContent = contentBuilder.toString())
            }
}

/** 递归Markdown处理器 - 在各级嵌套中应用不同的处理逻辑 */
object NestedMarkdownProcessor {

    /** 块级插件列表 */
    fun getBlockPlugins(): List<StreamPlugin> =
            listOf(
                    StreamMarkdownHeaderPlugin(),
                    StreamMarkdownFencedCodeBlockPlugin(),
                    StreamMarkdownBlockQuotePlugin(includeMarker = false),
                    StreamMarkdownOrderedListPlugin(),
                    StreamMarkdownUnorderedListPlugin(includeMarker = false),
                    StreamMarkdownHorizontalRulePlugin(),
                    // LaTeX 块级公式：同时支持 $$...$$ 和 \\[...\\]
                    StreamMarkdownBlockLaTeXPlugin(includeDelimiters = false),
                    // 对 \[...\] 保留分隔符，避免在结束匹配失败分支吞掉反斜杠；
                    // 渲染阶段会通过 extractLatexContent 移除分隔符。
                    StreamMarkdownBlockBracketLaTeXPlugin(includeDelimiters = true),
                    StreamMarkdownTablePlugin(),
                    StreamMarkdownImagePlugin(),
                    StreamXmlPlugin(includeTagsInOutput = true) // 使用现有的StreamXmlPlugin
            )

    /** 内联插件列表 */
    fun getInlinePlugins(): List<StreamPlugin> =
            listOf(
                    StreamMarkdownBoldPlugin(includeAsterisks = false),
                    StreamMarkdownItalicPlugin(includeAsterisks = false),
                    StreamMarkdownInlineCodePlugin(includeTicks = false),
                    StreamMarkdownLinkPlugin(),
                    StreamMarkdownStrikethroughPlugin(includeDelimiters = false),
                    StreamMarkdownUnderlinePlugin(),
                    // LaTeX 行内公式：支持 $...$ 与 \\(...\\)
                    StreamMarkdownInlineLaTeXPlugin(includeDelimiters = false),
                    // 对 \(...\) 保留分隔符，避免在结束匹配失败分支吞掉反斜杠；
                    // 渲染阶段会通过 extractLatexContent 移除分隔符。
                    StreamMarkdownInlineParenLaTeXPlugin(includeDelimiters = true)
            )

    /** 根据插件获取对应的Markdown处理器类型 */
    internal fun getTypeForPlugin(plugin: StreamPlugin?): MarkdownProcessorType {
        return when (plugin) {
            is StreamMarkdownHeaderPlugin -> MarkdownProcessorType.HEADER
            is StreamMarkdownBlockQuotePlugin -> MarkdownProcessorType.BLOCK_QUOTE
            is StreamMarkdownFencedCodeBlockPlugin -> MarkdownProcessorType.CODE_BLOCK
            is StreamMarkdownOrderedListPlugin -> MarkdownProcessorType.ORDERED_LIST
            is StreamMarkdownUnorderedListPlugin -> MarkdownProcessorType.UNORDERED_LIST
            is StreamMarkdownHorizontalRulePlugin -> MarkdownProcessorType.HORIZONTAL_RULE
            is StreamMarkdownBoldPlugin -> MarkdownProcessorType.BOLD
            is StreamMarkdownItalicPlugin -> MarkdownProcessorType.ITALIC
            is StreamMarkdownInlineCodePlugin -> MarkdownProcessorType.INLINE_CODE
            is StreamMarkdownLinkPlugin -> MarkdownProcessorType.LINK
            is StreamMarkdownImagePlugin -> MarkdownProcessorType.IMAGE
            is StreamMarkdownStrikethroughPlugin -> MarkdownProcessorType.STRIKETHROUGH
            is StreamMarkdownUnderlinePlugin -> MarkdownProcessorType.UNDERLINE
            is StreamMarkdownInlineLaTeXPlugin -> MarkdownProcessorType.INLINE_LATEX
            is StreamMarkdownInlineParenLaTeXPlugin -> MarkdownProcessorType.INLINE_LATEX
            is StreamMarkdownBlockLaTeXPlugin -> MarkdownProcessorType.BLOCK_LATEX
            is StreamMarkdownBlockBracketLaTeXPlugin -> MarkdownProcessorType.BLOCK_LATEX
            is StreamMarkdownTablePlugin -> MarkdownProcessorType.TABLE
            is StreamXmlPlugin -> MarkdownProcessorType.XML_BLOCK
            else -> MarkdownProcessorType.PLAIN_TEXT
        }
    }
}

/** UI绑定器 - 将处理后的StreamGroup绑定到UI渲染组件 这里使用抽象类型T表示UI组件，你可以实现具体的渲染逻辑 */
class MarkdownUIBinder<T>(
        private val component: T,
        private val renderStrategy: suspend (T, MarkdownNode) -> Unit
) {
    /** 绑定StreamGroup到UI组件 */
    suspend fun bind(group: StreamGroup<MarkdownProcessorType>) {
        // 递归处理所有组
        val nodes = processGroupToNodes(group)
        renderStrategy(component, nodes)
    }

    /** 将StreamGroup转换为MarkdownNode树 */
    private suspend fun processGroupToNodes(
            group: StreamGroup<MarkdownProcessorType>
    ): MarkdownNode {
        // 处理当前组
        val content = StringBuilder()
        group.stream.collect { content.append(it) }

        val node = MarkdownNode(group.tag, initialContent = content.toString())

        // 递归处理子组
        for (child in group.children) {
            @Suppress("UNCHECKED_CAST") val childGroup = child as StreamGroup<MarkdownProcessorType>
            val childNode = processGroupToNodes(childGroup)
            node.children.add(childNode)
        }

        return node
    }
}
