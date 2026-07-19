package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.AppLogger
import android.widget.ImageView
import androidx.collection.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.ai.assistance.operit.R
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.SmartString
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamInterceptor
import com.ai.assistance.operit.util.stream.share
import com.ai.assistance.operit.util.stream.splitBy as streamSplitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.streamnative.nativeMarkdownSplitByBlock
import com.ai.assistance.operit.util.streamnative.nativeMarkdownSplitByInline
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.noties.jlatexmath.JLatexMathDrawable

private const val TAG = "MarkdownRenderer"
private const val RENDER_INTERVAL_MS = 200L // 渲染间隔 0.2 秒
private const val FADE_IN_DURATION_MS = 800 // 淡入动画持续时间
private const val MAX_CONSECUTIVE_RENDERED_NEWLINES = 2

internal enum class MarkdownRenderMode {
    STREAMING,
    STATIC,
}

internal val LocalMarkdownRenderMode = compositionLocalOf { MarkdownRenderMode.STATIC }

private data class PendingLineBreakState(
    val count: Int = 0,
    val lastWasCarriageReturn: Boolean = false,
)

private fun appendPendingLineBreaks(
    parentNode: MarkdownNode,
    childNode: MarkdownNode,
    count: Int,
) {
    if (parentNode.content.isEmpty()) {
        return
    }

    repeat(count.coerceIn(0, MAX_CONSECUTIVE_RENDERED_NEWLINES)) {
        parentNode.content + '\n'
        childNode.content + '\n'
    }
}

private fun accumulatePendingLineBreak(
    state: PendingLineBreakState,
    char: Char,
): PendingLineBreakState {
    val normalizedCount = state.count.coerceIn(0, MAX_CONSECUTIVE_RENDERED_NEWLINES)
    if (char == '\n' && state.lastWasCarriageReturn && normalizedCount > 0) {
        return PendingLineBreakState(
            count = normalizedCount,
            lastWasCarriageReturn = false,
        )
    }

    return PendingLineBreakState(
        count = (normalizedCount + 1).coerceAtMost(MAX_CONSECUTIVE_RENDERED_NEWLINES),
        lastWasCarriageReturn = char == '\r',
    )
}

private fun appendInlineChunk(
    parentNode: MarkdownNode,
    getOrCreateChildNode: () -> MarkdownNode,
    chunk: String,
    pendingLineBreakState: PendingLineBreakState,
): PendingLineBreakState {
    var state =
        PendingLineBreakState(
            count = pendingLineBreakState.count.coerceIn(0, MAX_CONSECUTIVE_RENDERED_NEWLINES),
            lastWasCarriageReturn =
                pendingLineBreakState.lastWasCarriageReturn && pendingLineBreakState.count > 0,
        )

    for (char in chunk) {
        val isCurrentCharNewline = char == '\n' || char == '\r'
        if (isCurrentCharNewline) {
            state = accumulatePendingLineBreak(state, char)
            continue
        }

        val childNode = getOrCreateChildNode()
        if (state.count > 0) {
            appendPendingLineBreaks(
                parentNode = parentNode,
                childNode = childNode,
                count = state.count,
            )
            state = PendingLineBreakState()
        }

        parentNode.content + char
        childNode.content + char
    }

    return state
}

private fun canMergeWithHtmlBreak(node: MarkdownNode?): Boolean {
    return node?.type == MarkdownProcessorType.PLAIN_TEXT
}

private fun appendHtmlBreakNode(
    nodes: SnapshotStateList<MarkdownNode>,
    count: Int = 1,
) {
    repeat(count.coerceIn(0, MAX_CONSECUTIVE_RENDERED_NEWLINES)) {
        nodes.add(MarkdownNode(type = MarkdownProcessorType.HTML_BREAK, initialContent = "\n"))
    }
}

private fun appendHtmlBreakNode(
    nodes: MutableList<MarkdownNode>,
    count: Int = 1,
) {
    repeat(count.coerceIn(0, MAX_CONSECUTIVE_RENDERED_NEWLINES)) {
        nodes.add(MarkdownNode(type = MarkdownProcessorType.HTML_BREAK, initialContent = "\n"))
    }
}

/**
 * Converts a mutable [MarkdownNode] to an immutable, stable [MarkdownNodeStable].
 * This function is recursive and converts the entire node tree.
 */
internal fun MarkdownNode.toStableNode(): MarkdownNodeStable {
    return MarkdownNodeStable(
        type = this.type,
        content = this.content.toString(),
        children = this.children.map { it.toStableNode() }
    )
}

