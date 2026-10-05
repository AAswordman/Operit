package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.streamnative.nativeMarkdownSplitByBlock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Waifu模式消息处理器
 * 负责将AI回复按句号分割并模拟逐句发送
 */
object WaifuMessageProcessor {
    private const val URL_CHARS = "[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]"
    private const val ENTITY_PLACEHOLDER_PREFIX = "{WAIFUENTITY:"
    private const val ENTITY_PLACEHOLDER_SUFFIX = "}"
    private const val MAX_TYPING_DELAY_MS = 3000L
    private val FENCED_CODE_BLOCK_REGEX =
        Regex("""(?m)^[ \t]{0,3}```[^`\r\n]*\r?\n[\s\S]*?^[ \t]{0,3}```[ \t]*\r?$""")
    private val UNCLOSED_FENCED_CODE_BLOCK_REGEX =
        Regex("""(?m)^[ \t]{0,3}```[^`\r\n]*(?:\r?\n|$)[\s\S]*$""")
    private val SENTENCE_SPLIT_REGEX =
        Regex("(?<=[。！？~～])(?![\"'”’」』])|(?<=[!?])(?![\"'”’」』])|(?<=\\.)(?![.\\d\"'”’」』])|(?<=\\.)$|(?<=\\.{3})|(?<=[…](?![…]))")
    private val SENTENCE_END_REGEX =
        Regex("(?:[。！？~～.!?…]|\\.{3})\\s*$")
    private val HORIZONTAL_RULE_REGEX = Regex("^[-_*]{3,}$")
    private val MARKDOWN_ENTITY_REGEX = Regex("""!?\[[^\]]*?\]\([^)]*?\)""")
    private val INLINE_LATEX_REGEX =
        Regex("""(?<![\\$])\$(?![\s$])[^$\r\n]+?(?<!\s)\$(?![\d$])|\\\([\s\S]*?\\\)""")
    private val ORDERED_LIST_DOT_REGEX =
        Regex("""(?m)(?<![\p{L}\p{N}_])(\d+)\.(?=[ \t]|$)""")
    private val XML_TOKEN_REGEX = Regex(
        """<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|</?([A-Za-z][A-Za-z0-9_]*)(?=\s|/?>)(?:[^<>"']|"[^"]*"|'[^']*')*>"""
    )
    private val UNTERMINATED_MARKDOWN_LINE_REGEX =
        Regex("""^[ \t]{0,3}(?:\d+\.[ \t]*|[-*+](?:[ \t]+|$)|>[ \t]?|#{1,6}[ \t]+).*$""")
    private val BARE_URL_REGEX = Regex("""https?://$URL_CHARS+""")
    private val EMAIL_ADDRESS_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val DOMAIN_URL_REGEX =
        Regex("""(?<![@\w])(?:www\.)?(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}(?::\d+)?(?:[/?#]$URL_CHARS*)?""")
    private val ENTITY_PLACEHOLDER_REGEX =
        Regex("${Regex.escape(ENTITY_PLACEHOLDER_PREFIX)}\\d+${Regex.escape(ENTITY_PLACEHOLDER_SUFFIX)}")
    
    private var customEmojiRepository: CustomEmojiRepository? = null
    private var activePromptManager: ActivePromptManager? = null

    class StreamingSession(
        private val removePunctuation: Boolean = false
    ) {
        private val emittedSegments = mutableListOf<String>()

        fun collectStableSegments(content: String): List<String> {
            return collectSegments(
                splitMessageBySentencesInternal(
                    content = buildRenderableContentForWaifu(content),
                    removePunctuation = removePunctuation,
                    includeTrailingIncomplete = false,
                )
            )
        }

        fun collectFinalSegments(content: String): List<String> {
            return collectSegments(
                splitMessageBySentencesInternal(
                    content = buildRenderableContentForWaifu(content),
                    removePunctuation = removePunctuation,
                    includeTrailingIncomplete = true,
                )
            )
        }

        private fun collectSegments(segments: List<String>): List<String> {
            if (segments.isEmpty()) {
                return emptyList()
            }

            if (segments.size < emittedSegments.size) {
                AppLogger.w(
                    "WaifuMessageProcessor",
                    "流式分句结果短于已输出结果，忽略本次快照: emitted=${emittedSegments.size}, current=${segments.size}"
                )
                return emptyList()
            }

            val prefixMatches =
                emittedSegments.indices.all { index ->
                    emittedSegments[index] == segments[index]
                }
            if (!prefixMatches) {
                AppLogger.w(
                    "WaifuMessageProcessor",
                    "流式分句前缀发生变化，忽略不可回滚的增量输出"
                )
                return emptyList()
            }

            val newSegments = segments.drop(emittedSegments.size)
            emittedSegments.addAll(newSegments)
            return newSegments
        }
    }

