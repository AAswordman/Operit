package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewTransformTest {
    private val viewport = Size(1000f, 1000f)

    @Test
    fun zoomAtCenterKeepsTheImageCentered() {
        val result = change(ImagePreviewTransform(), 3f)
        assertEquals(3f, result.scale, 0.001f)
        assertOffset(0f, 0f, result.offset)
    }

    @Test
    fun zoomKeepsTheTappedPointAtItsScreenPosition() {
        val result = change(ImagePreviewTransform(), 3f, focus = Offset(750f, 500f))
        assertOffset(-500f, 0f, result.offset)
    }

    @Test
    fun landscapeImageCanOnlyPanWithinItsVisibleBounds() {
        val result = change(
            ImagePreviewTransform(), 2f,
            pan = Offset(900f, 900f), image = Size(2000f, 1000f),
        )
        assertOffset(500f, 0f, result.offset)
    }

    @Test
    fun portraitImageCanOnlyPanWithinItsVisibleBounds() {
        val result = change(
            ImagePreviewTransform(), 2f,
            pan = Offset(-900f, -900f), image = Size(1000f, 2000f),
        )
        assertOffset(0f, -500f, result.offset)
    }

    @Test
    fun zoomingBackToFitResetsPreviousPan() {
        val result = change(ImagePreviewTransform(4f, Offset(200f, -150f)), 0.5f)
        assertEquals(1f, result.scale, 0.001f)
        assertOffset(0f, 0f, result.offset)
    }

    @Test
    fun repeatedPinchesCannotExceedTheMaximumScale() {
        val result = change(ImagePreviewTransform(5f), 20f)
        assertEquals(6f, result.scale, 0.001f)
    }

    @Test
    fun simultaneousPanAndZoomPreserveTheGestureFocus() {
        val result = change(
            ImagePreviewTransform(2f, Offset(100f, 50f)), 3f,
            focus = Offset(750f, 750f), pan = Offset(10f, 20f),
        )
        assertOffset(35f, -30f, result.offset)
    }

    private fun change(
        current: ImagePreviewTransform,
        scale: Float,
        focus: Offset = Offset(500f, 500f),
        pan: Offset = Offset.Zero,
        image: Size = Size(1000f, 1000f),
    ) = transformImagePreview(current, scale, focus, pan, viewport, image)

    private fun assertOffset(x: Float, y: Float, actual: Offset) {
        assertEquals(x, actual.x, 0.001f)
        assertEquals(y, actual.y, 0.001f)
    }
}