private fun areRenderNodesSynchronized(
    nodes: SnapshotStateList<MarkdownNode>,
    renderNodes: SnapshotStateList<MarkdownNodeStable>,
    conversionCache: MutableMap<Int, Pair<Int, MarkdownNodeStable>>,
): Boolean {
    if (nodes.isEmpty() || nodes.size != renderNodes.size) {
        return false
    }

    nodes.forEachIndexed { index, sourceNode ->
        val contentLength = sourceNode.content.length
        val cached = conversionCache[index]
        val freshStableNode = sourceNode.toStableNode()
        val stableNode =
            if (cached != null && cached.first == contentLength && cached.second == freshStableNode) {
                cached.second
            } else {
                freshStableNode.also {
                    conversionCache[index] = contentLength to it
                }
            }

        if (renderNodes[index] != stableNode) {
            return false
        }
    }

    return true
}

// XML内容渲染器接口，用于自定义XML渲染
interface XmlContentRenderer {
    @Composable
    fun RenderXmlContent(
        xmlContent: String,
        modifier: Modifier,
        textColor: Color,
        xmlStream: Stream<String>?,
        renderInstanceKey: Any?
    )
}

@Composable
fun XmlContentRenderer.RenderXmlContent(
    xmlContent: String,
    modifier: Modifier,
    textColor: Color,
    renderInstanceKey: Any? = null
) {
    RenderXmlContent(xmlContent, modifier, textColor, null, renderInstanceKey)
}

