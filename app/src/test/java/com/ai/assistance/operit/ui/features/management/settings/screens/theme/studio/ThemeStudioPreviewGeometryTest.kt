package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ai.assistance.operit.data.model.theme.ThemeScriptPopupOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStudioPreviewGeometryTest {
    @Test
    fun compactAndFullPreviewsFitTheSameCompletePortraitScene() {
        val viewport = IntSize(360, 800)
        val compact = studioPreviewTransform(viewport, IntSize(360, 300))
        assertEquals(.375f, compact.scale, .0001f)
        assertEquals(IntOffset(113, 0), compact.position)
        val full = studioPreviewTransform(viewport, IntSize(360, 744))
        assertEquals(.93f, full.scale, .0001f)
        assertEquals(IntOffset(13, 0), full.position)
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
