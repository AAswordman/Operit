package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Node
import kotlin.math.*

private data class MemoryCurve(val start: Offset, val control: Offset, val end: Offset) {
    fun point(t: Float): Offset = start * ((1 - t) * (1 - t)) + control * (2 * (1 - t) * t) + end * (t * t)
}

private fun memoryEdgeCurve(a: Offset, b: Offset, halfWidth: Float, halfHeight: Float, bend: Float): MemoryCurve {
    fun border(from: Offset, to: Offset): Offset {
        val delta = to - from
        val ratio = max(abs(delta.x) / halfWidth, abs(delta.y) / halfHeight).coerceAtLeast(1f)
        return from + delta / ratio
    }
    val start = border(a, b)
    val end = border(b, a)
    val delta = end - start
    val normal = Offset(-delta.y, delta.x) / delta.getDistance().coerceAtLeast(1f)
    return MemoryCurve(start, (start + end) / 2f + normal * bend, end)
}

private fun memorySegmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val d = b - a
    val denominator = d.x * d.x + d.y * d.y
    val t = if (denominator == 0f) 0f else (((p - a).x * d.x + (p - a).y * d.y) / denominator).coerceIn(0f, 1f)
    return (p - (a + d * t)).getDistance()
}

/** 节点保留可读初始比例，通过平移探索；只组合视口附近的原生可访问按钮。 */
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun MemoryGraphCanvas(
    graph: Graph,
    modifier: Modifier = Modifier,
    overview: Boolean,
    resetKey: String,
    scopeFolder: String,
    selectedId: String?,
    focusedId: String?,
    selectedEdgeId: Long?,
    selectedIds: Set<String>,
    boxSelection: Boolean,
    linkingIds: List<String>,
    allLabels: Boolean,
    locateToken: Int,
    hasPeek: Boolean,
    onNodeClick: (Node) -> Unit,
    onEdgeClick: (Edge) -> Unit,
    onNodesSelected: (Set<String>) -> Unit,
) {
    val density = LocalDensity.current.density
    val uncategorized = stringResource(R.string.memory_uncategorized)
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val latestGraph by rememberUpdatedState(graph)
    val latestEdgeClick by rememberUpdatedState(onEdgeClick)
    val latestSelection by rememberUpdatedState(onNodesSelected)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selection by remember { mutableStateOf<Rect?>(null) }
    val layoutKey = graph.nodes.map { it.id } to (resetKey to focusedId)
    val positions: Map<String, Offset> = remember(layoutKey, density) {
        val root = graph.nodes.firstOrNull { it.id == focusedId }
        if (root != null) {
            buildMap<String, Offset> {
                put(root.id, Offset(190f, 300f) * density)
                val offsets = listOf(Offset(-92f, -183f), Offset(92f, -117f), Offset(-92f, 137f), Offset(92f, 218f), Offset(275f, 20f), Offset(-275f, 35f))
                graph.nodes.filter { it.id != root.id }.forEachIndexed { index, node ->
                    val origin = Offset(190f + index / offsets.size * 390f, 300f)
                    put(node.id, (origin + offsets[index % offsets.size]) * density)
                }
            }
        } else if (overview) {
            graph.nodes.mapIndexed { index, node ->
                val x = if (index % 2 == 0) 112f else 248f
                node.id to Offset(x, 125f + index * 155f) * density
            }.toMap()
        } else {
            buildMap<String, Offset> {
                val local = graph.nodes.filter { memoryMatchesFolder(it, scopeFolder, uncategorized) }
                val external = graph.nodes.filterNot { memoryMatchesFolder(it, scopeFolder, uncategorized) }
                local.forEachIndexed { index, node ->
                    val x = if (index % 2 == 0) 96f else 274f
                    val stagger = if (index % 2 == 0) 0f else 52f
                    put(node.id, Offset(x, 125f + index / 2 * 166f + stagger) * density)
                }
                external.forEachIndexed { index, node ->
                    val x = if (index % 2 == 0) 470f else 650f
                    val stagger = if (index % 2 == 0) 0f else 62f
                    put(node.id, Offset(x, 145f + index / 2 * 166f + stagger) * density)
                }
            }
        }
    }
    fun center(id: String? = selectedId) {
        val p = id?.let { positions[it] } ?: Offset(180f, if (overview && graph.nodes.size <= 3) 250f else 180f) * density
        scale = if (overview && graph.nodes.size <= 3) min(1.1f, min(viewport.width / (380f * density), viewport.height / (590f * density))).coerceAtLeast(.82f) else 1f
        pan = Offset(viewport.width / 2f, max(110f * density, viewport.height * if (hasPeek) .38f else .46f)) - p * scale
    }
    fun zoom(factor: Float) {
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val next = (scale * factor).coerceIn(.35f, 2f)
        pan = center - (center - pan) * (next / scale)
        scale = next
    }
    LaunchedEffect(layoutKey, viewport) { if (viewport != IntSize.Zero) center() }
    LaunchedEffect(locateToken, selectedId) { if (viewport != IntSize.Zero && selectedId != null) center() }
    val far = !overview && scale < .65f
    val cardWidth = (if (far) 44f else if (overview) 164f else 156f) * density
    val cardHeight = (if (far) 44f else if (overview) 128f else 92f) * density
    fun currentDimensions(): Offset = if (!overview && scale < .65f) Offset(44f, 44f) * density else Offset(if (overview) 164f else 156f, if (overview) 128f else 92f) * density
    fun nodeRect(id: String): Rect? = positions[id]?.let {
        val center = it * scale + pan
        val dimensions = currentDimensions() * scale
        Rect(center - dimensions / 2f, Size(dimensions.x, dimensions.y))
    }
    fun curve(edge: Edge, index: Int): MemoryCurve? {
        val a = positions[edge.sourceId] ?: return null
        val b = positions[edge.targetId] ?: return null
        return memoryEdgeCurve(a * scale + pan, b * scale + pan, currentDimensions().x * scale / 2f, currentDimensions().y * scale / 2f, (if (index % 2 == 0) 1 else -1) * (18 + index % 4 * 7) * density * scale)
    }
    val edgeNames = graph.edges.associate { edge ->
        edge.id to if (overview) stringResource(R.string.memory_workbench_count_links, edge.metadata["count"]?.toIntOrNull() ?: 0) else stringResource(memoryRelationName(edge.label.orEmpty()))
    }
    val labels = remember(edgeNames, labelStyle) { edgeNames.mapValues { measurer.measure(AnnotatedString(it.value), style = labelStyle) } }

    Box(modifier.clipToBounds().background(colors.background).onSizeChanged { viewport = it }.pointerInput(layoutKey, boxSelection) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            val start = first.position
            var moved = false
            var pinched = false
            do {
                val event = awaitPointerEvent()
                val handledTap = event.changes.any { it.isConsumed }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    pinched = true
                    selection = null
                    val centroid = pressed.map { it.previousPosition }.reduce { a, b -> a + b } / pressed.size.toFloat()
                    val next = (scale * event.calculateZoom()).coerceIn(.35f, 2f)
                    pan = centroid - (centroid - pan) * (next / scale) + event.calculatePan()
                    scale = next
                } else if (pressed.size == 1 && !pinched) {
                    val change = pressed.first()
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (moved) {
                        if (boxSelection) selection = Rect(Offset(min(start.x, change.position.x), min(start.y, change.position.y)), Offset(max(start.x, change.position.x), max(start.y, change.position.y)))
                        else pan += change.position - change.previousPosition
                    }
                }
                if (moved || pinched) event.changes.forEach { it.consume() }
                if (pressed.isEmpty()) {
                    val rect = selection
                    if (rect != null) latestSelection(positions.keys.filter { id -> nodeRect(id)?.overlaps(rect) == true }.toSet())
                    if (!moved && !pinched && !handledTap && !overview && positions.keys.none { nodeRect(it)?.contains(start) == true }) {
                        latestGraph.edges.withIndex().firstOrNull { (index, edge) ->
                            val line = curve(edge, index) ?: return@firstOrNull false
                            (0 until 18).any { memorySegmentDistance(start, line.point(it / 18f), line.point((it + 1) / 18f)) < 12f * density }
                        }?.let { latestEdgeClick(it.value) }
                    }
                    selection = null
                }
            } while (event.changes.any { it.pressed })
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) { drawCircle(colors.outlineVariant.copy(alpha = .35f), .7.dp.toPx(), Offset(x, y)); y += step }
                x += step
            }
            val occupied = positions.keys.mapNotNull(::nodeRect).toMutableList()
            graph.edges.forEachIndexed { index, edge ->
                val line = curve(edge, index) ?: return@forEachIndexed
                val active = edge.id == selectedEdgeId || selectedId == edge.sourceId || selectedId == edge.targetId
                val ink = if (active) colors.primary else colors.outline.copy(alpha = if (selectedId == null || overview) .7f else .25f)
                val path = Path().apply { moveTo(line.start.x, line.start.y); quadraticBezierTo(line.control.x, line.control.y, line.end.x, line.end.y) }
                drawPath(path, ink, style = Stroke(width = (if (active) 1.8f else 1.1f) * density, pathEffect = if (edge.isCrossFolderLink) PathEffect.dashPathEffect(floatArrayOf(4f * density, 5f * density)) else null))
                if (active && !overview) {
                    val direction = (line.end - line.control) / (line.end - line.control).getDistance().coerceAtLeast(1f)
                    val wing = Offset(-direction.y, direction.x) * 3f * density
                    drawLine(ink, line.end, line.end - direction * 7f * density + wing, 1.4f * density)
                    drawLine(ink, line.end, line.end - direction * 7f * density - wing, 1.4f * density)
                }
                if ((overview || allLabels || active) && !far) {
                    val label = labels[edge.id] ?: return@forEachIndexed
                    val middle = line.point(.5f)
                    val candidates = listOf(Offset.Zero, Offset(0f, -24f), Offset(0f, 24f), Offset(36f, -20f), Offset(-36f, 20f))
                    val rectangles = candidates.map { shift -> Rect(middle + shift * density - Offset(label.size.width / 2f + 5f * density, label.size.height / 2f + 2f * density), Size(label.size.width + 10f * density, label.size.height + 4f * density)) }
                    val rect = rectangles.firstOrNull { candidate -> occupied.none { it.overlaps(candidate) } }
                    if (rect != null) {
                        occupied.add(rect)
                        drawRoundRect(colors.background, rect.topLeft, rect.size, androidx.compose.ui.geometry.CornerRadius(6f * density))
                        drawText(label, topLeft = rect.topLeft + Offset(5f * density, 2f * density), color = if (active) colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
            selection?.let { rect -> drawRect(colors.primary.copy(alpha = .12f), rect.topLeft, rect.size); drawRect(colors.primary, rect.topLeft, rect.size, style = Stroke(1.dp.toPx())) }
        }
        graph.nodes.forEachIndexed { index, node ->
            val position = positions[node.id] ?: return@forEachIndexed
            val screen = position * scale + pan
            if (screen.x < -220f * density || screen.y < -180f * density || screen.x > viewport.width + 220f * density || screen.y > viewport.height + 180f * density) return@forEachIndexed
            key(node.id) {
                val selected = node.id == selectedId || node.id in selectedIds || node.id in linkingIds
                val tint = when (Math.floorMod(node.metadata["folder"].orEmpty().hashCode(), 3)) { 1 -> colors.secondary; 2 -> colors.tertiary; else -> colors.primary }
                Surface(
                    onClick = { onNodeClick(node) },
                    modifier = Modifier.offset { IntOffset((screen.x - cardWidth / 2f).roundToInt(), (screen.y - cardHeight / 2f).roundToInt()) }.size((cardWidth / density).dp, (cardHeight / density).dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    shape = if (far) CircleShape else RoundedCornerShape(if (overview) 20.dp else 15.dp),
                    color = if (selected || far) colors.primaryContainer else colors.surfaceContainer,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else if (overview) tint.copy(alpha = .6f) else colors.outlineVariant),
                ) {
                    if (far) Box(contentAlignment = Alignment.Center) { Icon(if (node.id in selectedIds) Icons.Default.Check else Icons.Default.Circle, node.label, Modifier.size(9.dp), tint = tint) }
                    else Column(Modifier.padding(if (overview) 14.dp else 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (overview) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp), tint = tint); Text("%02d".format(index + 1), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val kind = stringResource(if (node.metadata["document"] == "true") R.string.memory_workbench_document else R.string.memory_workbench_plain)
                            val external = scopeFolder.isNotEmpty() && !memoryMatchesFolder(node, scopeFolder, uncategorized)
                            Text(if (external) kind + " · ↗ " + node.metadata["folder"].orEmpty().ifEmpty { uncategorized } else kind, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                            if (selected) Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = colors.primary)
                        }
                        Text(node.label, style = if (overview) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (overview) Text(stringResource(R.string.memory_workbench_count_memories, node.metadata["count"]?.toIntOrNull() ?: 0), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = if (hasPeek) 166.dp else 68.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outlineVariant), color = colors.surfaceContainer) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { zoom(1.18f) }) { Icon(Icons.Default.Add, stringResource(R.string.memory_workbench_zoom_in)) }
                Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { zoom(1 / 1.18f) }) { Icon(Icons.Default.Remove, stringResource(R.string.memory_workbench_zoom_out)) }
                HorizontalDivider(Modifier.width(24.dp))
                IconButton(onClick = { center() }) { Icon(Icons.Default.MyLocation, stringResource(R.string.memory_workbench_recenter)) }
            }
        }
        if (!overview && positions.isNotEmpty()) Surface(onClick = { center() }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = if (hasPeek) 162.dp else 52.dp).size(56.dp, 82.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val minX = positions.values.minOf { it.x } - 100f * density
                val minY = positions.values.minOf { it.y } - 100f * density
                val width = (positions.values.maxOf { it.x } - minX + 100f * density).coerceAtLeast(1f)
                val height = (positions.values.maxOf { it.y } - minY + 100f * density).coerceAtLeast(1f)
                fun map(p: Offset) = Offset((p.x - minX) / width * size.width, (p.y - minY) / height * size.height)
                positions.forEach { (id, point) -> drawCircle(if (id == selectedId) colors.primary else colors.onSurfaceVariant, if (id == selectedId) 3f else 2f, map(point)) }
                val top = map((Offset.Zero - pan) / scale)
                val bottom = map((Offset(viewport.width.toFloat(), viewport.height.toFloat()) - pan) / scale)
                drawRect(colors.primary.copy(alpha = .14f), top, Size(bottom.x - top.x, bottom.y - top.y))
                drawRect(colors.primary, top, Size(bottom.x - top.x, bottom.y - top.y), style = Stroke(1f))
            }
        }
    }
}