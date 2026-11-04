package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Viewport-to-view mapping must never turn an out-of-viewport point into an edge tap. */
class NativeTapCoordinatesTest {
    private val delta = 0.0001f

    @Test fun mapsLearnMoreViewportPointOnce() {
        val (x, y) = nativeTapCoordinates(
            viewportX = 640.0, viewportY = 675.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 2560, viewHeight = 1440
        )
        assertEquals(1280f, x, delta)
        assertEquals(1350f, y, delta)
    }

    @Test fun preservesCssCoordinatesInAnUnscaledView() {
        val (x, y) = nativeTapCoordinates(
            viewportX = 640.0, viewportY = 675.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 1280, viewHeight = 720
        )
        assertEquals(640f, x, delta)
        assertEquals(675f, y, delta)
    }

    @Test fun usesMeasuredViewportRatioForFractionalDensity() {
        val (x, y) = nativeTapCoordinates(
            viewportX = 1162.0, viewportY = 52.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 3360, viewHeight = 1890
        )
        assertEquals(3050.25f, x, delta)
        assertEquals(136.5f, y, delta)
    }

    @Test fun supportsAViewportWiderThanTheNativeView() {
        val (x, y) = nativeTapCoordinates(
            viewportX = 960.0, viewportY = 400.0,
            viewportWidth = 1920.0, viewportHeight = 1080.0,
            viewWidth = 960, viewHeight = 540
        )
        assertEquals(480f, x, delta)
        assertEquals(200f, y, delta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReported818PixelTapIn720PixelViewport() {
        nativeTapCoordinates(
            viewportX = 640.0, viewportY = 818.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 3360, viewHeight = 1890
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeViewportCoordinates() {
        nativeTapCoordinates(
            viewportX = -1.0, viewportY = 20.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 1280, viewHeight = 720
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPointOutsideNativeViewInsteadOfClamping() {
        nativeTapCoordinates(
            viewportX = 640.0, viewportY = 675.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 2560, viewHeight = 1000
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnmeasuredView() {
        nativeTapCoordinates(
            viewportX = 20.0, viewportY = 40.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 0, viewHeight = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyViewport() {
        nativeTapCoordinates(
            viewportX = 20.0, viewportY = 40.0,
            viewportWidth = 0.0, viewportHeight = 720.0,
            viewWidth = 1280, viewHeight = 720
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteCoordinate() {
        nativeTapCoordinates(
            viewportX = Double.NaN, viewportY = 40.0,
            viewportWidth = 1280.0, viewportHeight = 720.0,
            viewWidth = 1280, viewHeight = 720
        )
    }
}
