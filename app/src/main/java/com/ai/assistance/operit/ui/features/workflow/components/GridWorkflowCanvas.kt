package com.ai.assistance.operit.ui.features.workflow.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.model.ConditionNode
import com.ai.assistance.operit.data.model.ExecuteNode
import com.ai.assistance.operit.data.model.ExtractNode
import com.ai.assistance.operit.data.model.LogicNode
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.data.model.WorkflowNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection
import kotlin.math.roundToInt

// 画布配置常量
private val CANVAS_WIDTH = 4000.dp
private val CANVAS_HEIGHT = 3000.dp
private val CELL_SIZE = 40.dp  // 更小的网格单元
private val NODE_WIDTH = 120.dp  // 节点宽度
private val NODE_HEIGHT = 80.dp  // 节点高度

/**
 * 网格工作流画布组件
 * 提供固定网格布局，支持节点拖放和连接线绘制
 */
@Composable
fun GridWorkflowCanvas(
    nodes: List<WorkflowNode>,
    connections: List<WorkflowNodeConnection>,
    nodeExecutionStates: Map<String, NodeExecutionState> = emptyMap(),
    onNodePositionChanged: (nodeId: String, x: Float, y: Float) -> Unit,
    onNodeLongPress: (nodeId: String) -> Unit,
    onNodeClick: (nodeId: String) -> Unit,
    modifier: Modifier = Modifier,
    cellSize: Dp = CELL_SIZE
) {
    val density = LocalDensity.current
    val cellSizePx = with(density) { cellSize.toPx() }
    val nodeWidthPx = with(density) { NODE_WIDTH.toPx() }
    val nodeHeightPx = with(density) { NODE_HEIGHT.toPx() }
    val nodeById = remember(nodes) { nodes.associateBy { it.id } }

    val referenceEdges = remember(nodes) {
        val edges = LinkedHashSet<Pair<String, String>>()
        val nodeIdSet = nodes.map { it.id }.toSet()

        fun addEdge(sourceId: String, targetId: String) {
            if (sourceId == targetId) return
            if (!nodeIdSet.contains(sourceId)) return
            if (!nodeIdSet.contains(targetId)) return
            edges.add(sourceId to targetId)
        }

        nodes.forEach { node ->
            when (node) {
                is ExecuteNode -> {
                    node.actionConfig.values.forEach { value ->
                        if (value is ParameterValue.NodeReference) {
                            addEdge(value.nodeId, node.id)
                        }
                    }
                }
                is ConditionNode -> {
                    val left = node.left
                    val right = node.right
                    if (left is ParameterValue.NodeReference) {
                        addEdge(left.nodeId, node.id)
                    }
                    if (right is ParameterValue.NodeReference) {
                        addEdge(right.nodeId, node.id)
                    }
                }
                is ExtractNode -> {
                    val source = node.source
                    if (source is ParameterValue.NodeReference) {
                        addEdge(source.nodeId, node.id)
                    }

                    node.others.forEach { other ->
                        if (other is ParameterValue.NodeReference) {
                            addEdge(other.nodeId, node.id)
                        }
                    }
                }
                else -> Unit
            }
        }

        edges.toList()
    }

    fun connectionLabelText(connection: WorkflowNodeConnection): String? {
        val raw = connection.condition?.trim().orEmpty()
        val sourceNode = nodeById[connection.sourceNodeId]
        val isConditionLike = sourceNode is ConditionNode || sourceNode is LogicNode

        if (raw.isBlank()) {
            return if (isConditionLike) {
                "T"
            } else {
                null
            }
        }

        return when (raw.lowercase()) {
            "true" -> "T"
            "false" -> "F"
            else -> {
                val short = if (raw.length > 12) raw.take(12) + "…" else raw
                "R:$short"
            }
        }
    }

    fun parseBooleanLike(value: String): Boolean? {
        val normalized = value.trim().lowercase()
        return when (normalized) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> null
        }
    }

    fun isConnectionActive(connection: WorkflowNodeConnection): Boolean {
        val sourceState = nodeExecutionStates[connection.sourceNodeId]
        val sourceResult = (sourceState as? NodeExecutionState.Success)?.result ?: return false
        if (sourceResult == NodeExecutionState.Skipped().reason) return false

        val rawCondition = connection.condition?.trim().orEmpty()
        val sourceNode = nodeById[connection.sourceNodeId]
        val effectiveCondition =
            if (rawCondition.isBlank() && (sourceNode is ConditionNode || sourceNode is LogicNode)) {
                "true"
            } else {
                rawCondition
            }

        if (effectiveCondition.isBlank()) {
            return true
        }

        val desiredBool =
            when (effectiveCondition.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }

        if (desiredBool != null) {
            val actual = parseBooleanLike(sourceResult) ?: false
            return actual == desiredBool
        }

        return try {
            Regex(effectiveCondition).containsMatchIn(sourceResult)
        } catch (_: Exception) {
            false
        }
    }

    // 维护节点位置状态（像素坐标）
    val nodePositions = remember(nodes) {
        mutableStateMapOf<String, Offset>().apply {
            nodes.forEach { node ->
                this[node.id] = Offset(node.position.x, node.position.y)
            }
        }
    }

    // 拖动状态
    var draggingNodeId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // 画布缩放和平移状态
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val nodeIds = remember(nodes) { nodes.map { it.id } }
    var hasAutoFitted by remember(nodeIds) { mutableStateOf(false) }
    val fitPaddingPx = with(density) { 48.dp.toPx() }

    fun fitViewportToNodes() {
        if (viewportSize == IntSize.Zero || nodePositions.isEmpty()) return

        val minX = nodePositions.values.minOf { it.x }
        val minY = nodePositions.values.minOf { it.y }
        val maxX = nodePositions.values.maxOf { it.x + nodeWidthPx }
        val maxY = nodePositions.values.maxOf { it.y + nodeHeightPx }

        val contentWidth = (maxX - minX).coerceAtLeast(nodeWidthPx)
        val contentHeight = (maxY - minY).coerceAtLeast(nodeHeightPx)
        val availableWidth = (viewportSize.width.toFloat() - fitPaddingPx * 2).coerceAtLeast(1f)
        val availableHeight = (viewportSize.height.toFloat() - fitPaddingPx * 2).coerceAtLeast(1f)
        val targetScale = minOf(
            availableWidth / contentWidth,
            availableHeight / contentHeight
        ).coerceIn(0.25f, 3f)

        scale = targetScale
        panOffset = Offset(
            x = (viewportSize.width.toFloat() - contentWidth * targetScale) / 2f - minX * targetScale,
            y = (viewportSize.height.toFloat() - contentHeight * targetScale) / 2f - minY * targetScale
        )
    }

    LaunchedEffect(viewportSize, nodeIds, hasAutoFitted) {
        if (!hasAutoFitted && viewportSize != IntSize.Zero && nodePositions.isNotEmpty()) {
            fitViewportToNodes()
            hasAutoFitted = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1214))
            .onGloballyPositioned { coordinates ->
                viewportSize = coordinates.size
            }
            .pointerInput(Unit) {
                // 检测双指缩放和平移手势
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    // 应用缩放（限制在 0.25x 到 3x 之间）
                    val newScale = (scale * zoom).coerceIn(0.25f, 3f)

                    val scaleChange = newScale / scale
                    panOffset = (panOffset - centroid) * scaleChange + centroid + pan
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                // 检测双击重置视图
                detectTapGestures(
                    onDoubleTap = {
                        fitViewportToNodes()
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val scaledCellSize = cellSizePx * scale
            if (scaledCellSize <= 0f) return@Canvas

            val gridDotColor = Color(0xFF52616A).copy(alpha = 0.62f)
            val startX = ((panOffset.x % scaledCellSize) + scaledCellSize) % scaledCellSize
            val startY = ((panOffset.y % scaledCellSize) + scaledCellSize) % scaledCellSize
            val points = mutableListOf<Offset>()

            var x = startX
            while (x <= size.width) {
                var y = startY
                while (y <= size.height) {
                    points.add(Offset(x, y))
                    y += scaledCellSize
                }
                x += scaledCellSize
            }

            drawPoints(
                points = points,
                pointMode = PointMode.Points,
                color = gridDotColor,
                strokeWidth = (3f * scale).coerceAtLeast(1.5f),
                cap = StrokeCap.Round
            )
        }

        Box(
            modifier = Modifier
                .width(CANVAS_WIDTH)
                .height(CANVAS_HEIGHT)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            // 绘制网格背景和连接线
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height

                val labelTextSize = 34f
                val labelTextPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = labelTextSize
                    color = android.graphics.Color.BLACK
                }

                // 绘制网格点背景
                val gridDotColor = Color(0xFF52616A).copy(alpha = 0.62f)
                val points = mutableListOf<Offset>()
                var x = 0f
                while (x <= width) {
                    var y = 0f
                    while (y <= height) {
                        points.add(Offset(x, y))
                        y += cellSizePx
                    }
                    x += cellSizePx
                }

                drawPoints(
                    points = points,
                    pointMode = PointMode.Points,
                    color = gridDotColor,
                    strokeWidth = 3f, // 保持点阵轻量，避免遮住节点和连线
                    cap = StrokeCap.Round
                )

                val referenceLineColor = Color(0xFFFF9800).copy(alpha = 0.75f)
                val referenceLineWidth = 2.5f
                val referenceDash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

                fun getEdgePoint(center: Offset, dir: Offset): Offset {
                    val halfWidth = nodeWidthPx / 2
                    val halfHeight = nodeHeightPx / 2

                    val absX = kotlin.math.abs(dir.x)
                    val absY = kotlin.math.abs(dir.y)

                    val tRight = if (dir.x > 0 && absX > 0f) halfWidth / absX else Float.POSITIVE_INFINITY
                    val tLeft = if (dir.x < 0 && absX > 0f) halfWidth / absX else Float.POSITIVE_INFINITY
                    val tBottom = if (dir.y > 0 && absY > 0f) halfHeight / absY else Float.POSITIVE_INFINITY
                    val tTop = if (dir.y < 0 && absY > 0f) halfHeight / absY else Float.POSITIVE_INFINITY

                    val t = minOf(tRight, tLeft, tBottom, tTop)
                    return Offset(center.x + dir.x * t, center.y + dir.y * t)
                }

                fun drawArrow(position: Offset, angle: Float, color: Color, size: Float = 14f) {
                    val arrowAngle = Math.PI / 6
                    val arrowPath = Path().apply {
                        moveTo(position.x, position.y)
                        lineTo(
                            position.x - size * kotlin.math.cos(angle - arrowAngle).toFloat(),
                            position.y - size * kotlin.math.sin(angle - arrowAngle).toFloat()
                        )
                        lineTo(
                            position.x - size * kotlin.math.cos(angle + arrowAngle).toFloat(),
                            position.y - size * kotlin.math.sin(angle + arrowAngle).toFloat()
                        )
                        close()
                    }
                    drawPath(path = arrowPath, color = color)
                }

                referenceEdges.forEach { (sourceNodeId, targetNodeId) ->
                    val sourcePos = nodePositions[sourceNodeId]
                    val targetPos = nodePositions[targetNodeId]
                    if (sourcePos != null && targetPos != null) {
                        val sourceCenter = Offset(sourcePos.x + nodeWidthPx / 2, sourcePos.y + nodeHeightPx / 2)
                        val targetCenter = Offset(targetPos.x + nodeWidthPx / 2, targetPos.y + nodeHeightPx / 2)
                        val delta = targetCenter - sourceCenter
                        val dist = kotlin.math.sqrt(delta.x * delta.x + delta.y * delta.y)
                        if (dist <= 1f) return@forEach

                        val dir = Offset(delta.x / dist, delta.y / dist)
                        val startEdge = getEdgePoint(sourceCenter, dir)
                        val endEdge = getEdgePoint(targetCenter, Offset(-dir.x, -dir.y))

                        val edgeDelta = endEdge - startEdge
                        val edgeDist = kotlin.math.sqrt(edgeDelta.x * edgeDelta.x + edgeDelta.y * edgeDelta.y)
                        val controlDistance = edgeDist * 0.35f

                        val controlPoint1 = Offset(
                            startEdge.x + controlDistance,
                            startEdge.y
                        )
                        val controlPoint2 = Offset(
                            endEdge.x - controlDistance,
                            endEdge.y
                        )

                        val path = Path().apply {
                            moveTo(startEdge.x, startEdge.y)
                            cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                endEdge.x, endEdge.y
                            )
                        }

                        drawPath(
                            path = path,
                            color = referenceLineColor,
                            style = Stroke(
                                width = referenceLineWidth,
                                cap = StrokeCap.Round,
                                pathEffect = referenceDash
                            )
                        )

                        val angle = kotlin.math.atan2(endEdge.y - controlPoint2.y, endEdge.x - controlPoint2.x)
                        drawArrow(endEdge, angle, referenceLineColor, 16f)
                    }
                }

                // 绘制连接线（贝塞尔曲线）
                connections.forEach { connection ->
                    val sourcePos = nodePositions[connection.sourceNodeId]
                    val targetPos = nodePositions[connection.targetNodeId]

                    if (sourcePos != null && targetPos != null) {
                        val targetState = nodeExecutionStates[connection.targetNodeId]
                        val hasExecutionInfo = nodeExecutionStates.isNotEmpty()
                        val isTargetSkipped = targetState is NodeExecutionState.Skipped
                        val active = hasExecutionInfo && !isTargetSkipped && isConnectionActive(connection)

                        val activeColor =
                            when (targetState) {
                                is NodeExecutionState.Running -> Color(0xFF2196F3)
                                is NodeExecutionState.Failed -> Color(0xFFF44336)
                                is NodeExecutionState.Success -> Color(0xFF4CAF50)
                                else -> Color(0xFF4285F4)
                            }

                        val inactiveColor = Color(0xFFBDBDBD)
                        val lineColor =
                            if (!hasExecutionInfo) {
                                Color(0xFF4285F4)
                            } else if (active) {
                                activeColor
                            } else {
                                inactiveColor
                            }

                        val lineWidth = if (active) 3.5f else 2.5f
                        val dash = if (hasExecutionInfo && !active) PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) else null

                        // 计算节点中心点
                        val sourceCenterX = sourcePos.x + nodeWidthPx / 2
                        val sourceCenterY = sourcePos.y + nodeHeightPx / 2
                        val targetCenterX = targetPos.x + nodeWidthPx / 2
                        val targetCenterY = targetPos.y + nodeHeightPx / 2
                        
                        val dx = targetCenterX - sourceCenterX
                        val dy = targetCenterY - sourceCenterY
                        val centerDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                        
                        // 计算从源节点到目标节点的方向
                        val directionX = dx / centerDistance
                        val directionY = dy / centerDistance
                        
                        // 计算最近边缘点的函数
                        fun getEdgePoint(
                            centerX: Float, 
                            centerY: Float, 
                            dirX: Float, 
                            dirY: Float,
                            isSource: Boolean
                        ): Offset {
                            // 节点的半宽和半高
                            val halfWidth = nodeWidthPx / 2
                            val halfHeight = nodeHeightPx / 2
                            
                            // 判断方向主要在哪个方向
                            val absX = kotlin.math.abs(dirX)
                            val absY = kotlin.math.abs(dirY)
                            
                            // 计算与四条边的交点，选择最近的
                            val tRight = if (dirX > 0) halfWidth / absX else Float.POSITIVE_INFINITY
                            val tLeft = if (dirX < 0) halfWidth / absX else Float.POSITIVE_INFINITY
                            val tBottom = if (dirY > 0) halfHeight / absY else Float.POSITIVE_INFINITY
                            val tTop = if (dirY < 0) halfHeight / absY else Float.POSITIVE_INFINITY
                            
                            val t = minOf(tRight, tLeft, tBottom, tTop)
                            
                            return Offset(
                                centerX + dirX * t,
                                centerY + dirY * t
                            )
                        }
                        
                        // 计算起点和终点在边缘上
                        val startEdge = getEdgePoint(sourceCenterX, sourceCenterY, directionX, directionY, true)
                        val endEdge = getEdgePoint(targetCenterX, targetCenterY, -directionX, -directionY, false)
                        
                        val edgeDx = endEdge.x - startEdge.x
                        val edgeDy = endEdge.y - startEdge.y
                        val edgeDistance = kotlin.math.sqrt(edgeDx * edgeDx + edgeDy * edgeDy)

                        // 绘制贝塞尔曲线
                        val controlDistance = edgeDistance * 0.4f
                        
                        val controlPoint1 = Offset(
                            startEdge.x + controlDistance,
                            startEdge.y
                        )
                        val controlPoint2 = Offset(
                            endEdge.x - controlDistance,
                            endEdge.y
                        )
                        
                        val path = Path().apply {
                            moveTo(startEdge.x, startEdge.y)
                            cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                endEdge.x, endEdge.y
                            )
                        }
                        
                        // 绘制连接线阴影
                        drawPath(
                            path = path,
                            color = if (hasExecutionInfo && !active) Color(0x14000000) else Color(0x30000000),
                            style = Stroke(
                                width = lineWidth + 1.5f,
                                cap = StrokeCap.Round,
                                pathEffect = dash
                            )
                        )
                        
                        // 绘制连接线主体
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(
                                width = lineWidth,
                                cap = StrokeCap.Round,
                                pathEffect = dash
                            )
                        )
                        
                        // 计算贝塞尔曲线上某一点的位置和切线方向的辅助函数
                        fun getBezierPointAndTangent(t: Float): Pair<Offset, Float> {
                            // 三次贝塞尔曲线公式
                            val oneMinusT = 1 - t
                            val oneMinusT2 = oneMinusT * oneMinusT
                            val oneMinusT3 = oneMinusT2 * oneMinusT
                            val t2 = t * t
                            val t3 = t2 * t
                            
                            // 位置
                            val x = oneMinusT3 * startEdge.x + 
                                    3 * oneMinusT2 * t * controlPoint1.x + 
                                    3 * oneMinusT * t2 * controlPoint2.x + 
                                    t3 * endEdge.x
                            val y = oneMinusT3 * startEdge.y + 
                                    3 * oneMinusT2 * t * controlPoint1.y + 
                                    3 * oneMinusT * t2 * controlPoint2.y + 
                                    t3 * endEdge.y
                            
                            // 切线（导数）
                            val tangentX = -3 * oneMinusT2 * startEdge.x + 
                                           3 * oneMinusT2 * controlPoint1.x - 
                                           6 * oneMinusT * t * controlPoint1.x + 
                                           6 * oneMinusT * t * controlPoint2.x - 
                                           3 * t2 * controlPoint2.x + 
                                           3 * t2 * endEdge.x
                            val tangentY = -3 * oneMinusT2 * startEdge.y + 
                                           3 * oneMinusT2 * controlPoint1.y - 
                                           6 * oneMinusT * t * controlPoint1.y + 
                                           6 * oneMinusT * t * controlPoint2.y - 
                                           3 * t2 * controlPoint2.y + 
                                           3 * t2 * endEdge.y
                            
                            val angle = kotlin.math.atan2(tangentY, tangentX)
                            return Pair(Offset(x, y), angle)
                        }
                        
                        // 绘制箭头的辅助函数
                        fun drawArrow(position: Offset, angle: Float, size: Float = 12f) {
                            val arrowAngle = Math.PI / 6 // 30度角
                            
                            val arrowPath = Path().apply {
                                // 箭头顶点
                                moveTo(position.x, position.y)
                                // 箭头左边
                                lineTo(
                                    position.x - size * kotlin.math.cos(angle - arrowAngle).toFloat(),
                                    position.y - size * kotlin.math.sin(angle - arrowAngle).toFloat()
                                )
                                // 箭头右边
                                lineTo(
                                    position.x - size * kotlin.math.cos(angle + arrowAngle).toFloat(),
                                    position.y - size * kotlin.math.sin(angle + arrowAngle).toFloat()
                                )
                                // 闭合路径
                                close()
                            }
                            
                            // 绘制箭头阴影
                            drawPath(
                                path = arrowPath,
                                color = if (hasExecutionInfo && !active) Color(0x24000000) else Color(0x40000000)
                            )
                            
                            // 绘制实心箭头
                            drawPath(
                                path = arrowPath,
                                color = lineColor
                            )
                        }
                        
                        // 在曲线中点绘制箭头（增大尺寸使其更明显）
                        val (midPoint, midAngle) = getBezierPointAndTangent(0.5f)
                        val label = connectionLabelText(connection)
                        if (label != null) {
                            val paddingX = 14f
                            val paddingY = 10f
                            val textWidth = labelTextPaint.measureText(label)
                            val rectWidth = textWidth + paddingX * 2
                            val rectHeight = labelTextSize + paddingY * 2
                            val rectTopLeft = Offset(midPoint.x - rectWidth / 2, midPoint.y - rectHeight / 2)

                            drawRoundRect(
                                color = Color(0xE0FFFFFF),
                                topLeft = rectTopLeft,
                                size = Size(rectWidth, rectHeight),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            drawRoundRect(
                                color = lineColor.copy(alpha = 0.9f),
                                topLeft = rectTopLeft,
                                size = Size(rectWidth, rectHeight),
                                cornerRadius = CornerRadius(12f, 12f),
                                style = Stroke(width = 2.5f)
                            )

                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                rectTopLeft.x + paddingX,
                                rectTopLeft.y + paddingY + labelTextSize * 0.85f,
                                labelTextPaint
                            )
                        }

                        drawArrow(midPoint, midAngle, 18f)
                        
                        // 在曲线终点绘制箭头
                        val (endPoint, endAngle) = getBezierPointAndTangent(1.0f)
                        drawArrow(endPoint, endAngle, 18f)
                    }
                }
            }
        
            // 放置节点卡片
            nodes.forEach { node ->
                val position = nodePositions[node.id] ?: Offset.Zero
                val displayPosition = if (node.id == draggingNodeId) {
                    position + dragOffset
                } else {
                    position
                }
                
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                displayPosition.x.roundToInt(),
                                displayPosition.y.roundToInt()
                            )
                        }
                ) {
                    DraggableNodeCard(
                        node = node,
                        isDragging = node.id == draggingNodeId,
                        executionState = nodeExecutionStates[node.id],
                        onDragStart = {
                            draggingNodeId = node.id
                        },
                        onDrag = { amount ->
                            if (node.id == draggingNodeId) {
                                // 根据缩放比例调整拖动量
                                dragOffset += amount / scale
                            }
                        },
                        onDragEnd = {
                            draggingNodeId?.let { nodeId ->
                                val startPosition = nodePositions[nodeId] ?: Offset.Zero
                                val finalPosition = startPosition + dragOffset
                                
                                // --- 吸附逻辑：吸附左上角 ---
                                val snappedX = (finalPosition.x / cellSizePx).roundToInt() * cellSizePx
                                val snappedY = (finalPosition.y / cellSizePx).roundToInt() * cellSizePx
                                
                                val finalX = snappedX.toFloat()
                                val finalY = snappedY.toFloat()
                                
                                nodePositions[nodeId] = Offset(finalX, finalY)
                                onNodePositionChanged(nodeId, finalX, finalY)
                            }
                            
                            // 重置拖动状态
                            draggingNodeId = null
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            // 重置拖动状态
                            draggingNodeId = null
                            dragOffset = Offset.Zero
                        },
                        onLongPress = {
                            onNodeLongPress(node.id)
                        },
                        onClick = {
                            onNodeClick(node.id)
                        }
                    )
                }
            }
        }
        
        if (nodes.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.workflow_empty_start_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFE5E7EB)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.workflow_empty_start_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA6AE)
                )
            }
        }

        // 缩放指示器
        if (scale != 1f || panOffset != Offset.Zero) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xE61F2A30)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = Color(0xFF9EDCFF)
                    )
                )
            }
        }
    }
}

