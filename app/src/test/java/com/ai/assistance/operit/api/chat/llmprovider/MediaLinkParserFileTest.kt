package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkParserFileTest {
    @Test
    fun extractsPdfFilenameAndRemovesFileLink() {
        val content = "Before <link type=\"file\" id=\"pdf-1\" filename=\"report&amp;one.pdf\">PDF</link> After"

        val tags = MediaLinkParser.extractMediaLinkTags(content)

        assertEquals(1, tags.size)
        assertEquals("file", tags.single().type)
        assertEquals("pdf-1", tags.single().id)
        assertEquals("report&one.pdf", tags.single().fileName)
        assertEquals("Before  After", MediaLinkParser.removeMediaLinks(content))
        assertTrue(MediaLinkParser.hasMediaLinks(content))
    }
}
