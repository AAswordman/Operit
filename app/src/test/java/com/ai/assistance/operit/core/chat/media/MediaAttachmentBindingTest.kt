package com.ai.assistance.operit.core.chat.media

import com.ai.assistance.operit.core.chat.media.mediaAttachmentMarkup
import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import org.junit.Assert.*
import org.junit.Test

class MediaAttachmentBindingTest {
    @Test fun audioAndVideoKeepIndependentOriginalFilesWithoutPoolLookup() {
        val audio = AttachmentInfo("/cleanOnExit/clip.wav", "clip.wav", "audio/wav", 32044)
        val video = AttachmentInfo("/cleanOnExit/clip.mp4", "clip.mp4", "video/mp4", 50000)
        val text = """<link type="audio" id="audio-pool"/> """ + mediaAttachmentMarkup(audio) +
            """ <link id="video-pool" type="video"/> """ + mediaAttachmentMarkup(video)
        val bindings = MediaLinkParser.extractMediaAttachmentBindings(text)
        assertEquals(mapOf("audio-pool" to audio, "video-pool" to video), bindings)
    }

    @Test fun originalFileAttributesRoundTripSpecialCharacters() {
        val original = AttachmentInfo(
            "/cleanOnExit/a & \"quote\" <clip>'&amp;.wav",
            "a & \"quote\" <clip>'&amp;.wav",
            "audio/wav",
            32044,
        )
        val markup = mediaAttachmentMarkup(original)
        assertTrue(markup.contains("&" + "amp;"))
        assertTrue(markup.contains("&" + "quot;"))
        assertTrue(markup.contains("&" + "lt;"))
        val parsed = MediaLinkParser.extractMediaAttachmentBindings("""<link type="audio" id="a"/>""" + markup)
        assertEquals(original, parsed["a"])
    }

    @Test fun unrelatedTextOrDifferentMediaTypeDoesNotCreateBinding() {
        val video = mediaAttachmentMarkup(AttachmentInfo("/v.mp4", "v.mp4", "video/mp4", fileSize = 50000L))
        val audio = mediaAttachmentMarkup(AttachmentInfo("/a.wav", "a.wav", "audio/wav", fileSize = 32044L))
        assertTrue(MediaLinkParser.extractMediaAttachmentBindings("""<link type="audio" id="a"/>""" + video).isEmpty())
        assertTrue(MediaLinkParser.extractMediaAttachmentBindings("""<link type="audio" id="a"/>说明""" + audio).isEmpty())
    }

    @Test fun selfClosingOriginalFileDescriptionIsSupported() {
        val markup = """<link type="audio" id="a"/> <attachment id="/a&amp;b.wav" filename="a&amp;b.wav" type="audio/wav" size="32044" />"""
        val original = MediaLinkParser.extractMediaAttachmentBindings(markup)["a"]
        assertNotNull(original)
        assertEquals("/a&b.wav", original!!.filePath)
        assertEquals("a&b.wav", original.fileName)
        assertEquals(32044L, original.fileSize)
        assertEquals("", original.content)
    }

    @Test fun legacyPoolLinkRemainsUnbound() {
        assertTrue(MediaLinkParser.extractMediaAttachmentBindings("""<link type="video" id="legacy"/>""").isEmpty())
        assertEquals(listOf(MediaLinkTag("video", "legacy")), MediaLinkParser.extractMediaLinkTags("""<link type="video" id="legacy"/>"""))
    }

    @Test fun imageBindingUsesDecodedOriginalPath() {
        val image = AttachmentInfo("/cleanOnExit/a & \"b\".png", "a & \"b\".png", "image/png", fileSize = 1024L)
        val bindings = MediaLinkParser.extractImageAttachmentBindings("""<link type="image" id="image"/>""" + mediaAttachmentMarkup(image))
        assertEquals(mapOf(image.filePath to "image"), bindings)
    }
}
