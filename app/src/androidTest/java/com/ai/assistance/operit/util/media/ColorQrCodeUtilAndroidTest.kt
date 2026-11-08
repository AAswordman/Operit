package com.ai.assistance.operit.util.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorQrCodeUtilAndroidTest {

    @Test
    fun roundTrip_2_4_8_16_colors() {
        val payload = ByteArray(128) { i -> ((i * 31) xor (i ushr 1)).toByte() }

        for (colors in listOf(2, 4, 8, 16)) {
            val bmp = ColorQrCodeUtil.generate(
                payload = payload,
                colorCount = colors,
                moduleSizePx = 16,
                marginModules = 4,
            )

            val decoded = ColorQrCodeUtil.decode(bmp, expectedColorCount = colors)
            assertEquals(colors, decoded.colorCount)
            assertArrayEquals(payload, decoded.payload)
        }
    }

    @Test
    fun roundTrip_autoDetectColorCount() {
        val payload = "hello-color-qr".encodeToByteArray()
        val colors = 8

        val bmp = ColorQrCodeUtil.generate(
            payload = payload,
            colorCount = colors,
            moduleSizePx = 16,
            marginModules = 4,
        )

        val decoded = ColorQrCodeUtil.decode(bmp)
        assertEquals(colors, decoded.colorCount)
        assertArrayEquals(payload, decoded.payload)
    }
}
