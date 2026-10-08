package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class FencedCodeBlockContentTest {
    @Test fun bothFenceMarkersExposeLanguageAndBody() {
        for (marker in listOf("```", "~~~")) {
            assertEquals(
                FencedCodeBlockContent("xml", "  example\n\ntail  "),
                extractFencedCodeBlockContent("${marker}xml\n  example\n\ntail  \n$marker\n"),
            )
        }
    }

    @Test fun shorterAndDifferentFencesRemainInsideCode() {
        assertEquals(
            FencedCodeBlockContent("xml", "```\n~~~\nexample"),
            extractFencedCodeBlockContent("````xml\n```\n~~~\nexample\n````\n"),
        )
    }

    @Test fun longerClosingFenceAndTrailingWhitespaceAreAccepted() {
        assertEquals(
            FencedCodeBlockContent("xml", "example"),
            extractFencedCodeBlockContent("~~~xml\nexample\n~~~~~ \t\n"),
        )
    }

    @Test fun crlfAndIndentedFencesKeepCodeIndentation() {
        assertEquals(
            FencedCodeBlockContent("xml", "  example"),
            extractFencedCodeBlockContent("  ~~~xml\r\n  example\r\n  ~~~\r\n"),
        )
    }

    @Test fun unclosedStreamingCodeKeepsItsCurrentTail() {
        assertEquals(
            FencedCodeBlockContent("xml", "example\n"),
            extractFencedCodeBlockContent("~~~xml\nexample\n"),
        )
    }

    @Test fun emptyCodeBlockHasNoFenceText() {
        assertEquals(FencedCodeBlockContent("", ""), extractFencedCodeBlockContent("~~~\n~~~"))
    }
}