package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.copy

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor

/** 复制预览独立持有文本选区和滚动位置，焦点或布局更新不重新创建字符串版编辑状态。 */
@Composable
internal fun MessageCopyTextField(text: String, modifier: Modifier = Modifier) {
    // 正文由消息源重新提供，状态包只保存选区，避免长消息重复占用系统保存状态的容量。
    val selectionSaver = remember(text) {
        Saver<TextFieldState, List<Int>>(
            save = { listOf(it.selection.start, it.selection.end) },
            restore = { TextFieldState(initialText = text, initialSelection = TextRange(it[0], it[1])) },
        )
    }
    val editor = rememberSaveable(text, saver = selectionSaver) {
        TextFieldState(initialText = text)
    }
    val scroll = rememberSaveable(text, saver = ScrollState.Saver) { ScrollState(0) }
    BasicTextField(
        state = editor,
        scrollState = scroll,
        readOnly = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
    )
}