// 默认XML渲染器
class DefaultXmlRenderer : XmlContentRenderer {
    @Composable
    override fun RenderXmlContent(
        xmlContent: String,
        modifier: Modifier,
        textColor: Color,
        xmlStream: Stream<String>?,
        renderInstanceKey: Any?
    ) {
        val xmlBlockDesc = stringResource(R.string.xml_block)
        
        Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = xmlBlockDesc },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(2.dp)
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outline.copy(
                                                            alpha = 0.5f
                                                    ),
                                            shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(8.dp)
            ) {
                Text(
                        text = stringResource(R.string.xml_content),
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = xmlContent,
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                ),
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 扩展函数：去除字符串首尾的所有空白字符（包括空格、制表符、换行符等） 与标准trim()相比，这个函数更明确地处理所有类型的空白字符 */
private fun String.trimAll(): String {
    return this.trim { it.isWhitespace() }
}

/**
 * StreamMarkdownRenderer的状态类
 * 用于在流式渲染和静态渲染之间共享状态，避免切换时重新计算
 */
@Stable
class StreamMarkdownRendererState {
    // 原始数据收集列表
    val nodes = mutableStateListOf<MarkdownNode>()
    // 用于UI渲染的列表
    val renderNodes = mutableStateListOf<MarkdownNodeStable>()
    // 节点动画状态映射表
    val nodeAnimationStates = mutableStateMapOf<String, Boolean>()
    // 缓存转换后的稳定节点，避免不必要的对象创建
    val conversionCache = mutableStateMapOf<Int, Pair<Int, MarkdownNodeStable>>()
    // 保存流式渲染收集的完整内容，用于切换时判断是否需要重新解析
    val collectedContent = SmartString()
    // XML 节点对应的子流（仅流式渲染有效）
    val xmlNodeStreams = mutableStateMapOf<Int, Stream<String>>()
    // 标记流式 Markdown 解析是否自然结束；若因切换静态而被取消，则不能信任现有节点
    var streamParsingCompletedSuccessfully: Boolean = false
    // 渲染器ID
    var rendererId: String = ""
        private set
    
    /**
     * 更新渲染器ID
     */
    fun updateRendererId(id: String) {
        rendererId = id
    }
    
    /**
     * 重置所有状态（用于切换内容源时）
     */
    fun reset() {
        nodes.clear()
        renderNodes.clear()
        nodeAnimationStates.clear()
        conversionCache.clear()
        collectedContent.clear()
        xmlNodeStreams.clear()
        streamParsingCompletedSuccessfully = false
    }
}

/** 高性能流式Markdown渲染组件 通过Jetpack Compose实现，支持流式渲染Markdown内容 使用Stream处理系统，实现高效的异步处理 */
@Composable
fun StreamMarkdownRenderer(
        markdownStream: Stream<Char>,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        fontSize: TextUnit = Unspecified,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() },
        nodeGrouper: MarkdownNodeGrouper = NoopMarkdownNodeGrouper,
        state: StreamMarkdownRendererState? = null,
        enableDialogs: Boolean = true,
        fillMaxWidth: Boolean = true,
) {
    // 使用传入的state或创建新的state
    val rendererState = state ?: remember { StreamMarkdownRendererState() }
    
    // 原始数据收集列表
    val nodes = rendererState.nodes
    // 用于UI渲染的列表
    val renderNodes = rendererState.renderNodes
    // 节点动画状态映射表
    val nodeAnimationStates = rendererState.nodeAnimationStates
    // 用于在`finally`块中启动协程
    val scope = rememberCoroutineScope()
    // 缓存转换后的稳定节点，避免不必要的对象创建
    val conversionCache = rendererState.conversionCache
    // XML 节点子流映射
    val xmlNodeStreams = rendererState.xmlNodeStreams

    // 当流实例变化时，获得一个稳定的渲染器ID
    val rendererId = remember(markdownStream) { 
        val id = "renderer-${System.identityHashCode(markdownStream)}"
        rendererState.updateRendererId(id)
        id
    }

    // 创建一个中间流，用于拦截和批处理渲染更新
    val interceptedStream =
            remember(markdownStream) {
                // 移除时间计算变量和日志
                // 先创建拦截器
                val processor =
                        StreamInterceptor<Char, Char>(
                                sourceStream = markdownStream,
                                onEach = { it } // 先使用简单的转发函数，后面再设置
                        )

                // 然后创建批处理更新器
                val batchUpdater =
                        BatchNodeUpdater(
                                nodes = nodes,
                                renderNodes = renderNodes,
                                conversionCache = conversionCache,
                                nodeAnimationStates = nodeAnimationStates,
                                xmlNodeStreams = xmlNodeStreams,
                                rendererId = rendererId,
                                isInterceptedStream = processor.interceptedStream,
                                scope = scope
                        )

                // 最后设置拦截器的onEach函数
                processor.setOnEach { char ->
                    // 收集字符到 state 的 collectedContent
                    rendererState.collectedContent + char
                    batchUpdater.startBatchUpdates()
                    char
                }

                processor.interceptedStream
            }

    // 处理Markdown流的变化
    LaunchedEffect(interceptedStream) {
        // 移除时间计算变量和日志

        // 重置状态
        nodes.clear()
        renderNodes.clear()
        rendererState.collectedContent.clear()
        xmlNodeStreams.clear()
        rendererState.streamParsingCompletedSuccessfully = false

        try {
            var pendingHtmlBreakCount = 0
            interceptedStream.nativeMarkdownSplitByBlock(flushIntervalMs = RENDER_INTERVAL_MS).collect { blockGroup ->
                val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT

                if (blockType == MarkdownProcessorType.HTML_BREAK) {
                    if (canMergeWithHtmlBreak(nodes.lastOrNull())) {
                        pendingHtmlBreakCount =
                            (pendingHtmlBreakCount + 1).coerceAtMost(MAX_CONSECUTIVE_RENDERED_NEWLINES)
                    } else {
                        appendHtmlBreakNode(nodes)
                    }
                    return@collect
                }

                if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                    if (pendingHtmlBreakCount > 0) {
                        appendHtmlBreakNode(nodes, pendingHtmlBreakCount)
                    }
                    nodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                    pendingHtmlBreakCount = 0
                    return@collect
                }

                val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
                val tempBlockType =
                    if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

                val isInlineContainer =
                    tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                        tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                        tempBlockType != MarkdownProcessorType.TABLE &&
                        tempBlockType != MarkdownProcessorType.XML_BLOCK

                val mergeWithPrevious =
                    pendingHtmlBreakCount > 0 &&
                        tempBlockType == MarkdownProcessorType.PLAIN_TEXT &&
                        canMergeWithHtmlBreak(nodes.lastOrNull())

                if (pendingHtmlBreakCount > 0 && !mergeWithPrevious) {
                    appendHtmlBreakNode(nodes, pendingHtmlBreakCount)
                    pendingHtmlBreakCount = 0
                }

                val newNode =
                    if (mergeWithPrevious) {
                        nodes.last()
                    } else {
                        MarkdownNode(type = tempBlockType).also { nodes.add(it) }
                    }
                val nodeIndex = nodes.lastIndex
                val blockStream: Stream<String> =
                    if (tempBlockType == MarkdownProcessorType.XML_BLOCK) {
                        blockGroup.stream.share(scope = scope, replay = Int.MAX_VALUE)
                    } else {
                        blockGroup.stream
                    }

                if (tempBlockType == MarkdownProcessorType.XML_BLOCK) {
                    xmlNodeStreams[nodeIndex] = blockStream
                }

                if (isInlineContainer) {
                    var pendingLineBreakState = PendingLineBreakState(count = pendingHtmlBreakCount)

                    blockStream.nativeMarkdownSplitByInline(flushIntervalMs = RENDER_INTERVAL_MS).collect { inlineGroup ->
                        val originalInlineType = inlineGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
                        val isInlineLatex = originalInlineType == MarkdownProcessorType.INLINE_LATEX
                        val tempInlineType =
                            if (isInlineLatex) MarkdownProcessorType.PLAIN_TEXT else originalInlineType

                        var childNode: MarkdownNode? = null

                        inlineGroup.stream.collect { chunk ->
                            pendingLineBreakState =
                                appendInlineChunk(
                                    parentNode = newNode,
                                    getOrCreateChildNode = {
                                        childNode
                                            ?: if (
                                                mergeWithPrevious &&
                                                    tempInlineType == MarkdownProcessorType.PLAIN_TEXT &&
                                                    newNode.children.lastOrNull()?.type == MarkdownProcessorType.PLAIN_TEXT
                                            ) {
                                                newNode.children.last().also { childNode = it }
                                            } else {
                                                MarkdownNode(type = tempInlineType).also {
                                                    childNode = it
                                                    newNode.children.add(it)
                                                }
                                            }
                                    },
                                    chunk = chunk,
                                    pendingLineBreakState = pendingLineBreakState,
                                )
                        }

                        if (isInlineLatex && childNode != null) {
                            val latexContent = childNode!!.content.toString()
                            val latexChildNode =
                                MarkdownNode(type = MarkdownProcessorType.INLINE_LATEX, initialContent = latexContent)
                            val childIndex = newNode.children.lastIndexOf(childNode)
                            if (childIndex != -1) {
                                newNode.children[childIndex] = latexChildNode
                            }
                        }

                        if (childNode != null &&
                            childNode!!.content.toString().trimAll().isEmpty() &&
                            originalInlineType == MarkdownProcessorType.PLAIN_TEXT
                        ) {
                            val lastIndex = newNode.children.lastIndex
                            if (lastIndex >= 0 && newNode.children[lastIndex] == childNode) {
                                newNode.children.removeAt(lastIndex)
                            }
                        }
                    }
                } else {
                    blockStream.collect { contentChunk ->
                        newNode.content + contentChunk
                    }
                }

                if (isLatexBlock) {
                    val latexContent = newNode.content.toString()
                    val latexNode =
                        MarkdownNode(type = MarkdownProcessorType.BLOCK_LATEX, initialContent = latexContent)
                    nodes[nodeIndex] = latexNode
                }

                pendingHtmlBreakCount = 0
            }
            rendererState.streamParsingCompletedSuccessfully = true
        } catch (e: CancellationException) {
            rendererState.streamParsingCompletedSuccessfully = false
            throw e
        } catch (e: Exception) {
            rendererState.streamParsingCompletedSuccessfully = false
            AppLogger.e(TAG, "【流渲染】Markdown流处理异常: ${e.message}", e)
        } finally {
            // 移除时间计算变量和日志
            synchronizeRenderNodes(
                nodes,
                renderNodes,
                conversionCache,
                nodeAnimationStates,
                xmlNodeStreams,
                rendererId,
                scope
            )
            // 移除最终同步耗时日志
        }
    }

    // 渲染Markdown内容 - 使用统一的Canvas渲染器
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        CompositionLocalProvider(LocalMarkdownRenderMode provides MarkdownRenderMode.STREAMING) {
            key(rendererId) {
                UnifiedMarkdownCanvas(
                    nodes = renderNodes,
                    rendererId = rendererId,
                    nodeAnimationStates = nodeAnimationStates,
                    textColor = textColor,
                    fontSize = fontSize,
                    onLinkClick = onLinkClick,
                    xmlRenderer = xmlRenderer,
                    xmlStreamsByIndex = xmlNodeStreams,
                    nodeGrouper = nodeGrouper,
                    enableDialogs = enableDialogs,
                    modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
                    fillMaxWidth = fillMaxWidth,
                )
            }
        }
    }
}

