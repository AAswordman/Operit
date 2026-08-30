package com.ai.assistance.operit.ui.features.chat.components.style.cursor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage

/**
 * A composable function for rendering memory summarization messages as a compact divider. This
 * displays a slim divider with a summary indicator, which expands to show full content when
 * clicked.
 */
@Composable
fun SummaryMessageComposable(
    message: ChatMessage,
    onDelete: () -> Unit,
    enableDialog: Boolean = true,  // 新增参数：是否启用弹窗功能，默认启用
    onEdit: ((ChatMessage) -> Unit)? = null
) {
    val context = LocalContext.current
    // 记住展开状态
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // 创建一个不占空间的分隔符
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(enabled = enableDialog) {
                        // 仅在启用弹窗时才允许点击
                        if (enableDialog) showSummaryDialog = true
                    }
                    .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Box(
                modifier =
                    Modifier.background(
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = context.getString(R.string.history_dialog_summary),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = context.getString(R.string.history_dialog_summary),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                thickness = 1.dp
            )
        }
    }

    // 显示详细内容的对话框 - 仅在启用弹窗时显示
    if (showSummaryDialog && enableDialog) {
        Dialog(onDismissRequest = { showSummaryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp).widthIn(max = 480.dp)) {
                    Text(
                        text = context.getString(R.string.history_dialog_summary),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(
                        color =
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.2f
                            ),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 添加滚动功能到摘要内容
                    val scrollState = rememberScrollState()
                    Box(
                        modifier =
                            Modifier.weight(1f, fill = false)
                                .heightIn(max = 400.dp)
                                .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onEdit != null) {
                            TextButton(
                                onClick = {
                                    onEdit.invoke(message)
                                    showSummaryDialog = false
                                }
                            ) {
                                Text(context.getString(R.string.edit))
                            }
                        }
                        TextButton(
                            onClick = {
                                showSummaryDialog = false
                                showDeleteConfirmDialog = true
                            }
                        ) {
                            Text(context.getString(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = { showSummaryDialog = false },
                        ) { Text(context.getString(R.string.close)) }
                    }
                }
            }
        }
    }

    // 删除确认对话框 - 仅在启用弹窗时显示
    if (showDeleteConfirmDialog && enableDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(context.getString(R.string.confirm_delete_summary)) },
            text = { Text(context.getString(R.string.confirm_delete_summary_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(context.getString(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}
