package com.ai.assistance.operit.ui.features.conversation.chat.modules.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 侧栏与聊天选择器使用相同的归档入口，归档为空时也能明确返回常规列表。 */
@Composable
internal fun ChatArchiveFilter(
    archived: Boolean,
    archivedCount: Int,
    onChange: (Boolean) -> Unit,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
) {
    val colors = FilterChipDefaults.filterChipColors(
        labelColor = contentColor,
        selectedContainerColor = selectedContainerColor,
        selectedLabelColor = selectedContentColor,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !archived,
            onClick = { onChange(false) },
            label = { Text(stringResource(R.string.chat_history), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = archived,
            onClick = { onChange(true) },
            label = { Text(stringResource(R.string.chat_archived_count, archivedCount), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
    }
}