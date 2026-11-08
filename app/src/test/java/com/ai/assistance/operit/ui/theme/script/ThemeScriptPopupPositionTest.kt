package com.ai.assistance.operit.ui.theme.script

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeScriptPopupPositionTest {
    private val window = IntSize(360, 720)
    private val panel = IntSize(300, 420)
    private val margin = IntOffset(12, 44)

    @Test
    fun zeroOffsetPreservesTheExistingBottomEndAnchor() {
        assertEquals(IntOffset(48, 256), themePopupPosition(window, panel, margin, IntOffset.Zero, LayoutDirection.Ltr))
        assertEquals(IntOffset(24, 156), themePopupPosition(window, panel, margin, IntOffset(-24, -100), LayoutDirection.Ltr))
    }

    @Test
    fun largeOffsetsKeepTheWholeMenuInsideTheWindow() {
        assertEquals(IntOffset.Zero, themePopupPosition(window, panel, margin, IntOffset(-256, -256), LayoutDirection.Ltr))
        assertEquals(IntOffset(48, 300), themePopupPosition(window, panel, margin, IntOffset(0, 256), LayoutDirection.Ltr))
    }

    @Test
    fun expandedContentAndSmallWindowsRecomputeAvailableSpace() {
        assertEquals(IntOffset(48, 576), themePopupPosition(window, IntSize(300, 100), margin, IntOffset.Zero, LayoutDirection.Ltr))
        assertEquals(IntOffset(48, 256), themePopupPosition(window, panel, margin, IntOffset.Zero, LayoutDirection.Ltr))
        assertEquals(IntOffset.Zero, themePopupPosition(IntSize(200, 120), IntSize(188, 76), margin, IntOffset(-256, -256), LayoutDirection.Ltr))
        assertEquals(IntOffset(0, 44), themePopupPosition(IntSize(200, 120), IntSize(188, 76), margin, IntOffset(-256, 256), LayoutDirection.Ltr))
    }

    @Test
    fun leftOffsetUsesThePhysicalDirectionInRtlLayouts() {
        assertEquals(IntOffset(12, 256), themePopupPosition(window, panel, margin, IntOffset.Zero, LayoutDirection.Rtl))
        assertEquals(IntOffset(4, 256), themePopupPosition(window, panel, margin, IntOffset(-8, 0), LayoutDirection.Rtl))
        assertEquals(IntOffset(0, 256), themePopupPosition(window, panel, margin, IntOffset(-256, 0), LayoutDirection.Rtl))
    }
}
