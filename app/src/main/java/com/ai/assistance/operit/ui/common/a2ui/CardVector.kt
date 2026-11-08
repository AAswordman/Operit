package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import kotlin.math.min

internal data class CardVectorData(val label: String, val width: Float, val height: Float, val shapes: List<Shape>) {
    data class Shape(val type: String, val tone: String, val filled: Boolean, val stroke: Float,
        val values: Map<String, Float>, val points: List<Offset>, val text: String)
    companion object {
        fun validateDefinition(value: JsonObject) {
            val shapes = value["shapes"] as? JsonArray
            require(shapes != null && shapes.size in 1..64) { "Vector needs 1-64 shapes" }
            shapes.forEach {
                val shape = it as? JsonObject ?: error("Invalid vector shape")
                require(shape["type"] is JsonPrimitive && shape["type"]?.jsonPrimitive?.isString == true) {
                    "Vector shape type must be literal"
                }
                shape["points"]?.let { points ->
                    require(points is JsonArray && points.size <= 128 &&
                        points.all { p -> p is JsonArray && p.size == 2 }) { "Invalid vector points" }
                }
            }
        }
        fun parse(value: JsonObject): CardVectorData {
            fun number(obj: JsonObject, key: String, range: ClosedFloatingPointRange<Float>): Float {
                val n = (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull
                require(n != null && n.isFinite() && n in range) { "Invalid vector coordinate: $key" }
                return n
            }
            val width = number(value, "width", 1f..1000f)
            val height = number(value, "height", 1f..1000f)
            val label = (value["label"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            require(label.isNotBlank() && label.length <= 256) { "Vector needs a short accessibility label" }
            val shapes = value["shapes"] as? JsonArray
            require(shapes != null && shapes.size in 1..64) { "Vector needs 1-64 shapes" }
            return CardVectorData(label, width, height, shapes.map { element ->
                val s = element as? JsonObject ?: error("Invalid vector shape")
                val type = s["type"]?.jsonPrimitive?.content
                val keys = when (type) {
                    "line", "arrow" -> setOf("x1", "y1", "x2", "y2")
                    "quadratic" -> setOf("x1", "y1", "cx", "cy", "x2", "y2")
                    "cubic" -> setOf("x1", "y1", "cx1", "cy1", "cx2", "cy2", "x2", "y2")
                    "rect" -> setOf("x", "y", "width", "height")
                    "circle" -> setOf("cx", "cy", "r")
                    "polyline", "polygon" -> setOf("points")
                    "text" -> setOf("x", "y", "text", "size")
                    else -> error("Unsupported vector shape")
                }
                require(s.keys.all { it in keys || it in setOf("type", "tone", "filled", "stroke") }) { "Unknown vector property" }
                val tone = s["tone"]?.jsonPrimitive?.content ?: "neutral"
                require(tone in setOf("neutral", "success", "info", "warning", "error", "accent")) { "Invalid vector tone" }
                val filled = s["filled"]?.let {
                    (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.booleanOrNull
                        ?: error("Invalid vector fill")
                } ?: false
                val stroke = if ("stroke" in s) number(s, "stroke", 0.5f..12f) else 2f
                val numbers = keys.filter { it !in setOf("text", "points", "size") }.associateWith { key ->
                    number(s, key, 0f..when (key) {
                        "y", "y1", "y2", "cy", "cy1", "cy2", "height" -> height
                        "r" -> min(width, height) / 2
                        else -> width
                    })
                }.toMutableMap()
                if (type == "rect") require(numbers.getValue("x") + numbers.getValue("width") <= width &&
                    numbers.getValue("y") + numbers.getValue("height") <= height) { "Vector shape outside viewport" }
                if (type == "circle") {
                    val r = numbers.getValue("r")
                    require(numbers.getValue("cx") in r..(width-r) && numbers.getValue("cy") in r..(height-r)) { "Vector shape outside viewport" }
                }
                val points = if ("points" in keys) {
                    val p = s["points"] as? JsonArray ?: error("Invalid vector points")
                    require(p.size in (if (type == "polygon") 3 else 2)..128) { "Invalid vector point count" }
                    p.map {
                        val pair = it as? JsonArray
                        require(pair != null && pair.size == 2) { "Invalid vector point" }
                        val obj = buildJsonObject { put("x", pair[0]); put("y", pair[1]) }
                        Offset(number(obj, "x", 0f..width), number(obj, "y", 0f..height))
                    }
                } else emptyList()
                val text = if (type == "text") {
                    numbers["size"] = if ("size" in s) number(s, "size", 8f..48f) else 16f
                    (s["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?.takeIf { it.length in 1..256 } ?: error("Invalid vector text")
                } else ""
                Shape(type!!, tone, filled, stroke, numbers, points, text)
            })
        }
    }
}

internal object CardVector : A2uiComponent {
    override val name = "Vector"
    override val description = "Bounded vector primitives with data-bound properties; no scripts or SVG markup."
    private val label = A2uiProperty.any("label")
    private val width = A2uiProperty.any("width")
    private val height = A2uiProperty.any("height")
    private val shapes = A2uiProperty.any("shapes")
    override val properties = listOf(label, width, height, shapes)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val resolve = LocalCardValueResolver.current
        // Keep invalid expressions inside the visual's boundary. AndroidX bind reports an
        // irreversible registry error; this pure evaluator lets the user correct input.
        val data = runCatching {
            CardVectorData.parse(CardRepeat.evaluate(A2uiProtocol.toJson(mapOf(
                "label" to properties[label], "width" to properties[width],
                "height" to properties[height], "shapes" to properties[shapes],
            )), resolve).jsonObject)
        }.getOrNull()
        if (data == null) {
            Text(stringResource(R.string.a2ui_invalid), modifier, color = MaterialTheme.colorScheme.error)
            return
        }
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        // In a row, a small coordinate viewport is also the graphic's preferred
        // width. A standalone diagram still expands; Frame can set an explicit size.
        Canvas(modifier
            .then(if (LocalCardRowItem.current) Modifier.widthIn(max = data.width.dp) else Modifier)
            .fillMaxWidth().heightIn(max = 320.dp).aspectRatio(data.width / data.height)
            .clipToBounds().semantics { contentDescription = data.label }) {
            val scale = min(size.width / data.width, size.height / data.height)
            val origin = Offset((size.width-data.width*scale)/2, (size.height-data.height*scale)/2)
            fun point(x: Float, y: Float) = origin + Offset(x*scale, y*scale)
            data.shapes.forEach { shape ->
                val n = shape.values
                val tones = if (shape.tone == "accent") scheme.primary to scheme.primaryContainer else cardToneColors(shape.tone, scheme)
                // Filled regions use the quiet semantic background, keeping overlaid labels readable.
                val color = if (shape.filled && shape.type in setOf("rect","circle","polygon")) tones.second else tones.first
                val style = if (shape.filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(shape.stroke*scale)
                when (shape.type) {
                    "line" -> drawLine(color, point(n.getValue("x1"),n.getValue("y1")), point(n.getValue("x2"),n.getValue("y2")), shape.stroke*scale)
                    "arrow" -> {
                        val start = point(n.getValue("x1"), n.getValue("y1"))
                        val end = point(n.getValue("x2"), n.getValue("y2"))
                        drawLine(color, start, end, shape.stroke * scale)
                        val delta = end - start
                        val length = delta.getDistance()
                        if (length > 0f) {
                            val unit = delta / length
                            val head = min(length / 2f, 8f * scale + shape.stroke * scale)
                            val side = Offset(-unit.y, unit.x) * head * 0.5f
                            drawPath(Path().apply {
                                moveTo(end.x, end.y)
                                val left = end - unit * head + side
                                val right = end - unit * head - side
                                lineTo(left.x, left.y); lineTo(right.x, right.y); close()
                            }, color)
                        }
                    }
                    "quadratic", "cubic" -> {
                        val start = point(n.getValue("x1"), n.getValue("y1"))
                        val end = point(n.getValue("x2"), n.getValue("y2"))
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            if (shape.type == "quadratic") {
                                val control = point(n.getValue("cx"), n.getValue("cy"))
                                quadraticTo(control.x, control.y, end.x, end.y)
                            } else {
                                val c1 = point(n.getValue("cx1"), n.getValue("cy1"))
                                val c2 = point(n.getValue("cx2"), n.getValue("cy2"))
                                cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
                            }
                        }
                        drawPath(path, color, style = Stroke(shape.stroke * scale))
                    }
                    "rect" -> drawRect(color, point(n.getValue("x"),n.getValue("y")), Size(n.getValue("width")*scale,n.getValue("height")*scale), style = style)
                    "circle" -> drawCircle(color,n.getValue("r")*scale,point(n.getValue("cx"),n.getValue("cy")),style=style)
                    "polyline", "polygon" -> {
                        val path = Path()
                        shape.points.forEachIndexed { i, p -> val q = point(p.x,p.y); if (i==0) path.moveTo(q.x,q.y) else path.lineTo(q.x,q.y) }
                        if (shape.type == "polygon") path.close()
                        drawPath(path,color,style=if(shape.type=="polyline") Stroke(shape.stroke*scale) else style)
                    }
                    "text" -> drawText(measurer, AnnotatedString(shape.text),
                        point(n.getValue("x"),n.getValue("y")),
                        style = TextStyle(color=color,fontSize=(n.getValue("size")*scale/density/fontScale).sp))
                }
            }
        }
    }
}
