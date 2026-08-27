package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLinkParserTagOrderTest {

    @Test fun extractMediaLinkTags_preservesEncounterOrder() {
        val input = "<link type=\"video\" id=\"v1\">V</link><link type=\"audio\" id=\"a1\">A</link>"
        assertEquals(listOf(MediaLinkTag("video", "v1"), MediaLinkTag("audio", "a1")), MediaLinkParser.extractMediaLinkTags(input))
    }

    @Test fun extractImageLinkIds_preservesEncounterOrder() {
        val input = "<link type=\"image\" id=\"i1\">I</link><link type=\"image\" id=\"i2\">I</link>"
        assertEquals(listOf("i1", "i2"), MediaLinkParser.extractImageLinkIds(input))
    }
}