/** A cache for parsed markdown nodes to improve performance. */
private object MarkdownNodeCache {
    // Limit static markdown cache by estimated bytes instead of entry count, so
    // incrementally growing content does not retain many large historical versions.
    private val maxCacheBytes =
        (Runtime.getRuntime().maxMemory() / 64L)
            .coerceIn(256 * 1024L, 4L * 1024L * 1024L)
            .toInt()

    private val cache =
        object : LruCache<String, List<MarkdownNode>>(maxCacheBytes) {
            override fun sizeOf(key: String, value: List<MarkdownNode>): Int {
                return (estimateStringBytes(key) + estimateNodeListBytes(value))
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }

    fun get(key: String): List<MarkdownNode>? {
        return cache.get(key)
    }

    fun put(key: String, value: List<MarkdownNode>) {
        cache.put(key, value)
    }

    private fun estimateNodeListBytes(nodes: List<MarkdownNode>): Long {
        var totalBytes = 64L
        nodes.forEach { node ->
            totalBytes += estimateNodeBytes(node)
        }
        return totalBytes
    }

    private fun estimateNodeBytes(node: MarkdownNode): Long {
        var totalBytes = 80L
        totalBytes += estimateStringBytes(node.content.toString())
        totalBytes += 16L * node.children.size
        node.children.forEach { child ->
            totalBytes += estimateNodeBytes(child)
        }
        return totalBytes
    }

