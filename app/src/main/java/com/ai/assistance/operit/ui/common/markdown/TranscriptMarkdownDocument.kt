package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.compositionLocalOf
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable

/** Full-document indices remain intact when individual blocks are recycled by the timeline. */
internal data class TranscriptMarkdownDocument(
    val nodes: List<MarkdownNodeStable>,
    val items: List<MarkdownGroupedItem>,
    val invocationIndices: List<Int?>,
    val processEnd: Int,
    val toolExecutions: Map<Int, com.ai.assistance.operit.ui.features.chat.components.part.PersistedToolExecution> = emptyMap(),
    val hiddenNodes: Set<Int> = emptySet(),
) {
    val nodeChars: List<Int> = nodes.map(::markdownNodeChars)
}

private fun markdownNodeChars(node: MarkdownNodeStable): Int =
    (node.content.length.toLong() + node.children.sumOf { markdownNodeChars(it).toLong() })
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal data class TranscriptMarkdownSlice(
    val document: TranscriptMarkdownDocument,
    val item: MarkdownGroupedItem?,
    val messageKey: String,
    val first: Boolean,
    val last: Boolean,
    val expanded: Boolean = false,
    val toggle: () -> Unit = {},
    val processHeader: Boolean = false,
    val indented: Boolean = false,
    val extraItems: List<MarkdownGroupedItem> = emptyList(),
)

internal val LocalTranscriptMarkdownSlice = compositionLocalOf<TranscriptMarkdownSlice?> { null }
