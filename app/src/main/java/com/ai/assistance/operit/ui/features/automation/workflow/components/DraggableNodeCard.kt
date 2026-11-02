package com.ai.assistance.operit.ui.features.automation.workflow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.core.workflow.model.WorkflowNode
import com.ai.assistance.operit.core.workflow.model.TriggerNode
import com.ai.assistance.operit.core.workflow.model.ExecuteNode
import com.ai.assistance.operit.core.workflow.model.ConditionNode
import com.ai.assistance.operit.core.workflow.model.LogicNode
import com.ai.assistance.operit.core.workflow.model.ExtractNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可拖动的节点卡片组件
 * 支持拖动、长按和点击手势
 */
@Composable
fun DraggableNodeCard(
    node: WorkflowNode,
    isDragging: Boolean,
    executionState: NodeExecutionState? = null,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 根据执行状态选择边框颜色
    val executionBorderColor = when (executionState) {
        is NodeExecutionState.Running -> Color(0xFF2196F3) // 蓝色
        is NodeExecutionState.Success -> Color(0xFF4CAF50) // 绿色
        is NodeExecutionState.Skipped -> Color(0xFF9E9E9E) // 灰色
        is NodeExecutionState.Failed -> Color(0xFFF44336) // 红色
        else -> null
    }
    
    // 根据节点类型选择颜色和图标
    val nodeStyle = when (node) {
        is TriggerNode -> NodeStyle(
            primaryColor = Color(0xFF4CAF50),
            backgroundColor = Color(0xFFE8F5E9),
            borderColor = Color(0xFF81C784),
            icon = Icons.Default.PlayArrow,
            label = stringResource(R.string.workflow_node_label_trigger)
        )
        is ExecuteNode -> NodeStyle(
            primaryColor = Color(0xFF2196F3),
            backgroundColor = Color(0xFFE3F2FD),
            borderColor = Color(0xFF64B5F6),
            icon = Icons.Default.Settings,
            label = stringResource(R.string.workflow_node_label_execute)
        )
        is ConditionNode -> NodeStyle(
            primaryColor = Color(0xFFFF9800),
            backgroundColor = Color(0xFFFFF3E0),
            borderColor = Color(0xFFFFB74D),
            icon = Icons.Default.Settings,
            label = stringResource(R.string.workflow_node_label_condition)
        )
        is LogicNode -> NodeStyle(
            primaryColor = Color(0xFF7E57C2),
            backgroundColor = Color(0xFFF3E5F5),
            borderColor = Color(0xFFB39DDB),
            icon = Icons.Default.Settings,
            label = stringResource(R.string.workflow_node_label_logic)
        )
        is ExtractNode -> NodeStyle(
            primaryColor = Color(0xFF009688),
            backgroundColor = Color(0xFFE0F2F1),
            borderColor = Color(0xFF4DB6AC),
            icon = Icons.Default.Settings,
            label = stringResource(R.string.workflow_node_label_extract)
        )
        else -> NodeStyle(
            primaryColor = Color(0xFF9E9E9E),
            backgroundColor = Color(0xFFF5F5F5),
            borderColor = Color(0xFFBDBDBD),
            icon = Icons.Default.Settings,
            label = stringResource(R.string.workflow_node_label_unknown)
        )
    }
    
    var hasDragged by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .width(120.dp)
            .height(80.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var longPressJob: kotlinx.coroutines.Job? = null
                    var isLongPressed = false
                    
                    detectTapGestures(
                        onPress = {
                            isLongPressed = false
                            hasDragged = false
                            
                            // 启动长按检测
                            longPressJob = coroutineScope.launch {
                                delay(500)
                                if (!hasDragged) {  // 只有没有拖动时才触发长按
                                    isLongPressed = true
                                    onLongPress()
                                }
                            }
                            
                            tryAwaitRelease()
                            longPressJob?.cancel()
                            
                            // 只有在没有长按、没有拖动、且不在拖动状态时才触发点击
                            if (!isLongPressed && !hasDragged && !isDragging) {
                                onClick()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            hasDragged = true
                            onDragStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = {
                            onDragEnd()
                            // 延迟重置拖动标志，避免拖动结束时误触发点击
                            coroutineScope.launch {
                                delay(100)
                                hasDragged = false
                            }
                        },
                        onDragCancel = {
                            onDragCancel()
                            hasDragged = false
                        }
                    )
                }
                .border(
                    width = if (executionBorderColor != null) 3.dp else 2.dp,
                    color = executionBorderColor ?: (if (isDragging) nodeStyle.primaryColor else nodeStyle.borderColor),
                    shape = RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isDragging) 12.dp else 3.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isDragging) 
                    nodeStyle.backgroundColor.copy(alpha = 0.8f) 
                else 
                    Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部：类型标签
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = nodeStyle.primaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = nodeStyle.icon,
                            contentDescription = null,
                            tint = nodeStyle.primaryColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = nodeStyle.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = nodeStyle.primaryColor
                        )
                    }
                }
                
                // 中间：节点名称
                Text(
                    text = node.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF212121),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
                
                // 底部：描述或执行状态
                if (executionState != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (executionState) {
                            is NodeExecutionState.Running -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF2196F3)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.workflow_node_running),
                                    fontSize = 9.sp,
                                    color = Color(0xFF2196F3),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            is NodeExecutionState.Success -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.workflow_node_success),
                                    fontSize = 9.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            is NodeExecutionState.Skipped -> {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFF9E9E9E),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.workflow_node_skipped),
                                    fontSize = 9.sp,
                                    color = Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            is NodeExecutionState.Failed -> {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.workflow_node_failed),
                                    fontSize = 9.sp,
                                    color = Color(0xFFF44336),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            else -> {}
                        }
                    }
                } else if (node.description.isNotEmpty()) {
                    Text(
                        text = node.description,
                        fontSize = 9.sp,
                        color = Color(0xFF757575),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 节点样式数据类
 */
private data class NodeStyle(
    val primaryColor: Color,
    val backgroundColor: Color,
    val borderColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)