    private fun estimateStringBytes(text: String): Long {
        return 40L + text.length.toLong() * 2L
    }
}

/**
 * 将完整的 Markdown 字符串解析为 [MarkdownNode] 树，不依赖 Compose 状态。
 * 静态渲染入口与其他需要完整 AST 的场景（如复制为纯文本）复用同一套解析语法。
 */
internal suspend fun parseMarkdownToNodes(content: String): List<MarkdownNode> {
    val parsedNodes = mutableListOf<MarkdownNode>()
    var pendingHtmlBreakCount = 0
    stream { emit(content) }.nativeMarkdownSplitByBlock().collect { blockGroup ->
        val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT

        if (blockType == MarkdownProcessorType.HTML_BREAK) {
            if (canMergeWithHtmlBreak(parsedNodes.lastOrNull())) {
                pendingHtmlBreakCount =
                    (pendingHtmlBreakCount + 1).coerceAtMost(MAX_CONSECUTIVE_RENDERED_NEWLINES)
            } else {
                appendHtmlBreakNode(parsedNodes)
            }
            return@collect
        }

        if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
            if (pendingHtmlBreakCount > 0) {
                appendHtmlBreakNode(parsedNodes, pendingHtmlBreakCount)
            }
            parsedNodes.add(MarkdownNode(type = blockType, initialContent = "---"))
            pendingHtmlBreakCount = 0
            return@collect
        }

        val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
        val tempBlockType =
            if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

        val isInlineContainer =
            tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                tempBlockType != MarkdownProcessorType.TABLE &&
                tempBlockType != MarkdownProcessorType.XML_BLOCK

        val mergeWithPrevious =
            pendingHtmlBreakCount > 0 &&
                tempBlockType == MarkdownProcessorType.PLAIN_TEXT &&
                canMergeWithHtmlBreak(parsedNodes.lastOrNull())

        if (pendingHtmlBreakCount > 0 && !mergeWithPrevious) {
            appendHtmlBreakNode(parsedNodes, pendingHtmlBreakCount)
            pendingHtmlBreakCount = 0
        }

        val newNode =
            if (mergeWithPrevious) {
                parsedNodes.last()
            } else {
                MarkdownNode(type = tempBlockType).also { parsedNodes.add(it) }
            }
        val nodeIndex = parsedNodes.lastIndex

        if (isInlineContainer) {
            val blockTextBuilder = StringBuilder()
            blockGroup.stream.collect { s ->
                blockTextBuilder.append(s)
            }
            val blockText = blockTextBuilder.toString()

            var pendingLineBreakState = PendingLineBreakState(count = pendingHtmlBreakCount)

            stream { emit(blockText) }.nativeMarkdownSplitByInline().collect { inlineGroup ->
                val originalInlineType = inlineGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
                val isInlineLatex = originalInlineType == MarkdownProcessorType.INLINE_LATEX
                val tempInlineType =
                    if (isInlineLatex) MarkdownProcessorType.PLAIN_TEXT else originalInlineType

                var childNode: MarkdownNode? = null

                inlineGroup.stream.collect { chunk ->
                    pendingLineBreakState =
                        appendInlineChunk(
                            parentNode = newNode,
                            getOrCreateChildNode = {
                                childNode
                                    ?: if (
                                        mergeWithPrevious &&
                                            tempInlineType == MarkdownProcessorType.PLAIN_TEXT &&
                                            newNode.children.lastOrNull()?.type == MarkdownProcessorType.PLAIN_TEXT
                                    ) {
                                        newNode.children.last().also { childNode = it }
                                    } else {
                                        MarkdownNode(type = tempInlineType).also {
                                            childNode = it
                                            newNode.children.add(it)
                                        }
                                    }
                            },
                            chunk = chunk,
                            pendingLineBreakState = pendingLineBreakState,
                        )
                }

                if (isInlineLatex && childNode != null) {
                    val latexContent = childNode!!.content.toString()
                    val latexChildNode =
                        MarkdownNode(type = MarkdownProcessorType.INLINE_LATEX, initialContent = latexContent)
                    val childIndex = newNode.children.lastIndexOf(childNode)
                    if (childIndex != -1) {
                        newNode.children[childIndex] = latexChildNode
                    }
                }

                if (childNode != null &&
                    childNode!!.content.toString().trimAll().isEmpty() &&
                    originalInlineType == MarkdownProcessorType.PLAIN_TEXT
                ) {
                    val lastIndex = newNode.children.lastIndex
                    if (lastIndex >= 0 && newNode.children[lastIndex] == childNode) {
                        newNode.children.removeAt(lastIndex)
                    }
                }
            }
        } else {
            blockGroup.stream.collect { contentChunk ->
                newNode.content + contentChunk
            }
        }

        if (isLatexBlock) {
            val latexContent = newNode.content.toString()
            val latexNode =
                MarkdownNode(type = MarkdownProcessorType.BLOCK_LATEX, initialContent = latexContent)
            parsedNodes[nodeIndex] = latexNode
        }

        pendingHtmlBreakCount = 0
    }
    return parsedNodes
}