    fun streamSegments(
        sourceStream: Stream<String>,
        removePunctuation: Boolean = false,
    ): Stream<String> = stream {
        val session = StreamingSession(removePunctuation = removePunctuation)
        val renderableBuffer = StringBuilder()

        suspend fun appendRenderableText(text: String) {
            if (text.isEmpty()) {
                return
            }
            renderableBuffer.append(text)
            session.collectStableSegments(renderableBuffer.toString()).forEach { emit(it) }
        }

        sourceStream.nativeMarkdownSplitByBlock().collect { blockGroup ->
            val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
            when (blockType) {
                MarkdownProcessorType.XML_BLOCK -> {
                    val blockBuilder = StringBuilder()
                    blockGroup.stream.collect { blockBuilder.append(it) }
                    val block = blockBuilder.toString()
                    if (shouldPreserveXmlBlockForWaifu(block)) {
                        appendRenderableText(block)
                    }
                }
                MarkdownProcessorType.BLOCK_QUOTE,
                MarkdownProcessorType.UNORDERED_LIST -> {
                    val marker =
                        if (blockType == MarkdownProcessorType.BLOCK_QUOTE) "> " else "- "
                    val restorer = MarkdownBlockMarkerRestorer(marker)
                    blockGroup.stream.collect { piece ->
                        appendRenderableText(restorer.append(piece))
                    }
                    appendRenderableText(restorer.finish())
                }
                MarkdownProcessorType.CODE_BLOCK,
                MarkdownProcessorType.TABLE,
                MarkdownProcessorType.IMAGE -> {
                    val blockBuilder = StringBuilder()
                    blockGroup.stream.collect { blockBuilder.append(it) }
                    appendRenderableText(blockBuilder.toString())
                }
                MarkdownProcessorType.BLOCK_LATEX -> {
                    val blockBuilder = StringBuilder()
                    blockGroup.stream.collect { blockBuilder.append(it) }
                    // 原生 $$ 公式块会剥离分隔符，括号公式块会保留分隔符。
                    // 补回缺失的 $$，让二次分块和气泡渲染继续识别为块公式。
                    appendRenderableText(ensureBlockLatexDelimiters(blockBuilder.toString()))
                }

                else -> {
                    blockGroup.stream.collect { piece ->
                        appendRenderableText(piece)
                    }
                }
            }
        }

        session.collectFinalSegments(renderableBuffer.toString()).forEach { emit(it) }
    }

    /**
     * Ensure a BLOCK_LATEX body carries a recognized block delimiter pair.
     *
     * `StreamMarkdownBlockLaTeXPlugin` strips the `$$…$$` delimiters from its output while
     * `StreamMarkdownBlockBracketLaTeXPlugin` keeps `\[…\]`. In Waifu mode the block content
     * is flattened back into a raw string buffer that is later re-parsed by the same block
     * splitter and, ultimately, rendered by `StreamMarkdownRenderer`. Without delimiters the
     * body is indistinguishable from plain text and the LaTeX renderer never fires. This
     * helper re-attaches `$$` around the body when neither `$$…$$` nor `\[…\]` is present.
     * Leading and trailing whitespace are preserved so the block still nests correctly inside
     * the surrounding buffer.
     */
    internal fun ensureBlockLatexDelimiters(rawContent: String): String {
        if (rawContent.isEmpty()) return rawContent
        val start = rawContent.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return rawContent
        val end = rawContent.indexOfLast { !it.isWhitespace() } + 1
        val body = rawContent.substring(start, end)
        val hasBracketDelims = body.length >= 4 && body.startsWith("\\[") && body.endsWith("\\]")
        val hasDollarDelims = body.length >= 4 && body.startsWith("$$") && body.endsWith("$$")
        if (hasBracketDelims || hasDollarDelims) {
            return rawContent
        }
        // A lone delimiter marker has no interior to wrap; leave it untouched.
        if (body == "$$" || body == "\\[" || body == "\\]") {
            return rawContent
        }
        return rawContent.substring(0, start) + "$$" + body + "$$" + rawContent.substring(end)
    }

    internal fun calculateTypingDelayMs(
        segmentLength: Int,
        charDelayMs: Int,
        isFirstSegment: Boolean,
    ): Long {
        if (isFirstSegment || segmentLength <= 0 || charDelayMs <= 0) {
            return 0L
        }

        return (segmentLength.toLong() * charDelayMs.toLong()).coerceAtMost(MAX_TYPING_DELAY_MS)
    }

    fun streamSegmentsWithTypingQueue(
        sourceStream: Stream<String>,
        removePunctuation: Boolean = false,
        charDelayMs: Int,
    ): Stream<String> = stream {
        coroutineScope {
            val segmentQueue = Channel<String>(Channel.UNLIMITED)
            val producerJob = launch {
                try {
                    streamSegments(
                        sourceStream = sourceStream,
                        removePunctuation = false,
                    ).collect { segment ->
                        val queuedSegment =
                            if (removePunctuation) {
                                removeSentenceEndPunctuation(segment)
                            } else {
                                segment
                            }
                        if (queuedSegment.isNotBlank()) {
                            segmentQueue.send(queuedSegment)
                        }
                    }
                } finally {
                    segmentQueue.close()
                }
            }

            var isFirstSegment = true
            for (segment in segmentQueue) {
                val waitMs =
                    calculateTypingDelayMs(
                        segmentLength = segment.length,
                        charDelayMs = charDelayMs,
                        isFirstSegment = isFirstSegment,
                    )
                if (waitMs > 0L) {
                    delay(waitMs)
                }

                emit(segment)
                isFirstSegment = false
            }

            producerJob.join()
        }
    }

    fun streamTtsText(sourceStream: Stream<String>): Stream<Char> = stream {
        var lastCharWasNewline = true

        suspend fun emitText(text: String) {
            text.forEach { char ->
                val isCurrentCharNewline = char == '\n'
                if (isCurrentCharNewline) {
                    if (!lastCharWasNewline) {
                        emit('\n')
                        lastCharWasNewline = true
                    }
                } else if (char.isWhitespace() && lastCharWasNewline) {
                    Unit
                } else {
                    emit(char)
                    lastCharWasNewline = false
                }
            }
        }

        sourceStream.nativeMarkdownSplitByBlock().collect { blockGroup ->
            val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
            when (blockType) {
                MarkdownProcessorType.XML_BLOCK,
                MarkdownProcessorType.CODE_BLOCK -> {
                    blockGroup.stream.collect { }
                    if (!lastCharWasNewline) {
                        emit('\n')
                        lastCharWasNewline = true
                    }
                }

                else -> {
                    blockGroup.stream.collect { piece ->
                        emitText(piece)
                    }
                }
            }
        }
    }

    private fun removeSentenceEndPunctuation(sentence: String): String {
        val trimmed = sentence.trim()
        return if (trimmed.endsWith("...")) {
            trimmed
        } else {
            trimmed.replace(Regex("[。！？.!?]+$"), "").trim()
        }
    }
    
