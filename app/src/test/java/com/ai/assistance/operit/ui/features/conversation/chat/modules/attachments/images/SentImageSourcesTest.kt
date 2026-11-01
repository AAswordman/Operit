package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import org.junit.Assert.*
import org.junit.Test

class SentImageSourcesTest {
    private fun linkedImage(index: Int) =
        """<link type="image" id="pool-$index">图片</link> <attachment id="/image-$index.jpg" filename="image-$index.jpg" type="image/jpeg">已附加</attachment>"""

    @Test fun sixUploadedImagesProduceSixItemsInsteadOfTwelveLabels() {
        val sources = sentImageSources((1..6).joinToString(" ", transform = ::linkedImage))
        assertEquals(6, sources.size)
        assertEquals((1..6).map { "/image-$it.jpg" }, sources.map { it.id })
        assertTrue(sources.all { it.poolId == null })
        assertEquals("image-1.jpg", sources.first().title)
    }
    @Test fun legacyImageLinkWithoutAttachmentIsRetained() {
        assertEquals("legacy", sentImageSources("""<link id="legacy" type="image">Image</link>""").single().poolId)
    }
    @Test fun ordinaryImageAttachmentUsesItsFileAndIsNotDuplicated() {
        val image = sentImageSources("""<attachment id="/photo.png" filename="photo.png" type="image/png" />""").single()
        assertEquals("/photo.png", image.id)
        assertNull(image.poolId)
    }
    @Test fun textAttachmentsAreNotCountedAsImages() {
        assertTrue(sentImageSources("""<attachment id="/file.txt" filename="file.txt" type="text/plain">text</attachment>""").isEmpty())
    }
    @Test fun unrelatedTextBetweenLinkAndAttachmentDoesNotCreateFalseBinding() {
        val sources = sentImageSources("""<link type="image" id="one">Image</link> hello <attachment id="/two.png" filename="two.png" type="image/png" />""")
        assertEquals(2, sources.size)
        assertNull(sources.last().poolId)
    }
    @Test fun escapedOriginalFileNameIsDecodedAndStillUsesOriginalFile() {
        val path = "/cleanOnExit/a & \"b\".jpg"
        val source = sentImageSources(
            com.ai.assistance.operit.core.chat.media.mediaAttachmentMarkup(
                com.ai.assistance.operit.data.model.AttachmentInfo(path, "a & \"b\".jpg", "image/jpeg", fileSize = 1024L)
            )
        ).single()
        assertEquals(path, source.id)
        assertEquals("a & \"b\".jpg", source.title)
        assertNull(source.poolId)
    }
}