/** 高性能静态Markdown渲染组件 接受一个完整的字符串，一次性解析和渲染，适用于静态内容显示。 */
@Composable
fun StreamMarkdownRenderer(
        content: String,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        fontSize: TextUnit = Unspecified,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() },
        nodeGrouper: MarkdownNodeGrouper = NoopMarkdownNodeGrouper,
        state: StreamMarkdownRendererState? = null,
        enableDialogs: Boolean = true,
        fillMaxWidth: Boolean = true,
) {
    // 使用传入的state或创建新的state
    val rendererState = state ?: remember(content) { StreamMarkdownRendererState() }
    
    // 使用流式版本相同的渲染器ID生成逻辑
    val rendererId = remember(content) { 
        val id = "static-renderer-${content.hashCode()}"
        rendererState.updateRendererId(id)
        id
    }

    // 使用与流式版本相同的节点列表结构
    val nodes = rendererState.nodes
    val renderNodes = rendererState.renderNodes
    // 添加节点动画状态映射表，与流式版本保持一致
    val nodeAnimationStates = rendererState.nodeAnimationStates
    val scope = rememberCoroutineScope()
    // 缓存转换后的稳定节点，避免不必要的对象创建
    val conversionCache = rendererState.conversionCache
    // XML 节点子流映射（静态渲染通常为空）
    val xmlNodeStreams = rendererState.xmlNodeStreams

    // 当content字符串变化时，一次性完成解析
    LaunchedEffect(content) {
        // 先检查内容是否与流式渲染收集的内容一致，如果一致则跳过解析
        val collectedContentStr = rendererState.collectedContent.toString()
        val streamParsingCompleted = rendererState.streamParsingCompletedSuccessfully
        val shouldReuseExistingNodes =
            collectedContentStr == content &&
                areRenderNodesSynchronized(nodes, renderNodes, conversionCache) &&
                streamParsingCompleted

        if (shouldReuseExistingNodes) {
            // 从流式渲染切到静态渲染时，避免沿用已结束的 XML 子流导致子节点渲染异常
            xmlNodeStreams.clear()
            // 内容一致且已有节点，跳过解析
            return@LaunchedEffect
        }

        xmlNodeStreams.clear()
        
        // 移除时间计算相关变量
        val cachedNodes = MarkdownNodeCache.get(content)

        if (cachedNodes != null) {
            // 移除时间计算相关的日志
            // 移除时间计算变量
            nodes.clear()
            nodes.addAll(cachedNodes)
            renderNodes.clear()
            renderNodes.addAll(cachedNodes.map { it.toStableNode() })
            // 确保动画状态也被设置
            val newStates = mutableMapOf<String, Boolean>()
            cachedNodes.forEachIndexed { index, node ->
                val nodeKey = "static-node-$rendererId-$index"
                newStates[nodeKey] = true
            }
            nodeAnimationStates.putAll(newStates)
            // 移除应用缓存节点相关时间日志
            return@LaunchedEffect
        }

        launch(Dispatchers.IO) {
            try {
                val parsedNodes = parseMarkdownToNodes(content)

                // 将解析完成的节点添加到节点列表，并更新动画状态
                withContext(Dispatchers.Main) {
                    // 保存到缓存，这样下次渲染同样内容时可以直接使用
                    MarkdownNodeCache.put(content, parsedNodes)

                    // 更新UI状态
                    // 清除现有节点
                    nodes.clear()
                    // 批量添加所有节点以减少UI重组次数
                    nodes.addAll(parsedNodes)
                    renderNodes.clear()
                    renderNodes.addAll(parsedNodes.map { it.toStableNode() })
                    // 清理转换缓存，因为内容已完全改变
                    conversionCache.clear()

                    // 更新所有节点的动画状态为可见
                    val newStates = mutableMapOf<String, Boolean>()
                    parsedNodes.forEachIndexed { index, node ->
                        val nodeKey = "static-node-$rendererId-$index"
                        newStates[nodeKey] = true
                    }
                    nodeAnimationStates.putAll(newStates)

                    // 移除UI更新时间相关日志
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "【静态渲染】解析Markdown内容出错: ${e.message}", e)
            }
        }
    }

    // 渲染Markdown内容 - 使用统一的Canvas渲染器
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        CompositionLocalProvider(LocalMarkdownRenderMode provides MarkdownRenderMode.STATIC) {
            key(rendererId) {
                UnifiedMarkdownCanvas(
                    nodes = renderNodes,
                    rendererId = rendererId,
                    nodeAnimationStates = nodeAnimationStates,
                    textColor = textColor,
                    fontSize = fontSize,
                    onLinkClick = onLinkClick,
                    xmlRenderer = xmlRenderer,
                    xmlStreamsByIndex = xmlNodeStreams,
                    nodeGrouper = nodeGrouper,
                    enableDialogs = enableDialogs,
                    modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
                    fillMaxWidth = fillMaxWidth,
                )
            }
        }
    }
}

