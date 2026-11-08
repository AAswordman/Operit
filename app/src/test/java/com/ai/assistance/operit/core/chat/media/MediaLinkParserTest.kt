package com.ai.assistance.operit.core.chat.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkParserTest {

    @Test fun extractImageLinkIds_readsPlainImageLink() {
        assertEquals(listOf("img1"), MediaLinkParser.extractImageLinkIds("<link type=\"image\" id=\"img1\">Image</link>"))
    }

    @Test fun extractImageLinkIds_deduplicatesRepeatedIds() {
        assertEquals(
            listOf("img1"),
            MediaLinkParser.extractImageLinkIds("<link type=\"image\" id=\"img1\">A</link><link type=\"image\" id=\"img1\">B</link>")
        )
    }

    @Test fun extractImageLinkIds_skipsErrorIds() {
        assertTrue(MediaLinkParser.extractImageLinkIds("<link type=\"image\" id=\"error\">Error</link>").isEmpty())
    }

    @Test fun removeImageLinks_removesMatchingBlocks() {
        assertEquals("hello", MediaLinkParser.removeImageLinks("hello<link type=\"image\" id=\"img1\">Image</link>"))
    }

    @Test fun replaceImageLinks_replacesEachId() {
        assertEquals("hello[img1]", MediaLinkParser.replaceImageLinks("hello<link type=\"image\" id=\"img1\">Image</link>") { id -> "[$id]" })
    }

    @Test fun replaceImageLinks_dropsErrorIds() {
        assertEquals("hello", MediaLinkParser.replaceImageLinks("hello<link type=\"image\" id=\"error\">Image</link>") { id -> "[$id]" })
    }

    @Test fun hasImageLinks_detectsImageBlock() {
        assertTrue(MediaLinkParser.hasImageLinks("<link type=\"image\" id=\"img1\">Image</link>"))
    }

    @Test fun hasImageLinks_returnsFalseWithoutImageBlock() {
        assertFalse(MediaLinkParser.hasImageLinks("plain text"))
    }

    @Test fun extractMediaLinkTags_readsAudioTag() {
        assertEquals(listOf(MediaLinkTag("audio", "aud1")), MediaLinkParser.extractMediaLinkTags("<link type=\"audio\" id=\"aud1\">Audio</link>"))
    }

    @Test fun extractMediaLinkTags_readsVideoTag() {
        assertEquals(listOf(MediaLinkTag("video", "vid1")), MediaLinkParser.extractMediaLinkTags("<link type=\"video\" id=\"vid1\">Video</link>"))
    }

    @Test fun extractMediaLinkTags_deduplicatesByTypeAndId() {
        assertEquals(
            listOf(MediaLinkTag("audio", "aud1")),
            MediaLinkParser.extractMediaLinkTags("<link type=\"audio\" id=\"aud1\">A</link><link type=\"audio\" id=\"aud1\">B</link>")
        )
    }

    @Test fun extractMediaLinkTags_skipsErrorIds() {
        assertTrue(MediaLinkParser.extractMediaLinkTags("<link type=\"audio\" id=\"error\">A</link>").isEmpty())
    }

    @Test fun replaceMediaLinks_replacesAudioAndVideo() {
        val input = "<link type=\"audio\" id=\"aud1\">A</link><link type=\"video\" id=\"vid1\">V</link>"
        assertEquals("[audio:aud1][video:vid1]", MediaLinkParser.replaceMediaLinks(input) { type, id -> "[$type:$id]" })
    }

    @Test fun removeMediaLinks_removesAudioAndVideoBlocks() {
        val input = "a<link type=\"audio\" id=\"aud1\">A</link>b<link type=\"video\" id=\"vid1\">V</link>c"
        assertEquals("abc", MediaLinkParser.removeMediaLinks(input))
    }

    @Test fun hasMediaLinks_detectsAudioOrVideoBlocks() {
        assertTrue(MediaLinkParser.hasMediaLinks("<link type=\"audio\" id=\"aud1\">A</link>"))
    }
}