    /**
     * 初始化处理器（需要在应用启动时调用）
     */
    fun initialize(appContext: Context) {
        customEmojiRepository = CustomEmojiRepository.getInstance(appContext)
        activePromptManager = ActivePromptManager.getInstance(appContext)
    }
    
    /**
     * 将完整的消息按句号分割成句子
     * @param content 完整的消息内容
     * @param removePunctuation 是否移除标点符号
     * @return 分割后的句子列表
     */
    fun splitMessageBySentences(content: String, removePunctuation: Boolean = false): List<String> {
        return splitMessageBySentencesInternal(
            content = buildRenderableContentForWaifu(content),
            removePunctuation = removePunctuation,
            includeTrailingIncomplete = true,
        )
    }

    fun splitStableMessageSegments(content: String, removePunctuation: Boolean = false): List<String> {
        return splitMessageBySentencesInternal(
            content = buildRenderableContentForWaifu(content),
            removePunctuation = removePunctuation,
            includeTrailingIncomplete = false,
        )
    }

    private fun splitMessageBySentencesInternal(
        content: String,
        removePunctuation: Boolean,
        includeTrailingIncomplete: Boolean,
    ): List<String> {
        if (content.isBlank()) return emptyList()

        val entities = mutableListOf<String>()
        val entityPrefix = unusedPlaceholderPrefix(content, ENTITY_PLACEHOLDER_PREFIX)

        fun createPlaceholder(value: String): String {
            val placeholder = "$entityPrefix${entities.size}$ENTITY_PLACEHOLDER_SUFFIX"
            entities.add(value)
            return placeholder
        }

        fun protectMatches(source: String, regex: Regex): String =
            regex.replace(source) { matchResult ->
                val (protectedValue, trailingPunctuation) =
                    splitTrailingProtectedText(matchResult.value)

                if (protectedValue.isBlank()) {
                    matchResult.value
                } else {
                    createPlaceholder(protectedValue) + trailingPunctuation
                }
            }

        // 1. 将Markdown实体、URL和邮箱替换为占位符，以保护它们不被错误分割
        var contentWithPlaceholders =
            MARKDOWN_ENTITY_REGEX.replace(content) { createPlaceholder(it.value) }
        contentWithPlaceholders =
            INLINE_LATEX_REGEX.replace(contentWithPlaceholders) { createPlaceholder(it.value) }
        contentWithPlaceholders = protectMatches(contentWithPlaceholders, BARE_URL_REGEX)
        contentWithPlaceholders = protectMatches(contentWithPlaceholders, EMAIL_ADDRESS_REGEX)
        contentWithPlaceholders = protectMatches(contentWithPlaceholders, DOMAIN_URL_REGEX)
        
        // 2. 首先分离表情包和文本内容（在处理占位符版本的内容上）
        val segments = splitIntoSegments(contentWithPlaceholders)
        val hasFollowingStableBoundarySegment =
            BooleanArray(segments.size).also { flags ->
                var seenStableBoundarySegment = false
                for (index in segments.indices.reversed()) {
                    flags[index] = seenStableBoundarySegment
                    if (
                        segmentProducesOutput(segments[index]) &&
                        segments[index].blockType.canCloseStableTextAtBlockBoundary()
                    ) {
                        seenStableBoundarySegment = true
                    }
                }
            }

        val resultWithPlaceholders = mutableListOf<String>()

        for ((segmentIndex, segment) in segments.withIndex()) {
            if (segment.isProtected) {
                val block = segment.content.trim('\n', '\r')
                if (block.isNotBlank()) {
                    resultWithPlaceholders.add(block)
                }
                continue
            }

            val contentWithoutThinking = ChatUtils.removeThinkingContent(segment.content)
            if (contentWithoutThinking.isBlank()) continue

            val separatedContent = separateEmotionAndText(contentWithoutThinking)

            for (item in separatedContent) {
                // 如果这个item是表情包（包含![开头的），直接添加
                if (item.startsWith("![")) {
                    resultWithPlaceholders.add(item)
                    continue
                }

                // 对于文本内容，进行正常的清理和分句处理
                val cleanedContent = cleanContentForWaifuDisplay(item)

                if (cleanedContent.isBlank()) continue

                var sentences = splitPlainTextIntoSentences(cleanedContent, removePunctuation = removePunctuation)

                if (shouldSplitStructuredMarkdownLines(item, sentences)) {
                    sentences = splitStructuredMarkdownLines(item, removePunctuation = removePunctuation)
                }

                if (!includeTrailingIncomplete && sentences.isNotEmpty()) {
                    val unclosedInlineMarkdownStart = findLastUnclosedInlineMarkdownStart(item)
                    if (unclosedInlineMarkdownStart != null) {
                        val stableRawContent = item.substring(0, unclosedInlineMarkdownStart)
                        val stableCleanedContent = cleanContentForWaifuDisplay(stableRawContent)
                        sentences =
                            splitStableSentencesForRawContent(
                                rawContent = stableRawContent,
                                cleanedContent = stableCleanedContent,
                                removePunctuation = removePunctuation,
                                segment = segment,
                                hasFollowingStableBoundarySegment =
                                    hasFollowingStableBoundarySegment[segmentIndex]
                            )
                    } else if (
                        shouldHoldLastStableSentence(
                            rawContent = item,
                            cleanedContent = cleanedContent,
                            segment = segment,
                            hasFollowingStableBoundarySegment =
                                hasFollowingStableBoundarySegment[segmentIndex]
                        )
                    ) {
                        sentences = sentences.dropLast(1)
                    }
                }

                resultWithPlaceholders.addAll(
                    sentences.map { restoreMarkdownBlockMarkers(it, segment.blockType) }
                )
            }
        }
        
        // 3.5. 合并仅包含标点符号的句子到前一句
        val mergedResultWithPlaceholders = mergePunctuationOnlySegments(resultWithPlaceholders)
        
        // 4. 将占位符恢复为原始的Markdown实体
        val finalResult = mergedResultWithPlaceholders.map { sentence ->
            var currentSentence = sentence
            val placeholderRegex =
                Regex(
                    "${Regex.escape(entityPrefix)}(\\d+)${Regex.escape(ENTITY_PLACEHOLDER_SUFFIX)}"
                )
            
            // 循环替换，以处理一个句子中可能存在的多个占位符
            while (placeholderRegex.containsMatchIn(currentSentence)) {
                currentSentence = placeholderRegex.replace(currentSentence) { matchResult ->
                    val index = matchResult.groupValues[1].toInt()
                    
                    // 前缀已避开原文，匹配到的索引均由本次分句生成。
                    entities[index]
                }
            }
            currentSentence
        }

        return finalResult
    }

