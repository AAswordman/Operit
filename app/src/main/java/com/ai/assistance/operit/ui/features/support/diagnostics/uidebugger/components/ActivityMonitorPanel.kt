package com.ai.assistance.operit.ui.features.support.diagnostics.uidebugger.components

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.ui.theme.script.text.ThemeBasicTextField as BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun ActivityMonitorPanel(
    isListening: Boolean,
    events: List<ActionListener.ActionEvent>,
    currentActivityName: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearEvents: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val TAG = "ActivityMonitorPanel"
    val context = LocalContext.current

    // 焦点管理
    val focusRequester = remember { FocusRequester() }
    val inputMethodManager = remember {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    var hiddenInputText by remember { mutableStateOf("") }
    var hasFocus by remember { mutableStateOf(false) }

    // 监听状态变化时管理焦点
    LaunchedEffect(isListening) {
        if (isListening) {
            // 开始监听时获取焦点
            try {
                focusRequester.requestFocus()
                hasFocus = true
                AppLogger.d(TAG, "Activity监听启动时已获取输入法焦点")
            } catch (e: Exception) {
                AppLogger.w(TAG, "请求焦点失败: ${e.message}")
                hasFocus = false
            }
        } else {
            // 停止监听时释放焦点
            hasFocus = false
            AppLogger.d(TAG, "Activity监听停止时释放输入法焦点")
        }
    }

    // 监控状态变化
    LaunchedEffect(isListening, events.size) {
        AppLogger.d(TAG, "面板状态更新: isListening=$isListening, events.size=${events.size}")
    }
    Surface(
        modifier = modifier
            .widthIn(min = 300.dp, max = 400.dp)
            .heightIn(max = 500.dp),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp
    ) {
        Box {
            // 隐藏的输入框用于获取焦点，参考FloatingFullscreenMode的实现
            BasicTextField(
                value = hiddenInputText,
                onValueChange = { hiddenInputText = it },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .size(1.dp) // 最小尺寸，几乎不可见
                    .absoluteOffset(x = (-1000).dp, y = (-1000).dp), // 移到屏幕外
                textStyle = androidx.compose.ui.text.TextStyle(color = Transparent)
            )

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.uidebugger_activity_monitor),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.activity_monitor_close)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 状态指示器
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isListening)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (isListening)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isListening) stringResource(R.string.uidebugger_activity_monitor_listening)
                            else stringResource(R.string.uidebugger_activity_monitor_stopped),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isListening)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 控制按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isListening) {
                        Button(
                            onClick = onStopListening,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.uidebugger_stop_monitoring))
                        }
                    } else {
                        Button(
                            onClick = onStartListening,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.uidebugger_start_monitoring))
                        }
                    }

                    OutlinedButton(
                        onClick = onClearEvents,
                        enabled = events.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.uidebugger_clear))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 当前活动显示
                if (currentActivityName != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.uidebugger_activity_monitor_current_activity),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = currentActivityName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 事件列表
                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isListening) stringResource(R.string.uidebugger_activity_monitor_waiting)
                            else stringResource(R.string.uidebugger_activity_monitor_click_to_start),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 添加调试信息显示
                    if (isListening) {
                        LaunchedEffect(Unit) {
                            AppLogger.d(TAG, "面板显示空事件列表，但监听状态为true")
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.uidebugger_activity_monitor_event_log, events.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(events.reversed()) { event -> // 最新的事件在上面
                            ActivityEventItem(event = event)
                        }
                    }

                    // 记录事件渲染
                    LaunchedEffect(events.size) {
                        AppLogger.d(TAG, "渲染事件列表，共${events.size}个事件")
                        if (events.isNotEmpty()) {
                            val latestEvent = events.last()
                            AppLogger.d(
                                TAG,
                                "最新事件: ${latestEvent.actionType} - ${latestEvent.elementInfo?.packageName}"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityEventItem(
    event: ActionListener.ActionEvent
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = getEventColor(event.actionType)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getEventTypeName(event.actionType),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (event.elementInfo != null) {
                Spacer(modifier = Modifier.height(4.dp))

                // 显示活动名称
                if (event.elementInfo.className != null) {
                    Text(
                        text = stringResource(R.string.uidebugger_activity_monitor_item_activity, event.elementInfo.className),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 显示元素信息
                Text(
                    text = buildString {
                        event.elementInfo.text?.let {
                            append(context.getString(R.string.uidebugger_activity_monitor_item_text, it) + " ")
                        }
                        event.elementInfo.resourceId?.let { append("ID: $it ") }
                        event.elementInfo.packageName?.let {
                            append(context.getString(R.string.uidebugger_activity_monitor_item_package, it))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (event.additionalData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.additionalData.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getEventColor(actionType: ActionListener.ActionType): androidx.compose.ui.graphics.Color {
    return when (actionType) {
        ActionListener.ActionType.CLICK -> MaterialTheme.colorScheme.primaryContainer
        ActionListener.ActionType.APP_SWITCH -> MaterialTheme.colorScheme.secondaryContainer
        ActionListener.ActionType.SCREEN_CHANGE -> MaterialTheme.colorScheme.tertiaryContainer
        ActionListener.ActionType.TEXT_INPUT -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun getEventTypeName(actionType: ActionListener.ActionType): String {
    return when (actionType) {
        ActionListener.ActionType.CLICK -> stringResource(R.string.uidebugger_activity_monitor_item_click)
        ActionListener.ActionType.LONG_CLICK -> stringResource(R.string.uidebugger_activity_monitor_item_long_click)
        ActionListener.ActionType.SWIPE -> stringResource(R.string.uidebugger_activity_monitor_item_swipe)
        ActionListener.ActionType.TEXT_INPUT -> stringResource(R.string.uidebugger_activity_monitor_item_text_input)
        ActionListener.ActionType.KEY_PRESS -> stringResource(R.string.uidebugger_activity_monitor_item_key_press)
        ActionListener.ActionType.SCROLL -> stringResource(R.string.uidebugger_activity_monitor_item_scroll)
        ActionListener.ActionType.GESTURE -> stringResource(R.string.uidebugger_activity_monitor_item_gesture)
        ActionListener.ActionType.APP_SWITCH -> stringResource(R.string.uidebugger_activity_monitor_item_app_switch)
        ActionListener.ActionType.SCREEN_CHANGE -> stringResource(R.string.uidebugger_activity_monitor_item_screen_change)
        ActionListener.ActionType.SYSTEM_EVENT -> stringResource(R.string.uidebugger_activity_monitor_item_system_event)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}