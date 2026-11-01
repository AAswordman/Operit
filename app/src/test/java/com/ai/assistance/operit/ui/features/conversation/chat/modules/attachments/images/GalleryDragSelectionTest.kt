package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryDragSelectionTest {
    @Test
    fun `drag across rows keeps original selection and adds each image once`() {
        val drag = GalleryDragSelection(
            visibleUris = (0..8).map(Int::toString),
            existing = listOf("8", "2"),
            anchor = 1,
        )

        assertEquals(listOf("8", "2", "1", "3", "4", "5", "6", "7"), drag.through(7))
        assertEquals(listOf("8", "2", "1", "3"), drag.through(3))
    }

    @Test
    fun `dragging backward retracts only images added by the current gesture`() {
        val drag = GalleryDragSelection(
            visibleUris = (0..5).map(Int::toString),
            existing = listOf("1", "5"),
            anchor = 4,
        )

        assertEquals(listOf("1", "5", "4", "3", "2", "0"), drag.through(0))
        assertEquals(listOf("1", "5", "4", "3"), drag.through(3))
    }
}