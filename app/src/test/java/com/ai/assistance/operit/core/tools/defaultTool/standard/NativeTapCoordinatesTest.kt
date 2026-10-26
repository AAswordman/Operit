package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coordinate mapping used by the native `browser_click(ref)` tap path.
 *
 * Regression coverage for #1318: the WebView properties behind this mapping must be read on the
 * main thread, which is why they arrive here as plain primitives. These tests pin the mapping
 * itself (page scale applied, center of the element rect, clamped into the view bounds) so the
 * thread fix cannot silently change where a ref click lands.
 */
class NativeTapCoordinatesTest {

    private val delta = 0.0001f

    @Test fun appliesPageScaleToRectCenter() {
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 100,
                rectTop = 40,
                rectRight = 200,
                rectBottom = 80,
                rawPageScale = 2f,
                viewWidth = 1000,
                viewHeight = 800
            )

        // Center of the rect is (150, 60) in CSS px; at scale 2 that is (300, 120) in view px.
        assertEquals(300f, x, delta)
        assertEquals(120f, y, delta)
    }

    @Test fun scaleOfOneKeepsCssPixelCenter() {
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 10,
                rectTop = 20,
                rectRight = 30,
                rectBottom = 60,
                rawPageScale = 1f,
                viewWidth = 400,
                viewHeight = 900
            )

        assertEquals(20f, x, delta)
        assertEquals(40f, y, delta)
    }

    @Test fun fractionalScaleMapsCenterProportionally() {
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 0,
                rectTop = 0,
                rectRight = 100,
                rectBottom = 100,
                rawPageScale = 0.5f,
                viewWidth = 1000,
                viewHeight = 1000
            )

        assertEquals(25f, x, delta)
        assertEquals(25f, y, delta)
    }

    @Test fun nonPositiveScaleIsTreatedAsOne() {
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 10,
                rectTop = 10,
                rectRight = 30,
                rectBottom = 30,
                rawPageScale = 0f,
                viewWidth = 400,
                viewHeight = 400
            )

        assertEquals(20f, x, delta)
        assertEquals(20f, y, delta)

        val (negativeX, negativeY) =
            nativeTapCoordinates(
                rectLeft = 10,
                rectTop = 10,
                rectRight = 30,
                rectBottom = 30,
                rawPageScale = -1.5f,
                viewWidth = 400,
                viewHeight = 400
            )

        assertEquals(20f, negativeX, delta)
        assertEquals(20f, negativeY, delta)
    }

    @Test fun coordinatesAreClampedInsideViewBounds() {
        // The rect center scaled up lands far outside a small view; the tap must stay inside it.
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 900,
                rectTop = 700,
                rectRight = 1100,
                rectBottom = 900,
                rawPageScale = 3f,
                viewWidth = 200,
                viewHeight = 150
            )

        assertEquals(199f, x, delta)
        assertEquals(149f, y, delta)
    }

    @Test fun coordinatesAreClampedToAtLeastOnePixel() {
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = -500,
                rectTop = -500,
                rectRight = -400,
                rectBottom = -400,
                rawPageScale = 1f,
                viewWidth = 200,
                viewHeight = 150
            )

        assertEquals(1f, x, delta)
        assertEquals(1f, y, delta)
    }

    @Test fun degenerateViewStillYieldsClampedPositiveCoordinates() {
        // An unmeasured WebView reports width/height 0; the lower bound wins over the empty range.
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 0,
                rectTop = 0,
                rectRight = 100,
                rectBottom = 100,
                rawPageScale = 1f,
                viewWidth = 0,
                viewHeight = 0
            )

        assertEquals(1f, x, delta)
        assertEquals(1f, y, delta)
    }

    @Test fun zeroSizeRectTargetsItsOwnCorner() {
        val (x, y) =
            nativeTapCoordinates(
                rectLeft = 64,
                rectTop = 32,
                rectRight = 64,
                rectBottom = 32,
                rawPageScale = 1f,
                viewWidth = 400,
                viewHeight = 400
            )

        assertEquals(64f, x, delta)
        assertEquals(32f, y, delta)
    }
}