/**
 * 统一的Markdown Canvas渲染器
 * 真正在一个大Canvas中批量绘制所有节点
 * 
 * 优势：
 * - 使用单个Canvas绘制所有内容，大幅减少Composable数量
 * - 批量绘制，避免为每个节点创建独立的组件
 * - 更高效的流式渲染体验
 * - 只有复杂组件（代码块、表格等）才单独渲染
 */
/**
 * 独立的动画节点组件 - 隔离 alpha 动画状态，避免触发父组件重组
 * 
 * 关键优化：
 * - alpha 动画状态被隔离在这个组件内部
 * - 动画状态变化不会触发外部 Column 重组
 * - 使用 graphicsLayer 避免触发内容重组
 */
@Composable
private fun AnimatedNode(
    nodeKey: String,
    node: MarkdownNodeStable,
    index: Int,
    isVisible: Boolean,
    textColor: Color,
    fontSize: TextUnit,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    xmlStream: Stream<String>?,
    enableDialogs: Boolean,
    fillMaxWidth: Boolean,
    isLastNode: Boolean = false
) {
    // alpha 动画状态在这里，变化只影响这个 Composable 的作用域
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_IN_DURATION_MS),
        label = "fadeIn-$nodeKey"
    )
    // 使用 graphicsLayer 代替 alpha modifier
    // graphicsLayer 只影响绘制层，不会触发内容的 recompose
    // CanvasMarkdownNodeRenderer 内部已经使用 key 来控制重组
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
        // 所有节点都使用CanvasMarkdownNodeRenderer
        // 它内部已经实现了Canvas绘制优化和基于内容长度的 key 控制
        CanvasMarkdownNodeRenderer(
            nodeKey = nodeKey,
            node = node,
            textColor = textColor,
            fontSize = fontSize,
            modifier = Modifier,
            onLinkClick = onLinkClick,
            index = index,
            xmlRenderer = xmlRenderer,
            xmlStream = xmlStream,
            enableDialogs = enableDialogs,
            fillMaxWidth = fillMaxWidth,
            isLastNode = isLastNode
        )
    }
}

@Composable
private fun UnifiedMarkdownCanvas(
    nodes: List<MarkdownNodeStable>,
    rendererId: String,
    nodeAnimationStates: Map<String, Boolean>,
    textColor: Color,
    fontSize: TextUnit,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    xmlStreamsByIndex: Map<Int, Stream<String>>,
    nodeGrouper: MarkdownNodeGrouper,
    enableDialogs: Boolean,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
) {
    val lastRenderableIndex = run {
        val idx = nodes.indexOfLast { it.content.isNotEmpty() || it.children.isNotEmpty() }
        if (idx >= 0) idx else nodes.lastIndex
    }

    fun nodeKeyForIndex(index: Int): String {
        return if (rendererId.startsWith("static-")) {
            "static-node-$rendererId-$index"
        } else {
            "node-$rendererId-$index"
        }
    }

    val tailNode = nodes.lastOrNull()
    val groupingKey = remember(nodes.size, tailNode?.content?.length, tailNode?.type, rendererId, nodeGrouper) {
        Triple(nodes.size, tailNode?.content?.length ?: -1, tailNode?.type)
    }

    val groupedItems =
        remember(groupingKey, rendererId, nodeGrouper) {
            nodeGrouper.group(nodes, rendererId)
        }
    Column(modifier = modifier) {
        groupedItems.forEach { item ->
            when (item) {
                is MarkdownGroupedItem.Single -> {
                    val index = item.index
                    val node = nodes.getOrNull(index) ?: return@forEach
                    val nodeKey = nodeKeyForIndex(index)
                    key(nodeKey) {
                        AnimatedNode(
                            nodeKey = nodeKey,
                            node = node,
                            index = index,
                            isVisible = nodeAnimationStates[nodeKey] ?: true,
                            textColor = textColor,
                            fontSize = fontSize,
                            onLinkClick = onLinkClick,
                            xmlRenderer = xmlRenderer,
                            xmlStream = xmlStreamsByIndex[index],
                            enableDialogs = enableDialogs,
                            fillMaxWidth = fillMaxWidth,
                            isLastNode = index == lastRenderableIndex,
                        )
                    }
                }

                is MarkdownGroupedItem.Group -> {
                    val groupKey = "group-$rendererId-${item.stableKey}"
                    val firstNodeKey = nodeKeyForIndex(item.startIndex)
                    key(groupKey) {
                        nodeGrouper.RenderGroup(
                            group = item,
                            nodes = nodes,
                            rendererId = rendererId,
                            isVisible = nodeAnimationStates[firstNodeKey] ?: true,
                            isLastNode = item.endIndexInclusive == lastRenderableIndex,
                            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
                            textColor = textColor,
                            onLinkClick = onLinkClick,
                            xmlRenderer = xmlRenderer,
                            xmlStreamResolver = { idx -> xmlStreamsByIndex[idx] },
                            fillMaxWidth = fillMaxWidth,
                            fontSize = fontSize,
                        )
                    }
                }
            }
        }
    }
}

