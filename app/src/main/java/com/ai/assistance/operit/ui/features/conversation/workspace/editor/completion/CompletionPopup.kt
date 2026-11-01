package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.language.LanguageSupport
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.theme.EditorTheme

/**
 * 代码补全弹出组件
 */
@Composable
fun CompletionPopup(
    completionItems: List<CompletionItem>,
    theme: EditorTheme,
    onItemSelected: (CompletionItem) -> Unit,
    onDismissRequest: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier
) {
    if (completionItems.isEmpty()) return
    
    val listState = rememberLazyListState()
    
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = false, // 设置为false，避免抢占焦点
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false // 允许在父布局边界外绘制，防止截断
        ) 
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 12.dp,
            color = theme.gutterBackground,
            contentColor = theme.textColor,
            border = BorderStroke(1.dp, theme.gutterBorder),
            modifier = modifier
                .width(260.dp)
                .heightIn(max = 220.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(completionItems) { item ->
                    CompletionItemRow(item, theme, onItemSelected)
                }
            }
        }
    }
}

/**
 * 补全项行组件
 */
@Composable
fun CompletionItemRow(
    item: CompletionItem,
    theme: EditorTheme,
    onItemSelected: (CompletionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemSelected(item) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = when (item.kind) {
                CompletionItemKind.KEYWORD -> Icons.Default.Code
                CompletionItemKind.FUNCTION -> Icons.Default.Functions
                CompletionItemKind.METHOD -> Icons.Default.PlayArrow
                CompletionItemKind.VARIABLE -> Icons.Default.DataObject
                CompletionItemKind.CLASS -> Icons.Default.Class
                CompletionItemKind.PROPERTY -> Icons.Default.Label
                CompletionItemKind.SNIPPET -> Icons.Default.ContentCopy
                else -> Icons.Default.TextFields
            },
            contentDescription = null,
            tint = when (item.kind) {
                CompletionItemKind.KEYWORD -> Color(LanguageSupport.KEYWORD_COLOR)
                CompletionItemKind.FUNCTION, CompletionItemKind.METHOD -> Color(LanguageSupport.FUNCTION_COLOR)
                CompletionItemKind.VARIABLE -> Color(LanguageSupport.VARIABLE_COLOR)
                CompletionItemKind.CLASS -> Color(LanguageSupport.TYPE_COLOR)
                CompletionItemKind.PROPERTY -> Color(LanguageSupport.VARIABLE_COLOR)
                else -> theme.lineNumberColor
            },
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp)) // 减小间隔
        
        // 标签
        Column {
            Text(
                text = item.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textColor
            )
            
            // 详情信息
            if (item.detail != null) {
                Text(
                    text = item.detail,
                    fontSize = 12.sp,
                    color = theme.lineNumberColor
                )
            }
        }
    }
}
