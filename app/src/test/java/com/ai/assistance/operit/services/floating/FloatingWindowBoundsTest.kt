package com.ai.assistance.operit.services.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowBoundsTest {
    @Test
    fun `negative x is clamped to zero so left title bar controls remain on screen`() {
        val (clampedX, clampedY) =
            FloatingWindowManager.clampWindowPosition(
                x = -231,
                y = 0,
                width = 691,
                height = 768,
                screenWidth = 1080,
                screenHeight = 2280
            )

        assertEquals(0, clampedX)
        assertEquals(0, clampedY)
    }

    @Test
    fun `right overflow x is clamped so right home and close buttons remain visible`() {
        val (clampedX, clampedY) =
            FloatingWindowManager.clampWindowPosition(
                x = 850,
                y = 240,
                width = 691,
                height = 768,
                screenWidth = 1080,
                screenHeight = 2280
            )

        assertEquals(1080 - 691, clampedX)
        assertEquals(240, clampedY)
    }

    @Test
    fun `vertical bounds clamp negative y to zero and keep visible height on screen`() {
        val (topClampedX, topClampedY) =
            FloatingWindowManager.clampWindowPosition(
                x = 120,
                y = -150,
                width = 691,
                height = 768,
                screenWidth = 1080,
                screenHeight = 2280
            )
        assertEquals(120, topClampedX)
        assertEquals(0, topClampedY)

        val (_, bottomClampedY) =
            FloatingWindowManager.clampWindowPosition(
                x = 120,
                y = 2200,
                width = 691,
                height = 768,
                screenWidth = 1080,
                screenHeight = 2280
            )
        assertEquals(2280 - (768 * 2 / 3), bottomClampedY)
    }

    @Test
    fun `oversized window dimensions clamp safely to zero without throwing empty range`() {
        val (clampedX, clampedY) =
            FloatingWindowManager.clampWindowPosition(
                x = -50,
                y = 500,
                width = 1400,
                height = 2600,
                screenWidth = 1080,
                screenHeight = 2280,
                minVisibleHeight = 2600
            )

        assertEquals(0, clampedX)
        assertEquals(0, clampedY)
    }
}
