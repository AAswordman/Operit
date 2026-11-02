package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ai.assistance.operit.data.model.theme.ThemeScriptPopupOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStudioPreviewGeometryTest {
    @Test
    fun compactUsesWidthWhileFullFitsTheSamePortraitScene() {
        val viewport = IntSize(360, 800)
        val compact = studioPreviewTransform(viewport, IntSize(360, 300), fitWidth = true)
        assertEquals(1f, compact.scale, 0f)
        assertEquals(IntOffset.Zero, compact.position)
        assertEquals(800, compact.contentHeight)
        val full = studioPreviewTransform(viewport, IntSize(360, 744))
        assertEquals(.93f, full.scale, .0001f)
        assertEquals(IntOffset(13, 0), full.position)
        assertEquals(744, full.contentHeight)
    }

    @Test
    fun compactHeightDoesNotShrinkTheSceneOrChangeItsContentHeight() {
        val viewport = IntSize(360, 800)
        val short = studioPreviewTransform(viewport, IntSize(360, 240), fitWidth = true)
        val tall = studioPreviewTransform(viewport, IntSize(360, 340), fitWidth = true)
        assertEquals(short, tall)
        assertEquals(800, short.contentHeight)
        assertTrue(short.contentHeight > 340)
    }

    @Test
    fun compactKeepsTheAspectRatioAndCentersShortScenes() {
        val portrait = studioPreviewTransform(IntSize(360, 800), IntSize(320, 260), fitWidth = true)
        assertEquals(320f / 360f, portrait.scale, .0001f)
        assertEquals(IntOffset.Zero, portrait.position)
        assertEquals(712, portrait.contentHeight)
        val landscape = studioPreviewTransform(IntSize(800, 360), IntSize(360, 300), fitWidth = true)
        assertEquals(.45f, landscape.scale, .0001f)
        assertEquals(IntOffset(0, 69), landscape.position)
        assertEquals(300, landscape.contentHeight)
    }

    @Test
    fun landscapeReferenceIsFittedWithoutCropping() {
        val result = studioPreviewTransform(IntSize(800, 360), IntSize(360, 300))
        assertEquals(.45f, result.scale, .0001f)
        assertEquals(IntOffset(0, 69), result.position)
    }

    @Test
    fun largerPreviewCentersTheSceneWithoutUpscaling() {
        val result = studioPreviewTransform(IntSize(360, 800), IntSize(720, 1600))
        assertEquals(1f, result.scale, 0f)
        assertEquals(IntOffset(180, 400), result.position)
    }

    @Test
    fun collapsedPreviewDoesNotCreateInvalidTransforms() {
        val result = studioPreviewTransform(IntSize(360, 800), IntSize(280, 0))
        assertEquals(0f, result.scale, 0f)
        assertTrue(result.scale.isFinite())
        assertEquals(IntOffset(140, 0), result.position)
        val compact = studioPreviewTransform(IntSize(360, 800), IntSize(280, 0), fitWidth = true)
        assertTrue(compact.scale.isFinite())
        assertEquals(280f / 360f, compact.scale, .0001f)
        assertEquals(623, compact.contentHeight)
    }

    @Test
    fun existingOpposingOffsetsKeepTheirNetPositionAndLeftOffset() {
        assertEquals(ThemeScriptPopupOffset(up = 16f, left = 12f), ThemeScriptPopupOffset(up = 24f, down = 8f, left = 12f).exclusiveVertical())
        assertEquals(ThemeScriptPopupOffset(down = 16f, left = 12f), ThemeScriptPopupOffset(up = 8f, down = 24f, left = 12f).exclusiveVertical())
    }

    @Test
    fun equalOrZeroVerticalOffsetsLeaveBothDirectionsEditable() {
        assertEquals(ThemeScriptPopupOffset(left = 12f), ThemeScriptPopupOffset(up = 24f, down = 24f, left = 12f).exclusiveVertical())
        assertEquals(ThemeScriptPopupOffset(), ThemeScriptPopupOffset().exclusiveVertical())
    }
}
