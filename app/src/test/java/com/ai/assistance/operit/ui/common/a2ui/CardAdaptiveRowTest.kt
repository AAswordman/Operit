package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class CardAdaptiveRowTest {
    @Test fun logoKeepsItsWidthAndTextGetsTheRemainingSpace() {
        assertEquals(listOf(48, 236), cardRowWidths(300, 16, listOf(0, 1), listOf(48, 0), 120))
        assertEquals(listOf(236, 48), cardRowWidths(300, 16, listOf(1, 0), listOf(0, 48), 120))
    }

    @Test fun narrowAndLargeFontRowsStackInsteadOfStarvingText() {
        assertNull(cardRowWidths(170, 16, listOf(0, 1), listOf(48, 0), 120))
        assertNull(cardRowWidths(300, 16, listOf(0, 1), listOf(48, 0), 240))
        assertNull(cardRowWidths(100, 16, listOf(0, 1), listOf(100, 0), 120))
        assertNull(cardRowWidths(100, 16, listOf(0, 0), listOf(70, 70), 120))
        assertEquals(listOf(24, 60), cardRowWidths(100, 8, listOf(0, 0), listOf(24, 60), 120))
    }

    @Test fun proportionalWidthsPreserveEveryPixelAndRespectSmallestRegion() {
        assertEquals(listOf(127, 255), cardRowWidths(390, 8, listOf(1, 2), listOf(0, 0), 120))
        assertNull(cardRowWidths(320, 8, listOf(1, 2), listOf(0, 0), 120))
        assertEquals(listOf(132, 132, 133), cardRowWidths(413, 8, listOf(1, 1, 1), listOf(0, 0, 0), 120))
    }

    @Test fun authoringAndRendererAcceptWeightsButRejectAmbiguousOrDynamicPlans() {
        val children = """children=[Icon(name="info"),Text(text="Description")]"""
        for (weights in listOf("[0,1]", "[1,1]", "[1,2]")) {
            assertEquals(JsonPrimitive(true), CardExpress.report(
                """root=Row($children,weights=$weights,minItemWidth=140,wrap=false)"""
            )["valid"])
        }
        for (weights in listOf("[]", "[1]", "[0,0]", "[-1,1]", "[1.5,1]", "[13,1]", """["1",1]""")) {
            assertEquals(weights, JsonPrimitive(false), CardExpress.report(
                """root=Row($children,weights=$weights)"""
            )["valid"])
        }
        assertEquals(JsonPrimitive(false), CardExpress.report(
            "${'$'}/widths=[0,1]\nroot=Row($children,weights=${'$'}/widths)"
        )["valid"])
        assertEquals(JsonPrimitive(false), CardExpress.report(
            """root=Row($children,weights=[0,1],minItemWidth=1)"""
        )["valid"])
    }
}
