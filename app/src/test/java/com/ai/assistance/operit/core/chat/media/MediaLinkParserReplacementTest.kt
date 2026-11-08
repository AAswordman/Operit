package com.ai.assistance.operit.core.chat.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLinkParserReplacementTest {

    @Test fun replaceMediaLinks_keepsSurroundingText() {
        val input = "a<link type=\"audio\" id=\"a1\">A</link>b"
        assertEquals("a[audio:a1]b", MediaLinkParser.replaceMediaLinks(input) { type, id -> "[$type:$id]" })
    }

    @Test fun replaceImageLinks_replacesMultipleDifferentIds() {
        val input = "<link type=\"image\" id=\"i1\">I</link><link type=\"image\" id=\"i2\">I</link>"
        assertEquals("[i1][i2]", MediaLinkParser.replaceImageLinks(input) { id -> "[$id]" })
    }
}
