package com.ai.assistance.operit.core.chat.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkParserImageOpsTest {

    @Test fun replaceImageLinks_keepsNonLinkTextOrder() {
        val input = "A<link type=\"image\" id=\"i1\">I</link>B"
        assertEquals("A[i1]B", MediaLinkParser.replaceImageLinks(input) { id -> "[$id]" })
    }

    @Test fun removeImageLinks_withNoImageReturnsOriginalText() {
        assertEquals("plain", MediaLinkParser.removeImageLinks("plain"))
    }

    @Test fun hasImageLinks_ignoresAudioLinks() {
        assertFalse(MediaLinkParser.hasImageLinks("<link type=\"audio\" id=\"a1\">A</link>"))
    }

    @Test fun extractImageLinkIds_returnsOrderedIds() {
        assertEquals(
            listOf("i1", "i2"),
            MediaLinkParser.extractImageLinkIds("<link type=\"image\" id=\"i1\">I</link><link type=\"image\" id=\"i2\">I</link>")
        )
    }
}