    private fun splitTrailingProtectedText(value: String): Pair<String, String> {
        if (value.isEmpty()) {
            return "" to ""
        }

        var splitIndex = value.length
        while (splitIndex > 0 && value[splitIndex - 1] in TRAILING_PROTECTED_TEXT_CHARS) {
            splitIndex--
        }

        return value.substring(0, splitIndex) to value.substring(splitIndex)
    }

    private fun unusedPlaceholderPrefix(content: String, initialPrefix: String): String {
        var prefix = initialPrefix
        while (content.contains(prefix)) prefix = "{$prefix"
        return prefix
    }

    /** 每行只恢复一次结构标记，并缓冲跨 chunk 的标记前缀。 */
    internal class MarkdownBlockMarkerRestorer(private val marker: String) {
        private var atLineStart = true
        private val pendingPrefix = StringBuilder()

        fun append(piece: String): String = buildString {
            piece.forEach { char ->
                if (!atLineStart) {
                    append(char)
                    atLineStart = char == '\n'
                    return@forEach
                }
                pendingPrefix.append(char)
                val prefix = pendingPrefix.toString()
                if (marker.startsWith(prefix)) {
                    if (prefix == marker) {
                        append(prefix)
                        pendingPrefix.clear()
                        atLineStart = false
                    }
                } else {
                    if (prefix != "\n" && prefix != "\r") append(marker)
                    append(prefix)
                    pendingPrefix.clear()
                    atLineStart = char == '\n'
                }
            }
        }

        fun finish(): String = pendingPrefix.toString().also { pendingPrefix.clear() }
    }

    internal fun restoreMarkdownBlockMarkers(
        content: String,
        blockType: MarkdownProcessorType,
    ): String {
        val marker = when (blockType) {
            MarkdownProcessorType.BLOCK_QUOTE -> "> "
            MarkdownProcessorType.UNORDERED_LIST -> "- "
            else -> return content
        }
        val restorer = MarkdownBlockMarkerRestorer(marker)
        return restorer.append(content) + restorer.finish()
    }

    internal fun splitPlainTextIntoSentences(
        cleanedContent: String,
        removePunctuation: Boolean,
    ): List<String> {
        if (cleanedContent.isBlank()) {
            return emptyList()
        }

        // 只替换序号后的点，并选取原文未使用的前缀，避免重复序号和字面量冲突。
        val listDotPrefix = unusedPlaceholderPrefix(cleanedContent, "{WAIFULISTDOT:")
        var markerIndex = 0
        val contentWithProtectedListDots =
            ORDERED_LIST_DOT_REGEX.replace(cleanedContent) { match ->
                "${match.groupValues[1]}$listDotPrefix${markerIndex++}}"
            }
        val listDotPlaceholderRegex = Regex("${Regex.escape(listDotPrefix)}\\d+}")
        var sentences =
            contentWithProtectedListDots.split(SENTENCE_SPLIT_REGEX)
                .filter { it.isNotBlank() }
                .map { it.trim() }

        if (removePunctuation) {
            sentences =
                sentences
                    .map { sentence ->
                        if (sentence.endsWith("...")) {
                            sentence.trim()
                        } else {
                            sentence.replace(Regex("[。！？.!?]+$"), "").trim()
                        }
                    }
                    .filter { it.isNotBlank() }
        }

        return sentences.map { sentence ->
            listDotPlaceholderRegex.replace(sentence, ".")
        }
    }

    private fun mergePunctuationOnlySegments(segments: List<String>): MutableList<String> {
        val mergedSegments = mutableListOf<String>()
        if (segments.isEmpty()) {
            return mergedSegments
        }

        mergedSegments.add(segments[0])
        for (i in 1 until segments.size) {
            val currentSentence = segments[i]
            val trimmedSentence = currentSentence.trim()
            if (trimmedSentence.isNotEmpty() && trimmedSentence.matches(Regex("^[。！？~～.!?…]+$"))) {
                val lastIndex = mergedSegments.size - 1
                val lastSentence = mergedSegments[lastIndex]
                if (!lastSentence.contains('\n') && !lastSentence.contains('\r')) {
                    mergedSegments[lastIndex] = lastSentence + currentSentence
                } else {
                    mergedSegments.add(currentSentence)
                }
            } else {
                mergedSegments.add(currentSentence)
            }
        }

        return mergedSegments
    }

    private fun shouldSplitStructuredMarkdownLines(
        content: String,
        sentences: List<String>,
    ): Boolean {
        if (sentences.size != 1) {
            return false
        }

        val nonEmptyLines =
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

        if (nonEmptyLines.size < 2) {
            return false
        }

        return nonEmptyLines.any { line ->
            isUrlOrEmailLine(cleanStructuredMarkdownLine(line))
        }
    }

    private fun splitStructuredMarkdownLines(
        content: String,
        removePunctuation: Boolean,
    ): List<String> {
        val results = mutableListOf<String>()

        content.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty() || HORIZONTAL_RULE_REGEX.matches(trimmedLine)) {
                return@forEach
            }

