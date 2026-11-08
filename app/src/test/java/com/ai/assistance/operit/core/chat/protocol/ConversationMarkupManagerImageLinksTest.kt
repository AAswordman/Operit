package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.core.chat.media.MediaLinkParser
import org.junit.Assert.*
import org.junit.Test

class ConversationMarkupManagerImageLinksTest {
    private fun image(id: String) = """<link type="image" id="$id">Image</link>"""

    @Test fun sourceCodeExamplesStayInsideToolText() {
        val source = "val sample = " + image("legacy") + " " + image("pool-1")
        val lookedUp = mutableListOf<String>()
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel(source) {
            lookedUp += it
            false
        }
        assertEquals(source, text)
        assertEquals("", links)
        assertEquals(listOf("legacy", "pool-1"), lookedUp)
    }

    @Test fun availableImageIsExtractedWithOriginalText() {
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel("截图 " + image("real")) { it == "real" }
        assertEquals("Image attached as multimodal input.\n截图", text)
        assertEquals(listOf("real"), MediaLinkParser.extractImageLinkIds(links))
    }

    @Test fun mixedResultKeepsUnknownExampleExactlyAndOnlyExtractsAvailableImage() {
        val example = """<link id='legacy' type='image' note='source'>example</link>"""
        val source = "source=$example\n截图=" + image("real")
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel(source) { it == "real" }
        assertEquals("Image attached as multimodal input.\nsource=$example\n截图=", text)
        assertEquals(listOf("real"), MediaLinkParser.extractImageLinkIds(links))
    }

    @Test fun duplicateAvailableImagesAreExtractedOnce() {
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel(image("real") + " " + image("real")) { true }
        assertEquals("Image attached as multimodal input.", text)
        assertEquals(listOf("real"), MediaLinkParser.extractImageLinkIds(links))
        assertEquals(1, Regex("id=\"real\"").findAll(links).count())
    }

    @Test fun unavailableImageDoesNotClaimAnImageWasAttached() {
        val source = "预览 " + image("deleted")
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel(source) { false }
        assertEquals(source, text)
        assertFalse(text.contains("Image attached as multimodal input."))
        assertTrue(links.isEmpty())
    }

    @Test fun textAndOtherMediaDoNotQueryImagePool() {
        val source = """文字 <link type="audio" id="audio"/> <link type="video" id="video"/>"""
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel(source) { error("不应查询图片池") }
        assertEquals(source, text)
        assertEquals("", links)
    }

    @Test fun errorSentinelIsNotExtractedOrLookedUp() {
        val source = image("error")
        val (text, links) = ConversationMarkupManager.splitImageLinksForModel(source) { error("不应查询错误占位符") }
        assertEquals(source, text)
        assertEquals("", links)
    }
}
