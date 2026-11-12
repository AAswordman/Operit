package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

internal class MeasuredCanvas(val height: Int, val draw: DrawScope.() -> Unit)

/** Width-dependent text layout belongs in measurement, not in a nested subcomposition. */
@Composable
internal fun WidthMeasuredCanvas(
    modifier: Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    measure: (width: Int) -> MeasuredCanvas,
) {
    if (overlay == null) {
        MeasuredCanvasContent(modifier, measure)
    } else {
        Box(modifier) {
            MeasuredCanvasContent(Modifier.fillMaxWidth(), measure)
            Box(Modifier.matchParentSize(), content = overlay)
        }
    }
}

@Composable
private fun MeasuredCanvasContent(modifier: Modifier, measure: (Int) -> MeasuredCanvas) {
    val measured = remember { mutableStateOf<MeasuredCanvas?>(null) }
    Canvas(modifier.layout { measurable, constraints ->
        val width = constraints.constrainWidth(
            if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth)
        val result = measure(width.coerceAtLeast(1))
        measured.value = result
        val height = constraints.constrainHeight(result.height.coerceAtLeast(0))
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }) {
        // Only draw observes this state; a new measurement never feeds a composition/measure loop.
        measured.value?.draw?.invoke(this)
    }
}
