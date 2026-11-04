package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** 
 * 错误弹窗组件，完整显示错误消息内容，包括堆栈跟踪
 * 不做任何简化或截取处理，保持错误消息的原始格式
 */
@Composable
fun ErrorDialog(
        errorMessage: String,
        onDismiss: () -> Unit,
        properties: DialogProperties =
                DialogProperties(
                    dismissOnBackPress = true, 
                    dismissOnClickOutside = true
                )
) {
    // 创建滚动状态
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val displayMessage = errorMessage.ifBlank { stringResource(R.string.unknown_error) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.request_failed)) },
            text = {
                Box(
                        modifier =
                                Modifier.padding(vertical = 8.dp)
                                        .heightIn(max = 350.dp) // 恢复适当的最大高度
                                        .verticalScroll(scrollState) // 添加垂直滚动功能
                ) {
                    SelectionContainer {
                        Text(
                                text = displayMessage,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace // 保留等宽字体，便于阅读堆栈信息
                                ),
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(displayMessage)) }
                ) {
                    Text(stringResource(android.R.string.copy))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            properties = properties
    )
}
