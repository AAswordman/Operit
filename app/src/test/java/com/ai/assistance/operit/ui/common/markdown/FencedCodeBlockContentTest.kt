package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class FencedCodeBlockContentTest {
    // 数据类相等比较包含闭合状态，避免完整围栏被期待为流式未闭合状态。
    @Test fun bothFenceMarkersExposeLanguageAndBody() {
        for (marker in listOf("```", "~~~")) {
            assertEquals(
                FencedCodeBlockContent("xml", "  example\n\ntail  ", closed = true),
                extractFencedCodeBlockContent("${marker}xml\n  example\n\ntail  \n$marker\n"),
            )
        }
    }

    @Test fun shorterAndDifferentFencesRemainInsideCode() {
        assertEquals(
            FencedCodeBlockContent("xml", "```\n~~~\nexample", closed = true),
            extractFencedCodeBlockContent("````xml\n```\n~~~\nexample\n````\n"),
        )
    }

    @Test fun longerClosingFenceAndTrailingWhitespaceAreAccepted() {
        assertEquals(
            FencedCodeBlockContent("xml", "example", closed = true),
            extractFencedCodeBlockContent("~~~xml\nexample\n~~~~~ \t\n"),
        )
    }

    @Test fun crlfAndIndentedFencesKeepCodeIndentation() {
        assertEquals(
            FencedCodeBlockContent("xml", "  example", closed = true),
            extractFencedCodeBlockContent("  ~~~xml\r\n  example\r\n  ~~~\r\n"),
        )
    }

    @Test fun unclosedStreamingCodeKeepsItsCurrentTail() {
        assertEquals(
            FencedCodeBlockContent("xml", "example\n", closed = false),
            extractFencedCodeBlockContent("~~~xml\nexample\n"),
        )
    }

    @Test fun emptyCodeBlockHasNoFenceText() {
        assertEquals(FencedCodeBlockContent("", "", closed = true), extractFencedCodeBlockContent("~~~\n~~~"))
    }
}
