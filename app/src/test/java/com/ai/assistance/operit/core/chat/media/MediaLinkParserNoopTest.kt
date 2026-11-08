package com.ai.assistance.operit.core.chat.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkParserNoopTest {

    @Test fun extractImageLinkIds_returnsEmptyForPlainText() {
        assertTrue(MediaLinkParser.extractImageLinkIds("plain").isEmpty())
    }

    @Test fun extractMediaLinkTags_returnsEmptyForPlainText() {
        assertTrue(MediaLinkParser.extractMediaLinkTags("plain").isEmpty())
    }

    @Test fun replaceMediaLinks_returnsOriginalWhenNoLinksExist() {
        assertEquals("plain", MediaLinkParser.replaceMediaLinks("plain") { type, id -> "$type:$id" })
    }
}