/** 批量节点更新器 - 负责将原始节点列表的更新批量应用到渲染节点列表 */
private class BatchNodeUpdater(
        private val nodes: SnapshotStateList<MarkdownNode>,
        private val renderNodes: SnapshotStateList<MarkdownNodeStable>,
        private val conversionCache: MutableMap<Int, Pair<Int, MarkdownNodeStable>>,
        private val nodeAnimationStates: MutableMap<String, Boolean>,
        private val xmlNodeStreams: MutableMap<Int, Stream<String>>,
        private val rendererId: String,
        private val isInterceptedStream: Stream<Char>,
        private val scope: CoroutineScope
) {
    private var updateJob: Job? = null

    fun startBatchUpdates() {
        if (updateJob?.isActive == true) {
            return
        }

        // 创建新的更新任务
        updateJob =
                scope.launch {
                    isInterceptedStream.lock()
                    delay(RENDER_INTERVAL_MS)
                    isInterceptedStream.unlock()

                    performBatchUpdate()
                    updateJob = null
                }
    }

    private fun performBatchUpdate() {
        synchronizeRenderNodes(
            nodes,
            renderNodes,
            conversionCache,
            nodeAnimationStates,
            xmlNodeStreams,
            rendererId,
            scope
        )
    }
}

/** 同步渲染节点 - 确保所有节点都被渲染 在流处理完成或出现异常时调用，确保最终状态一致 */
private fun synchronizeRenderNodes(
    nodes: SnapshotStateList<MarkdownNode>,
    renderNodes: SnapshotStateList<MarkdownNodeStable>,
    conversionCache: MutableMap<Int, Pair<Int, MarkdownNodeStable>>,
    nodeAnimationStates: MutableMap<String, Boolean>,
    xmlNodeStreams: MutableMap<Int, Stream<String>>,
    rendererId: String,
    scope: CoroutineScope
) {
    val keysToAnimate = mutableListOf<String>()

    // 1. 更新现有节点并添加新节点
    nodes.forEachIndexed { i, sourceNode ->
        val contentLength = sourceNode.content.length
        val cached = conversionCache[i]

        val stableNode = if (cached != null && cached.first == contentLength) {
            cached.second
        } else {
            sourceNode.toStableNode().also {
                conversionCache[i] = contentLength to it
            }
        }

        if (i < renderNodes.size) {
            // 如果节点内容发生变化，则更新
            if (renderNodes[i] != stableNode) {
                renderNodes[i] = stableNode
                // AppLogger.d(TAG, "【渲染性能】最终同步：替换节点 at index $i")
            }
        } else {
            // 添加新节点
            renderNodes.add(stableNode)
            val nodeKey = "node-$rendererId-$i"
            nodeAnimationStates[nodeKey] = false // 准备播放动画
            keysToAnimate.add(nodeKey)
        }
    }

    // 2. 如果源列表变小，则移除多余的节点
    while (renderNodes.size > nodes.size) {
        renderNodes.removeAt(renderNodes.lastIndex)
    }

    // 3. 清理多余的缓存条目
    if (nodes.size < conversionCache.size) {
        (nodes.size until conversionCache.size).forEach {
            conversionCache.remove(it)
        }
    }

    // 4. 清理已被移除节点的 XML 子流
    val keysToRemove = xmlNodeStreams.keys.filter { it !in nodes.indices }
    keysToRemove.forEach { xmlNodeStreams.remove(it) }


    // 启动所有新标记节点的动画
    if (keysToAnimate.isNotEmpty()) {
        scope.launch {
            // 等待下一帧，让 isVisible = false 的状态先生效
            delay(16.milliseconds)
            keysToAnimate.forEach { key ->
                // 检查以防万一节点在此期间被移除
                if (nodeAnimationStates.containsKey(key)) {
                    nodeAnimationStates[key] = true
                }
            }
        }
    }
}

/** 从链接Markdown中提取链接文本 例如：从 [链接文本](https://example.com) 中提取 "链接文本" */
internal fun extractLinkText(linkContent: String): String {
    val startBracket = linkContent.indexOf('[')
    val endBracket = linkContent.indexOf(']')
    val result =
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                linkContent.substring(startBracket + 1, endBracket)
            } else {
                linkContent
            }
    return result
}

/** 从链接Markdown中提取链接URL 例如：从 [链接文本](https://example.com) 中提取 "https://example.com" */
internal fun extractLinkUrl(linkContent: String): String {
    val startParenthesis = linkContent.indexOf('(')
    val endParenthesis = linkContent.indexOf(')')
    val result =
            if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
            ) {
                linkContent.substring(startParenthesis + 1, endParenthesis)
            } else {
                ""
            }
    return result
}
