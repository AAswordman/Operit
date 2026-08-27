package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLinkParserTagOrderTest {
    @Test fun extractImageLinkIds_supportsAttributesInEitherOrder() {
        val input = "<link id=\"i1\" data=\"unused\" type=\"image\">I</link>"
        assertEquals(listOf("i1"), MediaLinkParser.extractImageLinkIds(input))
    }

    @Test fun removeImageLinks_removesMissingPoolEntries() {
        val input = "before<link type=\"image\" id=\"missing\">I</link>after"
        assertEquals("beforeafter", MediaLinkParser.removeImageLinks(input))
    }

    @Test fun removeMediaLinks_supportsEscapedAndReorderedAttributes() {
        val input = "before<link id=\\\"a1\\\" type=\\\"audio\\\">A</link>after"
        assertEquals("beforeafter", MediaLinkParser.removeMediaLinks(input))
    }

    @Test fun extractMediaLinkTags_preservesEncounterOrder() {
        val input = "<link type=\"video\" id=\"v1\">V</link><link type=\"audio\" id=\"a1\">A</link>"
        assertEquals(listOf(MediaLinkTag("video", "v1"), MediaLinkTag("audio", "a1")), MediaLinkParser.extractMediaLinkTags(input))
    }

    @Test fun extractMediaLinkTags_preservesFileAndMediaEncounterOrder() {
        val input = "<link filename=\"a.pdf\" id=\"f1\" type=\"file\">F</link>" +
            "<link type=\"audio\" id=\"a1\">A</link>" +
            "<link type=\"file\" id=\"f2\" filename=\"b.pdf\">F</link>"
        assertEquals(
            listOf(
                MediaLinkTag("file", "f1", "a.pdf"),
                MediaLinkTag("audio", "a1"),
                MediaLinkTag("file", "f2", "b.pdf"),
            ),
            MediaLinkParser.extractMediaLinkTags(input),
        )
    }

    @Test fun extractImageLinkIds_preservesEncounterOrder() {
        val input = "<link type=\"image\" id=\"i1\">I</link><link type=\"image\" id=\"i2\">I</link>"
        assertEquals(listOf("i1", "i2"), MediaLinkParser.extractImageLinkIds(input))
    }
}