            val cleanedLine = cleanStructuredMarkdownLine(trimmedLine)
            if (cleanedLine.isBlank()) {
                return@forEach
            }

            val lineContent = cleanContentForWaifuDisplay(cleanedLine)
            results.addAll(splitPlainTextIntoSentences(lineContent, removePunctuation))
        }

        return mergePunctuationOnlySegments(results)
    }

    private fun cleanStructuredMarkdownLine(line: String): String = line.trim()

    private fun getLastVisibleLine(content: String): String =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
            ?: ""

    private fun lineAllowsStableWithoutSentenceEnding(line: String): Boolean {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) {
            return false
        }

        if (
            trimmedLine.startsWith("```") ||
            trimmedLine.startsWith("|") ||
            trimmedLine.startsWith("$$") ||
            Regex("^(?:#+\\s*|>\\s*|[-*+]\\s+|\\d+\\.\\s+)").containsMatchIn(trimmedLine)
        ) {
            return true
        }

        return isUrlOrEmailLine(cleanStructuredMarkdownLine(trimmedLine))
    }

    private fun isUrlOrEmailLine(line: String): Boolean {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) {
            return false
        }

        return BARE_URL_REGEX.containsMatchIn(trimmedLine) ||
            DOMAIN_URL_REGEX.containsMatchIn(trimmedLine) ||
            EMAIL_ADDRESS_REGEX.containsMatchIn(trimmedLine) ||
            ENTITY_PLACEHOLDER_REGEX.containsMatchIn(trimmedLine)
    }

    internal fun hasStableSentenceEnding(content: String): Boolean {
        val trimmed = content.trimEnd()
        // 流式尾部的 1. 仍是待完成的序号，不能因点号把它单独提交。
        if (ORDERED_LIST_DOT_REGEX.findAll(trimmed).any { it.range.last == trimmed.lastIndex }) {
            return false
        }
        return SENTENCE_END_REGEX.containsMatchIn(trimmed)
    }

    private fun splitStableSentencesForRawContent(
        rawContent: String,
        cleanedContent: String,
        removePunctuation: Boolean,
        segment: Segment,
        hasFollowingStableBoundarySegment: Boolean,
    ): List<String> {
        if (cleanedContent.isBlank()) {
            return emptyList()
        }

        var stableSentences =
            splitPlainTextIntoSentences(cleanedContent, removePunctuation = removePunctuation)

        if (shouldSplitStructuredMarkdownLines(rawContent, stableSentences)) {
            stableSentences =
                splitStructuredMarkdownLines(rawContent, removePunctuation = removePunctuation)
        }

        if (
            stableSentences.isNotEmpty() &&
            shouldHoldLastStableSentence(
                rawContent = rawContent,
                cleanedContent = cleanedContent,
                segment = segment,
                hasFollowingStableBoundarySegment = hasFollowingStableBoundarySegment
            )
        ) {
            stableSentences = stableSentences.dropLast(1)
        }

        return stableSentences
    }

    private fun shouldHoldLastStableSentence(
        rawContent: String,
        cleanedContent: String,
        segment: Segment,
        hasFollowingStableBoundarySegment: Boolean,
    ): Boolean {
        val lastRawLine = rawContent.substringAfterLast('\n')
        // 行首结构行在换行到达前仍可能增长，不能把当前快照作为稳定前缀提交。
        if (
            !rawContent.endsWith('\n') &&
            !segment.content.endsWith('\n') &&
            !hasFollowingStableBoundarySegment &&
            UNTERMINATED_MARKDOWN_LINE_REGEX.matches(lastRawLine)
        ) {
            return true
        }

        return !hasStableSentenceEnding(cleanedContent) &&
            !lineAllowsStableWithoutSentenceEnding(getLastVisibleLine(rawContent)) &&
            !segment.canUseBlockBoundaryAsStableEnding(
                hasFollowingStableBoundarySegment = hasFollowingStableBoundarySegment
            )
    }

    internal fun findLastUnclosedInlineMarkdownStart(content: String): Int? {
        val trimmedContent = content.trimEnd()
        if (trimmedContent.isEmpty()) {
            return null
        }

        val unclosedDelimiterStart =
            listOf("**", "__", "~~", "`")
                .mapNotNull { delimiter -> findLastUnclosedDelimiterStart(trimmedContent, delimiter) }
                .maxOrNull()
        val unclosedLatexStart = findLastUnclosedParenLatexStart(trimmedContent)
        val unclosedLinkStart = findLastUnclosedMarkdownLinkStart(trimmedContent)
        val unclosedDollarLatexStart = findLastUnclosedDollarLatexStart(trimmedContent)
        return listOfNotNull(
            unclosedDelimiterStart, unclosedLatexStart, unclosedLinkStart, unclosedDollarLatexStart
        ).maxOrNull()
    }

    private fun findLastUnclosedDollarLatexStart(content: String): Int? {
        val closedFormulas = INLINE_LATEX_REGEX.findAll(content).iterator()
        var closedFormula = if (closedFormulas.hasNext()) closedFormulas.next() else null
        var opening: Int? = null
        content.forEachIndexed { index, char ->
            if (char != '$' || isEscaped(content, index)) return@forEachIndexed
            while (closedFormula != null && index > closedFormula!!.range.last) {
                closedFormula = if (closedFormulas.hasNext()) closedFormulas.next() else null
            }
            if (closedFormula?.range?.contains(index) == true) return@forEachIndexed
            val previous = content.getOrNull(index - 1)
            val next = content.getOrNull(index + 1)
            if (previous == '$' || next == '$') return@forEachIndexed
            if (opening == null) {
                // 数字紧跟单个美元符号时按金额处理；完整数字公式由行内匹配保护。
                if (next == null || (!next.isWhitespace() && !next.isDigit())) opening = index
            } else if (previous?.isWhitespace() == false && next?.isDigit() != true) {
                opening = null
            }
        }
        return opening
    }

    private fun findLastUnclosedParenLatexStart(content: String): Int? {
        val unmatchedOpeners = mutableListOf<Int>()
        Regex("""(?<!\\)\\[()]""").findAll(content).forEach { match ->
            if (match.value.endsWith('(')) {
                unmatchedOpeners.add(match.range.first)
            } else if (unmatchedOpeners.isNotEmpty()) {
                unmatchedOpeners.removeAt(unmatchedOpeners.lastIndex)
            }
        }
        return unmatchedOpeners.lastOrNull()
    }

    private fun findLastUnclosedMarkdownLinkStart(content: String): Int? {
        val openingBracket = content.lastIndexOf('[')
        if (openingBracket < 0) {
            return null
        }
        val closingBracket = content.indexOf(']', openingBracket)
        if (closingBracket < 0) {
            return if (openingBracket > 0 && content[openingBracket - 1] == '!') openingBracket - 1 else openingBracket
        }
        if (content.getOrNull(closingBracket + 1) != '(') {
            return null
        }
        if (content.indexOf(')', closingBracket + 2) >= 0) {
            return null
        }
        return if (openingBracket > 0 && content[openingBracket - 1] == '!') openingBracket - 1 else openingBracket
    }

    private fun findLastUnclosedDelimiterStart(content: String, delimiter: String): Int? {
        val starts = mutableListOf<Int>()
        var index = 0
        while (index <= content.length - delimiter.length) {
            val foundIndex = content.indexOf(delimiter, startIndex = index)
            if (foundIndex < 0) {
                break
            }

            if (!isEscaped(content, foundIndex)) {
                starts.add(foundIndex)
            }
            index = foundIndex + delimiter.length
        }

        if (starts.size % 2 == 0) {
            return null
        }

        return starts.lastOrNull()
    }

    private fun isEscaped(content: String, index: Int): Boolean {
        var slashCount = 0
        var cursor = index - 1
        while (cursor >= 0 && content[cursor] == '\\') {
            slashCount += 1
            cursor -= 1
        }

        return slashCount % 2 == 1
    }

    private fun segmentProducesOutput(segment: Segment): Boolean {
        if (segment.isProtected) {
            return segment.content.trim('\n', '\r').isNotBlank()
        }

        val contentWithoutThinking = ChatUtils.removeThinkingContent(segment.content)
        if (contentWithoutThinking.isBlank()) {
            return false
        }

        return separateEmotionAndText(contentWithoutThinking).any { item ->
            item.startsWith("![") || cleanContentForWaifuDisplay(item).isNotBlank()
        }
    }

    fun buildRenderableContentForWaifu(content: String): String =
        buildContentForWaifu(content, preservePluginXml = true)

    private fun buildContentForWaifu(content: String, preservePluginXml: Boolean): String {
        if (content.isBlank()) return ""
        val builder = StringBuilder(content.length)
        var cursor = 0
        XML_TOKEN_REGEX.findAll(content).forEach { match ->
            if (match.range.first < cursor) return@forEach
            builder.append(content, cursor, match.range.first)
            val rawTagName = match.groupValues[1]
            if (rawTagName.isEmpty() || match.value.startsWith("</")) {
                cursor = match.range.last + 1
                return@forEach
            }
            val blockEnd = findXmlBlockEnd(content, match.range.first)
            val tagName = ChatMarkupRegex.normalizeToolLikeTagName(rawTagName) ?: rawTagName
            if (preservePluginXml && blockEnd != null && isRenderablePluginXmlTag(tagName)) {
                builder.append(content, match.range.first, blockEnd)
            }
            // 未闭合 XML 仍属于该块，不能把内部内容当作普通回复输出。
            cursor = blockEnd ?: content.length
        }
        builder.append(content, cursor, content.length)
        return builder.toString()
    }

    /** 同名标签按深度配对；属性、注释和 CDATA 中的伪结束标签不参与配对。 */
    private fun findXmlBlockEnd(content: String, start: Int): Int? {
        val opening = XML_TOKEN_REGEX.find(content, start)
            ?.takeIf { it.range.first == start && !it.value.startsWith("</") }
            ?: return null
        val rawTagName = opening.groupValues[1]
        if (rawTagName.isEmpty()) return null
        if (opening.value.endsWith("/>")) return opening.range.last + 1
        var depth = 1
        XML_TOKEN_REGEX.findAll(content, opening.range.last + 1).forEach { match ->
            if (match.groupValues[1] != rawTagName) return@forEach
            if (match.value.startsWith("</")) {
                depth--
                if (depth == 0) return match.range.last + 1
            } else if (!match.value.endsWith("/>")) {
                depth++
            }
        }
        return null
    }

    /**
     * 清理内容中的状态标签和XML标签，只保留纯文本
     */
    fun cleanContentForWaifu(content: String): String {
        val sanitizedContent =
            ChatUtils.removeThinkingContent(
                ChatUtils.stripGeminiThoughtSignatureMeta(
                    buildContentForWaifu(content, preservePluginXml = false)
                )
            )

        return sanitizedContent
            .replace(FENCED_CODE_BLOCK_REGEX, " ")
            .replace(UNCLOSED_FENCED_CODE_BLOCK_REGEX, " ")
            // 移除状态标签
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            // 移除工具标签
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            // 移除工具结果标签
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            // 移除emotion标签（因为已经在processEmotionTags中处理过了）
            .replace(ChatMarkupRegex.emotionTag, "")

            // --- 新增：移除Markdown相关标记 ---
            // 1. 移除图片和链接，保留替代文本或链接文本
            .replace(Regex("!?\\[(.*?)\\]\\(.*?\\)"), "$1")
            // 2. 移除标题标记
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            // 3. 移除引用标记
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            // 4. 移除列表标记
            .replace(Regex("^[\\*\\-\\+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // 5. 移除代码块标记
            .replace(Regex("```[a-zA-Z]*\\n?|\\n?```"), "")
            // 6. 移除加粗、斜体、删除线 (注意顺序和互斥)
            .replace(Regex("(\\*\\*\\*|___)(.+?)\\1"), "$2") // 加粗斜体
            .replace(Regex("(\\*\\*|__(?!MD_ENTITY__))(.+?)\\1"), "$2") // 加粗 (避免匹配占位符)
            .replace(Regex("(\\*|_)(.+?)\\1"), "$2")        // 斜体
            .replace(Regex("~~(.+?)~~"), "$1")              // 删除线
            // 7. 移除行内代码
            .replace(Regex("`(.+?)`"), "$1")
            // 8. 移除水平线
            .replace(Regex("^[-_*]{3,}\\s*$", RegexOption.MULTILINE), "")
            // --- Markdown移除结束 ---

            // 移除其他常见的XML标签
            .replace(ChatMarkupRegex.anyXmlTag, "")
            // 清理多余的空白
            .replace(Regex("\\s+"), " ")
            .trim()
    }



    /** 展示保留 Markdown 和已注册插件 XML；TTS 与通知仍使用纯文本清理。 */
    fun cleanContentForWaifuDisplay(content: String): String {
        val sanitizedContent =
            ChatUtils.removeThinkingContent(
                ChatUtils.stripGeminiThoughtSignatureMeta(
                    buildRenderableContentForWaifu(content)
                )
            )

        return sanitizedContent
            .replace(FENCED_CODE_BLOCK_REGEX, " ")
            .replace(UNCLOSED_FENCED_CODE_BLOCK_REGEX, " ")
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            .let(::removeUnsupportedXmlTags)
            .trim()
    }
    

    
    private data class Segment(
        val content: String,
        val isProtected: Boolean,
        val blockType: MarkdownProcessorType,
    ) {
        fun canUseBlockBoundaryAsStableEnding(hasFollowingStableBoundarySegment: Boolean): Boolean {
            return hasFollowingStableBoundarySegment || blockType.canCloseStableTextAtBlockBoundary()
        }
    }

    internal fun shouldPreserveXmlBlockForWaifu(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        val rawTagName = ChatMarkupRegex.extractOpeningTagName(trimmed) ?: return false
        val tagName = ChatMarkupRegex.normalizeToolLikeTagName(rawTagName) ?: rawTagName
        if (!isRenderablePluginXmlTag(tagName)) {
            return false
        }
        return findXmlBlockEnd(trimmed, 0) == trimmed.length
    }

    private fun isRenderablePluginXmlTag(tagName: String): Boolean {
        val normalizedTagName = tagName.trim()
        if (normalizedTagName.isBlank() || isInternalWaifuXmlTag(normalizedTagName)) {
            return false
        }
        return XmlRenderPluginRegistry.supports(normalizedTagName)
    }

    private fun isInternalWaifuXmlTag(tagName: String): Boolean {
        return when (tagName.lowercase()) {
            "think",
            "thinking",
            "tool",
            "tool_result",
            "status",
            "search",
            "meta" -> true
            else -> false
        }
    }

    private fun removeUnsupportedXmlTags(content: String): String {
        if (!content.contains('<')) {
            return content
        }
        val preservedRanges = mutableListOf<IntRange>()
        ChatMarkupRegex.anyXmlTag.findAll(content).forEach { match ->
            val rawTagName = ChatMarkupRegex.extractOpeningTagName(match.value) ?: return@forEach
            val tagName = ChatMarkupRegex.normalizeToolLikeTagName(rawTagName) ?: rawTagName
            if (!isRenderablePluginXmlTag(tagName)) {
                return@forEach
            }
            val blockEnd = findXmlBlockEnd(content, match.range.first) ?: return@forEach
            preservedRanges += match.range.first until blockEnd
        }

        val builder = StringBuilder(content.length)
        var index = 0
        ChatMarkupRegex.anyXmlTag.findAll(content).forEach { match ->
            if (preservedRanges.any { match.range.first in it }) {
                return@forEach
            }
            builder.append(content, index, match.range.first)
            index = match.range.last + 1
        }
        builder.append(content, index, content.length)
        return builder.toString()
    }

    private fun MarkdownProcessorType.canCloseStableTextAtBlockBoundary(): Boolean =
        when (this) {
            MarkdownProcessorType.HEADER,
            MarkdownProcessorType.BLOCK_QUOTE,
            MarkdownProcessorType.CODE_BLOCK,
            MarkdownProcessorType.ORDERED_LIST,
            MarkdownProcessorType.UNORDERED_LIST,
            MarkdownProcessorType.BLOCK_LATEX,
            MarkdownProcessorType.TABLE,
            MarkdownProcessorType.IMAGE -> true

            MarkdownProcessorType.HORIZONTAL_RULE,
            MarkdownProcessorType.XML_BLOCK,
            MarkdownProcessorType.BOLD,
            MarkdownProcessorType.ITALIC,
            MarkdownProcessorType.INLINE_CODE,
            MarkdownProcessorType.LINK,
            MarkdownProcessorType.STRIKETHROUGH,
            MarkdownProcessorType.UNDERLINE,
            MarkdownProcessorType.INLINE_LATEX,
            MarkdownProcessorType.PLAIN_TEXT,
            MarkdownProcessorType.HTML_BREAK -> false
        }

    private val TRAILING_PROTECTED_TEXT_CHARS =
        setOf('。', '！', '？', '.', '!', '?', '…', '，', ',', '；', ';', '：', ':', ')', '）', ']', '】', '}', '」', '"', '\'')

    private fun splitIntoSegments(content: String): List<Segment> {
        if (content.isEmpty()) {
            return listOf(
                Segment(
                    content = "",
                    isProtected = false,
                    blockType = MarkdownProcessorType.PLAIN_TEXT,
                )
            )
        }

        val segments = mutableListOf<Segment>()

        runBlocking {
            content.stream()
                .nativeMarkdownSplitByBlock()
                .collect { blockGroup ->
                    val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
                    val sb = StringBuilder()
                    blockGroup.stream.collect { sb.append(it) }
                    val block = sb.toString()
                    if (block.isEmpty()) return@collect

                    val isProtected =
                        when (blockType) {
                            MarkdownProcessorType.CODE_BLOCK,
                            MarkdownProcessorType.TABLE,
                            MarkdownProcessorType.BLOCK_LATEX -> true
                            MarkdownProcessorType.XML_BLOCK -> shouldPreserveXmlBlockForWaifu(block)
                            else -> false
                        }

                    // 原生块公式会剥离 $$；恢复分隔符后气泡才能识别为 LaTeX 块。
                    val normalizedBlock =
                        if (blockType == MarkdownProcessorType.BLOCK_LATEX) {
                            ensureBlockLatexDelimiters(block)
                        } else {
                            restoreMarkdownBlockMarkers(block, blockType)
                        }

                    segments.add(
                        Segment(
                            content = normalizedBlock,
                            isProtected = isProtected,
                            blockType = blockType,
                        )
                    )
                }
        }

        return segments
    }
    
    /**
     * 处理表情包标签，将<emotion>标签替换为对应的表情图片
     * @param content 包含emotion标签的内容
     * @return 处理后的内容，emotion标签被替换为表情图片
     */
    fun processEmotionTags(content: String): String {
        if (content.isBlank()) return content
        
        // 匹配<emotion>标签的正则表达式
        val emotionRegex = Regex("<emotion>([^<]+)</emotion>")
        
        return emotionRegex.replace(content) { matchResult ->
            val emotion = matchResult.groupValues[1].trim()
            val emojiPath = getRandomEmojiPath(emotion)
            
            if (emojiPath != null) {
                // 判断是自定义表情（绝对路径）还是assets表情（相对路径）
                val imageUrl = if (emojiPath.startsWith("/")) {
                    // 自定义表情：使用绝对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file://$encodedPath"
                } else {
                    // assets表情：使用相对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file:///android_asset/emoji/$encodedPath"
                }
                "![$emotion]($imageUrl)"
            } else {
                // 如果找不到对应的表情，返回原始文本
                matchResult.value
            }
        }
    }
    
    /**
     * 分离表情包和文本内容
     * @param content 包含emotion标签的内容
     * @return 包含文本内容和表情包内容的列表，表情包会单独作为一个元素
     */
    fun separateEmotionAndText(content: String): List<String> {
        if (content.isBlank()) return listOf(content)
        
        val result = mutableListOf<String>()
        val emotionRegex = Regex("<emotion>([^<]+)</emotion>")
        
        // 找到所有emotion标签的位置
        val matches = emotionRegex.findAll(content)
        var lastEnd = 0
        
        for (match in matches) {
            // 添加emotion标签之前的文本（如果有的话）
            val beforeText = content.substring(lastEnd, match.range.first).trim()
            if (beforeText.isNotEmpty()) {
                result.add(beforeText)
            }
            
            // 处理emotion标签
            val emotion = match.groupValues[1].trim()
            val emojiPath = getRandomEmojiPath(emotion)
            
            if (emojiPath != null) {
                // 判断是自定义表情（绝对路径）还是assets表情（相对路径）
                val imageUrl = if (emojiPath.startsWith("/")) {
                    // 自定义表情：使用绝对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file://$encodedPath"
                } else {
                    // assets表情：使用相对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file:///android_asset/emoji/$encodedPath"
                }
                result.add("![$emotion]($imageUrl)")
            }
            
            lastEnd = match.range.last + 1
        }
        
        // 添加最后一个emotion标签之后的文本（如果有的话）
        val afterText = content.substring(lastEnd).trim()
        if (afterText.isNotEmpty()) {
            result.add(afterText)
        }
        
        // 如果没有找到任何emotion标签，返回原始内容
        if (result.isEmpty()) {
            result.add(content)
        }
        
        return result
    }
    
    /**
     * 根据情绪名称获取随机的表情图片路径
     * @param emotion 情绪名称（如：happy、sad、miss_you等）
     * @return 表情图片的完整路径（file:// 格式），如果找不到则返回null
     */
    private fun getRandomEmojiPath(emotion: String): String? {
        try {
            // 只从自定义表情中查找
            val customEmoji = try {
                customEmojiRepository?.let { repo ->
                    runBlocking {
                        val activePrompt = activePromptManager?.getActivePrompt() ?: return@runBlocking null
                        repo.initializeBuiltinEmojis(activePrompt)
                        val emojis = repo.getEmojisForCategory(activePrompt, emotion).first()
                        if (emojis.isNotEmpty()) {
                            val randomEmoji = emojis.random()
                            val file = repo.getEmojiFile(activePrompt, randomEmoji)
                            if (file.exists()) {
                                return@runBlocking file.absolutePath
                            }
                        }
                        null
                    }
                }
            } catch (e: Exception) {
                com.ai.assistance.operit.util.AppLogger.e("WaifuMessageProcessor", "查询自定义表情失败", e)
                null
            }
            
            // 如果找到自定义表情，直接返回（已经是完整路径）
            if (customEmoji != null) {
                return customEmoji
            }
            
            // 如果自定义表情中没有找到，则直接返回null
            com.ai.assistance.operit.util.AppLogger.w("WaifuMessageProcessor", "在自定义表情中未找到对于情绪 '$emotion' 的表情")
            return null
            
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("WaifuMessageProcessor", "获取表情图片失败: $emotion", e)
            return null
        }
    }
}
