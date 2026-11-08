package com.ai.assistance.operit.core.chat.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkParserMediaOpsTest {

    @Test fun replaceMediaLinks_skipsErrorIds() {
        val input = "<link type=\"audio\" id=\"error\">A</link>"
        assertEquals("", MediaLinkParser.replaceMediaLinks(input) { type, id -> "[$type:$id]" })
    }

    @Test fun removeMediaLinks_withNoMediaReturnsOriginalText() {
        assertEquals("plain", MediaLinkParser.removeMediaLinks("plain"))
    }

    @Test fun hasMediaLinks_detectsVideoLink() {
        assertTrue(MediaLinkParser.hasMediaLinks("<link type=\"video\" id=\"v1\">V</link>"))
    }

    @Test fun hasMediaLinks_ignoresImageLink() {
        assertFalse(MediaLinkParser.hasMediaLinks("<link type=\"image\" id=\"i1\">I</link>"))
    }
}
