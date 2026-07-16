package com.ai.assistance.operit.ui.features.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSyntaxHighlightingTest {
    @Test
    fun highlightsBlockMarkersAndHeadingText() {
        val source = "# Heading\n> quote\n- [x] task\n---"

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.MARKER, "#"),
                HighlightedText(MarkdownSyntaxKind.HEADING, "Heading"),
                HighlightedText(MarkdownSyntaxKind.QUOTE, "> "),
                HighlightedText(MarkdownSyntaxKind.MARKER, "-"),
                HighlightedText(MarkdownSyntaxKind.MARKER, "[x]"),
                HighlightedText(MarkdownSyntaxKind.MARKER, "---"),
            ),
            source.highlightedText(),
        )
    }

    @Test
    fun givesCodeAndLinksPrecedenceOverNestedMarkers() {
        val source =
            "**bold** `*code*` [**link**](url) <u>tag</u> <https://example.com> \\*plain*"

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.EMPHASIS, "**bold**"),
                HighlightedText(MarkdownSyntaxKind.CODE, "`*code*`"),
                HighlightedText(MarkdownSyntaxKind.LINK, "[**link**](url)"),
                HighlightedText(MarkdownSyntaxKind.HTML, "<u>"),
                HighlightedText(MarkdownSyntaxKind.HTML, "</u>"),
                HighlightedText(MarkdownSyntaxKind.LINK, "<https://example.com>"),
            ),
            source.highlightedText(),
        )
    }

    @Test
    fun treatsFencedContentAsCode() {
        val source = "```md\n# not heading\n**not emphasis**\n```\n# heading"

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.CODE, "```md"),
                HighlightedText(MarkdownSyntaxKind.CODE, "# not heading"),
                HighlightedText(MarkdownSyntaxKind.CODE, "**not emphasis**"),
                HighlightedText(MarkdownSyntaxKind.CODE, "```"),
                HighlightedText(MarkdownSyntaxKind.MARKER, "#"),
                HighlightedText(MarkdownSyntaxKind.HEADING, "heading"),
            ),
            source.highlightedText(),
        )
    }

    @Test
    fun rejectsBackticksInFenceInfoString() {
        val source = "```lang`\n# heading"

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.MARKER, "#"),
                HighlightedText(MarkdownSyntaxKind.HEADING, "heading"),
            ),
            source.highlightedText(),
        )
    }

    @Test
    fun handlesUnclosedDelimitersWithoutProducingRanges() {
        val source = "[a](".repeat(3_000) + "\na" + "*".repeat(12_000)

        assertTrue(source.highlightRanges().isEmpty())
    }

    @Test
    fun continuesHighlightingAfterUnclosedLinkMarker() {
        val source = "[ *bold* <!-- comment"

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.EMPHASIS, "*bold*"),
                HighlightedText(MarkdownSyntaxKind.COMMENT, "<!-- comment"),
            ),
            source.highlightedText(),
        )
    }

    @Test
    fun carriesHtmlCommentsAcrossLines() {
        val source = "before <!-- comment\n# hidden\n--> after"

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.COMMENT, "<!-- comment"),
                HighlightedText(MarkdownSyntaxKind.COMMENT, "# hidden"),
                HighlightedText(MarkdownSyntaxKind.COMMENT, "-->"),
            ),
            source.highlightedText(),
        )
    }

    @Test
    fun emitsOrderedRangesForCrLfAndUnicode() {
        val source = "## 你好 👋\r\n1. `值` | [链接](url)"
        val ranges = source.highlightRanges()

        assertEquals(
            listOf(
                HighlightedText(MarkdownSyntaxKind.MARKER, "##"),
                HighlightedText(MarkdownSyntaxKind.HEADING, "你好 👋"),
                HighlightedText(MarkdownSyntaxKind.MARKER, "1."),
                HighlightedText(MarkdownSyntaxKind.CODE, "`值`"),
                HighlightedText(MarkdownSyntaxKind.MARKER, "|"),
                HighlightedText(MarkdownSyntaxKind.LINK, "[链接](url)"),
            ),
            ranges.map { HighlightedText(it.kind, source.substring(it.start, it.end)) },
        )
        ranges.zipWithNext().forEach { (current, next) ->
            assertTrue(current.end <= next.start)
        }
        ranges.forEach { range ->
            assertTrue(range.start >= 0)
            assertTrue(range.start < range.end)
            assertTrue(range.end <= source.length)
        }
    }
}

private data class HighlightRange(
    val kind: MarkdownSyntaxKind,
    val start: Int,
    val end: Int,
)

private data class HighlightedText(
    val kind: MarkdownSyntaxKind,
    val text: String,
)

private fun String.highlightRanges(): List<HighlightRange> =
    buildList {
        scanMarkdownSyntax(this@highlightRanges) { kind, start, end ->
            add(HighlightRange(kind, start, end))
        }
    }

private fun String.highlightedText(): List<HighlightedText> =
    highlightRanges().map { range ->
        HighlightedText(range.kind, substring(range.start, range.end))
    }
