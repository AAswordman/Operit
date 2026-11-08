package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleImageLayoutTest {
    @Test
    fun zeroParametersKeepOnePixelPatternAndFullMessageArea() {
        val config = BubbleImageStyleConfig(
            imageUri = "test",
            repeatXStartRatio = 0f,
            repeatXEndRatio = 0f,
            repeatYStartRatio = 0f,
            repeatYEndRatio = 0f,
            imageScale = 0f,
        )
        val source = buildSliceLayout(100, 100, config)
        val destination = computeDstSliceLayout(source, 1080, 2400, config.imageScale)

        assertEquals(1, source.centerWidth)
        assertEquals(1, source.centerHeight)
        assertEquals(1, bubbleTileExtent(source.centerWidth, destination.scaleX))
        assertEquals(1, bubbleTileExtent(source.centerHeight, destination.scaleY))
        assertEquals(1080, destination.leftDstWidth +
            destination.centerDstEndX - destination.centerDstStartX + destination.rightDstWidth)
        assertEquals(2400, destination.topDstHeight +
            destination.centerDstEndY - destination.centerDstStartY + destination.bottomDstHeight)
    }

    @Test
    fun tinyAndCroppedImagesKeepEverySliceInsideTheSource() {
        val configs = listOf(
            BubbleImageStyleConfig(imageUri = "test"),
            BubbleImageStyleConfig(
                imageUri = "test",
                cropLeftRatio = 0.45f,
                cropTopRatio = 0.45f,
                cropRightRatio = 0.45f,
                cropBottomRatio = 0.45f,
            ),
            BubbleImageStyleConfig(
                imageUri = "test",
                cropLeftRatio = 0.2f,
                cropBottomRatio = 0.1f,
                repeatXStartRatio = 0.1f,
                repeatXEndRatio = 0.7f,
                repeatYStartRatio = 0.6f,
                repeatYEndRatio = 0.9f,
            ),
        )
        for (config in configs) {
            for (width in listOf(1, 2, 3, 4, 100, 2048)) {
                for (height in listOf(1, 2, 3, 4, 100, 2048)) {
                    val source = buildSliceLayout(width, height, config)
                    assertTrue(source.srcX >= 0 && source.srcY >= 0)
                    assertTrue(source.srcX + source.srcWidth <= width)
                    assertTrue(source.srcY + source.srcHeight <= height)
                    assertEquals(source.srcWidth, source.leftCapWidth + source.centerWidth + source.rightCapWidth)
                    assertEquals(source.srcHeight, source.topCapHeight + source.centerHeight + source.bottomCapHeight)
                    assertTrue(source.centerWidth >= 1 && source.centerHeight >= 1)
                }
            }
        }
    }

    @Test
    fun oneAndTwoPixelImagesUseTheWholeImageAsTheirCenter() {
        for (width in 1..2) {
            for (height in 1..2) {
                val source = buildSliceLayout(width, height, BubbleImageStyleConfig("test"))
                assertEquals(width, source.centerWidth)
                assertEquals(height, source.centerHeight)
                assertEquals(0, source.leftCapWidth + source.rightCapWidth)
                assertEquals(0, source.topCapHeight + source.bottomCapHeight)
            }
        }
    }

    @Test
    fun tilePeriodsKeepPixelRoundingAndAllowOnePixelRepeats() {
        assertEquals(1, bubbleTileExtent(sourceExtent = 1, scale = 0.2f))
        assertEquals(1, bubbleTileExtent(sourceExtent = 2, scale = 0.2f))
        assertEquals(11, bubbleTileExtent(sourceExtent = 7, scale = 1.5f))
        assertEquals(21, bubbleTileExtent(sourceExtent = 7, scale = 3f))
    }

    @Test
    fun narrowDestinationsKeepCapsFromOverlappingTheCenter() {
        val source = buildSliceLayout(100, 100, BubbleImageStyleConfig("test"))
        for (width in listOf(1, 2, 20, 1080)) {
            for (height in listOf(1, 2, 20, 2400)) {
                val destination = computeDstSliceLayout(source, width, height, imageScale = 3f)
                assertTrue(destination.centerDstStartX < destination.centerDstEndX)
                assertTrue(destination.centerDstStartY < destination.centerDstEndY)
                assertEquals(width, destination.centerDstEndX + destination.rightDstWidth)
                assertEquals(height, destination.centerDstEndY + destination.bottomDstHeight)
            }
        }
    }

    @Test
    fun messageBlocksReuseTheSameCenterAndKeepDecorationsOnlyAtTheEnds() {
        val source = buildSliceLayout(100, 100, BubbleImageStyleConfig("test"))
        val first = source.forMessageBlock(first = true, last = false)
        val middle = source.forMessageBlock(first = false, last = false)
        val last = source.forMessageBlock(first = false, last = true)

        assertEquals(source.topCapHeight, first.topCapHeight)
        assertEquals(0, first.bottomCapHeight)
        assertEquals(0, middle.topCapHeight)
        assertEquals(0, middle.bottomCapHeight)
        assertEquals(source.centerHeight, middle.srcHeight)
        assertEquals(source.bottomCapHeight, last.bottomCapHeight)
        for (block in listOf(first, middle, last)) {
            assertEquals(source.srcY + source.topCapHeight, block.srcY + block.topCapHeight)
            assertEquals(source.centerWidth, block.centerWidth)
            assertEquals(source.centerHeight, block.centerHeight)
        }
    }
}
