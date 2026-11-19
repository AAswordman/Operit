package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.plugins.StreamMarkdownFencedCodeBlockPlugin
import com.ai.assistance.operit.util.stream.plugins.StreamMarkdownInlineCodePlugin
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin

/** A preempted draft must never repair or execute unfinished tool arguments. */
internal suspend fun retainBeforeUnexecutedTool(content: String): String {
    val toolOpening = Regex(
        "<(?:${ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE})(?=\\s|/?>|$)",
        RegexOption.IGNORE_CASE,
    )
    val plugins = listOf(
        StreamMarkdownFencedCodeBlockPlugin(includeFences = true),
        StreamMarkdownInlineCodePlugin(includeTicks = true),
        StreamXmlPlugin(includeTagsInOutput = true),
    )
    var offset = 0
    var cut: Int? = null
    content.stream().splitBy(plugins).collect { group ->
        val text = buildString { group.stream.collect { append(it) } }
        if (cut == null) {
            val localCut = when (group.tag) {
                is StreamXmlPlugin ->
                    if (ChatMarkupRegex.isToolTagName(ChatMarkupRegex.extractOpeningTagName(text)))
                        text.indexOf('<') else null
                null -> toolOpening.find(text)?.range?.first
                else -> null
            }
            if (localCut != null) cut = offset + localCut
        }
        // Drain every group: abandoning a nested stream leaves its producer suspended.
        offset += text.length
    }
    return cut?.let(content::take) ?: content
}
