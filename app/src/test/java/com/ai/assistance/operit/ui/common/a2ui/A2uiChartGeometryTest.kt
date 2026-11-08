package com.ai.assistance.operit.ui.common.a2ui

import org.junit.Assert.*
import org.junit.Test

class A2uiChartGeometryTest {
    @Test fun paletteKeepsFourSeriesColorsForAnyPrimary() {
        for (dark in listOf(false, true)) for (sat in listOf(0f, .1f, .6f, .9f)) for (hue in 0 until 360) {
            val primary = androidx.compose.ui.graphics.Color.hsv(hue.toFloat(), sat, .7f)
            val palette = cardChartPalette(primary, dark)
            assertEquals(primary, palette.first())
            assertTrue("hue $hue sat $sat dark $dark", palette.size >= 4 && palette.distinct().size == palette.size)
        }
    }

    @Test fun readableTicksCoverTheReportedCharts() {
        assertEquals("35", cardChartPercent(35.0, 100.0))
        assertEquals("33.3", cardChartPercent(1.0, 3.0))
        assertEquals("100", cardChartPercent(5.0, 5.0))
        assertEquals("<0.1", cardChartPercent(1.0, 5000.0))
        assertEquals("0", cardChartPercent(0.0, 10.0))
        val line = cardChartAxis(32.0, 288.0, false)
        assertEquals(listOf("0", "100", "200", "300"), cardChartTickLabels(line.ticks))
        val bars = cardChartAxis(24.0, 604.0, true)
        assertEquals(0.0, bars.min, 0.0)
        assertTrue(bars.max >= 604)
        assertTrue(cardChartTickLabels(bars.ticks).none { it.contains("E") || it.endsWith(".0") })
        assertEquals("69", cardChartNumber(69.0))
        assertEquals("0", cardChartNumber(-0.0))
    }

    @Test fun axesHandleZeroNegativeConstantDecimalAndTightlySpacedLargeValues() {
        listOf(0.0 to 0.0, -10.0 to -2.0, -4.0 to 8.0, 42.0 to 42.0,
            .1 to .4, 999_999_999_998.0 to 1e12, 1e12 - .001 to 1e12, 1e-300 to 2e-300).forEach { (min, max) ->
            val axis = cardChartAxis(min, max, false)
            assertTrue("$min $max $axis", axis.min <= min && axis.max >= max)
            assertTrue(axis.ticks.zipWithNext().all { (a, b) -> a.isFinite() && b > a })
            assertEquals(axis.ticks.size, cardChartTickLabels(axis.ticks).distinct().size)
        }
        assertTrue(cardChartTickLabels(cardChartAxis(.1, .4, false).ticks).contains("0.3"))
        assertEquals(listOf("0", "0.0000025", "0.000005", "0.0000075", "0.00001"),
            cardChartTickLabels(cardChartAxis(0.0, 9e-6, false).ticks))
        assertEquals(0.0, cardChartAxis(0.0, 0.0, true).min, 0.0)
        assertEquals(0.0, cardChartAxis(-10.0, -2.0, true).max, 0.0)
    }

    @Test fun lineInspectionUsesOneCategoryForAllSeries() {
        assertEquals(3, cardChartLineIndex(160f, 10f, 300f, 7))
        assertEquals(0, cardChartLineIndex(-100f, 10f, 300f, 7))
        assertEquals(6, cardChartLineIndex(500f, 10f, 300f, 7))
        assertEquals(0, cardChartLineIndex(160f, 10f, 300f, 1))
    }

    @Test fun barHitTargetsDependOnlyOnHorizontalSlotsNotBarHeight() {
        // Six categories of width 100, two bars at x=32.5 and 67.5 within each slot.
        // Height and touch y are deliberately absent: whitespace over short/zero bars is selectable.
        assertEquals(0 to 5, cardChartBarSelection(542.5f, 10f, 600f, 6, 2))
        assertEquals(1 to 5, cardChartBarSelection(577.5f, 10f, 600f, 6, 2))
        assertEquals(0 to 0, cardChartBarSelection(-10f, 10f, 600f, 6, 2))
        assertEquals(1 to 5, cardChartBarSelection(900f, 10f, 600f, 6, 2))
    }
}
