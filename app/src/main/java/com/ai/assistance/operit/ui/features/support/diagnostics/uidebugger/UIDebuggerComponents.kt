package com.ai.assistance.operit.ui.features.support.diagnostics.uidebugger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelStoreOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.support.diagnostics.uidebugger.components.ActivityMonitorPanel


@Composable
fun UIDebuggerOverlay(
    viewModelStoreOwner: ViewModelStoreOwner,
    onClose: () -> Unit,
    onMinimize: (() -> Unit)? = null
) {
    val viewModel: UIDebuggerViewModel = UIDebuggerViewModel.getInstance()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // 简化状态管理 - 只管理UI分析和元素选择
    var isUIAnalysisActive by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<UIElement?>(null) }
    var showAnalysisPanel by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // 注释掉自动停止监听的逻辑，让监听保持运行
    // 用户可以通过手动点击停止按钮来停止监听
    // DisposableEffect(Unit) {
    //     onDispose {
    //         try {
    //             if (uiState.isActivityListening) {
    //                 viewModel.stopActivityListening()
    //             }
    //         } catch (e: Exception) {
    //             // 忽略清理时的异常
    //         }
    //     }
    // }

    Box(modifier = Modifier.fillMaxSize()) {
        // 元素列表放在局部面板内，底层应用仍可接收普通触摸事件。
        if (isUIAnalysisActive && selectedElement == null) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                    .heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.elements.forEach { element ->
                    Surface(onClick = {
                        selectedElement = element
                        showAnalysisPanel = false
                    }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                            Text(element.className, style = MaterialTheme.typography.labelSmall)
                            if (element.text.isNotBlank()) {
                                Text(element.text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // 选中元素信息面板
        if (selectedElement != null && isUIAnalysisActive) {
            ElementInfoPanel(
                element = selectedElement!!,
                onDismiss = {
                    selectedElement = null
                    showAnalysisPanel = true
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .zIndex(10f)
            )
        }

        // Activity监听面板
        if (uiState.showActivityMonitor) {
            ActivityMonitorPanel(
                isListening = uiState.isActivityListening,
                events = uiState.activityEvents,
                currentActivityName = uiState.currentActivityName,
                onStartListening = { viewModel.startActivityListening() },
                onStopListening = { viewModel.stopActivityListening() },
                onClearEvents = { viewModel.clearActivityEvents() },
                onDismiss = { 
                    viewModel.toggleActivityMonitor()
                    // 不再自动停止监听，让用户手动控制
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(20f)
            )
        }

        // 当前分析Activity信息面板
        if (isUIAnalysisActive && showAnalysisPanel && (uiState.currentAnalyzedActivityName != null || uiState.currentAnalyzedPackageName != null)) {
            val clipboardManager = LocalClipboardManager.current
            val currentActivityName = uiState.currentAnalyzedActivityName
            val currentPackageName = uiState.currentAnalyzedPackageName
            
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp)
                    .widthIn(max = 280.dp)
                    .zIndex(15f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.uidebugger_current_interface),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        // 复制按钮
                        if (currentActivityName != null) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(currentActivityName))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.uidebugger_copy_activity_name),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        // 关闭按钮
                        IconButton(
                            onClick = { showAnalysisPanel = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.uidebugger_close_panel),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    
                    if (currentActivityName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentActivityName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    if (currentPackageName != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.uidebugger_package_name, currentPackageName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // 紧凑的控制面板 - 右下角，4个小按钮
        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .zIndex(5f),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // UI分析按钮 - 更小的尺寸
                FloatingActionButton(
                    onClick = {
                        try {
                            if (isUIAnalysisActive) {
                                // 关闭UI分析
                                isUIAnalysisActive = false
                                selectedElement = null
                                showAnalysisPanel = false
                            } else {
                                // 开启UI分析
                                viewModel.refreshUI()
                                isUIAnalysisActive = true
                                showAnalysisPanel = true
                            }
                        } catch (e: Exception) {
                            // 处理异常
                        }
                    },
                    containerColor = if (isUIAnalysisActive) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = if (isUIAnalysisActive) stringResource(R.string.uidebugger_close_ui_analysis) else stringResource(R.string.uidebugger_ui_analysis),
                        tint = if (isUIAnalysisActive)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Activity监听按钮
                FloatingActionButton(
                    onClick = {
                        try {
                            if (uiState.showActivityMonitor) {
                                // 关闭监听面板，但不停止监听
                                viewModel.toggleActivityMonitor()
                            } else {
                                // 打开监听面板
                                viewModel.toggleActivityMonitor()
                            }
                        } catch (e: Exception) {
                            // 处理异常
                        }
                    },
                    containerColor = when {
                        uiState.showActivityMonitor -> MaterialTheme.colorScheme.primary
                        uiState.isActivityListening -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = when {
                            uiState.isActivityListening -> Icons.Default.VisibilityOff
                            else -> Icons.Default.Visibility
                        },
                        contentDescription = if (uiState.showActivityMonitor) stringResource(R.string.uidebugger_collapse_panel) else stringResource(R.string.uidebugger_activity_monitor),
                        tint = when {
                            uiState.showActivityMonitor -> MaterialTheme.colorScheme.onPrimary
                            uiState.isActivityListening -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 最小化按钮
                onMinimize?.let { minimizeCallback ->
                    FloatingActionButton(
                        onClick = minimizeCallback,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = stringResource(R.string.uidebugger_minimize),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 关闭按钮
                FloatingActionButton(
                    onClick = onClose,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.pkg_close),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 操作反馈提示
        if (uiState.showActionFeedback) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 100.dp)
                    .zIndex(25f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.actionFeedbackMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                }
            }
        }

        // 错误消息提示
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 100.dp)
                    .zIndex(25f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = error,
                        style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
    }
}

// 调试器模式枚举 - 简化版本
enum class DebuggerMode {
    CLOSED,           // 关闭状态
    UI_ANALYSIS,      // UI分析模式
    ACTIVITY_MONITOR  // Activity监听模式
}

@Composable
fun ElementHighlightOverlay(
    elements: List<UIElement>,
    onElementClick: (UIElement) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(elements) {
                detectTapGestures { offset ->
                    val tappedElement = elements
                        .filter { element ->
                            element.bounds?.let { bounds ->
                                offset.x >= bounds.left && 
                                offset.x <= bounds.right && 
                                offset.y >= bounds.top && 
                                offset.y <= bounds.bottom
                            } ?: false
                        }
                        .minByOrNull { element ->
                            element.bounds?.let { bounds ->
                                bounds.width() * bounds.height()
                            } ?: Int.MAX_VALUE
                        }
                    
                    tappedElement?.let(onElementClick)
                }
            }
    ) {
        elements
            .sortedByDescending { element ->
                element.bounds?.let { bounds ->
                    bounds.width() * bounds.height()
                } ?: 0
            }
            .forEach { element ->
                element.bounds?.let { bounds ->
                    drawElementHighlight(bounds)
                }
            }
    }
}

private fun DrawScope.drawElementHighlight(
    bounds: android.graphics.Rect
) {
    val color = Color.Red
    
    drawRect(
        color = color,
        topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
        size = Size(bounds.width().toFloat(), bounds.height().toFloat()),
        style = Stroke(width = 2.dp.toPx())
    )
}

@Composable
fun ElementInfoPanel(
    element: UIElement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    Surface(
        modifier = modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 400.dp),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.uidebugger_element_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.pkg_close)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = element.getTypeDescription(context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ActivityName特殊显示区域
            if (element.activityName != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.uidebugger_element_info_current_activity),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(element.activityName))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.uidebugger_copy_activity_name),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = element.activityName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (element.packageName != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.uidebugger_element_info_package_name, element.packageName),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false)
            ) {
                Text(
                    text = element.getFullDetails(context),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (element.bounds != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.uidebugger_element_info_size_px, element.bounds.width(), element.bounds.height()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 

 


// 编辑相关组件

@Composable
fun CreatePackageDialog(
    onCreatePackage: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.uidebugger_create_new_package))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text(stringResource(R.string.uidebugger_app_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(stringResource(R.string.uidebugger_package_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.uidebugger_description_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (appName.isNotBlank() && packageName.isNotBlank()) {
                        onCreatePackage(appName, packageName, description)
                        onDismiss()
                    }
                },
                enabled = appName.isNotBlank() && packageName.isNotBlank()
            ) {
                Text(stringResource(R.string.uidebugger_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.uidebugger_cancel2))
            }
        }
    )
}



