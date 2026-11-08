package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardFrameCurveTest {
    @Test fun constraintsWinWhenRatioCannotFit() {
        assertEquals(IntSize(300,50), cardFrameSize(Constraints(300,400,0,50),1f))
        assertEquals(IntSize(100,50), cardFrameSize(Constraints(0,180,0,50),2f))
        assertEquals(IntSize(180,180), cardFrameSize(Constraints(180,180,0,480),1f))
    }
    @Test fun curvesRejectOutOfBoundsControls() {
        val valid = """root = Vector(label="Curve",width=100,height=100,shapes=[{type:"cubic",x1:0,y1:50,cx1:20,cy1:0,cx2:80,cy2:100,x2:100,y2:50}])"""
        assertTrue(CardExpress.report(valid).getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report(valid.replace("cy2:100","cy2:101")).getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report("""root = Frame(child=Text(text="x"),minWidth=300,maxWidth=200)""").getValue("valid").jsonPrimitive.boolean)
    }
}
